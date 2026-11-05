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
 * registry immediately and registers it on {@code NewRegistryEvent}. Indices and
 * the ordered phase list only become valid at {@link #bootstrap()}, which runs
 * once the register events have finished (see {@code MeteorosProtocol}).</p>
 */
public final class PhaseRegistry {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final String NAMESPACE = Phase.CORE_NAMESPACE;
    public static final ResourceKey<Registry<Phase>> KEY =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(NAMESPACE, "phase"));

    /** The main mod's phase register. Call {@link #create(String)} from addons instead. */
    public static final DeferredRegister<Phase> PHASES = DeferredRegister.create(KEY, NAMESPACE);

    private static volatile List<Phase> ordered;
    private static volatile List<Phase> extras;
    private static volatile Map<ResourceLocation, Phase> byKey;

    static {
        PHASES.register("aetheron", () -> Phase.AETHERON);
        PHASES.register("thermion", () -> Phase.THERMION);
        PHASES.register("cryon", () -> Phase.CRYON);
        PHASES.register("voltix_pos", () -> Phase.VOLTIX_POS);
        PHASES.register("voltix_neg", () -> Phase.VOLTIX_NEG);
        PHASES.register("aquantum", () -> Phase.AQUANTUM);
        PHASES.makeRegistry(builder -> {
            // The registry key comes from the DeferredRegister itself; the
            // builder only needs the default (empty) configuration.
        });
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

    /** All phases in registration order; triggers bootstrap on first use. */
    public static List<Phase> all() {
        List<Phase> list = ordered;
        if (list == null) {
            list = bootstrap();
        }
        return list;
    }

    /** Non-core phases in registration order. */
    public static List<Phase> extraPhases() {
        all();
        return extras;
    }

    public static boolean isBootstrapped() {
        return ordered != null;
    }

    /**
     * Finalises indices and the ordered phase list. Idempotent; may be called
     * from the load-complete handler and lazily from {@link #all()}.
     */
    public static synchronized List<Phase> bootstrap() {
        List<Phase> existing = ordered;
        if (existing != null) {
            return existing;
        }
        Registry<Phase> registry = PHASES.getRegistry().get();
        if (registry == null) {
            throw new IllegalStateException("The meteorosprotocol:phase registry is not available yet");
        }
        List<Phase> list = new ArrayList<>();
        registry.iterator().forEachRemaining(list::add);
        if (list.isEmpty()) {
            throw new IllegalStateException("The meteorosprotocol:phase registry is empty; "
                    + "bootstrap() ran before the register events finished");
        }
        // Registration order: MappedRegistry hands out increasing ids in
        // registration order, so sort by id to stay deterministic even if the
        // registry's own iteration order ever changes.
        list.sort(Comparator.comparingInt(registry::getId));

        Map<ResourceLocation, Phase> keys = new HashMap<>();
        for (int index = 0; index < list.size(); index++) {
            Phase phase = list.get(index);
            phase.setIndex(index);
            keys.put(phase.key(), phase);
        }
        validateCore(list, keys);

        List<Phase> nonCore = new ArrayList<>(Math.max(0, list.size() - Phase.corePhases().size()));
        for (Phase phase : list) {
            if (!phase.core()) {
                nonCore.add(phase);
            }
        }
        byKey = Map.copyOf(keys);
        extras = List.copyOf(nonCore);
        ordered = List.copyOf(list);
        LOGGER.info("Particle phase registry bootstrapped: {} phases ({} core, {} addon)",
                ordered.size(), Phase.corePhases().size(), extras.size());
        return ordered;
    }

    private static void validateCore(List<Phase> list, Map<ResourceLocation, Phase> keys) {
        for (Phase core : Phase.corePhases()) {
            Phase registered = keys.get(core.key());
            if (registered == null) {
                throw new IllegalStateException("Core phase " + core.id() + " is missing from the phase registry");
            }
            if (registered != core) {
                throw new IllegalStateException("Core phase " + core.id()
                        + " was replaced by a different Phase instance during registration");
            }
        }
        int previous = -1;
        for (Phase core : Phase.corePhases()) {
            int index = core.index();
            if (index <= previous) {
                throw new IllegalStateException("Core phases are not in their canonical order ("
                        + core.id() + " has index " + index + " after " + previous + ")");
            }
            previous = index;
        }
        if (list.size() < Phase.corePhases().size() || !list.subList(0, Phase.corePhases().size())
                .equals(Phase.corePhases())) {
            LOGGER.warn("Core phases are not at the head of the phase registry; "
                    + "addon phases registered before meteorosprotocol. This is supported, "
                    + "but display order will put addon phases first");
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
            all();
            keys = byKey;
        }
        if (lower.indexOf(':') >= 0) {
            ResourceLocation parsed = ResourceLocation.tryParse(lower);
            return parsed == null ? null : keys.get(parsed);
        }
        ResourceLocation core = ResourceLocation.fromNamespaceAndPath(NAMESPACE, lower);
        Phase direct = keys.get(core);
        if (direct != null) {
            return direct;
        }
        for (Phase phase : all()) {
            if (phase.key().getPath().equals(lower)) {
                return phase;
            }
        }
        return null;
    }
}
