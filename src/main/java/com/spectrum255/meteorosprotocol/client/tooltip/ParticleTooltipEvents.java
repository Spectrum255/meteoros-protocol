package com.spectrum255.meteorosprotocol.client.tooltip;

import com.mojang.datafixers.util.Either;
import com.spectrum255.meteorosprotocol.MeteorosProtocol;
import com.spectrum255.meteorosprotocol.item.CanisterItem;
import com.spectrum255.meteorosprotocol.item.FilterItem;
import com.spectrum255.meteorosprotocol.item.InfiniteParticleCanisterItem;
import com.spectrum255.meteorosprotocol.particle.Phase;
import java.util.List;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Injects the {@link ParticleTooltip} bar right under the item name for the
 * three particle-storing items. The plain text totals stay in each item's
 * {@code appendHoverText}; this only adds the graphical breakdown.
 */
@EventBusSubscriber(modid = MeteorosProtocol.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class ParticleTooltipEvents {
    private ParticleTooltipEvents() {
    }

    @SubscribeEvent
    public static void onGatherTooltipComponents(RenderTooltipEvent.GatherComponents event) {
        @Nullable ParticleTooltip component = build(event.getItemStack());
        if (component == null) {
            return;
        }
        List<Either<FormattedText, TooltipComponent>> elements = event.getTooltipElements();
        elements.add(Math.min(1, elements.size()), Either.right(component));
    }

    @Nullable
    private static ParticleTooltip build(ItemStack stack) {
        if (stack.is(MeteorosProtocol.CANISTER.get())) {
            long[] amounts = CanisterItem.getAmounts(stack);
            long total = 0L;
            for (long amount : amounts) {
                total += amount;
            }
            return new ParticleTooltip(total, CanisterItem.CAPACITY, amounts, false);
        }
        if (stack.is(MeteorosProtocol.INFINITE_PARTICLE_CANISTER.get())) {
            long[] amounts = new long[Phase.all().size()];
            Phase phase = InfiniteParticleCanisterItem.getSelectedPhase(stack);
            if (phase == null) {
                return new ParticleTooltip(0L, 1L, amounts, false);
            }
            amounts[phase.index()] = 1L;
            return new ParticleTooltip(1L, 1L, amounts, true);
        }
        if (stack.is(MeteorosProtocol.FILTER_CARTRIDGE.get())) {
            List<Phase> phases = Phase.all();
            long[] amounts = new long[phases.size()];
            int selected = 0;
            for (Phase phase : phases) {
                if (FilterItem.isSelected(stack, phase)) {
                    amounts[phase.index()] = 1L;
                    selected++;
                }
            }
            return new ParticleTooltip(selected, phases.size(), amounts, false, true);
        }
        return null;
    }
}
