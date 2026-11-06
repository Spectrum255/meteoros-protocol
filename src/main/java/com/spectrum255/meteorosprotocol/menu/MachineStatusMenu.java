package com.spectrum255.meteorosprotocol.menu;

import com.spectrum255.meteorosprotocol.MeteorosProtocol;
import com.spectrum255.meteorosprotocol.client.gui.IntakeLayout;
import com.spectrum255.meteorosprotocol.machine.MachineBlock;
import com.spectrum255.meteorosprotocol.machine.MachineBlockEntity;
import com.spectrum255.meteorosprotocol.machine.SideChannel;
import com.spectrum255.meteorosprotocol.particle.Phase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * Shared status menu for the machines without their own layout (thermal
 * generator, phase separator, weather station, weather tank). It has no item
 * slots: the synced ints are, in order, 0 energy, 1 flags, 2 mixed total, one
 * slot per phase in the mixed buffer, then one slot per phase tank. With six
 * core phases this is 15 ints; addon phases widen it automatically.
 *
 * <p>Flags layout (index 1): bit 0 enabled, bits 1-2 redstone mode, bit 3
 * active, bits 4-6 machine kind (0 generic, 1 thermal generator, 2 phase
 * separator, 3 weather station/tank), bit 7 hide the mixed-buffer gauge. The
 * kind lets the client pick which phases are worth showing without inventing
 * new data indices.
 */
public class MachineStatusMenu extends AbstractContainerMenu {
    public static final int ENERGY_CAPACITY = (int) MachineBlockEntity.ENERGY_CAPACITY;

    /** Value of the kind field stored in bits 4-6 of the flags int. */
    public static final int KIND_GENERIC = 0;
    public static final int KIND_GENERATOR = 1;
    public static final int KIND_SEPARATOR = 2;
    public static final int KIND_WEATHER = 3;

    /**
     * Side-config buttons: 100 + channel*8 + side cycles forward, 200 + ... back.
     * The range is disjoint from the existing 0/1 control ids.
     */
    public static final int SIDE_BUTTON_BASE = 100;

    private final BlockPos pos;
    private final MachineBlockEntity machine;
    private final boolean clientSide;
    private final int[] syncedData;
    private final int phaseCount;
    private final int mixedBase = 3;
    private final int tankBase;
    private final int mixedMaskIndex;
    private final int phaseMaskIndex;
    private final int energyMaskIndex;
    private final int itemMaskIndex;
    private final int autoIoIndex;
    private ContainerData data;

    /** 3 header ints + mixed buffer + phase tanks + four side masks + auto I/O. */
    public static int dataCount() {
        return 3 + 2 * Phase.all().size() + 5;
    }

    public MachineStatusMenu(int containerId, Inventory inventory, MachineBlockEntity machine) {
        this(containerId, inventory, machine.getBlockPos(), machine);
    }

