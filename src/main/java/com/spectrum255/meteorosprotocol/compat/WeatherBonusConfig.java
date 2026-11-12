package com.spectrum255.meteorosprotocol.compat;

import com.spectrum255.localizedrain.api.WeatherType;
import com.spectrum255.localizedrain.api.WeatherTypes;
import com.spectrum255.meteorosprotocol.MeteorosProtocol;
import com.spectrum255.meteorosprotocol.compat.WeatherBonusRules.Entry;
import com.spectrum255.meteorosprotocol.compat.WeatherBonusRules.Rules;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Owns {@code config/meteorosprotocol-weather.toml}.
 *
 * <p>Localized rain 0.2.x lets add-ons register weather types at runtime, so this file
 * cannot be a {@code ModConfigSpec}: its keys are fixed at compile time. The file is
 * therefore assembled and parsed by hand (UTF-8, {@code \n}, sorted ids) and only
 * rewritten when the registry adds a type or changes a read-only mirror field.</p>
 *
 * <p>Never read the file from a tick path: {@link #load(MinecraftServer)} builds an
 * identity cache once per server start (or on {@code /meteoros weather reload}).</p>
 */
public final class WeatherBonusConfig {
    public static final String FILE_NAME = "meteorosprotocol-weather.toml";

    private static final String GENERAL = "general";
    private static final String TYPES = "types";
    private static final Set<String> GENERAL_KEYS = Set.of("enabled", "auto_add_new_types");
    private static final Set<String> READ_ONLY_KEYS = Set.of(
            "display_key", "has_precipitation", "precipitation", "allows_thunder");
    private static final Set<String> RULE_KEYS = Set.of(
            "thermion", "cryon", "aquantum", "aetheron", "charge_density");

    private static volatile boolean enabled = true;
    private static volatile boolean autoAddNewTypes = true;
    private static volatile Map<ResourceLocation, Entry> entries = Map.of();
    private static volatile Map<WeatherType, Rules> cache = Map.of();
    private static volatile int lastAdded;
    private static volatile int lastLoaded;

    private WeatherBonusConfig() {
    }

    public static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
    }

    public static boolean enabled() {
        return enabled;
    }

    public static boolean autoAddNewTypes() {
        return autoAddNewTypes;
    }

    public static int lastAdded() {
        return lastAdded;
    }

    public static int lastLoaded() {
        return lastLoaded;
    }

    /** Rules for a registry type, or {@code null} when the type was not loaded. */
    public static Rules rulesFor(WeatherType type) {
        if (type == null) {
            return null;
        }
        return cache.get(type);
    }

    /** Immutable snapshot, sorted by id, for commands and diagnostics. */
    public static Map<ResourceLocation, Entry> snapshot() {
        return entries;
    }

    /**
     * Scans the registry, merges the file and rebuilds the identity cache.
     * Safe to call again (the reload command does).
     */
    public static void load(MinecraftServer server) {
        if (server == null) {
            return;
        }
        Registry<WeatherType> registry;
        try {
            registry = server.registryAccess().registryOrThrow(WeatherTypes.REGISTRY_KEY);
        } catch (RuntimeException exception) {
            MeteorosProtocol.LOGGER.warn("Weather particle bonuses: weather type registry unavailable", exception);
            return;
        }

        Map<ResourceLocation, WeatherType> types = new TreeMap<>();
        for (Map.Entry<net.minecraft.resources.ResourceKey<WeatherType>, WeatherType> e : registry.entrySet()) {
            types.put(e.getKey().location(), e.getValue());
        }

        Path path = file();
        boolean fileExists = Files.isRegularFile(path);
        Parsed parsed = fileExists ? parseFile(path) : Parsed.empty();
        if (!fileExists) {
            enabled = true;
            autoAddNewTypes = true;
        } else {
            enabled = parsed.enabled;
            autoAddNewTypes = parsed.autoAddNewTypes;
        }

        MergeResult merged = merge(parsed.entries, types, autoAddNewTypes);
        boolean write = !fileExists || merged.added() > 0 || merged.readOnlyChanged() > 0;
        entries = Collections.unmodifiableMap(new LinkedHashMap<>(merged.entries()));
        lastAdded = merged.added();
        lastLoaded = types.size();

        if (write) {
            try {
                Files.createDirectories(path.getParent());
                Files.writeString(path, render(entries, enabled, autoAddNewTypes), StandardCharsets.UTF_8);
            } catch (IOException exception) {
                MeteorosProtocol.LOGGER.warn("Failed to write {}", path, exception);
            }
        }

        Map<WeatherType, Rules> identity = new IdentityHashMap<>();
        for (Map.Entry<ResourceLocation, WeatherType> e : types.entrySet()) {
            Entry entry = entries.get(e.getKey());
            if (entry != null) {
                identity.put(e.getValue(), entry.rules());
            }
        }
        cache = identity;

        MeteorosProtocol.LOGGER.info("weather particle bonuses loaded: {} types, added {}",
                types.size(), merged.added());
    }

    /** Reload entry point for {@code /meteoros weather reload}. */
    public static void reload(MinecraftServer server) {
        load(server);
    }

    /**
     * Merges registry types into the parsed entries. Missing types are added with
     * defaults when {@code autoAdd} is true (the {@code auto_add_new_types} switch);
     * existing rules are preserved while the four read-only mirror fields are refreshed.
     */
    public static MergeResult merge(Map<ResourceLocation, Entry> existing,
                                    Map<ResourceLocation, WeatherType> types,
                                    boolean autoAdd) {
        Map<ResourceLocation, Entry> result = new LinkedHashMap<>(new TreeMap<>(existing));
        int added = 0;
        int changed = 0;
        for (Map.Entry<ResourceLocation, WeatherType> e : types.entrySet()) {
            ResourceLocation id = e.getKey();
            WeatherType type = e.getValue();
            Entry old = result.get(id);
            if (old == null) {
                if (!autoAdd) {
                    // The player asked us not to extend the file; the type simply keeps
                    // the empty rule set, so it never changes the field until it is
                    // written into the file (manually or after switching the switch on).
                    continue;
                }
                result.put(id, Entry.fromType(type, WeatherBonusRules.defaultsFor(type)));
                added++;
                continue;
            }
            Entry refreshed = Entry.fromType(type, old.rules());
            if (!old.matchesType(type)) {
                result.put(id, refreshed);
                changed++;
            }
        }
        return new MergeResult(new TreeMap<>(result), added, changed);
    }

    /** Convenience overload that always extends the file (used by the self-test). */
    public static MergeResult merge(Map<ResourceLocation, Entry> existing,
                                    Map<ResourceLocation, WeatherType> types) {
        return merge(existing, types, true);
    }

    // ------------------------------------------------------------------
    // Writing
    // ------------------------------------------------------------------

    public static String render(Map<ResourceLocation, Entry> entries, boolean enabled, boolean autoAdd) {
        StringBuilder out = new StringBuilder(4096);
        out.append("# Meteoros Protocol — 天气粒子加成\n");
        out.append("# 本文件由游戏自动维护：每次服务器启动扫描已注册天气，把新增类型补进来。\n");
        out.append("# 已有条目的数值不会被覆盖；删除某个条目会按默认值重建。\n");
        out.append("# 单位：六相浓度增量（与 FieldSample 同单位），直接加在采样结果上。\n");
        out.append('\n');
        out.append("[general]\n");
        out.append('\t').append("enabled = ").append(enabled)
                .append("              # 关闭后所有天气加成都不生效（平流层锁不受影响）\n");
        out.append('\t').append("auto_add_new_types = ").append(autoAdd)
                .append("   # 启动时是否自动补入新注册的天气类型\n");

        for (Map.Entry<ResourceLocation, Entry> e : new TreeMap<>(entries).entrySet()) {
            Entry entry = e.getValue();
            out.append('\n');
            out.append("[types.\"").append(e.getKey()).append("\"]\n");
            out.append('\t').append("# 只读信息（每次启动刷新，改这些不生效）\n");
            out.append('\t').append("display_key = \"").append(escape(entry.displayKey())).append("\"\n");
            out.append('\t').append("has_precipitation = ").append(entry.hasPrecipitation()).append('\n');
            out.append('\t').append("precipitation = \"").append(escape(entry.precipitation())).append("\"\n");
            out.append('\t').append("allows_thunder = ").append(entry.allowsThunder()).append('\n');
            out.append('\t').append("# 可调：粒子加成\n");
            out.append('\t').append("thermion = ").append(number(entry.rules().thermion())).append('\n');
            out.append('\t').append("cryon = ").append(number(entry.rules().cryon())).append('\n');
            out.append('\t').append("aquantum = ").append(number(entry.rules().aquantum())).append('\n');
            out.append('\t').append("aetheron = ").append(number(entry.rules().aetheron())).append('\n');
            out.append('\t').append("charge_density = ").append(number(entry.rules().chargeDensity())).append('\n');
        }
        return out.toString();
    }

    private static String number(float value) {
        if (value == Math.rint(value) && Math.abs(value) < 1_000_000.0F) {
            return String.format(Locale.ROOT, "%.1f", value);
        }
        return Float.toString(value);
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // ------------------------------------------------------------------
    // Parsing
    // ------------------------------------------------------------------

    public record Parsed(boolean enabled, boolean autoAddNewTypes,
                         Map<ResourceLocation, Entry> entries) {
        static Parsed empty() {
            return new Parsed(true, true, Map.of());
        }
    }

    public static Parsed parseFile(Path path) {
        try {
            return parse(Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            MeteorosProtocol.LOGGER.warn("Failed to read {}", path, exception);
            return Parsed.empty();
        }
    }

    /**
     * Tolerant hand-written parser: unknown lines and keys warn once and are skipped;
     * nothing here throws on malformed input.
     */
    public static Parsed parse(String text) {
        boolean parsedEnabled = true;
        boolean parsedAuto = true;
        Map<ResourceLocation, Entry> result = new TreeMap<>();
        Set<String> warned = new TreeSet<>();

        String section = null;
        ResourceLocation typeId = null;
        String displayKey = "";
        boolean hasPrecipitation = false;
        String precipitation = "NONE";
        boolean allowsThunder = false;
        float thermion = 0.0F;
        float cryon = 0.0F;
        float aquantum = 0.0F;
        float aetheron = 0.0F;
        float chargeDensity = 0.0F;

        for (String rawLine : text.split("\\R", -1)) {
            String line = stripComment(rawLine).trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("[") && line.endsWith("]")) {
                flushType(result, typeId, displayKey, hasPrecipitation, precipitation,
                        allowsThunder, thermion, cryon, aquantum, aetheron, chargeDensity);
                section = line.substring(1, line.length() - 1).trim();
                typeId = null;
                displayKey = "";
                hasPrecipitation = false;
                precipitation = "NONE";
                allowsThunder = false;
                thermion = cryon = aquantum = aetheron = chargeDensity = 0.0F;
                if (section.startsWith(TYPES + ".")) {
                    String quoted = section.substring(TYPES.length() + 1);
                    if (quoted.startsWith("\"") && quoted.endsWith("\"") && quoted.length() > 1) {
                        quoted = quoted.substring(1, quoted.length() - 1);
                    }
                    ResourceLocation id = ResourceLocation.tryParse(quoted);
                    if (id == null) {
                        warnOnce(warned, "section " + section, "invalid weather type id");
                    } else {
                        typeId = id;
                    }
                } else if (!section.equals(GENERAL)) {
                    warnOnce(warned, "section " + section, "unknown section");
                }
                continue;
            }

            int equals = line.indexOf('=');
            if (equals < 0) {
                warnOnce(warned, "line " + line, "expected key = value");
                continue;
            }
            String key = line.substring(0, equals).trim();
            String value = line.substring(equals + 1).trim();

            if (GENERAL.equals(section)) {
                if ("enabled".equals(key)) {
                    Boolean parsedValue = parseBoolean(value);
                    if (parsedValue != null) {
                        parsedEnabled = parsedValue;
                    } else {
                        warnOnce(warned, key, "expected boolean");
                    }
                } else if ("auto_add_new_types".equals(key)) {
                    Boolean parsedValue = parseBoolean(value);
                    if (parsedValue != null) {
                        parsedAuto = parsedValue;
                    } else {
                        warnOnce(warned, key, "expected boolean");
                    }
                } else {
                    warnOnce(warned, key, "unknown general key");
                }
                continue;
            }

            if (typeId == null) {
                warnOnce(warned, "key " + key, "key outside of a known section");
                continue;
            }
            if (READ_ONLY_KEYS.contains(key)) {
                switch (key) {
                    case "display_key" -> {
                        String parsed = parseString(value);
                        if (parsed == null) {
                            warnOnce(warned, key, "expected quoted string");
                        } else {
                            displayKey = parsed;
                        }
                    }
                    case "has_precipitation" -> {
                        Boolean parsed = parseBoolean(value);
                        if (parsed == null) {
                            warnOnce(warned, key, "expected boolean");
                        } else {
                            hasPrecipitation = parsed;
                        }
                    }
                    case "precipitation" -> {
                        String parsed = parseString(value);
                        if (parsed == null) {
                            warnOnce(warned, key, "expected quoted string");
                        } else {
                            precipitation = parsed;
                        }
                    }
                    case "allows_thunder" -> {
                        Boolean parsed = parseBoolean(value);
                        if (parsed == null) {
                            warnOnce(warned, key, "expected boolean");
                        } else {
                            allowsThunder = parsed;
                        }
                    }
                    default -> warnOnce(warned, key, "unknown key");
                }
                continue;
            }
            if (!RULE_KEYS.contains(key)) {
                warnOnce(warned, key, "unknown key");
                continue;
            }
            Float parsed = parseFloat(value);
            if (parsed == null) {
                warnOnce(warned, key, "expected number");
                continue;
            }
            switch (key) {
                case "thermion" -> thermion = parsed;
                case "cryon" -> cryon = parsed;
                case "aquantum" -> aquantum = parsed;
                case "aetheron" -> aetheron = parsed;
                case "charge_density" -> chargeDensity = parsed;
                default -> warnOnce(warned, key, "unknown key");
            }
        }

        flushType(result, typeId, displayKey, hasPrecipitation, precipitation,
                allowsThunder, thermion, cryon, aquantum, aetheron, chargeDensity);
        return new Parsed(parsedEnabled, parsedAuto, result);
    }

    private static void flushType(Map<ResourceLocation, Entry> result, ResourceLocation id,
                                  String displayKey, boolean hasPrecipitation, String precipitation,
                                  boolean allowsThunder, float thermion, float cryon,
                                  float aquantum, float aetheron, float chargeDensity) {
        if (id == null) {
            return;
        }
        result.put(id, new Entry(displayKey, hasPrecipitation, precipitation, allowsThunder,
                new Rules(thermion, cryon, aquantum, aetheron, chargeDensity)));
    }

    private static void warnOnce(Set<String> warned, String key, String problem) {
        if (warned.add(key)) {
            MeteorosProtocol.LOGGER.warn("Weather particle bonuses: {} for '{}'", problem, key);
        }
    }

    private static String stripComment(String line) {
        boolean quoted = false;
        boolean escaped = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\' && quoted) {
                escaped = true;
                continue;
            }
            if (c == '"') {
                quoted = !quoted;
                continue;
            }
            if (c == '#' && !quoted) {
                return line.substring(0, i);
            }
        }
        return line;
    }

    private static Boolean parseBoolean(String value) {
        if ("true".equalsIgnoreCase(value)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(value)) {
            return Boolean.FALSE;
        }
        return null;
    }

    private static Float parseFloat(String value) {
        try {
            float parsed = Float.parseFloat(value);
            return Float.isFinite(parsed) ? parsed : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String parseString(String value) {
        if (value.length() < 2 || !value.startsWith("\"") || !value.endsWith("\"")) {
            return null;
        }
        String body = value.substring(1, value.length() - 1);
        StringBuilder out = new StringBuilder(body.length());
        boolean escaped = false;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (escaped) {
                out.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /** Result of merging registry types into the file entries. */
    public record MergeResult(Map<ResourceLocation, Entry> entries, int added, int readOnlyChanged) {
    }

    /** Exposed for the self-test: the sorted registry ids are stable in the file. */
    public static List<ResourceLocation> sortedIds(Map<ResourceLocation, ?> map) {
        return new ArrayList<>(new TreeSet<>(map.keySet()));
    }
}
