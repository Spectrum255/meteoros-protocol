package com.spectrum255.meteorosprotocol.item;

import com.spectrum255.meteorosprotocol.MeteorosProtocol;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

/**
 * Handheld particle concentration detector. Right-click opens the nearby
 * concentration map on the client. The client screen class is loaded
 * reflectively so a dedicated server never resolves client-only types.
 */
public class ParticleDetectorItem extends Item {
    private static final String CLIENT_HOOKS =
            "com.spectrum255.meteorosprotocol.client.DetectorClientHooks";

    public ParticleDetectorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide && FMLEnvironment.dist == Dist.CLIENT) {
            try {
                Class.forName(CLIENT_HOOKS).getMethod("openMap").invoke(null);
            } catch (ReflectiveOperationException | LinkageError exception) {
                MeteorosProtocol.LOGGER.warn("Could not open the particle detector map", exception);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