    public MachineStatusMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), null);
    }

    private MachineStatusMenu(int containerId, Inventory inventory, BlockPos pos, MachineBlockEntity machine) {
        super(com.spectrum255.meteorosprotocol.MeteorosProtocol.MACHINE_STATUS_MENU.get(), containerId);
        this.pos = pos;
        this.clientSide = inventory.player.level().isClientSide();
        this.machine = machine != null
                ? machine
                : (inventory.player.level().getBlockEntity(pos) instanceof MachineBlockEntity found ? found : null);
        this.phaseCount = Phase.all().size();
        this.tankBase = mixedBase + phaseCount;
        this.mixedMaskIndex = tankBase + phaseCount;
        this.phaseMaskIndex = mixedMaskIndex + 1;
        this.energyMaskIndex = phaseMaskIndex + 1;
        this.itemMaskIndex = energyMaskIndex + 1;
        this.autoIoIndex = itemMaskIndex + 1;
        this.syncedData = new int[dataCount()];

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inventory, col + row * 9 + 9,
                        IntakeLayout.INVENTORY_X + col * IntakeLayout.INVENTORY_STEP,
                        IntakeLayout.INVENTORY_Y + row * IntakeLayout.INVENTORY_STEP));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inventory, col,
                    IntakeLayout.INVENTORY_X + col * IntakeLayout.INVENTORY_STEP,
                    IntakeLayout.HOTBAR_Y));
        }

        this.data = new ContainerData() {
            @Override
            public int get(int index) {
                return clientSide ? syncedData[index] : serverValue(index);
            }

            @Override
            public void set(int index, int value) {
                if (clientSide) {
                    syncedData[index] = value;
                }
            }

            @Override
            public int getCount() {
                return syncedData.length;
            }
        };
        this.addDataSlots(data);
    }

    private int serverValue(int index) {
        if (machine == null) {
            return 0;
        }
        if (index == 0) {
            return (int) Math.min(Integer.MAX_VALUE, machine.energy());
        }
        if (index == 1) {
            int flags = machine.isEnabled() ? 1 : 0;
            flags |= machine.redstoneMode().ordinal() << 1;
            flags |= activeFlag() ? 8 : 0;
            flags |= machineKind() << 4;
            flags |= machineKind() == KIND_SEPARATOR ? 0x80 : 0;
            return flags;
        }
        if (index == 2) {
            return (int) Math.min(Integer.MAX_VALUE, machine.getTotal());
        }
        if (index >= mixedBase && index < tankBase) {
            return (int) Math.min(Integer.MAX_VALUE,
                    machine.getAmount(Phase.all().get(index - mixedBase)));
        }
        if (index >= tankBase && index < tankBase + phaseCount) {
            return (int) Math.min(Integer.MAX_VALUE,
                    machine.phaseStorage().getAmount(Phase.all().get(index - tankBase)));
        }
        if (index == mixedMaskIndex) {
            return machine.mixedSideMask();
        }
        if (index == phaseMaskIndex) {
            return machine.phaseSideMask();
        }
        if (index == energyMaskIndex) {
            return machine.energySideMask();
        }
        if (index == itemMaskIndex) {
            return machine.itemSideMask();
        }
        if (index == autoIoIndex) {
            return machine.autoIo() ? 1 : 0;
        }
        return 0;
    }

    private boolean activeFlag() {
        if (machine == null) {
            return false;
        }
        return machine.getBlockState().hasProperty(MachineBlock.ACTIVE)
                && machine.getBlockState().getValue(MachineBlock.ACTIVE);
    }

    /** Encodes the machine family into the 3 high bits of the flags int. */
    private int machineKind() {
        if (machine == null) {
            return KIND_GENERIC;
        }
        Block block = machine.getBlockState().getBlock();
        if (block == MeteorosProtocol.THERMAL_GENERATOR.get()) {
            return KIND_GENERATOR;
        }
        if (block == MeteorosProtocol.PHASE_SEPARATOR.get()) {
            return KIND_SEPARATOR;
        }
        if (block == MeteorosProtocol.WEATHER_STATION.get() || block == MeteorosProtocol.WEATHER_TANK.get()) {
            return KIND_WEATHER;
        }
        return KIND_GENERIC;
    }

    public int kind() {
        return (data(1) >> 4) & 7;
    }

    /** True when the mixed buffer is internal plumbing and its gauge should be hidden. */
    public boolean hidesMixedBuffer() {
        return (data(1) & 0x80) != 0;
    }

    /** Phases this machine can actually consume or produce; weather blocks use none. */
    public List<Phase> relevantPhases() {
        return switch (kind()) {
            case KIND_GENERATOR -> List.of(Phase.THERMION, Phase.CRYON);
            case KIND_WEATHER -> List.of();
            default -> Phase.all();
        };
    }

    public int data(int index) {
        return this.data.get(index);
    }

    public long energy() {
        return data(0);
    }

    public boolean enabled() {
        return (data(1) & 1) != 0;
    }

    public int redstoneMode() {
        return (data(1) >> 1) & 3;
    }

    public boolean active() {
        return (data(1) & 8) != 0;
    }

    public long mixedTotal() {
        return data(2);
    }

    public long mixedAmount(int index) {
        return data(mixedBase + index);
    }

    public long tankAmount(int index) {
        return data(tankBase + index);
    }

    public int mixedMask() {
        return data(mixedMaskIndex);
    }

    public int phaseMask() {
        return data(phaseMaskIndex);
    }

    public int energyMask() {
        return data(energyMaskIndex);
    }

    public int itemMask() {
        return data(itemMaskIndex);
    }

    /** Master switch for the machine's active I/O pass. */
    public boolean autoIo() {
        return data(autoIoIndex) != 0;
    }

    /** Unified accessor: one mask per {@link SideChannel}, in channel order. */
    public int sideMask(SideChannel channel) {
        return switch (channel) {
            case MIXED -> mixedMask();
            case PHASE -> phaseMask();
            case ENERGY -> energyMask();
            case ITEM -> itemMask();
        };
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (machine == null) {
            return false;
        }
        if (id >= SIDE_BUTTON_BASE && id < SIDE_BUTTON_BASE + 200) {
            boolean backwards = id >= SIDE_BUTTON_BASE + 100;
            int encoded = id - (backwards ? SIDE_BUTTON_BASE + 100 : SIDE_BUTTON_BASE);
            int channel = encoded / 8;
            int side = encoded % 8;
            if (channel >= SideChannel.values().length || side >= Direction.values().length) {
                return false;
            }
            machine.cycleSideMode(
                    Direction.values()[side],
                    SideChannel.values()[channel],
                    backwards);
            this.broadcastChanges();
            return true;
        }
        switch (id) {
            case 0 -> machine.setEnabled(!machine.isEnabled());
            case 1 -> machine.cycleRedstoneMode();
            case 9 -> machine.setAutoIo(!machine.autoIo());
            default -> {
                return false;
            }
        }
        this.broadcastChanges();
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < 27) {
            if (!this.moveItemStackTo(stack, 27, 36, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(stack, 0, 27, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
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
