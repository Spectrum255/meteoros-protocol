package com.spectrum255.meteorosprotocol;

import com.spectrum255.meteorosprotocol.client.InfiniteParticleCellScreen;
import com.spectrum255.meteorosprotocol.client.FilterScreen;
import com.spectrum255.meteorosprotocol.client.IntakeScreen;
import com.spectrum255.meteorosprotocol.client.MachineStatusScreen;
import com.spectrum255.meteorosprotocol.client.ParticleIntakeRenderer;
import com.spectrum255.meteorosprotocol.client.tooltip.ParticleTooltip;
import com.spectrum255.meteorosprotocol.client.tooltip.ParticleTooltipRenderer;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = MeteorosProtocol.MODID, dist = Dist.CLIENT)
// You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
@EventBusSubscriber(modid = MeteorosProtocol.MODID, value = Dist.CLIENT)
public class MeteorosProtocolClient {
    public MeteorosProtocolClient(ModContainer container) {
        // Allows NeoForge to create a config screen for this mod's configs.
        // The config screen is accessed by going to the Mods screen > clicking on your mod > clicking on config.
        // Do not forget to add translations for your config options to the en_us.json file.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                MeteorosProtocol.MACHINE_BLOCK_ENTITY.get(),
                context -> new ParticleIntakeRenderer()
        );
    }

    @SubscribeEvent
    static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(MeteorosProtocol.INTAKE_MENU.get(), IntakeScreen::new);
        event.register(MeteorosProtocol.FILTER_MENU.get(), FilterScreen::new);
        event.register(MeteorosProtocol.INFINITE_PARTICLE_CELL_MENU.get(), InfiniteParticleCellScreen::new);
        event.register(MeteorosProtocol.MACHINE_STATUS_MENU.get(), MachineStatusScreen::new);
    }

    @SubscribeEvent
    static void registerTooltipComponents(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(ParticleTooltip.class, ParticleTooltipRenderer::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        // Some client setup code
        MeteorosProtocol.LOGGER.info("HELLO FROM CLIENT SETUP");
        MeteorosProtocol.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
    }
}
