package com.spectrum255.meteorosprotocol.machine;

import com.spectrum255.meteorosprotocol.MeteorosProtocol;
import com.spectrum255.meteorosprotocol.menu.IntakeMenu;
import com.spectrum255.meteorosprotocol.menu.MachineStatusMenu;
import com.spectrum255.meteorosprotocol.compat.WeatherAdjustedField;
import com.spectrum255.meteorosprotocol.item.CanisterItem;
import com.spectrum255.meteorosprotocol.item.FilterItem;
import com.spectrum255.meteorosprotocol.item.InfiniteParticleCanisterItem;
import com.spectrum255.meteorosprotocol.particle.*;
import java.util.List;
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
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
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
    /**
     * Byproduct budget of an <em>upgraded</em> generator, in mPU per operation.
     * A bare generator produces no byproduct (see {@link #hasGeneratorUpgrade()}),
     * so this constant is not the current output rate.
     */
    public static final long GENERATOR_OUTPUT_MPU_PER_TICK = 25L;
    /**
     * 20 FE per mPU burned. A single intake tower captures 25 mPU/t, so one
     * generator fed by one tower powers roughly twenty 25 FE/t machines.
     */
    public static final long GENERATOR_FE_PER_TICK = 500L;
    /** How long the generator's "active" back texture stays on after a successful tick. */
    private static final int GENERATOR_ACTIVE_HOLD_TICKS = 40;

    /**
     * Mixed pipe transfer budget per active pipe tick (every 5 ticks):
     * 500 / 5 t = 100 mPU/t, four times a tower's capture rate.
     */
    public static final long PIPE_MPU_PER_TRANSFER = 500L;
    /**
     * Per-phase pipe budget per active pipe tick: 100 / 5 t = 20 mPU/t for each
     * of the six phases (120 mPU/t when every phase is moving).
     */
    public static final long PIPE_PHASE_MPU_PER_TRANSFER = 100L;
    private static final long PIPE_INTERVAL = 5L;

    /**
     * Small per-phase budget used by the machines' own active I/O (generator
     * pull, separator push) once every 5 ticks: 25 / 5 t = 5 mPU/t per phase.
     * The intake tower uses the larger pipe budgets for its output because it
     * is the source of the whole production chain.
     */
    private static final long ACTIVE_IO_SMALL_BUDGET = 25L;

    /** Shared total capacity of the mixed buffer (1,000 PU). */
    public static final long MIXED_CAPACITY = 1_000_000L;
    /** Independent capacity of each phase tank (8,000 PU). */
    public static final long PHASE_CAPACITY = 8_000_000L;
    /** Internal FE buffer: 2,000 generator ticks (1.7 min) of headroom. */
    public static final long ENERGY_CAPACITY = 1_000_000L;

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
            if (!acceptsInput(phase)) {
                return 0L;
            }
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
    private boolean autoIo = true;
    private RedstoneMode redstoneMode = RedstoneMode.ALWAYS_ON;
    /** Phase the intake tower will pump into the loading slot's canister. */
    private Phase canisterPhase = Phase.AETHERON;

    /**
     * Per-face I/O modes. Two bits per {@link Direction#ordinal()} for each
     * channel, so the whole configuration fits in two ints and can be synced
     * through the menu data slots.
     */
    private int sideMixedMask;
    private int sidePhaseMask;
    private int sideEnergyMask;
    private int sideItemMask;
    private boolean sideConfigInitialised;

    /** Cached per-face energy/item views; index 6 is the {@code null} side. */
    private final IEnergyStorage[] energyViews = new IEnergyStorage[7];
    private final IItemHandler[] itemViews = new IItemHandler[7];

    /** FE pushed through an OUT face per active-I/O window (500 FE/t). */
    public static final int ENERGY_TRANSFER_PER_INTERVAL = 2_500;
    /** Items moved through an ITEM face per active-I/O window. */
    public static final int ITEM_TRANSFER_PER_INTERVAL = 4;

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

    /** Every face set to {@link SideMode#BOTH} (two bits per direction). */
    private static final int DEFAULT_ALL_BOTH = 0xFFF;
    /** Every face set to {@link SideMode#IN}. */
    private static final int DEFAULT_ALL_IN = 0x555;
    /** Every face set to {@link SideMode#OUT}. */
    private static final int DEFAULT_ALL_OUT = 0xAAA;

    /**
     * Applies the per-machine side defaults. Weather blocks have no I/O at all;
     * the generator only outputs FE, the separator only takes FE, the intake
     * tower takes FE and exchanges items, and the particle port moves particles
     * only. Called lazily because the block state is only reliably available
     * after the block entity has been attached.
     */
    private void ensureSideConfig() {
        if (sideConfigInitialised) {
            return;
        }
        sideConfigInitialised = true;
        Block block = getBlockState().getBlock();
        if (block == MeteorosProtocol.WEATHER_STATION.get()
                || block == MeteorosProtocol.WEATHER_TANK.get()) {
            sideMixedMask = 0;
            sidePhaseMask = 0;
            sideEnergyMask = 0;
            sideItemMask = 0;
            return;
        }
        sideMixedMask = DEFAULT_ALL_BOTH;
        sidePhaseMask = DEFAULT_ALL_BOTH;
        if (block == MeteorosProtocol.THERMAL_GENERATOR.get()) {
            sideEnergyMask = DEFAULT_ALL_OUT;
            sideItemMask = DEFAULT_ALL_BOTH;
        } else if (block == MeteorosProtocol.PHASE_SEPARATOR.get()) {
            sideEnergyMask = DEFAULT_ALL_IN;
            sideItemMask = DEFAULT_ALL_BOTH;
        } else if (block == MeteorosProtocol.PARTICLE_INTAKE.get()) {
            sideEnergyMask = DEFAULT_ALL_IN;
            sideItemMask = DEFAULT_ALL_BOTH;
        } else if (block == MeteorosProtocol.PARTICLE_PORT.get()) {
            sideEnergyMask = 0;
            sideItemMask = 0;
        } else {
            sideEnergyMask = DEFAULT_ALL_BOTH;
            sideItemMask = DEFAULT_ALL_BOTH;
        }
    }

    /** Reads one face's mode out of a side mask. */
    public static SideMode modeAt(int mask, Direction direction) {
        return SideMode.byId((mask >>> (direction.ordinal() * 2)) & 3);
    }

    /** Replaces one face's mode inside a side mask, leaving the other faces alone. */
    public static int withMode(int mask, Direction direction, SideMode mode) {
        int shift = direction.ordinal() * 2;
        return (mask & ~(3 << shift)) | (mode.id() << shift);
    }

    public int mixedSideMask() {
        ensureSideConfig();
        return sideMixedMask;
    }

    public int phaseSideMask() {
        ensureSideConfig();
        return sidePhaseMask;
    }

    public int energySideMask() {
        ensureSideConfig();
        return sideEnergyMask;
    }

    public int itemSideMask() {
        ensureSideConfig();
        return sideItemMask;
    }

    /** Reads one channel's mask; all four masks share the same 2-bit layout. */
    public int sideMask(SideChannel channel) {
        ensureSideConfig();
        return switch (channel) {
            case MIXED -> sideMixedMask;
            case PHASE -> sidePhaseMask;
            case ENERGY -> sideEnergyMask;
            case ITEM -> sideItemMask;
        };
    }

    private void setMask(SideChannel channel, int mask) {
        switch (channel) {
            case MIXED -> sideMixedMask = mask;
            case PHASE -> sidePhaseMask = mask;
            case ENERGY -> sideEnergyMask = mask;
            case ITEM -> sideItemMask = mask;
        }
    }

    public SideMode sideMode(Direction direction, SideChannel channel) {
        return modeAt(sideMask(channel), direction);
    }

    public void setSideMode(Direction direction, SideChannel channel, SideMode mode) {
        setMask(channel, withMode(sideMask(channel), direction, mode));
        setChanged();
    }

    public void cycleSideMode(Direction direction, SideChannel channel, boolean backwards) {
        SideMode current = sideMode(direction, channel);
        setSideMode(direction, channel, backwards ? current.previous() : current.next());
    }

    private boolean allowsIn(@Nullable Direction direction, SideChannel channel) {
        if (direction == null) {
            return true;
        }
        SideMode mode = sideMode(direction, channel);
        return mode == SideMode.IN || mode == SideMode.BOTH;
    }

    private boolean allowsOut(@Nullable Direction direction, SideChannel channel) {
        if (direction == null) {
            return true;
        }
        SideMode mode = sideMode(direction, channel);
        return mode == SideMode.OUT || mode == SideMode.BOTH;
    }

    public enum RedstoneMode {
        /** Run only while a redstone signal reaches the block. */
        HIGH,
        /** Run only while no redstone signal reaches the block. */
        LOW,
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
        } else if (block == MeteorosProtocol.PARTICLE_PORT.get()) {
            if (machine.progress % PIPE_INTERVAL == 0L) {
                machine.tickPort(level, pos);
            }
        }
        // Machines with particle I/O act on their direct neighbours every 5 ticks;
        // pipes keep running their own tickPipe pass and are left out of this one.
        if (block != MeteorosProtocol.PARTICLE_PIPE.get()
                && machine.autoIo
                && machine.progress % PIPE_INTERVAL == 0L) {
            machine.tickActiveIo(level, pos);
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
        // The tower only captures the degraded form of the ambient medium: pure
        // Aetheron is the medium itself and passes straight through. Impure
        // Aetheron keeps its sampler share and is captured normally.
        zeroPureAetheronShare(shares);
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
            // Built-in separation: the tower turns the buffer it just filled into
            // phase-tank contents in the same tick. Lossless (100%): the tower is
            // a capture device, not a refinery, so nothing is dropped. No extra FE
            // is charged for this step for now (暂不收费，待平衡).
            separateMixedIntoTanks(INTAKE_MPU_PER_TICK, 100L);
        }
    }

    /**
     * Zeros the pure Aetheron entry of a composition array. The intake tower
     * never captures pure Aetheron (it is the ambient medium itself); only its
     * degraded form, impure Aetheron, is collected. Shared with the self-test so
     * the rule is verifiable without a level.
     */
    public static void zeroPureAetheronShare(float[] shares) {
        int index = Phase.AETHERON.index();
        if (shares != null && index >= 0 && index < shares.length) {
            shares[index] = 0.0F;
        }
    }

    /**
     * Moves up to {@code maxInput} mPU from the mixed buffer into the phase tanks:
     * takes the buffer's current composition, applies the separator's 95%
     * efficiency with the shared {@code separatorCarry}, and writes the result
     * into {@code phases[]}. The whole batch is skipped when any phase tank cannot
     * fit its share, so no input is consumed and no loss occurs. Used by the
     * separator every tick and by the intake tower for its built-in separation step.
     *
     * @return mPU actually consumed from the mixed buffer (0 when skipped)
     */
    private long separateMixedIntoTanks(long maxInput) {
        return separateMixedIntoTanks(maxInput, 95L);
    }

    /**
     * Same as {@link #separateMixedIntoTanks(long)} but with an explicit
     * efficiency. {@code efficiencyPercent >= 100} means lossless: the whole
     * batch moves into the phase tanks, no carry is consumed or updated and
     * {@code lastSeparatorLoss} stays 0. The intake tower uses 100 (无损捕获),
     * the standalone separator keeps 95.
     */
    private long separateMixedIntoTanks(long maxInput, long efficiencyPercent) {
        long inputTotal = Math.min(Math.max(0L, maxInput), getTotal());
        if (inputTotal <= 0L) {
            return 0L;
        }

        long[] input = ParticleMath.amountsFromShares(inputTotal, shares());
        long actualInput = 0L;
        for (int index = 0; index < input.length; index++) {
            input[index] = Math.min(input[index], mixed[index]);
            actualInput += input[index];
        }
        if (actualInput <= 0L) {
            return 0L;
        }

        // 95% with a 0-99 carry gives an exact 5% loss on average without
        // losing a whole mPU every tick: 25 -> 23, 24, 24, 24 ...
        // 100% is the intake tower's lossless path: nothing is lost and the
        // separator's carry is left untouched.
        boolean lossless = efficiencyPercent >= 100L;
        long scaled = lossless ? actualInput : actualInput * efficiencyPercent + separatorCarry;
        long outputTotal = lossless ? actualInput : scaled / 100L;
        long nextCarry = lossless ? separatorCarry : scaled % 100L;
        long[] output = ParticleMath.amountsFromShares(
                outputTotal,
                ParticleMath.sharesFromAmounts(input)
        );

        // Precheck every phase tank before extracting anything. If any output
        // cannot fit, skip the whole tick: no input consumed, no energy, no loss.
        if (!separatorFits(output, phaseAmounts())) {
            return 0L;
        }

        for (Phase phase : Phase.all()) {
            extract(phase, input[phase.index()], false);
        }
        for (Phase phase : Phase.all()) {
            long requested = output[phase.index()];
            if (requested <= 0L) {
                continue;
            }
            long moved = addPhaseInternal(phase, requested);
            if (moved < requested) {
                // The precheck above makes this theoretically impossible. Put
                // the shortfall back into the mixed buffer instead of deleting
                // it, and warn once per machine.
                long missing = requested - moved;
                long returned = insertMixedInternal(phase, missing);
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
        return actualInput;
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

    /**
     * Machines with particle I/O exchange with their six direct neighbours once
     * every {@link #PIPE_INTERVAL} ticks, so a machine touching a container or
     * another machine moves material without a pipe. Nothing is chained, and
     * adjacent pipes are skipped because {@link #tickPipe(ServerLevel, BlockPos)}
     * already pulls from machines at the same cadence (skipping avoids moving the
     * same material twice per tick window).
     *
     * <p>Direction table:</p>
     * <ul>
     *   <li>generator: pull up to 25 mPU Thermion (phase tanks first, mixed buffer
     *       second); its Cryon output is still handled in {@code tickGenerator};</li>
     *   <li>separator: pull up to 25 mPU mixed while below 25% buffered, push up to
     *       25 mPU of each stored phase;</li>
     *   <li>intake tower: push up to 500 mPU mixed and 100 mPU of each phase;</li>
     *   <li>weather blocks and pipes: no active I/O.</li>
     * </ul>
     */
    private void tickActiveIo(ServerLevel level, BlockPos pos) {
        Block block = getBlockState().getBlock();
        if (block == MeteorosProtocol.THERMAL_GENERATOR.get()) {
            long pulled = pullPhaseFromNeighbours(level, Phase.THERMION, ACTIVE_IO_SMALL_BUDGET, false);
            if (pulled < ACTIVE_IO_SMALL_BUDGET) {
                pullPhaseFromNeighbours(level, Phase.THERMION, ACTIVE_IO_SMALL_BUDGET - pulled, true);
            }
        } else if (block == MeteorosProtocol.PHASE_SEPARATOR.get()) {
            if (getTotal() < MIXED_CAPACITY / 4L) {
                pullMixedFromNeighbours(level, ACTIVE_IO_SMALL_BUDGET);
            }
            for (Phase phase : Phase.all()) {
                if (phases[phase.index()] > 0L) {
                    pushPhaseToNeighbours(level, phase, ACTIVE_IO_SMALL_BUDGET);
                }
            }
        } else if (block == MeteorosProtocol.PARTICLE_INTAKE.get()) {
            if (getTotal() > 0L) {
                pushMixedToNeighbours(level, PIPE_MPU_PER_TRANSFER);
            }
            for (Phase phase : Phase.all()) {
                if (phases[phase.index()] > 0L) {
                    pushPhaseToNeighbours(level, phase, PIPE_PHASE_MPU_PER_TRANSFER);
                }
            }
        }
        pushEnergyToNeighbours(level);
        if (block == MeteorosProtocol.PARTICLE_INTAKE.get()) {
            pushItemsToNeighbours(level);
            pullItemsFromNeighbours(level);
        }
    }

    /**
     * Pushes FE through every face whose ENERGY mode allows output. Nothing is
     * pulled: generators are the only FE producers, and machines configured as
     * IN keep receiving through the normal energy capability.
     */
    private void pushEnergyToNeighbours(ServerLevel level) {
        for (Direction direction : Direction.values()) {
            if (!allowsOut(direction, SideChannel.ENERGY)) {
                continue;
            }
            BlockPos neighbour = worldPosition.relative(direction);
            if (!isActiveIoNeighbour(level, neighbour)) {
                continue;
            }
            IEnergyStorage target = level.getCapability(
                    Capabilities.EnergyStorage.BLOCK, neighbour, direction.getOpposite());
            if (target == null) {
                continue;
            }
            int budget = (int) Math.min(Integer.MAX_VALUE, Math.min(ENERGY_TRANSFER_PER_INTERVAL, energy));
            if (budget <= 0) {
                continue;
            }
            int accepted = target.receiveEnergy(budget, true);
            if (accepted <= 0) {
                continue;
            }
            accepted = target.receiveEnergy(accepted, false);
            if (accepted > 0) {
                energy -= accepted;
                setChanged();
            }
        }
    }

    /** Pushes the canister in slot 1 through ITEM OUT faces. */
    private void pushItemsToNeighbours(ServerLevel level) {
        ItemStack output = items.getItem(1);
        if (output.isEmpty()) {
            return;
        }
        for (Direction direction : Direction.values()) {
            if (!allowsOut(direction, SideChannel.ITEM)) {
                continue;
            }
            BlockPos neighbour = worldPosition.relative(direction);
            if (!isActiveIoNeighbour(level, neighbour)) {
                continue;
            }
            IItemHandler target = level.getCapability(
                    Capabilities.ItemHandler.BLOCK, neighbour, direction.getOpposite());
            if (target == null) {
                continue;
            }
            ItemStack offered = output.copyWithCount(Math.min(ITEM_TRANSFER_PER_INTERVAL, output.getCount()));
            ItemStack leftover = offered;
            for (int slot = 0; slot < target.getSlots() && !leftover.isEmpty(); slot++) {
                leftover = target.insertItem(slot, leftover, true);
            }
            int accepted = offered.getCount() - leftover.getCount();
            if (accepted <= 0) {
                continue;
            }
            ItemStack toInsert = offered.copyWithCount(accepted);
            for (int slot = 0; slot < target.getSlots() && !toInsert.isEmpty(); slot++) {
                toInsert = target.insertItem(slot, toInsert, false);
            }
            int inserted = accepted - toInsert.getCount();
            if (inserted > 0) {
                items.removeItem(1, inserted);
                items.setChanged();
                setChanged();
                return;
            }
        }
    }

    /** Pulls a filter into slot 0 or a canister into slot 1 through ITEM IN faces. */
    private void pullItemsFromNeighbours(ServerLevel level) {
        for (int targetSlot = 0; targetSlot < 2; targetSlot++) {
            if (!items.getItem(targetSlot).isEmpty()) {
                continue;
            }
            for (Direction direction : Direction.values()) {
                if (!allowsIn(direction, SideChannel.ITEM)) {
                    continue;
                }
                BlockPos neighbour = worldPosition.relative(direction);
                if (!isActiveIoNeighbour(level, neighbour)) {
                    continue;
                }
                IItemHandler source = level.getCapability(
                        Capabilities.ItemHandler.BLOCK, neighbour, direction.getOpposite());
                if (source == null) {
                    continue;
                }
                for (int slot = 0; slot < source.getSlots(); slot++) {
                    ItemStack candidate = source.extractItem(slot, ITEM_TRANSFER_PER_INTERVAL, true);
                    if (candidate.isEmpty() || !items.canPlaceItem(targetSlot, candidate)) {
                        continue;
                    }
                    ItemStack extracted = source.extractItem(slot, candidate.getCount(), false);
                    if (extracted.isEmpty()) {
                        continue;
                    }
                    items.setItem(targetSlot, extracted.copy());
                    items.setChanged();
                    setChanged();
                    return;
                }
            }
        }
    }

    /** Direct neighbour that can take part in active I/O (not air, not a pipe). */
    private static boolean isActiveIoNeighbour(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.isAir() && !state.is(MeteorosProtocol.PARTICLE_PIPE.get());
    }

    /**
     * Pulls {@code phase} from adjacent {@code PHASE_STORAGE}s (or, when
     * {@code fromMixed} is set, from their mixed buffers) into this machine's
     * phase tank. Simulate first, execute, put back anything the tank refused.
     */
    private long pullPhaseFromNeighbours(ServerLevel level, Phase phase, long budget, boolean fromMixed) {
        long moved = 0L;
        for (Direction direction : Direction.values()) {
            if (moved >= budget) {
                break;
            }
            BlockPos neighbour = worldPosition.relative(direction);
            if (!isActiveIoNeighbour(level, neighbour)) {
                continue;
            }
            if (!allowsIn(direction, SideChannel.PHASE)) {
                continue;
            }
            long got;
            if (fromMixed) {
                MixedStorage source = level.getCapability(
                        ParticleCapabilities.MIXED_STORAGE, neighbour, direction.getOpposite());
                if (source == null || source == this) {
                    continue;
                }
                got = pullPhaseFromMixed(source, phase, budget - moved);
            } else {
                PhaseStorage source = level.getCapability(ParticleCapabilities.PHASE_STORAGE, neighbour);
                if (source == null || source == phaseStorage) {
                    continue;
                }
                got = pullPhaseFromStorage(source, phase, budget - moved);
            }
            moved += got;
        }
        return moved;
    }

    /** Pulls mixed particles from adjacent mixed storages into this machine. */
    private long pullMixedFromNeighbours(ServerLevel level, long budget) {
        long moved = 0L;
        for (Direction direction : Direction.values()) {
            if (moved >= budget) {
                break;
            }
            BlockPos neighbour = worldPosition.relative(direction);
            if (!isActiveIoNeighbour(level, neighbour)) {
                continue;
            }
            if (!allowsIn(direction, SideChannel.MIXED)) {
                continue;
            }
            MixedStorage source = level.getCapability(
                    ParticleCapabilities.MIXED_STORAGE, neighbour, direction.getOpposite());
            if (source == null || source == this) {
                continue;
            }
            moved += pullMixedFrom(source, budget - moved);
        }
        return moved;
    }

    /** Pushes up to {@code budget} mPU of one stored phase into adjacent phase tanks. */
    private long pushPhaseToNeighbours(ServerLevel level, Phase phase, long budget) {
        long moved = 0L;
        for (Direction direction : Direction.values()) {
            if (moved >= budget) {
                break;
            }
            BlockPos neighbour = worldPosition.relative(direction);
            if (!isActiveIoNeighbour(level, neighbour)) {
                continue;
            }
            if (!allowsOut(direction, SideChannel.PHASE)) {
                continue;
            }
            PhaseStorage target = level.getCapability(ParticleCapabilities.PHASE_STORAGE, neighbour);
            if (target == null || target == phaseStorage) {
                continue;
            }
            moved += pushPhaseTo(target, phase, budget - moved);
        }
        return moved;
    }

    /** Pushes up to {@code budget} mPU of the mixed buffer into adjacent mixed storages. */
    private long pushMixedToNeighbours(ServerLevel level, long budget) {
        long moved = 0L;
        for (Direction direction : Direction.values()) {
            if (moved >= budget) {
                break;
            }
            BlockPos neighbour = worldPosition.relative(direction);
            if (!isActiveIoNeighbour(level, neighbour)) {
                continue;
            }
            if (!allowsOut(direction, SideChannel.MIXED)) {
                continue;
            }
            MixedStorage target = level.getCapability(
                    ParticleCapabilities.MIXED_STORAGE, neighbour, direction.getOpposite());
            if (target == null || target == this) {
                continue;
            }
            moved += pushMixedTo(target, budget - moved);
        }
        return moved;
    }

    // ---- Single-target transfers -------------------------------------------
    // Shared by the machine-to-storage I/O above and by the port, which talks to
    // exactly one container: the block it faces.

    private long pushMixedTo(MixedStorage target, long budget) {
        long limit = Math.min(budget, getTotal());
        if (limit <= 0L) {
            return 0L;
        }
        long moved = 0L;
        long[] plan = ParticleMath.amountsFromShares(limit, shares());
        for (Phase phase : Phase.all()) {
            if (moved >= limit) {
                break;
            }
            long want = Math.min(plan[phase.index()], mixed[phase.index()]);
            if (want <= 0L) {
                continue;
            }
            long accepted = target.insert(phase, want, true);
            if (accepted <= 0L) {
                continue;
            }
            long extracted = extract(phase, accepted, false);
            long inserted = target.insert(phase, extracted, false);
            if (inserted < extracted) {
                insert(phase, extracted - inserted, false);
            }
            moved += inserted;
        }
        if (moved > 0L) {
            setChanged();
        }
        return moved;
    }

    private long pullMixedFrom(MixedStorage source, long budget) {
        long space = MIXED_CAPACITY - getTotal();
        long limit = Math.min(budget, Math.min(space, source.getTotal()));
        if (limit <= 0L) {
            return 0L;
        }
        long moved = 0L;
        long[] plan = ParticleMath.amountsFromShares(limit, source.shares());
        for (Phase phase : Phase.all()) {
            if (moved >= limit || space <= 0L) {
                break;
            }
            long want = Math.min(plan[phase.index()], source.getAmount(phase));
            if (want <= 0L) {
                continue;
            }
            long extracted = source.extract(phase, want, true);
            long accepted = insert(phase, extracted, true);
            if (accepted <= 0L) {
                continue;
            }
            long reallyExtracted = source.extract(phase, accepted, false);
            long inserted = insert(phase, reallyExtracted, false);
            if (inserted < reallyExtracted) {
                source.insert(phase, reallyExtracted - inserted, false);
            }
            moved += inserted;
            space -= inserted;
        }
        if (moved > 0L) {
            setChanged();
        }
        return moved;
    }

    private long pushPhaseTo(PhaseStorage target, Phase phase, long budget) {
        long want = Math.min(Math.min(budget, phases[phase.index()]),
                target.getCapacity(phase) - target.getAmount(phase));
        if (want <= 0L) {
            return 0L;
        }
        long accepted = target.insert(phase, want, true);
        if (accepted <= 0L) {
            return 0L;
        }
        long extracted = phaseStorage.extract(phase, accepted, false);
        long inserted = target.insert(phase, extracted, false);
        if (inserted < extracted) {
            addPhaseInternal(phase, extracted - inserted);
        }
        if (inserted > 0L) {
            setChanged();
        }
        return inserted;
    }

    private long pullPhaseFromStorage(PhaseStorage source, Phase phase, long budget) {
        long space = phase.tankCapacity() - phases[phase.index()];
        long want = Math.min(Math.min(budget, space), source.getAmount(phase));
        if (want <= 0L) {
            return 0L;
        }
        long extracted = source.extract(phase, want, true);
        long accepted = phaseStorage.insert(phase, extracted, true);
        if (accepted <= 0L) {
            return 0L;
        }
        long reallyExtracted = source.extract(phase, accepted, false);
        long inserted = phaseStorage.insert(phase, reallyExtracted, false);
        if (inserted < reallyExtracted) {
            source.insert(phase, reallyExtracted - inserted, false);
        }
        if (inserted > 0L) {
            setChanged();
        }
        return inserted;
    }

    private long pullPhaseFromMixed(MixedStorage source, Phase phase, long budget) {
        long space = phase.tankCapacity() - phases[phase.index()];
        long want = Math.min(Math.min(budget, space), source.getAmount(phase));
        if (want <= 0L) {
            return 0L;
        }
        long extracted = source.extract(phase, want, true);
        long accepted = phaseStorage.insert(phase, extracted, true);
        if (accepted <= 0L) {
            return 0L;
        }
        long reallyExtracted = source.extract(phase, accepted, false);
        long inserted = phaseStorage.insert(phase, reallyExtracted, false);
        if (inserted < reallyExtracted) {
            source.insert(phase, reallyExtracted - inserted, false);
        }
        if (inserted > 0L) {
            setChanged();
        }
        return inserted;
    }

    /**
     * The port's I/O: FACING points at the container it services. When the port
     * holds anything it pushes into that container; when it is empty it pulls
     * from it, using the pipe budgets (500 mPU mixed, 100 mPU per phase per
     * 5-tick window).
     */
    private void tickPort(ServerLevel level, BlockPos pos) {
        BlockState ownState = getBlockState();
        if (!ownState.hasProperty(MachineBlock.FACING)) {
            return;
        }
        Direction facing = ownState.getValue(MachineBlock.FACING);
        SideMode mixedMode = sideMode(facing, SideChannel.MIXED);
        SideMode phaseMode = sideMode(facing, SideChannel.PHASE);
        if (mixedMode == SideMode.NONE && phaseMode == SideMode.NONE) {
            return;
        }
        BlockPos targetPos = pos.relative(facing);
        if (!isActiveIoNeighbour(level, targetPos)) {
            return;
        }
        MixedStorage mixedTarget = level.getCapability(
                ParticleCapabilities.MIXED_STORAGE, targetPos, facing.getOpposite());
        if (mixedTarget == this) {
            mixedTarget = null;
        }
        PhaseStorage phaseTarget = level.getCapability(ParticleCapabilities.PHASE_STORAGE, targetPos);
        if (phaseTarget == phaseStorage) {
            phaseTarget = null;
        }
        if (mixedTarget == null && phaseTarget == null) {
            return;
        }

        if (mixedTarget != null) {
            boolean hasMixed = getTotal() > 0L;
            if (mixedMode == SideMode.OUT || (mixedMode == SideMode.BOTH && hasMixed)) {
                if (hasMixed) {
                    pushMixedTo(mixedTarget, PIPE_MPU_PER_TRANSFER);
                }
            } else if (mixedMode == SideMode.IN || (mixedMode == SideMode.BOTH && !hasMixed)) {
                pullMixedFrom(mixedTarget, PIPE_MPU_PER_TRANSFER);
            }
        }

        // The phase channel is decided independently from the mixed channel.
        if (phaseMode != SideMode.NONE) {
            boolean hasPhase = !phaseStorage.isEmpty();
            if (phaseMode == SideMode.OUT || (phaseMode == SideMode.BOTH && hasPhase)) {
                if (phaseTarget != null && hasPhase) {
                    for (Phase phase : Phase.all()) {
                        if (phases[phase.index()] > 0L) {
                            pushPhaseTo(phaseTarget, phase, PIPE_PHASE_MPU_PER_TRANSFER);
                        }
                    }
                }
            } else if (phaseMode == SideMode.IN || (phaseMode == SideMode.BOTH && !hasPhase)) {
                if (phaseTarget != null) {
                    for (Phase phase : Phase.all()) {
                        pullPhaseFromStorage(phaseTarget, phase, PIPE_PHASE_MPU_PER_TRANSFER);
                    }
                } else if (mixedTarget != null) {
                    for (Phase phase : Phase.all()) {
                        pullPhaseFromMixed(mixedTarget, phase, PIPE_PHASE_MPU_PER_TRANSFER);
                    }
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
     * Moves up to {@code PIPE_PHASE_MPU_PER_TRANSFER} mPU of each phase into
     * {@code target}. Returns the total amount accepted by the destination;
     * anything it could not take is put back into its own tank.
     */
    private long pushPhaseContents(PhaseStorage target) {
        long perPhase = Math.max(1L, PIPE_PHASE_MPU_PER_TRANSFER);
        long moved = 0L;
        for (Phase phase : Phase.all()) {
            long budget = Math.min(perPhase, phaseStorage.getAmount(phase));
            if (budget <= 0L) {
                continue;
            }
            long extracted = phaseStorage.extract(phase, budget, false);
            long accepted = target.insert(phase, extracted, false);
            if (accepted < extracted) {
                addPhaseInternal(phase, extracted - accepted);
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
        long perPhase = Math.max(1L, PIPE_PHASE_MPU_PER_TRANSFER);
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
        // The 95% conversion lives in separateMixedIntoTanks so the intake tower
        // can run the same step without an adjacent separator (it passes 100%,
        // lossless).
        long processed = separateMixedIntoTanks(SEPARATOR_MAX_INPUT_MPU_PER_TICK);
        if (processed <= 0L) {
            return;
        }
        energy -= SEPARATOR_FE_PER_TICK;
    }

    /**
     * Runs one 25 T -> 500 FE conversion; returns whether the machine actually
     * worked this tick.
     *
     * <p>A bare thermal generator has no byproduct: the Thermion is consumed and
     * only FE comes out. The impure-aetheron product path is kept behind
     * {@link #hasGeneratorUpgrade()}, which stays false until the machine upgrade
     * slot ships.</p>
     */
    private boolean tickGenerator() {
        if (energy > ENERGY_CAPACITY - GENERATOR_FE_PER_TICK) {
            return false;
        }
        int thermionIndex = Phase.THERMION.index();

        // Thermion comes from the phase tank; when the tank holds less than one
        // operation, take the whole input from the mixed buffer instead.
        boolean fromTank =
                phaseStorage.extract(Phase.THERMION, GENERATOR_INPUT_MPU_PER_TICK, true)
                        >= GENERATOR_INPUT_MPU_PER_TICK;
        if (!fromTank && mixed[thermionIndex] < GENERATOR_INPUT_MPU_PER_TICK) {
            return false;
        }

        if (fromTank) {
            phaseStorage.extract(Phase.THERMION, GENERATOR_INPUT_MPU_PER_TICK, false);
        } else {
            mixed[thermionIndex] -= GENERATOR_INPUT_MPU_PER_TICK;
            setChanged();
        }

        energy = Math.min(ENERGY_CAPACITY, energy + GENERATOR_FE_PER_TICK);

        // Byproduct: gated until a generator upgrade is installed. With the
        // upgrade the consumed Thermion will also yield impure Aetheron.
        if (hasGeneratorUpgrade()) {
            emitGeneratorByproduct();
        }
        return true;
    }

    /**
     * Whether this generator carries an upgrade that unlocks its byproduct. The
     * upgrade slot is reserved but not implemented yet, so this is always false:
     * a bare generator emits only FE.
     */
    public boolean hasGeneratorUpgrade() {
        return false;
    }

    /**
     * Emits one operation's worth of impure aetheron as the generator's
     * byproduct. Only reached once a generator upgrade exists; neighbour
     * storages are filled first, then the local tank, and only a full tank spills
     * into the mixed buffer.
     */
    private void emitGeneratorByproduct() {
        int productIndex = Phase.IMPURE_AETHERON.index();
        long remaining = GENERATOR_OUTPUT_MPU_PER_TICK;
        long pushed = pushToNeighbourStorages(Phase.IMPURE_AETHERON, remaining);
        remaining -= pushed;
        long tankSpace = Math.max(0L, Phase.IMPURE_AETHERON.tankCapacity() - phases[productIndex]);
        long tankAccepted = Math.min(remaining, tankSpace);
        long overflow = remaining - tankAccepted;

        if (tankAccepted > 0L) {
            addPhaseInternal(Phase.IMPURE_AETHERON, tankAccepted);
        }
        if (overflow > 0L) {
            long mixedRoom = Math.max(0L, MIXED_CAPACITY - getTotal());
            long mixedAccepted = Math.min(overflow, mixedRoom);
            if (mixedAccepted > 0L) {
                mixed[productIndex] += mixedAccepted;
                setChanged();
            }
        }
    }

    /**
     * Runs one generator tick for the self-test. The conversion itself is
     * level-independent (neighbour pushes are skipped when the entity has no
     * level), so the self-test can verify that a bare generator emits FE and no
     * byproduct.
     */
    public boolean runGeneratorTickForSelfTest() {
        return tickGenerator();
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
                    "FE %d/%d | T %d mPU",
                    energy, ENERGY_CAPACITY,
                    phases[Phase.THERMION.index()]));
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

    /**
     * Face-filtered view of the energy buffer for the ENERGY channel: IN faces
     * may receive, OUT faces may extract. The internal {@code null} side keeps
     * full access so the machine itself is unaffected by its configuration.
     */
    public IEnergyStorage energyStorageForSide(@Nullable Direction side) {
        ensureSideConfig();
        int index = side == null ? 6 : side.ordinal();
        IEnergyStorage cached = energyViews[index];
        if (cached == null) {
            cached = new FaceEnergyStorage(side);
            energyViews[index] = cached;
        }
        return cached;
    }

    /** Energy extraction used by the face view; the machine itself still only receives. */
    long extractEnergyInternal(long maxExtract, boolean simulate) {
        long moved = Math.min(Math.max(0L, maxExtract), energy);
        if (moved <= 0L) {
            return 0L;
        }
        if (!simulate) {
            energy -= moved;
            setChanged();
        }
        return moved;
    }

    private final class FaceEnergyStorage implements IEnergyStorage {
        private final Direction side;

        private FaceEnergyStorage(@Nullable Direction side) {
            this.side = side;
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            if (!allowsIn(side, SideChannel.ENERGY)) {
                return 0;
            }
            return MachineBlockEntity.this.receiveEnergy(maxReceive, simulate);
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            if (!allowsOut(side, SideChannel.ENERGY)) {
                return 0;
            }
            return (int) extractEnergyInternal(maxExtract, simulate);
        }

        @Override
        public int getEnergyStored() {
            return MachineBlockEntity.this.getEnergyStored();
        }

        @Override
        public int getMaxEnergyStored() {
            return MachineBlockEntity.this.getMaxEnergyStored();
        }

        @Override
        public boolean canExtract() {
            return allowsOut(side, SideChannel.ENERGY);
        }

        @Override
        public boolean canReceive() {
            return allowsIn(side, SideChannel.ENERGY);
        }
    }

    /**
     * Item slots exposed to automation. Only the intake tower has item slots;
     * other machines return {@code null} so the capability is absent. Slot 0
     * (filter cartridge) can never be extracted; slot 1 (canister) can.
     */
    @Nullable
    public IItemHandler itemHandlerForSide(@Nullable Direction side) {
        ensureSideConfig();
        if (getBlockState().getBlock() != MeteorosProtocol.PARTICLE_INTAKE.get()) {
            return null;
        }
        int index = side == null ? 6 : side.ordinal();
        IItemHandler cached = itemViews[index];
        if (cached == null) {
            cached = new FaceItemHandler(side);
            itemViews[index] = cached;
        }
        return cached;
    }

    private final class FaceItemHandler implements IItemHandler {
        private final Direction side;

        private FaceItemHandler(@Nullable Direction side) {
            this.side = side;
        }

        @Override
        public int getSlots() {
            return 2;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return slot >= 0 && slot < 2 ? items.getItem(slot) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty() || slot < 0 || slot >= 2) {
                return stack;
            }
            if (!allowsIn(side, SideChannel.ITEM) || !items.canPlaceItem(slot, stack)) {
                return stack;
            }
            ItemStack existing = items.getItem(slot);
            int limit = Math.min(items.getMaxStackSize(), stack.getMaxStackSize());
            if (!existing.isEmpty()) {
                if (!ItemStack.isSameItemSameComponents(existing, stack)) {
                    return stack;
                }
                limit -= existing.getCount();
            }
            if (limit <= 0) {
                return stack;
            }
            int moved = Math.min(limit, stack.getCount());
            if (!simulate) {
                if (existing.isEmpty()) {
                    items.setItem(slot, stack.copyWithCount(moved));
                } else {
                    existing.grow(moved);
                }
                items.setChanged();
                setChanged();
            }
            return stack.copyWithCount(stack.getCount() - moved);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            // Slot 0 holds the filter cartridge and is never extractable, so a
            // hopper cannot pull the tower's filter back out.
            if (slot != 1 || amount <= 0 || !allowsOut(side, SideChannel.ITEM)) {
                return ItemStack.EMPTY;
            }
            ItemStack existing = items.getItem(1);
            if (existing.isEmpty()) {
                return ItemStack.EMPTY;
            }
            int taken = Math.min(amount, existing.getCount());
            if (!simulate) {
                items.removeItem(1, taken);
                items.setChanged();
                setChanged();
            }
            return existing.copyWithCount(taken);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return items.canPlaceItem(slot, stack);
        }
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

    /** Master switch for this machine's active I/O pass (tickActiveIo). */
    public boolean autoIo() {
        return autoIo;
    }

    public void setAutoIo(boolean autoIo) {
        if (this.autoIo != autoIo) {
            this.autoIo = autoIo;
            setChanged();
        }
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
            case HIGH -> level != null && level.hasNeighborSignal(worldPosition);
            case LOW -> level == null || !level.hasNeighborSignal(worldPosition);
            case ALWAYS_ON -> true;
        };
    }

    /**
     * Phases this machine can actually consume or produce. The generator only
     * burns Thermion and (once upgraded) would produce impure Aetheron, so its
     * gauges currently show Thermion alone; the separator handles the whole
     * mixed buffer, and the weather blocks have no particle logic yet. Used by
     * the shared status screen to avoid drawing irrelevant gauges.
     */
    public List<Phase> relevantPhases() {
        Block block = getBlockState().getBlock();
        if (block == MeteorosProtocol.THERMAL_GENERATOR.get()) {
            return List.of(Phase.THERMION);
        }
        if (block == MeteorosProtocol.WEATHER_STATION.get() || block == MeteorosProtocol.WEATHER_TANK.get()) {
            return List.of();
        }
        return Phase.all();
    }

    /**
     * Default input whitelist: phases that external inserts may push into this
     * machine. Only the generator is picky (it burns Thermion; its impure
     * Aetheron byproduct needs a future upgrade); weather blocks accept nothing.
     * Internal production is not routed through the filtered entry points, so a
     * machine can still fill its own tanks.
     */
    public List<Phase> inputPhases() {
        Block block = getBlockState().getBlock();
        if (block == MeteorosProtocol.THERMAL_GENERATOR.get()) {
            return List.of(Phase.THERMION);
        }
        if (block == MeteorosProtocol.WEATHER_STATION.get() || block == MeteorosProtocol.WEATHER_TANK.get()) {
            return List.of();
        }
        return Phase.all();
    }

    /** {@code true} when {@code phase} may be pushed in from outside. */
    private boolean acceptsInput(Phase phase) {
        return inputPhases().contains(phase);
    }

    /**
     * Pushes up to {@code amount} of a freshly produced phase into adjacent
     * {@code PHASE_STORAGE}s (the capability has no direction context, so it is
     * queried with two arguments). Anything the neighbours cannot take is left
     * to the caller; returns the amount actually moved.
     */
    private long pushToNeighbourStorages(Phase phase, long amount) {
        if (amount <= 0L || level == null) {
            return 0L;
        }
        long remaining = amount;
        long moved = 0L;
        for (Direction direction : Direction.values()) {
            if (remaining <= 0L) {
                break;
            }
            PhaseStorage target = level.getCapability(
                    ParticleCapabilities.PHASE_STORAGE,
                    worldPosition.relative(direction)
            );
            if (target == null) {
                continue;
            }
            long accepted = target.insert(phase, remaining, false);
            if (accepted > 0L) {
                moved += accepted;
                remaining -= accepted;
            }
        }
        if (moved > 0L) {
            setChanged();
        }
        return moved;
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
     * Fills the canister in slot 1 with one phase, taking from that phase's tank
     * first and topping the remainder up from the mixed buffer. This is what the
     * intake GUI's extract button calls: storages are drained before the buffer.
     *
     * @return total mPU moved into the canister
     */
    public long fillCanister(Phase phase) {
        long moved = fillCanisterFromPhase(phase);
        ItemStack canister = items.getItem(1);
        boolean infiniteBound = canister.is(MeteorosProtocol.INFINITE_PARTICLE_CANISTER.get())
                && InfiniteParticleCanisterItem.hasPhase(canister);
        boolean normalFull = canister.is(MeteorosProtocol.CANISTER.get())
                && CanisterItem.getTotal(canister) >= CanisterItem.CAPACITY;
        if (infiniteBound || normalFull) {
            return moved;
        }
        return moved + fillCanisterFromMixed(phase);
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
            if (block == MeteorosProtocol.PARTICLE_PORT.get()) {
                return Component.translatable("container.meteorosprotocol.particle_port");
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

    /**
     * Unfiltered write into a phase tank, used by the machine's own production
     * paths (generator Cryon, separator output). External callers must go
     * through {@link #phaseStorage()} so the input whitelist applies.
     * Public only so the self-test can verify that internal writes bypass it.
     */
    public long addPhaseInternal(Phase phase, long amount) {
        long moved = phaseAccepted(phase, amount, phases[phase.index()]);
        if (moved > 0L) {
            phases[phase.index()] += moved;
            setChanged();
        }
        return moved;
    }

    /** Unfiltered write into the mixed buffer, for internal return paths. */
    private long insertMixedInternal(Phase phase, long amount) {
        long moved = mixedAccepted(amount, getTotal());
        if (moved > 0L) {
            mixed[phase.index()] += moved;
            setChanged();
        }
        return moved;
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
        if (!acceptsInput(phase)) {
            return 0L;
        }
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
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("MixedParticles", ParticleNbt.save(this).getCompound("Particles"));
        tag.put("PhaseParticles", ParticleNbt.save(phaseStorage).getCompound("Particles"));
        tag.putLong("SeparatorCarry", separatorCarry);
        tag.putLong("Energy", energy);
        tag.putLong("Progress", progress);
        ContainerHelper.saveAllItems(tag, items.getItems(), registries);
        tag.putBoolean("Enabled", enabled);
        tag.putBoolean("AutoIo", autoIo);
        tag.putString("RedstoneMode", redstoneMode.name());
        tag.putString("CanisterPhase", canisterPhase.id());
        ensureSideConfig();
        tag.putInt("SideMixed", sideMixedMask);
        tag.putInt("SidePhase", sidePhaseMask);
        tag.putInt("SideEnergy", sideEnergyMask);
        tag.putInt("SideItem", sideItemMask);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
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
        autoIo = !tag.contains("AutoIo") || tag.getBoolean("AutoIo");
        String savedRedstoneMode = tag.getString("RedstoneMode");
        try {
            redstoneMode = RedstoneMode.valueOf(savedRedstoneMode);
        } catch (IllegalArgumentException exception) {
            // Legacy S3/S4 saves used REDSTONE_CONTROL / IGNORE. The old
            // signal-gated mode maps to HIGH; IGNORE and unknown values keep the
            // machine running. HIGH/LOW already hit the new enum directly.
            redstoneMode = savedRedstoneMode.equals("REDSTONE_CONTROL")
                    ? RedstoneMode.HIGH
                    : RedstoneMode.ALWAYS_ON;
        }
        Phase savedCanisterPhase = Phase.byId(tag.getString("CanisterPhase"));
        canisterPhase = savedCanisterPhase != null ? savedCanisterPhase : Phase.AETHERON;

        // Older saves have no side configuration; ensureSideConfig() applies the
        // per-machine defaults, and a stored mask (if present) overrides it.
        ensureSideConfig();
        if (tag.contains("SideMixed", Tag.TAG_INT)) {
            sideMixedMask = tag.getInt("SideMixed");
        }
        if (tag.contains("SidePhase", Tag.TAG_INT)) {
            sidePhaseMask = tag.getInt("SidePhase");
        }
        if (tag.contains("SideEnergy", Tag.TAG_INT)) {
            sideEnergyMask = tag.getInt("SideEnergy");
        }
        if (tag.contains("SideItem", Tag.TAG_INT)) {
            sideItemMask = tag.getInt("SideItem");
        }
    }
}
