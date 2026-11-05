package com.spectrum255.meteorosprotocol.particle;

import java.util.List;
import java.util.Objects;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * A particle phase. Since the phase registry rework this is a registry value
 * type, not an enum: the main mod registers the six core phases and addon mods
 * may register their own through {@link PhaseRegistry}.
 *
 * <p>The six core phases keep their compile-time constant names
 * ({@link #AETHERON} ... {@link #AQUANTUM}). They are created eagerly here and
 * registered by {@link PhaseRegistry} through suppliers that return these very
 * instances, so a static initialiser elsewhere may safely reference them. Only
 * the dense {@link #index()} becomes valid once {@link PhaseRegistry#bootstrap()}
 * has run (after the register events, at load-complete).</p>
 */
public final class Phase {
    /** Namespace of the core phases. */
    public static final String CORE_NAMESPACE = "meteorosprotocol";

    /** The six core phases, in their canonical historical order. */
    public static final Phase AETHERON = core("aetheron", 0xFFFFFF);
    public static final Phase THERMION = core("thermion", 0xFF4A3F);
    public static final Phase CRYON = core("cryon", 0x5FE8FF);
    public static final Phase VOLTIX_POS = core("voltix_pos", 0xFFE14A);
    public static final Phase VOLTIX_NEG = core("voltix_neg", 0x9A5CFF);
    public static final Phase AQUANTUM = core("aquantum", 0x3A54E0);

    private static final List<Phase> CORE_PHASES =
            List.of(AETHERON, THERMION, CRYON, VOLTIX_POS, VOLTIX_NEG, AQUANTUM);

    private final ResourceLocation key;
    private final int rgb;
    private final long tankCapacity;
    private final boolean atmospheric;
    private final boolean core;
    private final PhaseSampler sampler;
    private final Component customName;
    /** Dense registry index; -1 until {@link PhaseRegistry#bootstrap()} ran. */
    private int index = -1;

    private Phase(
            ResourceLocation key,
            int rgb,
            long tankCapacity,
            boolean atmospheric,
            boolean core,
            PhaseSampler sampler,
            @Nullable Component customName
    ) {
        this.key = Objects.requireNonNull(key, "key");
        this.rgb = rgb & 0xFFFFFF;
        this.tankCapacity = Math.max(0L, tankCapacity);
        this.atmospheric = atmospheric;
        this.core = core;
        this.sampler = sampler;
        this.customName = customName;
    }

    private static Phase core(String path, int rgb) {
        return new Builder(ResourceLocation.fromNamespaceAndPath(CORE_NAMESPACE, path))
                .color(rgb)
                .atmospheric(true)
                .core()
                .build();
    }

    public static Builder builder(ResourceLocation id) {
        return new Builder(id);
    }

    /** All registered phases, in registry order. Never null, never empty after bootstrap. */
    public static List<Phase> all() {
        return PhaseRegistry.all();
    }

    /** The six core phases, in their canonical order. */
    public static List<Phase> corePhases() {
        return CORE_PHASES;
    }

    /**
     * Looks a phase up by NBT id. A value containing {@code ':'} is a full key;
     * a bare value first tries the core namespace (legacy saves) and then falls
     * back to a path match across the whole registry.
     */
    @Nullable
    public static Phase byId(@Nullable String id) {
        return PhaseRegistry.byId(id);
    }

    public ResourceLocation key() {
        return key;
    }

    /** Full registry id, e.g. {@code meteorosprotocol:thermion}; use this in NBT. */
    public String id() {
        return key.toString();
    }

    /** Packed 0xRRGGBB display colour. */
    public int rgb() {
        return rgb;
    }

    /** Opaque ARGB display colour (0xFF alpha). */
    public int argb() {
        return 0xFF000000 | rgb;
    }

    public Component displayName() {
        return customName != null
                ? customName
                : Component.translatable("phase." + key.getNamespace() + "." + key.getPath());
    }

    /** Per-phase tank capacity inside a machine (default 8,000 PU). */
    public long tankCapacity() {
        return tankCapacity;
    }

    /** Whether the phase participates in the atmospheric field by default. */
    public boolean atmospheric() {
        return atmospheric;
    }

    /** Dense index inside the registry; only valid after bootstrap. */
    public int index() {
        if (index < 0) {
            throw new IllegalStateException("Phase " + key + " has no registry index yet; "
                    + "PhaseRegistry.bootstrap() has not run");
        }
        return index;
    }

    public boolean indexed() {
        return index >= 0;
    }

    /** True for the six built-in phases. */
    public boolean core() {
        return core;
    }

    /** Optional addon sampler; null for phases that have none. */
    @Nullable
    public PhaseSampler sampler() {
        return sampler;
    }

    void setIndex(int index) {
        this.index = index;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Phase phase && key.equals(phase.key);
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }

    @Override
    public String toString() {
        return id();
    }

    public static final class Builder {
        private final ResourceLocation id;
        private int rgb = 0xFFFFFF;
        private long tankCapacity = 8_000_000L;
        private boolean atmospheric;
        private boolean core;
        private PhaseSampler sampler;
        private Component displayName;

        private Builder(ResourceLocation id) {
            this.id = Objects.requireNonNull(id, "id");
        }

        public Builder color(int rgb) {
            this.rgb = rgb & 0xFFFFFF;
            return this;
        }

        public Builder tankCapacity(long tankCapacity) {
            this.tankCapacity = tankCapacity;
            return this;
        }

        public Builder atmospheric(boolean atmospheric) {
            this.atmospheric = atmospheric;
            return this;
        }

        public Builder displayName(Component displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder sampler(PhaseSampler sampler) {
            this.sampler = sampler;
            return this;
        }

        private Builder core() {
            this.core = true;
            return this;
        }

        public Phase build() {
            Phase phase = new Phase(id, rgb, tankCapacity, atmospheric, core, sampler, displayName);
            if (sampler != null) {
                PhaseSamplers.register(phase, sampler);
            }
            return phase;
        }
    }
}
