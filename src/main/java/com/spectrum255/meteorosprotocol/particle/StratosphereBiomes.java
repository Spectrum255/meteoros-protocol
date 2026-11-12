package com.spectrum255.meteorosprotocol.particle;

import com.spectrum255.meteorosprotocol.MeteorosProtocol;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundChunksBiomesPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.jetbrains.annotations.Nullable;

/**
 * Writes the {@code meteorosprotocol:stratosphere} biome above the configured
 * start height.
 *
 * <p>Two entry points share this code:</p>
 * <ul>
 *   <li>{@code mixin/StratosphereBiomeMixin} - injects into
 *       {@code ChunkGenerator#createBiomes}, so <em>newly generated</em> chunks
 *       come out of worldgen already carrying the biome;</li>
 *   <li>{@code particle/StratosphereChunkEvents} - listens to
 *       {@code ChunkEvent.Load}, so chunks generated before this mod (or before
 *       the configuration existed) are retrofitted when they load.</li>
 * </ul>
 *
 * <p>1.21.1 has no {@code ChunkAccess#setNoiseBiome}; biomes are written through
 * {@link ChunkAccess#fillBiomesFromNoise(BiomeResolver, Climate.Sampler)}. The
 * method therefore snapshots the existing biome palette, then refills the chunk
 * through a resolver that returns stratosphere above {@code startY} and the
 * snapshot below it.</p>
 */
public final class StratosphereBiomes {
    public static final ResourceKey<Biome> STRATOSPHERE = ResourceKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath(MeteorosProtocol.MODID, "stratosphere"));
    /** Quartets per chunk section (4x4x4). */
    private static final int QUARTETS_PER_SECTION = 64;
    private static boolean warnedMissingBiome;
    private static boolean loggedRetrofit;

    private StratosphereBiomes() {
    }

    public static boolean enabled() {
        return StratosphereParticleModifier.enabled();
    }

    public static int startY() {
        return StratosphereParticleModifier.startY();
    }

    /**
     * Whether this chunk can hold the stratosphere at all (its build limit must
     * reach above {@code startY}). The Nether and the End never match.
     */
    public static boolean appliesTo(ChunkAccess chunk) {
        if (!enabled()) {
            return false;
        }
        int startY = startY();
        if (chunk.getMaxBuildHeight() - 1 <= startY) {
            return false;
        }
        return firstStratosphereQuart() < QuartPos.fromBlock(chunk.getMaxBuildHeight());
    }

    /** First biome quartet at or above the stratosphere start height. */
    public static int firstStratosphereQuart() {
        return QuartPos.fromBlock(startY()) + 1;
    }

    /**
     * Rewrites every quartet above the start height to stratosphere.
     *
     * @return {@code true} when the chunk was actually rewritten (callers use
     *         this to decide whether clients need a biome resync)
     */
    public static boolean apply(ChunkAccess chunk, Registry<Biome> biomes) {
        if (!appliesTo(chunk)) {
            return false;
        }
        Holder.Reference<Biome> stratosphere = biomes.getHolder(STRATOSPHERE).orElse(null);
        if (stratosphere == null) {
            warnMissingBiomeOnce();
            return false;
        }

        LevelChunkSection[] sections = chunk.getSections();
        if (sections.length == 0) {
            return false;
        }
        // Fast path: the top section already carries the biome, so the chunk was
        // generated with this mod (or retrofitted on a previous load).
        LevelChunkSection top = sections[sections.length - 1];
        if (top != null && top.getNoiseBiome(0, 3, 0).value() == stratosphere.value()) {
            return false;
        }

        int firstStratosphereQuart = firstStratosphereQuart();
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
        dirty(chunk);
        return true;
    }

    /**
     * Sends the updated biome palettes to every player tracking the chunk.
     * Only needed for chunks that were already sent to clients (retrofit of an
     * existing save); freshly generated chunks go out with the biome already in
     * place.
     */
    public static void syncToTracking(ServerLevel level, LevelChunk chunk) {
        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) {
            return;
        }
        ClientboundChunksBiomesPacket packet = ClientboundChunksBiomesPacket.forChunks(List.of(chunk));
        for (ServerPlayer player : players) {
            player.connection.send(packet);
        }
    }

    /** Marks the chunk dirty so the rewrite survives the next save. */
    private static void dirty(ChunkAccess chunk) {
        if (chunk instanceof LevelChunk levelChunk) {
            levelChunk.setUnsaved(true);
        }
    }

    private static void warnMissingBiomeOnce() {
        if (warnedMissingBiome) {
            return;
        }
        warnedMissingBiome = true;
        MeteorosProtocol.LOGGER.warn("Biome {} is missing; stratosphere biome fill is disabled",
                STRATOSPHERE.location());
    }

    /** Logs the first successful retrofit so a test session can confirm it ran. */
    public static void logFirstRetrofit(ChunkAccess chunk) {
        if (loggedRetrofit) {
            return;
        }
        loggedRetrofit = true;
        MeteorosProtocol.LOGGER.info("Retrofitted stratosphere biome into chunk [{}, {}] (startY={})",
                chunk.getPos().x, chunk.getPos().z, startY());
    }

    @SuppressWarnings("unchecked")
    private static Holder<Biome>[] newBiomeSnapshot(int sectionCount) {
        return (Holder<Biome>[]) new Holder<?>[Math.max(0, sectionCount) * QUARTETS_PER_SECTION];
    }

    private static int snapshotIndex(int sectionIndex, int x, int y, int z) {
        return sectionIndex * QUARTETS_PER_SECTION + (x * 4 + y) * 4 + z;
    }

    @Nullable
    public static ResourceKey<Biome> stratosphereKey() {
        return STRATOSPHERE;
    }
}
