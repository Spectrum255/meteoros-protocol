package com.spectrum255.meteorosprotocol.particle;

import com.spectrum255.meteorosprotocol.MeteorosProtocol;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Retrofits the stratosphere biome into chunks that already exist in a save.
 *
 * <p>{@code ChunkGenerator#createBiomes} only runs during world generation, so a
 * world created before this mod (or before the height change) keeps its old
 * biomes forever. Loading such a chunk fires {@link ChunkEvent.Load}, and the
 * rewrite happens before the chunk is sent to clients, so no extra packet is
 * needed here.</p>
 */
@EventBusSubscriber(modid = MeteorosProtocol.MODID)
public final class StratosphereChunkEvents {
    private StratosphereChunkEvents() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        boolean biomePresent;
        try {
            biomePresent = level.registryAccess().registryOrThrow(Registries.BIOME)
                    .containsKey(StratosphereBiomes.STRATOSPHERE.location());
        } catch (RuntimeException exception) {
            biomePresent = false;
        }
        int startY = StratosphereParticleModifier.startY();
        MeteorosProtocol.LOGGER.info(
                "Stratosphere diagnostics: enabled={} startY={} minY={} height={} maxBuildHeight={} biomePresent={} applies={}",
                StratosphereParticleModifier.enabled(), startY,
                level.getMinBuildHeight(), level.getHeight(), level.getMaxBuildHeight(),
                biomePresent, level.getMaxBuildHeight() - 1 > startY);
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!level.dimension().equals(Level.OVERWORLD)) {
            return;
        }
        ChunkAccess chunk = event.getChunk();
        try {
            Registry<Biome> biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
            if (StratosphereBiomes.apply(chunk, biomes)) {
                StratosphereBiomes.logFirstRetrofit(chunk);
            }
        } catch (RuntimeException exception) {
            MeteorosProtocol.LOGGER.warn("Failed to retrofit the stratosphere biome into chunk [{}, {}]",
                    chunk.getPos().x, chunk.getPos().z, exception);
        }
    }
}
