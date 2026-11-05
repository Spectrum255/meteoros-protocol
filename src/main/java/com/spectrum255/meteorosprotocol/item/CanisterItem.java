package com.spectrum255.meteorosprotocol.item;

import com.spectrum255.meteorosprotocol.particle.ParticleNbt;
import com.spectrum255.meteorosprotocol.particle.Phase;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

/**
 * A 1,000 PU particle canister. Contents live in the vanilla custom-data
 * component as {@code {Particles:{unit:"mPU", <phase>:<long>...}}} so the
 * same payload can be moved between machines and items.
 *
 * <p>Amounts are indexed by {@link Phase#index()}. New saves use the full
 * registry id as the key; saves from before the phase registry used the bare
 * path, which {@link ParticleNbt#readAmount} still accepts.</p>
 */
public class CanisterItem extends Item {
    public static final long CAPACITY = 1_000_000L;

    public CanisterItem(Properties properties) {
        super(properties);
    }

    /** Per-phase mPU, in {@link Phase#all()} order. */
    public static long[] getAmounts(ItemStack stack) {
        long[] amounts = new long[Phase.all().size()];
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return amounts;
        }
        CompoundTag particles = customData.copyTag().getCompound("Particles");
        boolean legacyPu = !"mPU".equals(particles.getString("unit"));
        for (Phase phase : Phase.all()) {
            amounts[phase.index()] = ParticleNbt.readAmount(particles, phase, legacyPu);
        }
        return amounts;
    }

    public static long getTotal(ItemStack stack) {
        long total = 0L;
        for (long amount : getAmounts(stack)) {
            total += amount;
        }
        return total;
    }

    /** Writes phase amounts, clamping each phase so the total cannot exceed capacity. */
    public static void setAmounts(ItemStack stack, long[] amounts) {
        CompoundTag particles = new CompoundTag();
        particles.putString("unit", "mPU");
        long remaining = CAPACITY;
        for (Phase phase : Phase.all()) {
            long amount = Math.max(0L, Math.min(amounts[phase.index()], remaining));
            particles.putLong(phase.id(), amount);
            remaining -= amount;
        }
        CompoundTag root = new CompoundTag();
        root.put("Particles", particles);
        CustomData.set(DataComponents.CUSTOM_DATA, stack, root);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        long total = getTotal(stack);
        tooltip.add(Component.translatable("tooltip.meteorosprotocol.canister.total", total, CAPACITY)
                .withStyle(ChatFormatting.GRAY));
        long[] amounts = getAmounts(stack);
        for (Phase phase : Phase.all()) {
            if (amounts[phase.index()] > 0L) {
                tooltip.add(Component.translatable(
                        "tooltip.meteorosprotocol.canister.phase",
                        phase.displayName(),
                        amounts[phase.index()]
                ).withStyle(ChatFormatting.DARK_GRAY));
            }
        }
    }
}
