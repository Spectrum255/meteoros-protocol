package com.spectrum255.meteorosprotocol.mixin;

import com.spectrum255.meteorosprotocol.MeteorosProtocol;
import com.spectrum255.meteorosprotocol.particle.StratosphereBiomes;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Forces every chunk quartet above the configured start height to the
 * {@code meteorosprotocol:stratosphere} biome for <em>newly generated</em>
 * chunks.
 *
 * <p>Chunks that already exist in a save never run worldgen again, so this
 * injector alone cannot retrofit an old world; see
 * {@code particle/StratosphereChunkEvents} and {@code /meteoros stratofix} for
 * that path.</p>
 */
@Mixin(ChunkGenerator.class)
public abstract class StratosphereBiomeMixin {
    @Inject(method = "createBiomes", at = @At("RETURN"), cancellable = true)
    private void meteorosprotocol$forceStratosphere(RandomState randomState, Blender blender,
                                                    StructureManager structureManager, ChunkAccess chunk,
                                                    CallbackInfoReturnable<CompletableFuture<ChunkAccess>> callback) {
        CompletableFuture<ChunkAccess> original = callback.getReturnValue();
        if (original == null) {
            return;
        }
        callback.setReturnValue(original.thenApply(generated -> {
            try {
                MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                if (server != null) {
                    Registry<Biome> biomes = server.registryAccess().registryOrThrow(Registries.BIOME);
                    StratosphereBiomes.apply(generated, biomes);
                }
            } catch (RuntimeException exception) {
                MeteorosProtocol.LOGGER.warn("Failed to force stratosphere biomes", exception);
            }
            return generated;
        }));
    }
}
