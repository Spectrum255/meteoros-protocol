package com.spectrum255.meteorosprotocol.menu;

import com.spectrum255.meteorosprotocol.MeteorosProtocol;
import com.spectrum255.meteorosprotocol.machine.CreativeParticleBlockEntity;
import com.spectrum255.meteorosprotocol.particle.Phase;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;

/**
 * Slotless six-face configuration menu for the creative particle cell.
 *
 * <p>Synced data: first {@code SIDE_COUNT} ints = SideMode ordinal, next
 * {@code SIDE_COUNT} ints = output phase index. Button ids: 0..5 cycle that
 * face's mode; {@code 100 + side * phaseCount + phaseIndex} sets that face's
 * output phase. The client screen keeps its own activeSide/scroll state and
 * only sends these ids.</p>
 */
public class InfiniteParticleCellMenu extends AbstractContainerMenu {
    public static final int SIDE_COUNT = CreativeParticleBlockEntity.SIDE_COUNT;
    public static final int MODE_DATA_OFFSET = 0;
    public static final int PHASE_DATA_OFFSET = SIDE_COUNT;
    public static final int PHASE_BUTTON_BASE = 100;

    private final BlockPos pos;
    private final CreativeParticleBlockEntity machine;
    private final boolean clientSide;
    private final int phaseCount;
    private final int[] synced;
    private final ContainerData data;

    public static int dataCount() {
        return SIDE_COUNT * 2;
    }

    public InfiniteParticleCellMenu(int containerId, Inventory inventory, CreativeParticleBlockEntity machine) {
        this(containerId, inventory, machine.getBlockPos(), machine);
    }

    public InfiniteParticleCellMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), null);
    }

    private InfiniteParticleCellMenu(int containerId, Inventory inventory, BlockPos pos,
                                     CreativeParticleBlockEntity machine) {
        super(MeteorosProtocol.INFINITE_PARTICLE_CELL_MENU.get(), containerId);
        this.pos = pos;
        this.clientSide = inventory.player.level().isClientSide();
        this.machine = machine != null
                ? machine
                : (inventory.player.level().getBlockEntity(pos) instanceof CreativeParticleBlockEntity found
                        ? found
                        : null);
        this.phaseCount = Phase.all().size();
        this.synced = new int[dataCount()];
        this.data = new ContainerData() {
            @Override
            public int get(int index) {
                return clientSide ? synced[index] : serverValue(index);
            }

            @Override
            public void set(int index, int value) {
                if (clientSide && index >= 0 && index < synced.length) {
                    synced[index] = value;
                }
            }

            @Override
            public int getCount() {
                return synced.length;
            }
        };
        this.addDataSlots(data);
    }

    private int serverValue(int index) {
        if (machine == null) {
            return 0;
        }
        if (index >= MODE_DATA_OFFSET && index < MODE_DATA_OFFSET + SIDE_COUNT) {
            return machine.sideMode(index - MODE_DATA_OFFSET).ordinal();
        }
        if (index >= PHASE_DATA_OFFSET && index < PHASE_DATA_OFFSET + SIDE_COUNT) {
            return machine.outputPhase(index - PHASE_DATA_OFFSET).index();
        }
        return 0;
    }

    public int data(int index) {
        return this.data.get(index);
    }

    public CreativeParticleBlockEntity.SideMode sideMode(int side) {
        int ordinal = data(MODE_DATA_OFFSET + side);
        CreativeParticleBlockEntity.SideMode[] modes = CreativeParticleBlockEntity.SideMode.values();
        return ordinal >= 0 && ordinal < modes.length
                ? modes[ordinal]
                : CreativeParticleBlockEntity.SideMode.OFF;
    }

    public Phase outputPhase(int side) {
        int index = data(PHASE_DATA_OFFSET + side);
        return index >= 0 && index < phaseCount ? Phase.all().get(index) : Phase.THERMION;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (clientSide || machine == null) {
            return false;
        }
        if (id >= 0 && id < SIDE_COUNT) {
            machine.cycleSideMode(id);
            this.broadcastChanges();
            return true;
        }
        if (id >= PHASE_BUTTON_BASE) {
            int encoded = id - PHASE_BUTTON_BASE;
            int side = encoded / phaseCount;
            int phase = encoded % phaseCount;
            if (side >= 0 && side < SIDE_COUNT && phase >= 0 && phase < phaseCount) {
                machine.setOutputPhase(side, Phase.all().get(phase));
                this.broadcastChanges();
                return true;
            }
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        if (machine == null || machine.isRemoved()) {
            return false;
        }
        if (player.level().getBlockEntity(pos) != machine) {
            return false;
        }
        return player.distanceToSqr(
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5
        ) <= 64.0;
    }
}
