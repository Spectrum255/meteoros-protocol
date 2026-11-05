package com.spectrum255.meteorosprotocol.item;

import com.spectrum255.meteorosprotocol.MeteorosProtocol;
import com.spectrum255.meteorosprotocol.machine.MachineBlockEntity;
import com.spectrum255.meteorosprotocol.particle.MixedStorage;
import com.spectrum255.meteorosprotocol.particle.ParticleCapabilities;
import com.spectrum255.meteorosprotocol.particle.Phase;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Handheld creative particle source. A stack may be empty or hold exactly one
 * phase. Empty stacks are loaded like a normal canister: put one in the intake
 * tower's canister slot and press the take-out button. Once filled, right-click
 * a machine to fill its mixed buffer with the held phase.
 */
public class InfiniteParticleCanisterItem extends Item {
    public static final String PHASE_TAG = "ParticleType";

    public InfiniteParticleCanisterItem(Properties properties) {
        super(properties);
    }

    @Nullable
    public static Phase getSelectedPhase(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        return Phase.byId(data.copyTag().getString(PHASE_TAG));
    }

    public static boolean hasPhase(ItemStack stack) {
        return getSelectedPhase(stack) != null;
    }

    /** Passing null clears the phase and removes the tag. */
    public static void setSelectedPhase(ItemStack stack, @Nullable Phase phase) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = data == null ? new CompoundTag() : data.copyTag();
        if (phase == null) {
            tag.remove(PHASE_TAG);
        } else {
            tag.putString(PHASE_TAG, phase.id());
        }
        if (tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
        }
    }

    /** Creative-tab variant: one phase plus a searchable custom name. */
    public static ItemStack createFilled(Phase phase) {
        ItemStack stack = new ItemStack(MeteorosProtocol.INFINITE_PARTICLE_CANISTER.get());
        setSelectedPhase(stack, phase);
        stack.set(DataComponents.CUSTOM_NAME, Component.translatable(
                "item.meteorosprotocol.infinite_particle_canister.filled",
                phase.displayName()
        ));
        return stack;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Phase selected = getSelectedPhase(context.getItemInHand());
        if (selected == null) {
            if (!level.isClientSide) {
                context.getPlayer().displayClientMessage(
                        Component.translatable("tooltip.meteorosprotocol.infinite_particle_canister.load_hint"),
                        true
                );
            }
            return InteractionResult.SUCCESS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        MixedStorage storage = level.getCapability(
                ParticleCapabilities.MIXED_STORAGE,
                context.getClickedPos(),
                context.getClickedFace()
        );
        // Intake towers only expose their mixed buffer through the output port;
        // this creative item is meant to fill any machine from any side.
        if (!(storage instanceof MachineBlockEntity)
                && level.getBlockEntity(context.getClickedPos()) instanceof MachineBlockEntity machine) {
            storage = machine;
        }
        if (storage == null) {
            return InteractionResult.PASS;
        }
        long space = Math.max(0L, storage.getCapacity(selected) - storage.getTotal());
        if (space <= 0L) {
            return InteractionResult.PASS;
        }
        long moved = storage.insert(selected, space, false);
        return moved > 0L ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return 13;
    }

    @Override
    public int getBarColor(ItemStack stack) {
        Phase phase = getSelectedPhase(stack);
        return phase == null ? 0xFFFFFFFF : phase.argb();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        Phase selected = getSelectedPhase(stack);
        if (selected == null) {
            tooltip.add(Component.translatable("tooltip.meteorosprotocol.infinite_particle_canister.empty"));
            tooltip.add(Component.translatable("tooltip.meteorosprotocol.infinite_particle_canister.load_hint"));
            return;
        }
        tooltip.add(Component.translatable(
                "tooltip.meteorosprotocol.infinite_particle_canister.selected",
                selected.displayName()
        ));
        tooltip.add(Component.translatable("tooltip.meteorosprotocol.infinite_particle_canister.hint"));
    }
}
