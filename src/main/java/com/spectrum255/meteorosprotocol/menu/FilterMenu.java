package com.spectrum255.meteorosprotocol.menu;

import com.spectrum255.meteorosprotocol.MeteorosProtocol;
import com.spectrum255.meteorosprotocol.item.FilterItem;
import com.spectrum255.meteorosprotocol.particle.Phase;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;

/**
 * Checkbox tuning menu for the filter cartridge. It has no slots: the edited
 * stack is the one that opened the menu, and the selection bitmap is synced
 * through {@link ContainerData} (two int slots per long word).
 */
public class FilterMenu extends AbstractContainerMenu {
    private static final int SYNC_BITS = Integer.SIZE;

    private final ItemStack filter;
    private final boolean clientSide;
    private final int[] synced;
    private final ContainerData data;

    public FilterMenu(int containerId, Inventory inventory, ItemStack filter) {
        super(MeteorosProtocol.FILTER_MENU.get(), containerId);
        this.filter = filter;
        this.clientSide = inventory.player.level().isClientSide();
        this.synced = new int[syncCount()];
        this.data = new ContainerData() {
            @Override
            public int get(int index) {
                if (clientSide) {
                    return index >= 0 && index < synced.length ? synced[index] : 0;
                }
                long[] mask = FilterItem.getMask(filter);
                int word = index / 2;
                long value = word < mask.length ? mask[word] : 0L;
                return (index & 1) == 0 ? (int) (value & 0xFFFFFFFFL) : (int) (value >>> 32);
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

    public FilterMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf ignored) {
        this(containerId, inventory, ItemStack.EMPTY);
    }

    /** Two int slots per long word, enough for every registered phase. */
    private static int syncCount() {
        return Math.max(2, FilterItem.wordCount() * 2);
    }

    /** Selection bitmap as last synced. */
    public long[] mask() {
        long[] mask = new long[FilterItem.wordCount()];
        for (int word = 0; word < mask.length; word++) {
            long low = data.get(word * 2) & 0xFFFFFFFFL;
            long high = data.get(word * 2 + 1) & 0xFFFFFFFFL;
            mask[word] = (high << 32) | low;
        }
        return mask;
    }

    public boolean isSelected(Phase phase) {
        long[] mask = mask();
        int index = phase.index();
        int word = index / Long.SIZE;
        return word < mask.length && (mask[word] & (1L << (index % Long.SIZE))) != 0L;
    }

    /** Phase index used by the screen when a checkbox is clicked. */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (clientSide || filter.isEmpty() || id < 0 || id >= Phase.all().size()) {
            return false;
        }
        Phase phase = Phase.all().get(id);
        long[] mask = FilterItem.getMask(filter);
        int word = id / Long.SIZE;
        long bit = 1L << (id % Long.SIZE);
        long[] next = mask.clone();
        if (word >= next.length) {
            return false;
        }
        next[word] ^= bit;
        if (!FilterItem.hasAny(next)) {
            return false;
        }
        FilterItem.setMask(filter, next);
        this.broadcastChanges();
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return clientSide || !filter.isEmpty();
    }
}
