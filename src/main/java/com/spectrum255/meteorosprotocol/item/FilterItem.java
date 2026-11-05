package com.spectrum255.meteorosprotocol.item;

import com.spectrum255.meteorosprotocol.menu.FilterMenu;
import com.spectrum255.meteorosprotocol.particle.Phase;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/**
 * Tunable selective cartridge. The selection is a bitmap with one bit per
 * registered phase (64 phases per long word), stored in the vanilla custom-data
 * component so the same stack keeps its tuning when moved between machines.
 *
 * <p>An empty/absent bitmap means "collect every registered phase". Saves from
 * before the phase registry used an {@code int} under the same key; it is
 * expanded into the bitmap on read. The GUI refuses to clear the last selected
 * bit.</p>
 */
public class FilterItem extends Item {
    /** NBT key; historically an {@code int}, now a {@code long[]} bitmap. */
    public static final String MASK_KEY = "FilterMask";
    private static final int WORD_BITS = Long.SIZE;

    public FilterItem(Properties properties) {
        super(properties);
    }

    /** Number of long words needed for the current registry. */
    public static int wordCount() {
        return Math.max(1, (Phase.all().size() + WORD_BITS - 1) / WORD_BITS);
    }

    /** Bitmap with every registered phase selected. */
    public static long[] fullMask() {
        int phases = Phase.all().size();
        long[] mask = new long[wordCount()];
        int remainder = phases % WORD_BITS;
        for (int word = 0; word < mask.length; word++) {
            mask[word] = -1L;
        }
        if (remainder != 0) {
            mask[mask.length - 1] = (1L << remainder) - 1L;
        }
        return mask;
    }

    /** Trims a bitmap to the current registry size and copies it. */
    public static long[] normalize(long[] raw) {
        long[] mask = new long[wordCount()];
        if (raw == null) {
            return fullMask();
        }
        System.arraycopy(raw, 0, mask, 0, Math.min(raw.length, mask.length));
        int phases = Phase.all().size();
        int remainder = phases % WORD_BITS;
        if (remainder != 0) {
            mask[mask.length - 1] &= (1L << remainder) - 1L;
        }
        return mask;
    }

    public static long[] getMask(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return fullMask();
        }
        CompoundTag tag = customData.copyTag();
        if (tag.contains(MASK_KEY, Tag.TAG_LONG_ARRAY)) {
            return normalize(tag.getLongArray(MASK_KEY));
        }
        if (tag.contains(MASK_KEY, Tag.TAG_INT)) {
            return fromLegacyInt(tag.getInt(MASK_KEY));
        }
        if (tag.contains(MASK_KEY, Tag.TAG_BYTE)) {
            return fromLegacyInt(tag.getByte(MASK_KEY));
        }
        return fullMask();
    }

    public static void setMask(ItemStack stack, long[] mask) {
        CompoundTag tag = new CompoundTag();
        tag.putLongArray(MASK_KEY, normalize(mask));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static boolean isSelected(ItemStack stack, Phase phase) {
        if (phase == null) {
            return false;
        }
        int index = phase.index();
        long[] mask = getMask(stack);
        int word = index / WORD_BITS;
        return word < mask.length && (mask[word] & (1L << (index % WORD_BITS))) != 0L;
    }

    public static void setSelected(ItemStack stack, Phase phase, boolean selected) {
        if (phase == null) {
            return;
        }
        long[] mask = getMask(stack);
        int index = phase.index();
        int word = index / WORD_BITS;
        if (word >= mask.length) {
            return;
        }
        long bit = 1L << (index % WORD_BITS);
        mask[word] = selected ? (mask[word] | bit) : (mask[word] & ~bit);
        setMask(stack, mask);
    }

    /** True when at least one phase is selected. */
    public static boolean hasAny(long[] mask) {
        for (long word : mask) {
            if (word != 0L) {
                return true;
            }
        }
        return false;
    }

    /** True when every registered phase is selected. */
    public static boolean isFull(long[] mask) {
        long[] full = fullMask();
        for (int word = 0; word < full.length; word++) {
            if ((word < mask.length ? mask[word] : 0L) != full[word]) {
                return false;
            }
        }
        return true;
    }

    /** Number of selected phases, for the GUI. */
    public static int selectedCount(long[] mask) {
        int count = 0;
        for (long word : mask) {
            count += Long.bitCount(word);
        }
        return count;
    }

    /** Legacy {@code int} bitmask: bit N meant the phase with ordinal N. */
    private static long[] fromLegacyInt(int legacyMask) {
        long[] mask = new long[wordCount()];
        int phases = Phase.all().size();
        for (int index = 0; index < Math.min(phases, Integer.SIZE); index++) {
            if ((legacyMask & (1 << index)) != 0) {
                mask[index / WORD_BITS] |= 1L << (index % WORD_BITS);
            }
        }
        return mask;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            player.openMenu(new SimpleMenuProvider(
                    (containerId, inventory, ignored) -> new FilterMenu(containerId, inventory, stack),
                    Component.translatable("container.meteorosprotocol.filter_cartridge")
            ));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return !isFull(getMask(stack));
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        long[] mask = getMask(stack);
        int total = Math.max(1, Phase.all().size());
        int selected = selectedCount(mask);
        return (int) Math.max(0L, Math.min(13L, Math.round(13.0 * selected / total)));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0xFF73FBFD;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        long[] mask = getMask(stack);
        if (isFull(mask)) {
            tooltip.add(Component.translatable("tooltip.meteorosprotocol.filter_cartridge.all")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(Component.translatable("tooltip.meteorosprotocol.filter_cartridge.selected")
                .withStyle(ChatFormatting.GRAY));
    }
}
