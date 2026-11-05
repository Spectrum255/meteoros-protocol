package com.spectrum255.meteorosprotocol.machine;

import com.spectrum255.meteorosprotocol.MeteorosProtocol;
import com.spectrum255.meteorosprotocol.menu.InfiniteParticleCellMenu;
import com.spectrum255.meteorosprotocol.particle.MixedStorage;
import com.spectrum255.meteorosprotocol.particle.Phase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Creative particle source/sink with six independently configured faces.
 *
 * <p>Each face is OFF, INPUT (trash can) or OUTPUT (infinite source of one
 * selected phase). The block itself is not a storage: all capabilities go
 * through the per-face views returned by {@link #storageForSide(Direction)}.</p>
 */
public class CreativeParticleBlockEntity extends BlockEntity implements MenuProvider {
    public static final long TOTAL = Integer.MAX_VALUE;
    public static final Direction[] SIDES = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH,
            Direction.WEST, Direction.UP, Direction.DOWN
    };
    public static final int SIDE_COUNT = SIDES.length;

    private static final String SIDE_MODES_TAG = "SideModes";
    private static final String OUTPUT_PHASES_TAG = "OutputPhases";
    private static final String LEGACY_SELECTED_PHASE_TAG = "SelectedPhase";

    public enum SideMode {
        OFF,
        INPUT,
        OUTPUT
    }

    private final SideMode[] sideModes = new SideMode[SIDE_COUNT];
    private final Phase[] outputPhases = new Phase[SIDE_COUNT];
    private final SideView[] views = new SideView[SIDE_COUNT];
    private final SideView offView = new SideView(-1);

    public CreativeParticleBlockEntity(BlockPos pos, BlockState state) {
        super(MeteorosProtocol.CREATIVE_PARTICLE_BLOCK_ENTITY.get(), pos, state);
        for (int index = 0; index < SIDE_COUNT; index++) {
            sideModes[index] = SideMode.OUTPUT;
            outputPhases[index] = Phase.all().get(index % Phase.all().size());
            views[index] = new SideView(index);
        }
    }

    public SideMode sideMode(int index) {
        return index >= 0 && index < SIDE_COUNT ? sideModes[index] : SideMode.OFF;
    }

    public Phase outputPhase(int index) {
        return index >= 0 && index < SIDE_COUNT ? outputPhases[index] : Phase.THERMION;
    }

    public void cycleSideMode(int index) {
        if (index < 0 || index >= SIDE_COUNT) {
            return;
        }
        sideModes[index] = SideMode.values()[(sideModes[index].ordinal() + 1) % SideMode.values().length];
        setChanged();
    }

    public void setOutputPhase(int index, Phase phase) {
        if (index < 0 || index >= SIDE_COUNT || phase == null || outputPhases[index] == phase) {
            return;
        }
        outputPhases[index] = phase;
        setChanged();
    }

    /** Per-face capability view. A null face (unsided query) behaves as OFF. */
    public MixedStorage storageForSide(@Nullable Direction side) {
        int index = sideIndex(side);
        return index < 0 ? offView : views[index];
    }

    private static int sideIndex(@Nullable Direction side) {
        if (side == null) {
            return -1;
        }
        for (int index = 0; index < SIDE_COUNT; index++) {
            if (SIDES[index] == side) {
                return index;
            }
        }
        return -1;
    }

    private final class SideView implements MixedStorage {
        private final int index;

        private SideView(int index) {
            this.index = index;
        }

        private SideMode mode() {
            return index < 0 ? SideMode.OFF : sideModes[index];
        }

        private Phase selected() {
            return index < 0 ? Phase.THERMION : outputPhases[index];
        }

        @Override
        public long getAmount(Phase phase) {
            return mode() == SideMode.OUTPUT && phase == selected() ? TOTAL : 0L;
        }

        @Override
        public long getCapacity(Phase phase) {
            return TOTAL;
        }

        @Override
        public long getTotal() {
            return mode() == SideMode.OUTPUT ? TOTAL : 0L;
        }

        @Override
        public float[] shares() {
            float[] shares = new float[Phase.all().size()];
            if (mode() == SideMode.OUTPUT) {
                shares[selected().index()] = 1.0F;
            }
            return shares;
        }

        /** INPUT destroys anything; OUTPUT and OFF accept nothing. */
        @Override
        public long insert(Phase phase, long amount, boolean simulate) {
            return mode() == SideMode.INPUT ? Math.max(0L, amount) : 0L;
        }

        @Override
        public long extract(Phase phase, long amount, boolean simulate) {
            if (mode() != SideMode.OUTPUT || phase != selected()) {
                return 0L;
            }
            return Math.max(0L, Math.min(amount, TOTAL));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        byte[] modes = new byte[SIDE_COUNT];
        ListTag phases = new ListTag();
        for (int index = 0; index < SIDE_COUNT; index++) {
            modes[index] = (byte) sideModes[index].ordinal();
            phases.add(StringTag.valueOf(outputPhases[index].id()));
        }
        tag.putByteArray(SIDE_MODES_TAG, modes);
        tag.put(OUTPUT_PHASES_TAG, phases);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);

        byte[] modes = tag.getByteArray(SIDE_MODES_TAG);
        if (modes.length == SIDE_COUNT) {
            for (int index = 0; index < SIDE_COUNT; index++) {
                int ordinal = modes[index];
                sideModes[index] = ordinal >= 0 && ordinal < SideMode.values().length
                        ? SideMode.values()[ordinal]
                        : SideMode.OUTPUT;
            }
        } else if (tag.contains(LEGACY_SELECTED_PHASE_TAG, Tag.TAG_STRING)) {
            Phase legacy = Phase.byId(tag.getString(LEGACY_SELECTED_PHASE_TAG));
            Phase phase = legacy != null ? legacy : Phase.THERMION;
            for (int index = 0; index < SIDE_COUNT; index++) {
                sideModes[index] = SideMode.OUTPUT;
                outputPhases[index] = phase;
            }
        }

        ListTag phases = tag.getList(OUTPUT_PHASES_TAG, Tag.TAG_STRING);
        int count = Math.min(phases.size(), SIDE_COUNT);
        for (int index = 0; index < count; index++) {
            Phase loaded = Phase.byId(phases.getString(index));
            if (loaded != null) {
                outputPhases[index] = loaded;
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.meteorosprotocol.infinite_particle_cell");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new InfiniteParticleCellMenu(containerId, inventory, this);
    }

    @Override
    public void writeClientSideData(AbstractContainerMenu menu, RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(worldPosition);
    }
}
