package com.spectrum255.meteorosprotocol.machine;

import com.spectrum255.meteorosprotocol.MeteorosProtocol;
import com.spectrum255.meteorosprotocol.menu.IntakeMenu;
import com.spectrum255.meteorosprotocol.menu.MachineStatusMenu;
import com.spectrum255.meteorosprotocol.compat.WeatherAdjustedField;
import com.spectrum255.meteorosprotocol.item.CanisterItem;
import com.spectrum255.meteorosprotocol.item.FilterItem;
import com.spectrum255.meteorosprotocol.item.InfiniteParticleCanisterItem;
import com.spectrum255.meteorosprotocol.particle.*;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

/**
 * v2 S4 machine storage. All particle amounts are milli-PU (mPU, 1 PU = 1000
 * mPU); energy is FE.
 *
 * <p>The mixed buffer ({@link #mixed}) and the six phase tanks ({@link #phases})
 * are separate stores. The block entity is the mixed buffer for the
 * {@code MIXED_STORAGE} capability; the phase tanks are exposed through
 * {@link #phaseStorage()}. Recipes run every tick: 25 mPU intake, a 25 mPU
 * separator input with a 0.95 carry, and 25 T -> 25 C generation.</p>
 */
public class MachineBlockEntity extends BlockEntity implements MixedStorage, MenuProvider, IEnergyStorage {
    /** Per-tick intake volume. */
    public static final long INTAKE_MPU_PER_TICK = 25L;
    public static final long INTAKE_FE_PER_TICK = 15L;

    /** Per-tick separator cap; output is 95% of the actual input. */
    public static final long SEPARATOR_MAX_INPUT_MPU_PER_TICK = 25L;
    public static final long SEPARATOR_FE_PER_TICK = 25L;

    public static final long GENERATOR_INPUT_MPU_PER_TICK = 25L;
    public static final long GENERATOR_OUTPUT_MPU_PER_TICK = 25L;
    public static final long GENERATOR_FE_PER_TICK = 50L;
    /** How long the generator's "active" back texture stays on after a successful tick. */
    private static final int GENERATOR_ACTIVE_HOLD_TICKS = 40;

    /** Mixed pipe transfer budget per active pipe tick (every 5 ticks). */
    public static final long PIPE_MPU_PER_TRANSFER = 20L;
    private static final long PIPE_INTERVAL = 5L;

    /** Shared total capacity of the mixed buffer (1,000 PU). */
    public static final long MIXED_CAPACITY = 1_000_000L;
    /** Independent capacity of each phase tank (8,000 PU). */
    public static final long PHASE_CAPACITY = 8_000_000L;
    private static final long ENERGY_CAPACITY = 100_000L;

    private final long[] mixed = new long[Phase.all().size()];
    private final long[] phases = new long[Phase.all().size()];
    private final PhaseStorage phaseStorage = new PhaseStorage() {
        @Override
        public long getAmount(Phase phase) {
            return phases[phase.index()];
        }

        @Override
        public long getCapacity(Phase phase) {
            return phase.tankCapacity();
        }

        @Override
        public long insert(Phase phase, long amount, boolean simulate) {
            long moved = phaseAccepted(phase, amount, phases[phase.index()]);
            if (!simulate && moved > 0L) {
                phases[phase.index()] += moved;
                setChanged();
            }
            return moved;
        }

        @Override
        public long extract(Phase phase, long amount, boolean simulate) {
            long moved = Math.min(Math.max(0L, amount), phases[phase.index()]);
            if (!simulate && moved > 0L) {
                phases[phase.index()] -= moved;
                setChanged();
            }
            return moved;
        }
    };

    private long energy;
    private long progress;
    /** Remaining ticks of the generator's "active" visual; not persisted (blockstate corrects itself). */
    private int activeHold;
    /** Sub-mPU separator remainder, 0-99; persisted so output averages 95%. */
    private long separatorCarry;
    private long lastSeparatorLoss;
    private boolean separatorInsertWarningLogged;
    /** Last composition bitmask sent to clients; MIN_VALUE means never sent. */
    private long lastCompositionSignature = Long.MIN_VALUE;

