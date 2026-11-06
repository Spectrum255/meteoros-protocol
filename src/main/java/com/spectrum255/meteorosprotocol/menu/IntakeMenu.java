package com.spectrum255.meteorosprotocol.menu;

import com.spectrum255.meteorosprotocol.MeteorosProtocol;
import com.spectrum255.meteorosprotocol.client.gui.IntakeLayout;
import com.spectrum255.meteorosprotocol.item.CanisterItem;
import com.spectrum255.meteorosprotocol.item.InfiniteParticleCanisterItem;
import com.spectrum255.meteorosprotocol.machine.MachineBlockEntity;
import com.spectrum255.meteorosprotocol.particle.Phase;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Server-authored menu for the intake tower. The synced ints are, in order:
 * 0 energy, 1 mixed total, one slot per registered phase tank, one flags slot,
 * one canister-total slot, one slot per phase in the mixed buffer, and the
 * selected extraction phase index. With the six core phases this is 17 ints;
 * addon phases widen it automatically.
 */
public class IntakeMenu extends AbstractContainerMenu {
    public static final long ENERGY_CAPACITY = MachineBlockEntity.ENERGY_CAPACITY;

    private final BlockPos pos;
    private final MachineBlockEntity machine;
    private final Container container;
    private final boolean clientSide;
    private final int[] syncedData;
    private final int phaseCount;
    private final int flagsIndex;
    private final int canisterTotalIndex;
    private final int mixedBase;
    private final int selectionIndex;
    private ContainerData data;

    /** 2 header ints + phase tanks + flags + canister total + mixed buffer + selection. */
    public static int dataCount() {
        return 2 * Phase.all().size() + 5;
    }

    public IntakeMenu(int containerId, Inventory inventory, MachineBlockEntity machine) {
        this(containerId, inventory, machine.getBlockPos(), machine);
    }

    public IntakeMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), null);
    }

    private IntakeMenu(int containerId, Inventory inventory, BlockPos pos, MachineBlockEntity machine) {
        super(MeteorosProtocol.INTAKE_MENU.get(), containerId);
        this.pos = pos;
        this.clientSide = inventory.player.level().isClientSide();
        this.machine = machine != null
                ? machine
                : (inventory.player.level().getBlockEntity(pos) instanceof MachineBlockEntity found ? found : null);
        this.container = this.machine != null ? this.machine.items() : new SimpleContainer(2);
        this.phaseCount = Phase.all().size();
        this.flagsIndex = 2 + phaseCount;
        this.canisterTotalIndex = flagsIndex + 1;
        this.mixedBase = canisterTotalIndex + 1;
        this.selectionIndex = mixedBase + phaseCount;
        this.syncedData = new int[dataCount()];

        // Filter cartridge slot and canister loading slot.
        this.addSlot(new Slot(container, 0, IntakeLayout.FILTER_SLOT_X, IntakeLayout.FILTER_SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(MeteorosProtocol.FILTER_CARTRIDGE.get());
            }
        });
        this.addSlot(new Slot(container, 1, IntakeLayout.LOAD_SLOT_X, IntakeLayout.LOAD_SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return IntakeMenu.this.container.canPlaceItem(1, stack);
            }
        });

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
            return (int) Math.min(Integer.MAX_VALUE, machine.getTotal());
        }
        if (index >= 2 && index < flagsIndex) {
            return (int) Math.min(Integer.MAX_VALUE,
                    machine.phaseStorage().getAmount(Phase.all().get(index - 2)));
        }
        if (index == flagsIndex) {
            int flags = machine.isEnabled() ? 1 : 0;
            flags |= machine.redstoneMode().ordinal() << 1;
            flags |= machine.isActive() ? 8 : 0;
            return flags;
        }
        if (index == canisterTotalIndex) {
            return (int) Math.min(Integer.MAX_VALUE, CanisterItem.getTotal(machine.items().getItem(1)));
        }
        if (index >= mixedBase && index < selectionIndex) {
            return (int) Math.min(Integer.MAX_VALUE, machine.getAmount(Phase.all().get(index - mixedBase)));
        }
        if (index == selectionIndex) {
            return machine.canisterPhase().index();
        }
        return 0;
    }

    public int data(int index) {
        return this.data.get(index);
    }

    public long energy() {
        return data(0);
    }

    public long mixedTotal() {
        return data(1);
    }

    public long phaseAmount(int index) {
        return data(2 + index);
    }

    public long mixedAmount(int index) {
        return data(mixedBase + index);
    }

    public boolean enabled() {
        return (data(flagsIndex) & 1) != 0;
    }

    public int redstoneMode() {
        return (data(flagsIndex) >> 1) & 3;
    }

    public boolean active() {
        return (data(flagsIndex) & 8) != 0;
    }

    public long canisterTotal() {
        return data(canisterTotalIndex);
    }

    /** Index of the phase currently selected for extraction. */
    public int canisterPhaseIndex() {
        int value = data(selectionIndex);
        return value >= 0 && value < phaseCount ? value : 0;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (machine == null) {
            return false;
        }
        if (id >= 2 && id < 2 + phaseCount) {
            machine.setCanisterPhase(Phase.all().get(id - 2));
            this.broadcastChanges();
            return true;
        }
        if (id == 2 + phaseCount) {
            machine.fillCanister(machine.canisterPhase());
            this.broadcastChanges();
            return true;
        }
        switch (id) {
            case 0 -> machine.setEnabled(!machine.isEnabled());
            case 1 -> machine.cycleRedstoneMode();
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
        if (index < 2) {
            if (!this.moveItemStackTo(stack, 2, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (stack.is(MeteorosProtocol.FILTER_CARTRIDGE.get())) {
                if (!this.moveItemStackTo(stack, 0, 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (stack.is(MeteorosProtocol.CANISTER.get())
                    || (stack.is(MeteorosProtocol.INFINITE_PARTICLE_CANISTER.get())
                            && !InfiniteParticleCanisterItem.hasPhase(stack))) {
                if (!this.moveItemStackTo(stack, 1, 2, false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                return ItemStack.EMPTY;
            }
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
