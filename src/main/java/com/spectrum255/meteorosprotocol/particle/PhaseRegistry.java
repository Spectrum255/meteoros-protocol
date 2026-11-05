package com.spectrum255.meteorosprotocol.particle;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The phase registry ({@code meteorosprotocol:phase}).
 *
 * <p>The six core phases are declared in {@link Phase} as static constants and
 * registered here through suppliers returning those constants. Addon mods create
 * their own {@link DeferredRegister} with {@link #create(String)} and register
 * extra phases; nothing else has to change in this mod.</p>
 *
 * <p>Registration uses {@link DeferredRegister#makeRegistry}, which creates the
 * registry immediately and registers it on {@code NewRegistryEvent}. The
 * registry is created with {@code sync(true)}, so the phase ids are synced to
 * clients and addon phases are usable on both sides. Indices and the ordered
 * phase list only become valid at {@link #bootstrap()}, which runs once the
 * register events have finished (see {@code MeteorosProtocol}).</p>
 */
public final class PhaseRegistry {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final String NAMESPACE = Phase.CORE_NAMESPACE;
    public static final ResourceKey<Registry<Phase>> KEY =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(NAMESPACE, "phase"));

    /** The main mod's phase register. Call {@link #create(String)} from addons instead. */
    public static final DeferredRegister<Phase> PHASES = DeferredRegister.create(KEY, NAMESPACE);

    /** How long to wait before retrying a bootstrap that ran before the registry was ready. */
    private static final long RETRY_INTERVAL_NANOS = 1_000_000_000L;

    private static volatile List<Phase> ordered;
    private static volatile List<Phase> extras;
    private static volatile Map<ResourceLocation, Phase> byKey;
    private static volatile long nextBootstrapAttempt;
    private static volatile boolean pendingSyncWarned;

    static {
        PHASES.register("aetheron", () -> Phase.AETHERON);
        PHASES.register("thermion", () -> Phase.THERMION);
        PHASES.register("cryon", () -> Phase.CRYON);
        PHASES.register("voltix_pos", () -> Phase.VOLTIX_POS);
        PHASES.register("voltix_neg", () -> Phase.VOLTIX_NEG);
        PHASES.register("aquantum", () -> Phase.AQUANTUM);
        PHASES.makeRegistry(builder -> builder.sync(true));
    }

    private PhaseRegistry() {
    }

    /** Creates an addon-owned phase register sharing the main registry key. */
    public static DeferredRegister<Phase> create(String modId) {
        return DeferredRegister.create(KEY, modId);
    }

    /** Registers the main mod's phase entries on the mod event bus. */
    public static void register(IEventBus modBus) {
        PHASES.register(modBus);
    }

    /**
     * All phases in canonical order; never null. Until the registry has been
     * frozen this falls back to the six core phases, so client code (HUD, F3,
     * tooltips) keeps working even before the registry sync arrives.
     */
    public static List<Phase> all() {
        List<Phase> list = ordered;
        if (list != null) {
            return list;
        }
        return bootstrap();
    }

    /** Non-core phases in registration order; empty until the registry is frozen. */
    public static List<Phase> extraPhases() {
        List<Phase> list = ordered;
        if (list == null) {
            bootstrap();
            list = ordered;
        }
        return list == null ? List.of() : extras;
    }

    public static boolean isBootstrapped() {
        return ordered != null;
    }

    /**
     * Finalises indices and the ordered phase list. Idempotent and never throws:
     * when the registry is unavailable, empty, or missing one of the core phases
     * (the normal situation on a client before the registry sync arrives) it
     * warns once, leaves the registry unfrozen and returns the core phases. A
     * later call retries — after the sync that call freezes for real — so the
     * game keeps working and the client eventually sees addon phases too.
     */
    public static synchronized List<Phase> bootstrap() {
        List<Phase> existing = ordered;
        if (existing != null) {
            return existing;
        }
        long now = System.nanoTime();
        if (now < nextBootstrapAttempt) {
            return Phase.corePhases();
        }
        Registry<Phase> registry = PHASES.getRegistry().get();
        List<Phase> registered = new ArrayList<>();
        if (registry != null) {
            registry.iterator().forEachRemaining(registered::add);
        }
        List<Phase> core = Phase.corePhases();
        if (registry == null || registered.size() < core.size() || !registered.containsAll(core)) {
            deferBootstrap(now, registered.size());
            return core;
        }
        // Registration order: MappedRegistry hands out increasing ids in
        // registration order, so sort by id to stay deterministic even if the
        // registry's own iteration order ever changes.
        registered.sort(Comparator.comparingInt(registry::getId));

        Map<ResourceLocation, Phase> keys = new HashMap<>();
        for (Phase phase : registered) {
            keys.put(phase.key(), phase);
        }
        for (Phase phase : core) {
            if (keys.get(phase.key()) != phase) {
                // A different instance claimed a core id; wait for the real registry.
                deferBootstrap(now, registered.size());
                return core;
            }
        }

        boolean coreAtHead = registered.size() >= core.size()
                && registered.subList(0, core.size()).equals(core);
        List<Phase> canonical = new ArrayList<>(core);
        List<Phase> nonCore = new ArrayList<>();
        for (Phase phase : registered) {
            if (!phase.core()) {
                nonCore.add(phase);
            }
        }
        canonical.addAll(nonCore);
        for (int index = 0; index < canonical.size(); index++) {
            canonical.get(index).setIndex(index);
        }
        byKey = Map.copyOf(keys);
        extras = List.copyOf(nonCore);
        ordered = List.copyOf(canonical);
        pendingSyncWarned = false;
        if (!coreAtHead) {
            LOGGER.warn("Core phases were not at the head of the phase registry (an addon registered "
                    + "first); reordering them to keep the core indices 0..5 stable");
        }
        LOGGER.info("Particle phase registry bootstrapped: {} phases ({} core, {} addon)",
                ordered.size(), core.size(), extras.size());
        return ordered;
    }

    /** Throttles retries and logs the "waiting for sync" warning only once. */
    private static void deferBootstrap(long now, int registeredCount) {
        nextBootstrapAttempt = now + RETRY_INTERVAL_NANOS;
        if (!pendingSyncWarned) {
            pendingSyncWarned = true;
            LOGGER.warn("The meteorosprotocol:phase registry is not fully populated yet ({} entries); "
                    + "waiting for the registry sync before freezing phase indices", registeredCount);
        }
    }

    @Nullable
    static Phase byId(@Nullable String id) {
        if (id == null) {
            return null;
        }
        String trimmed = id.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        Map<ResourceLocation, Phase> keys = byKey;
        if (keys == null) {
            bootstrap();
            keys = byKey;
        }
        if (keys != null) {
            if (lower.indexOf(':') >= 0) {
                ResourceLocation parsed = ResourceLocation.tryParse(lower);
                return parsed == null ? null : keys.get(parsed);
            }
            Phase direct = keys.get(ResourceLocation.fromNamespaceAndPath(NAMESPACE, lower));
            if (direct != null) {
                return direct;
            }
            List<Phase> list = ordered;
            if (list != null) {
                for (Phase phase : list) {
                    if (phase.key().getPath().equals(lower)) {
                        return phase;
                    }
                }
            }
        }
        // Not frozen yet (client before sync): core ids must still resolve from
        // NBT and item data, by full id or by bare legacy path.
        for (Phase phase : Phase.corePhases()) {
            if (phase.id().equals(lower) || phase.key().getPath().equals(lower)) {
                return phase;
            }
        }
        return null;
    }
}