    /**
     * Slot 0 is the filter slot (no filter item exists yet, so nothing may be
     * placed); slot 1 is the canister loading slot.
     */
    private final SimpleContainer items = new SimpleContainer(2) {
        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return slot == 0
                    ? stack.is(MeteorosProtocol.FILTER_CARTRIDGE.get())
                    : slot == 1 && (stack.is(MeteorosProtocol.CANISTER.get())
                            || (stack.is(MeteorosProtocol.INFINITE_PARTICLE_CANISTER.get())
                                    && !InfiniteParticleCanisterItem.hasPhase(stack)));
        }
    };

    private boolean enabled = true;
    private RedstoneMode redstoneMode = RedstoneMode.ALWAYS_ON;
    /** Phase the intake tower will pump into the loading slot's canister. */
    private Phase canisterPhase = Phase.AETHERON;

    /** Extract-only view used by the intake tower's output port. */
    private final MixedStorage extractOnly = new MixedStorage() {
        @Override
        public long getAmount(Phase phase) {
            return mixed[phase.index()];
        }

        @Override
        public long getCapacity(Phase phase) {
            return MIXED_CAPACITY;
        }

        @Override
        public long insert(Phase phase, long amount, boolean simulate) {
            return 0L;
        }

        @Override
        public long extract(Phase phase, long amount, boolean simulate) {
            return MachineBlockEntity.this.extract(phase, amount, simulate);
        }
    };

    public enum RedstoneMode {
        /** Run only while a redstone signal reaches the block. */
        REDSTONE_CONTROL,
        /** Ignore redstone and keep running. */
        ALWAYS_ON
    }

    public MachineBlockEntity(BlockPos pos, BlockState state) {
        super(MeteorosProtocol.MACHINE_BLOCK_ENTITY.get(), pos, state);
        this.items.addListener(container -> this.setChanged());
    }

    public static void serverTick(ServerLevel level, BlockPos pos, BlockState state, MachineBlockEntity machine) {
        Block block = state.getBlock();
        if (block == MeteorosProtocol.PARTICLE_INTAKE.get()) {
            machine.tickIntake(level, pos);
        } else if (block == MeteorosProtocol.PARTICLE_PIPE.get()) {
            machine.tickPipe(level, pos);
        } else if (block == MeteorosProtocol.PHASE_SEPARATOR.get()) {
            machine.tickSeparator();
        } else if (block == MeteorosProtocol.THERMAL_GENERATOR.get()) {
            boolean worked = machine.tickGenerator();
            if (worked) {
                machine.activeHold = GENERATOR_ACTIVE_HOLD_TICKS;
            } else if (machine.activeHold > 0) {
                machine.activeHold--;
            }
            // Drive the idle/active back texture pair through the block state.
            // Only send an update on the actual transition, and never use
            // setBlockAndUpdate so a machine ticking does not fire neighbour
            // updates every tick.
            boolean active = machine.activeHold > 0;
            if (state.hasProperty(MachineBlock.ACTIVE) && state.getValue(MachineBlock.ACTIVE) != active) {
                level.setBlock(pos, state.setValue(MachineBlock.ACTIVE, active), Block.UPDATE_CLIENTS);
            }
        }
        machine.progress++;
        if ((machine.progress & 19) == 0) {
            machine.setChanged();
        }
        // The renderer only needs the composition, so a 4-bit-per-phase mask is
        // enough. Send once on the first tick and then every 10 ticks, but only
        // when the quantised composition actually changed.
        if (machine.lastCompositionSignature == Long.MIN_VALUE
                || machine.progress % 10L == 0L) {
            machine.syncComposition(level, pos, state);
        }
    }

    private void tickIntake(ServerLevel level, BlockPos pos) {
        if (!isActive()) {
            return;
        }
        long remaining = MIXED_CAPACITY - getTotal();
        if (energy < INTAKE_FE_PER_TICK || remaining <= 0L) {
            return;
        }
        FieldSample sample = WeatherAdjustedField.sampleAt(level, pos);
        if (sample.totalDensity() <= 0.0F) {
            return;
        }
        // Fixed per-tick volume of the local mixture; the field's absolute
        // concentrations only decide its composition. A filter cartridge
        // removes unselected phases and lowers throughput by the selected
        // share (the docs' 2 PU/s * selected share, rescaled to 25 mPU/t).
        float[] shares = sample.composition();
        long volume = Math.min(INTAKE_MPU_PER_TICK, remaining);
        ItemStack filterStack = items.getItem(0);
        if (filterStack.getItem() instanceof FilterItem) {
            long[] mask = FilterItem.getMask(filterStack);
            float selectedShare = 0.0F;
            for (Phase phase : Phase.all()) {
                int word = phase.index() / Long.SIZE;
                boolean selected = word < mask.length
                        && (mask[word] & (1L << (phase.index() % Long.SIZE))) != 0L;
                if (selected) {
                    selectedShare += shares[phase.index()];
                } else {
                    shares[phase.index()] = 0.0F;
                }
            }
            if (selectedShare <= 0.0F) {
                return;
            }
            volume = Math.round(volume * selectedShare / 100.0F);
            if (volume <= 0L) {
                return;
            }
        }
        long[] input = ParticleMath.amountsFromShares(volume, shares);
        long moved = 0L;
        for (Phase phase : Phase.all()) {
            moved += insert(phase, input[phase.index()], false);
        }
        if (moved > 0L) {
            energy -= INTAKE_FE_PER_TICK;
        }
    }

    /**
     * Minimal particle pipe: every fifth tick, push its mixed buffer into an
     * adjacent mixed storage, then push the phase tanks into an adjacent
     * {@code PHASE_STORAGE}. When empty it pulls from neighbours instead. The
     * intake tower's west face is an extract-only {@code MIXED_STORAGE}, so a
     * pipe placed on that face can drain the tower through the modelled
     * SidePort.
     */
    private void tickPipe(ServerLevel level, BlockPos pos) {
        if (progress % PIPE_INTERVAL != 0L) {
            return;
        }
        BlockState pipeState = level.getBlockState(pos);
        if (!(pipeState.getBlock() instanceof ParticlePipeBlock)) {
            return;
        }
        if (getTotal() > 0L) {
            for (Direction direction : Direction.values()) {
                if (!ParticlePipeBlock.isConnected(pipeState, direction)) {
                    continue;
                }
                MixedStorage target = level.getCapability(
                        ParticleCapabilities.MIXED_STORAGE,
                        pos.relative(direction),
                        direction.getOpposite()
                );
                if (target != null && target != this) {
                    if (pushPipeContents(target) > 0L) {
                        return;
                    }
                }
            }
        }
        if (!phaseStorage.isEmpty()) {
            for (Direction direction : Direction.values()) {
                if (!ParticlePipeBlock.isConnected(pipeState, direction)) {
                    continue;
                }
                PhaseStorage target = level.getCapability(
                        ParticleCapabilities.PHASE_STORAGE,
                        pos.relative(direction)
                );
                if (target != null && target != phaseStorage) {
                    if (pushPhaseContents(target) > 0L) {
                        return;
                    }
                }
            }
        }

        if (getTotal() > 0L) {
            return;
        }
        for (Direction direction : Direction.values()) {
            if (!ParticlePipeBlock.isConnected(pipeState, direction)) {
                continue;
            }
            BlockPos sourcePos = pos.relative(direction);
            if (level.getBlockState(sourcePos).is(MeteorosProtocol.PARTICLE_PIPE.get())) {
                continue;
            }
            MixedStorage source = level.getCapability(
                    ParticleCapabilities.MIXED_STORAGE,
                    sourcePos,
                    direction.getOpposite()
            );
            if (source != null) {
                if (pullPipeContents(source) > 0L) {
                    return;
                }
            }
        }

        if (!phaseStorage.isEmpty()) {
            return;
        }
        for (Direction direction : Direction.values()) {
            if (!ParticlePipeBlock.isConnected(pipeState, direction)) {
                continue;
            }
            BlockPos sourcePos = pos.relative(direction);
            if (level.getBlockState(sourcePos).is(MeteorosProtocol.PARTICLE_PIPE.get())) {
                continue;
            }
            PhaseStorage source = level.getCapability(
                    ParticleCapabilities.PHASE_STORAGE,
                    sourcePos
            );
            if (source != null && source != phaseStorage) {
                if (pullPhaseContents(source) > 0L) {
                    return;
                }
            }
        }
    }

    private long pushPipeContents(MixedStorage target) {
        long budget = Math.min(PIPE_MPU_PER_TRANSFER, getTotal());
        if (budget <= 0L) {
            return 0L;
        }
        long[] planned = ParticleMath.amountsFromShares(budget, shares());
        long moved = 0L;
        for (Phase phase : Phase.all()) {
            long want = Math.min(planned[phase.index()], mixed[phase.index()]);
            if (want <= 0L) {
                continue;
            }
            long extracted = extract(phase, want, false);
            long accepted = target.insert(phase, extracted, false);
            if (accepted < extracted) {
                // Put back whatever the destination could not take.
                insert(phase, extracted - accepted, false);
            }
            moved += accepted;
        }
        return moved;
    }

    private long pullPipeContents(MixedStorage source) {
        long budget = Math.min(PIPE_MPU_PER_TRANSFER, source.getTotal());
        if (budget <= 0L) {
            return 0L;
        }
        long[] planned = ParticleMath.amountsFromShares(budget, source.shares());
        long moved = 0L;
        for (Phase phase : Phase.all()) {
            long want = Math.min(planned[phase.index()], source.getAmount(phase));
            if (want <= 0L) {
                continue;
            }
            long extracted = source.extract(phase, want, false);
            long accepted = insert(phase, extracted, false);
            if (accepted < extracted) {
                // Return the part that did not fit the pipe buffer.
                source.insert(phase, extracted - accepted, false);
            }
            moved += accepted;
        }
        return moved;
    }

    /**
     * Moves up to {@code PIPE_MPU_PER_TRANSFER / 4} mPU of each phase into
     * {@code target}. Returns the total amount accepted by the destination;
     * anything it could not take is put back into its own tank.
     */
    private long pushPhaseContents(PhaseStorage target) {
        long perPhase = Math.max(1L, PIPE_MPU_PER_TRANSFER / 4L);
        long moved = 0L;
        for (Phase phase : Phase.all()) {
            long budget = Math.min(perPhase, phaseStorage.getAmount(phase));
            if (budget <= 0L) {
                continue;
            }
            long extracted = phaseStorage.extract(phase, budget, false);
            long accepted = target.insert(phase, extracted, false);
            if (accepted < extracted) {
                phaseStorage.insert(phase, extracted - accepted, false);
            }
            moved += accepted;
        }
        return moved;
    }

    /**
     * Mirror of {@link #pushPhaseContents(PhaseStorage)}: pulls per-phase
     * amounts from {@code source} into the local tanks and puts back what does
     * not fit.
     */
    private long pullPhaseContents(PhaseStorage source) {
        long perPhase = Math.max(1L, PIPE_MPU_PER_TRANSFER / 4L);
        long moved = 0L;
        for (Phase phase : Phase.all()) {
            long budget = Math.min(perPhase, source.getAmount(phase));
            if (budget <= 0L) {
                continue;
            }
            long extracted = source.extract(phase, budget, false);
            long accepted = phaseStorage.insert(phase, extracted, false);
            if (accepted < extracted) {
                source.insert(phase, extracted - accepted, false);
            }
            moved += accepted;
        }
        return moved;
    }

    private void tickSeparator() {
        lastSeparatorLoss = 0L;
        if (energy < SEPARATOR_FE_PER_TICK) {
            return;
        }

        long inputTotal = Math.min(SEPARATOR_MAX_INPUT_MPU_PER_TICK, getTotal());
        if (inputTotal <= 0L) {
            return;
        }

        long[] input = ParticleMath.amountsFromShares(inputTotal, shares());
        long actualInput = 0L;
        for (int index = 0; index < input.length; index++) {
            input[index] = Math.min(input[index], mixed[index]);
            actualInput += input[index];
        }
        if (actualInput <= 0L) {
            return;
        }

        // 95% with a 0-99 carry gives an exact 5% loss on average without
        // losing a whole mPU every tick: 25 -> 23, 24, 24, 24 ...
        long scaled = actualInput * 95L + separatorCarry;
        long outputTotal = scaled / 100L;
        long nextCarry = scaled % 100L;
        long[] output = ParticleMath.amountsFromShares(
                outputTotal,
                ParticleMath.sharesFromAmounts(input)
        );

        // Precheck every phase tank before extracting anything. If any output
        // cannot fit, skip the whole tick: no input consumed, no energy, no loss.
        if (!separatorFits(output, phaseAmounts())) {
            return;
        }

        for (Phase phase : Phase.all()) {
            extract(phase, input[phase.index()], false);
        }
        for (Phase phase : Phase.all()) {
            long requested = output[phase.index()];
            if (requested <= 0L) {
                continue;
            }
            long moved = phaseStorage.insert(phase, requested, false);
            if (moved < requested) {
                // The precheck above makes this theoretically impossible. Put
                // the shortfall back into the mixed buffer instead of deleting
                // it, and warn once per machine.
                long missing = requested - moved;
                long returned = insert(phase, missing, false);
                if (returned < missing && !separatorInsertWarningLogged) {
                    separatorInsertWarningLogged = true;
                    MeteorosProtocol.LOGGER.warn(
                            "Separator at {} could not place {} mPU of {} after the phase precheck",
                            getBlockPos(), missing - returned, phase.id()
                    );
                }
            }
        }
        separatorCarry = nextCarry;
        lastSeparatorLoss = actualInput - outputTotal;
        energy -= SEPARATOR_FE_PER_TICK;
    }

    /** Runs one 25 T -> 25 C conversion; returns whether the machine actually worked this tick. */
    private boolean tickGenerator() {
        if (energy > ENERGY_CAPACITY - GENERATOR_FE_PER_TICK) {
            return false;
        }
        int thermionIndex = Phase.THERMION.index();
        int cryonIndex = Phase.CRYON.index();

        // Thermion comes from the phase tank; when the tank holds less than one
        // operation, take the whole input from the mixed buffer instead.
        boolean fromTank =
                phaseStorage.extract(Phase.THERMION, GENERATOR_INPUT_MPU_PER_TICK, true)
                        >= GENERATOR_INPUT_MPU_PER_TICK;
        if (!fromTank && mixed[thermionIndex] < GENERATOR_INPUT_MPU_PER_TICK) {
            return false;
        }

        // v2 replaces the old T -> Ae recipe with T -> C. The output fills the
        // Cryon tank first and spills into the mixed buffer; whatever fits
        // neither is discarded, so neither store exceeds its capacity.
        long tankAccepted = phaseStorage.insert(Phase.CRYON, GENERATOR_OUTPUT_MPU_PER_TICK, true);
        long overflow = GENERATOR_OUTPUT_MPU_PER_TICK - tankAccepted;

        if (fromTank) {
            phaseStorage.extract(Phase.THERMION, GENERATOR_INPUT_MPU_PER_TICK, false);
        } else {
            mixed[thermionIndex] -= GENERATOR_INPUT_MPU_PER_TICK;
            setChanged();
        }

        if (tankAccepted > 0L) {
            phaseStorage.insert(Phase.CRYON, tankAccepted, false);
        }
        if (overflow > 0L) {
            long mixedRoom = Math.max(0L, MIXED_CAPACITY - getTotal());
            long mixedAccepted = Math.min(overflow, mixedRoom);
            if (mixedAccepted > 0L) {
                mixed[cryonIndex] += mixedAccepted;
                setChanged();
            }
        }

        energy = Math.min(ENERGY_CAPACITY, energy + GENERATOR_FE_PER_TICK);
        return true;
    }

    /** {@code (input * 95 + carry) / 100}; the separator's output this tick. */
    public static long separatorOutput(long input, long carry) {
        if (input <= 0L) {
            return 0L;
        }
        long safeCarry = Math.max(0L, Math.min(99L, carry));
        return (input * 95L + safeCarry) / 100L;
    }

    /** One-arg helper for a fresh carry of 0 (25 -> 23). */
    public static long separatorOutput(long input) {
        return separatorOutput(input, 0L);
    }

    /** {@code (input * 95 + carry) % 100}; the carry for the next tick. */
    public static long separatorNextCarry(long input, long carry) {
        if (input <= 0L) {
            return Math.max(0L, Math.min(99L, carry));
        }
        long safeCarry = Math.max(0L, Math.min(99L, carry));
        return (input * 95L + safeCarry) % 100L;
    }

    /** Intended separator loss for a fresh carry (25 -> 2 mPU). */
    public static long separatorLoss(long input) {
        return Math.max(0L, input) - separatorOutput(input);
    }

    /** Shared-total insert result: never exceeds the remaining mixed space. */
    public static long mixedAccepted(long amount, long mixedTotal) {
        return Math.max(0L, Math.min(Math.max(0L, amount), MIXED_CAPACITY - mixedTotal));
    }

    /** Per-phase insert result, capped by that phase's own tank. */
    public static long phaseAccepted(Phase phase, long amount, long phaseAmount) {
        return Math.max(0L, Math.min(Math.max(0L, amount), phase.tankCapacity() - phaseAmount));
    }

    /** Pure precheck: true only when every planned output fits its phase tank. */
    public static boolean separatorFits(long[] output, long[] phaseAmounts) {
        java.util.List<Phase> phases = Phase.all();
        for (int index = 0; index < output.length && index < phases.size(); index++) {
            if (phaseAmounts[index] + output[index] > phases.get(index).tankCapacity()) {
                return false;
            }
        }
        return true;
    }

    private long[] phaseAmounts() {
        long[] copy = new long[phases.length];
        System.arraycopy(phases, 0, copy, 0, phases.length);
        return copy;
    }

    /**
     * Short action-bar text (<= 60 chars) selected by machine type; built only
     * on interaction, never per tick.
     */
    public Component statusMessage() {
        Block block = getBlockState().getBlock();
        if (block == MeteorosProtocol.PARTICLE_INTAKE.get()) {
            return Component.literal(String.format(Locale.ROOT,
                    "FE %d/%d | %d mPU | %s",
                    energy, ENERGY_CAPACITY, getTotal(), dominantStoredPhase().id()));
        }
        if (block == MeteorosProtocol.PHASE_SEPARATOR.get()) {
            return Component.literal(String.format(Locale.ROOT,
                    "FE %d/%d | %d mPU | loss %d",
                    energy, ENERGY_CAPACITY, getTotal(), lastSeparatorLoss));
        }
        if (block == MeteorosProtocol.THERMAL_GENERATOR.get()) {
            return Component.literal(String.format(Locale.ROOT,
                    "FE %d/%d | T %d mPU | C %d mPU",
                    energy, ENERGY_CAPACITY,
                    phases[Phase.THERMION.index()], phases[Phase.CRYON.index()]));
        }
        return Component.literal(String.format(Locale.ROOT,
                "FE %d/%d | %d mPU", energy, ENERGY_CAPACITY, getTotal()));
    }

    private Phase dominantStoredPhase() {
        int best = 0;
        for (int index = 1; index < mixed.length; index++) {
            if (mixed[index] > mixed[best]) {
                best = index;
            }
        }
        return Phase.all().get(best);
    }

    private void syncComposition(ServerLevel level, BlockPos pos, BlockState state) {
        long signature = compositionSignature();
        if (signature == lastCompositionSignature) {
            return;
        }
        lastCompositionSignature = signature;
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
    }

    /** Composition fingerprint across every registered phase, for client sync. */
    private long compositionSignature() {
        long total = getTotal();
        long signature = 0x9E3779B97F4A7C15L;
        for (Phase phase : Phase.all()) {
            int quantised = total <= 0L
                    ? 0
                    : (int) Math.min(15L, mixed[phase.index()] * 16L / total);
            signature = signature * 31L + quantised;
        }
        return signature;
    }

    public long energy() {
        return energy;
    }

    public long energyCapacity() {
        return ENERGY_CAPACITY;
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        if (maxReceive <= 0) {
            return 0;
        }
        long moved = Math.min((long) maxReceive, ENERGY_CAPACITY - energy);
        if (moved <= 0L) {
            return 0;
        }
        if (!simulate) {
            energy += moved;
            setChanged();
        }
        return (int) moved;
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        return 0;
    }

    @Override
    public int getEnergyStored() {
        return (int) Math.min(Integer.MAX_VALUE, energy);
    }

    @Override
    public int getMaxEnergyStored() {
        return (int) ENERGY_CAPACITY;
    }

    @Override
    public boolean canExtract() {
        return false;
    }

    @Override
    public boolean canReceive() {
        return true;
    }

    /** Last separator tick's intentional 5% loss, for tooltip display. */
    public long lastSeparatorLoss() {
        return lastSeparatorLoss;
    }

    /** The six independent phase tanks; also exposed as the PHASE_STORAGE capability. */
    public PhaseStorage phaseStorage() {
        return phaseStorage;
    }

    public SimpleContainer items() {
        return items;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        setChanged();
    }

    public RedstoneMode redstoneMode() {
        return redstoneMode;
    }

    public void cycleRedstoneMode() {
        redstoneMode = RedstoneMode.values()[(redstoneMode.ordinal() + 1) % RedstoneMode.values().length];
        setChanged();
    }

    /** Intake only runs when enabled and the selected redstone mode allows it. */
    public boolean isActive() {
        if (!enabled) {
            return false;
        }
        return switch (redstoneMode) {
            case ALWAYS_ON -> true;
            case REDSTONE_CONTROL -> level != null && level.hasNeighborSignal(worldPosition);
        };
    }

    /**
     * Moves material from one of the six phase tanks into the canister in slot 1.
     * An empty infinite canister binds to that phase after consuming 1 mPU.
     * A normal canister receives as much as its remaining capacity allows.
     */
    public long fillCanisterFromPhase(Phase phase) {
        ItemStack canister = items.getItem(1);
        long available = phases[phase.index()];
        if (available <= 0L) {
            return 0L;
        }
        if (canister.is(MeteorosProtocol.INFINITE_PARTICLE_CANISTER.get())) {
            if (InfiniteParticleCanisterItem.hasPhase(canister)) {
                return 0L;
            }
            InfiniteParticleCanisterItem.setSelectedPhase(canister, phase);
            phases[phase.index()] = available - 1L;
            items.setChanged();
            setChanged();
            return 1L;
        }
        if (!canister.is(MeteorosProtocol.CANISTER.get())) {
            return 0L;
        }
        long space = CanisterItem.CAPACITY - CanisterItem.getTotal(canister);
        long move = Math.min(space, available);
        if (move <= 0L) {
            return 0L;
        }
        long[] existing = CanisterItem.getAmounts(canister);
        existing[phase.index()] += move;
        CanisterItem.setAmounts(canister, existing);
        phases[phase.index()] = available - move;
        items.setChanged();
        setChanged();
        return move;
    }

    /**
     * Phase currently selected on the intake tower GUI for canister extraction.
     */
    public Phase canisterPhase() {
        return canisterPhase;
    }

    /** Server-authoritative phase selection; the menu syncs it back via data slot 16. */
    public void setCanisterPhase(Phase phase) {
        if (phase == null || phase == canisterPhase) {
            return;
        }
        canisterPhase = phase;
        setChanged();
    }

    /**
     * Moves material for one phase out of the mixed buffer into the canister in
     * slot 1. Mirrors {@link #fillCanisterFromPhase(Phase)} but reads and writes
     * {@code mixed[]} only: the intake tower never fills the phase tanks.
     */
    public long fillCanisterFromMixed(Phase phase) {
        ItemStack canister = items.getItem(1);
        long available = mixed[phase.index()];
        if (available <= 0L) {
            return 0L;
        }
        if (canister.is(MeteorosProtocol.INFINITE_PARTICLE_CANISTER.get())) {
            if (InfiniteParticleCanisterItem.hasPhase(canister)) {
                return 0L;
            }
            InfiniteParticleCanisterItem.setSelectedPhase(canister, phase);
            mixed[phase.index()] = available - 1L;
            items.setChanged();
            setChanged();
            return 1L;
        }
        if (!canister.is(MeteorosProtocol.CANISTER.get())) {
            return 0L;
        }
        long space = CanisterItem.CAPACITY - CanisterItem.getTotal(canister);
        long move = Math.min(space, available);
        if (move <= 0L) {
            return 0L;
        }
        long[] existing = CanisterItem.getAmounts(canister);
        existing[phase.index()] += move;
        CanisterItem.setAmounts(canister, existing);
        mixed[phase.index()] = available - move;
        items.setChanged();
        setChanged();
        return move;
    }

    /**
     * Sided mixed-storage view. The intake tower exposes an extract-only output
     * port on the face to the right of its front (north -> west); every other
     * face returns null. Other machines keep their all-face behaviour.
     */
    @Nullable
    public MixedStorage mixedStorageForSide(@Nullable Direction side) {
        if (getBlockState().getBlock() == MeteorosProtocol.PARTICLE_INTAKE.get()) {
            if (side == null) {
                return null;
            }
            Direction facing = getBlockState().getValue(IntakeBlock.FACING);
            return side == facing.getCounterClockWise() ? extractOnly : null;
        }
        return this;
    }

    /** Drops the filter/canister slots when the block is broken by a player. */
    public void dropContents() {
        if (level != null && !level.isClientSide) {
            Containers.dropContents(level, worldPosition, items);
            for (int index = 0; index < items.getContainerSize(); index++) {
                items.setItem(index, ItemStack.EMPTY);
            }
        }
    }

    @Override
    public Component getDisplayName() {
        if (level != null) {
            Block block = getBlockState().getBlock();
            if (block == MeteorosProtocol.THERMAL_GENERATOR.get()) {
                return Component.translatable("container.meteorosprotocol.thermal_generator");
            }
            if (block == MeteorosProtocol.PHASE_SEPARATOR.get()) {
                return Component.translatable("container.meteorosprotocol.phase_separator");
            }
            if (block == MeteorosProtocol.WEATHER_STATION.get()) {
                return Component.translatable("container.meteorosprotocol.weather_station");
            }
            if (block == MeteorosProtocol.WEATHER_TANK.get()) {
                return Component.translatable("container.meteorosprotocol.weather_tank");
            }
        }
        return Component.translatable("container.meteorosprotocol.particle_intake");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        Block block = getBlockState().getBlock();
        if (block == MeteorosProtocol.PARTICLE_INTAKE.get()) {
            return new IntakeMenu(containerId, inventory, this);
        }
        return new MachineStatusMenu(containerId, inventory, this);
    }

    @Override
    public void writeClientSideData(AbstractContainerMenu menu, RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(worldPosition);
    }

    @Override
    public long getAmount(Phase phase) {
        return mixed[phase.index()];
    }

    @Override
    public long getCapacity(Phase phase) {
        // Shared whole-buffer limit, not a per-phase cap; see MixedStorage.
        return MIXED_CAPACITY;
    }

    @Override
    public long insert(Phase phase, long amount, boolean simulate) {
        long moved = mixedAccepted(amount, getTotal());
        if (!simulate && moved > 0L) {
            mixed[phase.index()] += moved;
            setChanged();
        }
        return moved;
    }

    @Override
    public long extract(Phase phase, long amount, boolean simulate) {
        long moved = Math.min(Math.max(0L, amount), mixed[phase.index()]);
        if (!simulate && moved > 0L) {
            mixed[phase.index()] -= moved;
            setChanged();
        }
        return moved;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        // The renderer reads the same ParticleNbt payload the save uses; no
        // separate sync-only structure is needed.
        return saveWithoutMetadata(registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("MixedParticles", ParticleNbt.save(this).getCompound("Particles"));
        tag.put("PhaseParticles", ParticleNbt.save(phaseStorage).getCompound("Particles"));
        tag.putLong("SeparatorCarry", separatorCarry);
        tag.putLong("Energy", energy);
        tag.putLong("Progress", progress);
        ContainerHelper.saveAllItems(tag, items.getItems(), registries);
        tag.putBoolean("Enabled", enabled);
        tag.putString("RedstoneMode", redstoneMode.name());
        tag.putString("CanisterPhase", canisterPhase.id());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);

        // New saves carry MixedParticles/PhaseParticles. Old S1-S3 saves only
        // had Particles; those values now populate the mixed buffer (the unit
        // marker still decides whether a legacy PU -> mPU conversion is needed).
        CompoundTag mixedParticles = tag.contains("MixedParticles", Tag.TAG_COMPOUND)
                ? tag.getCompound("MixedParticles")
                : tag.getCompound("Particles");
        CompoundTag mixedWrapper = new CompoundTag();
        mixedWrapper.put("Particles", mixedParticles);
        ParticleNbt.load(this, mixedWrapper);

        CompoundTag phaseWrapper = new CompoundTag();
        phaseWrapper.put("Particles", tag.getCompound("PhaseParticles"));
        ParticleNbt.load(phaseStorage, phaseWrapper);

        separatorCarry = Math.max(0L, Math.min(99L, tag.getLong("SeparatorCarry")));
        energy = Math.max(0L, Math.min(ENERGY_CAPACITY, tag.getLong("Energy")));
        progress = Math.max(0L, tag.getLong("Progress"));

        items.getItems().clear();
        ContainerHelper.loadAllItems(tag, items.getItems(), registries);
        enabled = !tag.contains("Enabled") || tag.getBoolean("Enabled");
        String savedRedstoneMode = tag.getString("RedstoneMode");
        try {
            redstoneMode = RedstoneMode.valueOf(savedRedstoneMode);
        } catch (IllegalArgumentException exception) {
            // Legacy S3/S4 saves used IGNORE/HIGH/LOW. IGNORE and unknown values
            // keep the machine running; HIGH/LOW become the single redstone mode.
            redstoneMode = savedRedstoneMode.equals("HIGH") || savedRedstoneMode.equals("LOW")
                    ? RedstoneMode.REDSTONE_CONTROL
                    : RedstoneMode.ALWAYS_ON;
        }
        Phase savedCanisterPhase = Phase.byId(tag.getString("CanisterPhase"));
        canisterPhase = savedCanisterPhase != null ? savedCanisterPhase : Phase.AETHERON;
    }
}
