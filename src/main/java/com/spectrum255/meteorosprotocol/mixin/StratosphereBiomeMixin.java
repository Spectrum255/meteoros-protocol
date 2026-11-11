package com.spectrum255.meteorosprotocol.mixin;

import com.spectrum255.meteorosprotocol.MeteorosProtocol;
import com.spectrum255.meteorosprotocol.particle.StratosphereParticleModifier;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Forces every chunk quartet above the configured start height to the
 * {@code meteorosprotocol:stratosphere} biome.
 *
 * <p>1.21.1 has no {@code ChunkAccess#setNoiseBiome}; biomes are written through
 * {@link ChunkAccess#fillBiomesFromNoise(BiomeResolver, net.minecraft.world.level.biome.Climate.Sampler)}.
 * The injector therefore snapshots the freshly generated biome palette, then
 * refills the chunk through a resolver that returns stratosphere above
 * {@code startY} and the snapshot below it. Chunks whose build height does not
 * reach the start height (the Nether, the End, short modded dimensions) are
 * skipped, so this is effectively overworld-only.</p>
 */
@Mixin(ChunkGenerator.class)
public abstract class StratosphereBiomeMixin {
    private static final ResourceKey<Biome> STRATOSPHERE = ResourceKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath("meteorosprotocol", "stratosphere"));
    private static final int QUARTETS_PER_SECTION = 64;
    private static boolean warnedMissingBiome;

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
                applyStratosphereBiomes(generated);
            } catch (RuntimeException exception) {
                MeteorosProtocol.LOGGER.warn("Failed to force stratosphere biomes", exception);
            }
            return generated;
        }));
    }

    private static void applyStratosphereBiomes(ChunkAccess chunk) {
        if (!StratosphereParticleModifier.enabled()) {
            return;
        }
        int startY = StratosphereParticleModifier.startY();
        // Dimensions whose build limit stops below the start height never match.
        if (chunk.getMaxBuildHeight() - 1 <= startY) {
            return;
        }
        int firstStratosphereQuart = QuartPos.fromBlock(startY) + 1;
        if (firstStratosphereQuart >= QuartPos.fromBlock(chunk.getMaxBuildHeight())) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        Registry<Biome> biomes = server.registryAccess().registryOrThrow(Registries.BIOME);
        Holder.Reference<Biome> stratosphere = biomes.getHolder(STRATOSPHERE).orElse(null);
        if (stratosphere == null) {
            if (!warnedMissingBiome) {
                warnedMissingBiome = true;
                MeteorosProtocol.LOGGER.warn("Biome {} is missing; stratosphere biome fill is disabled",
                        STRATOSPHERE.location());
            }
            return;
        }

        LevelChunkSection[] sections = chunk.getSections();
        Holder<Biome>[] snapshot = newBiomeSnapshot(sections.length);
        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            LevelChunkSection section = sections[sectionIndex];
            if (section == null) {
                continue;
            }
            for (int x = 0; x < 4; x++) {
                for (int y = 0; y < 4; y++) {
                    for (int z = 0; z < 4; z++) {
                        snapshot[snapshotIndex(sectionIndex, x, y, z)] = section.getNoiseBiome(x, y, z);
                    }
                }
            }
        }
        if (snapshot.length == 0) {
            return;
        }

        Holder<Biome> fallback = biomes.getHolderOrThrow(Biomes.PLAINS);
        BiomeResolver resolver = (x, y, z, sampler) -> {
            if (y >= firstStratosphereQuart) {
                return stratosphere;
            }
            int sectionIndex = chunk.getSectionIndexFromSectionY(QuartPos.toSection(y));
            if (sectionIndex < 0 || sectionIndex >= sections.length) {
                return fallback;
            }
            Holder<Biome> previous = snapshot[snapshotIndex(sectionIndex, x & 3, y & 3, z & 3)];
            return previous != null ? previous : fallback;
        };
        // The sampler is only forwarded to the resolver, which ignores it.
        chunk.fillBiomesFromNoise(resolver, null);
    }

    @SuppressWarnings("unchecked")
    private static Holder<Biome>[] newBiomeSnapshot(int sectionCount) {
        return (Holder<Biome>[]) new Holder<?>[Math.max(0, sectionCount) * QUARTETS_PER_SECTION];
    }

    private static int snapshotIndex(int sectionIndex, int x, int y, int z) {
        return sectionIndex * QUARTETS_PER_SECTION + (x * 4 + y) * 4 + z;
    }
}
