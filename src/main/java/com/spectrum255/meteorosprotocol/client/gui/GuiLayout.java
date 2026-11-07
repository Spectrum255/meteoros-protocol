package com.spectrum255.meteorosprotocol.client.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.spectrum255.meteorosprotocol.MeteorosProtocol;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Data-driven layout for the Meteoros Protocol container screens (S1).
 *
 * <p>Both the screens and the menus read their geometry from
 * {@code assets/meteorosprotocol/gui/<id>.json} (schema v1, see
 * {@code docs/gui-layout-schema.md}). The menu places the slots and the screen
 * draws the art, but both load the same document, so click boxes, drawing and
 * slot hit boxes can never drift apart.</p>
 *
 * <p>Failure handling is deliberately total: anything that goes wrong while
 * reading or validating the file (missing resource, malformed JSON, an element
 * outside the window, duplicate roles) logs one warning and returns
 * {@link #defaults(String)}, i.e. the previous hard-coded layout. This class
 * never throws to its callers and never stops a container from opening.</p>
 *
 * <p>A dedicated server never reads the client resource pack: a menu built on
 * the logical server gets {@link #defaults(String)} (it only needs plausible
 * slot coordinates), while the client menu and screen load the JSON. Setting
 * the system property {@code -Dmeteoros.gui.debug=true} logs every loaded
 * element.</p>
 */
public final class GuiLayout {
    /** Layout id of the intake tower screen, matching {@code gui/intake.json}. */
    public static final String INTAKE = "intake";
    /** Layout id of the shared machine status screen, matching {@code gui/machine_status.json}. */
    public static final String MACHINE_STATUS = "machine_status";

    /** Schema revision this build understands; newer files still load with a warning. */
    public static final int SCHEMA_VERSION = 1;
    /** Item cell size. The JSON stores the cell's top-left corner, like vanilla slots. */
    public static final int SLOT_SIZE = 16;
    /** Title text placeholder meaning "use the screen's own title component". */
    public static final String DYNAMIC_TEXT = "dynamic";

    private static final String RESOURCE_DIRECTORY = "gui/";
    private static final int DEFAULT_WIDTH = 176;
    private static final int DEFAULT_HEIGHT = 224;

    private static final Set<String> KNOWN_TYPES = Set.of(
            "title", "text", "phase_rows", "bar_phase", "bar_energy",
            "slot", "icon_button", "inventory", "spacer");
    private static final Set<String> KNOWN_HIDE_WHEN = Set.of("hidesMixedBuffer");

    /** Keys already warned about, so a broken file does not spam the log every open. */
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();
    private static final boolean DEBUG = Boolean.getBoolean("meteoros.gui.debug");

    private final String id;
    private final int width;
    private final int height;
    private final List<Element> elements;
    private final Map<String, Rect> slots;
    private final Map<String, Rect> buttons;
    private final Map<String, BarSpec> bars;
    private final Map<String, TextSpec> texts;
    private final Map<String, Rect> spacers;
    private final PhaseRows phaseRows;
    private final Rect energyBar;
    private final Title title;
    private final InventorySpec inventory;
    /** Complete layout used when this (parsed) layout is missing an element. */
    private final GuiLayout fallback;

    private GuiLayout(String id, int width, int height, List<Element> elements, Specs specs, GuiLayout fallback) {
        this.id = id;
        this.width = width;
        this.height = height;
        this.elements = List.copyOf(elements);
        this.slots = Collections.unmodifiableMap(new LinkedHashMap<>(specs.slots));
        this.buttons = Collections.unmodifiableMap(new LinkedHashMap<>(specs.buttons));
        this.bars = Collections.unmodifiableMap(new LinkedHashMap<>(specs.bars));
        this.texts = Collections.unmodifiableMap(new LinkedHashMap<>(specs.texts));
        this.spacers = Collections.unmodifiableMap(new LinkedHashMap<>(specs.spacers));
        this.phaseRows = specs.phaseRows;
        this.energyBar = specs.energyBar;
        this.title = specs.title;
        this.inventory = specs.inventory;
        this.fallback = fallback;
    }

    // ------------------------------------------------------------------
    // Value types
    // ------------------------------------------------------------------

    /** Axis-aligned rectangle in panel-local pixels. */
    public record Rect(int x, int y, int w, int h) {
        public Rect offset(int dx, int dy) {
            return new Rect(x + dx, y + dy, w, h);
        }

        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        }

        public boolean intersects(Rect other) {
            return x < other.x + other.w && other.x < x + w && y < other.y + other.h && other.y < y + h;
        }
    }

    /** One raw entry of the {@code elements} array, in document (= draw) order. */
    public record Element(String type, JsonObject raw) {
    }

    /** Title element; {@code text} is a translation key or {@link #DYNAMIC_TEXT}. */
    public record Title(String text, int x, int y, boolean centered) {
    }

    /** Free-standing text element, addressed by its {@code role}. */
    public record TextSpec(String role, int x, int y) {
    }

    /** Phase row list: row window, row pitch/visibility and the scrollbar beside it. */
    public record PhaseRows(Rect area, int step, int visible, Rect scrollbar) {
        /** Height of one full window: {@code visible} rows on a {@code step} px pitch. */
        public int windowHeight() {
            return visible * step - 1;
        }

        public int rowY(int slot) {
            return area.y() + slot * step;
        }
    }

    /** Bar element. {@code hideWhen} names an optional screen predicate that hides it. */
    public record BarSpec(Rect rect, String channel, String hideWhen) {
        /** True when the named predicate is set and the screen reports it. */
        public boolean hiddenBy(String predicate) {
            return predicate != null && predicate.equals(hideWhen);
        }
    }

    /** Player inventory block: label line, 3 x 9 grid rows, hotbar and slot pitch. */
    public record InventorySpec(int labelX, int labelY, int gridX, int[] rows, int hotbar, int step) {
        public InventorySpec {
            rows = rows.clone();
        }
    }

    // ------------------------------------------------------------------
    // Loading
    // ------------------------------------------------------------------

    /** Loads a layout for the current physical side. */
    public static GuiLayout load(String id) {
        return load(id, FMLEnvironment.dist == Dist.CLIENT);
    }

    /**
     * Loads a layout. {@code allowClientResources} must be {@code false} on the
     * logical server, where no client resource manager exists.
     */
    public static GuiLayout load(String id, boolean allowClientResources) {
        GuiLayout defaults = defaults(id);
        if (!allowClientResources) {
            return defaults;
        }
        try {
            GuiLayout parsed = fromJson(id, readResource(id), defaults);
            if (DEBUG) {
                try {
                    MeteorosProtocol.LOGGER.info("[gui-layout] loaded '{}' from {}{} ({}x{}, {} elements)",
                            id, RESOURCE_DIRECTORY, id + ".json", parsed.width, parsed.height,
                            parsed.elements.size());
                    for (Element element : parsed.elements) {
                        MeteorosProtocol.LOGGER.info("[gui-layout]   {} {}", element.type(), element.raw());
                    }
                } catch (Throwable ignored) {
                    // Debug logging must never invalidate a successfully parsed layout.
                }
            }
            return parsed;
        } catch (Throwable throwable) {
            warnOnce("load:" + id,
                    "Could not load GUI layout '{}' from {}{}.json; using the built-in layout instead",
                    id, RESOURCE_DIRECTORY, id, throwable);
            return defaults;
        }
    }

    /**
     * Built-in layout for {@code id}, matching the previous hard-coded geometry.
     * This is the fallback whenever the JSON is missing or invalid.
     */
    public static GuiLayout defaults(String id) {
        try {
            return switch (id) {
                case INTAKE -> intakeDefaults();
                case MACHINE_STATUS -> machineStatusDefaults();
                default -> new GuiLayout(id, DEFAULT_WIDTH, DEFAULT_HEIGHT, List.of(), new Specs(), null);
            };
        } catch (Throwable throwable) {
            warnOnce("defaults:" + id, "Could not build the built-in GUI layout '{}'", id, throwable);
            return new GuiLayout(id, DEFAULT_WIDTH, DEFAULT_HEIGHT, List.of(), new Specs(), null);
        }
    }

    private static GuiLayout intakeDefaults() {
        Specs specs = new Specs();
        // The intake title used to be drawn at x = 8, not centred, so the built-in
        // fallback keeps the old appearance; the shipped JSON says centred = true.
        specs.title = new Title(DYNAMIC_TEXT, IntakeLayout.TITLE_X, IntakeLayout.TITLE_Y, false);
        specs.phaseRows = new PhaseRows(
                new Rect(IntakeLayout.PHASE_ROW_X, IntakeLayout.PHASE_ROW_Y,
                        IntakeLayout.PHASE_ROW_WIDTH, IntakeLayout.PHASE_ROW_HEIGHT),
                IntakeLayout.PHASE_ROW_STEP,
                IntakeLayout.PHASE_ROW_VISIBLE,
                new Rect(IntakeLayout.PHASE_SCROLLBAR_X, IntakeLayout.PHASE_SCROLLBAR_Y,
                        IntakeLayout.PHASE_SCROLLBAR_WIDTH, IntakeLayout.PHASE_SCROLLBAR_HEIGHT));
        specs.bars.put("mixed", new BarSpec(
                new Rect(IntakeLayout.MIXED_BAR_X, IntakeLayout.MIXED_BAR_Y,
                        IntakeLayout.MIXED_BAR_WIDTH, IntakeLayout.MIXED_BAR_HEIGHT),
                "mixed", null));
        specs.energyBar = new Rect(IntakeLayout.FE_X, IntakeLayout.FE_Y,
                IntakeLayout.FE_WIDTH, IntakeLayout.FE_HEIGHT);
        specs.slots.put("filter", new Rect(IntakeLayout.FILTER_SLOT_X, IntakeLayout.FILTER_SLOT_Y,
                SLOT_SIZE, SLOT_SIZE));
        specs.slots.put("canister", new Rect(IntakeLayout.LOAD_SLOT_X, IntakeLayout.LOAD_SLOT_Y,
                SLOT_SIZE, SLOT_SIZE));
        specs.buttons.put("toggle", iconRect(IntakeLayout.BUTTON_X, IntakeLayout.ICON_BUTTON_TOGGLE_Y));
        specs.buttons.put("redstone", iconRect(IntakeLayout.BUTTON_X, IntakeLayout.ICON_BUTTON_REDSTONE_Y));
        specs.buttons.put("extract", iconRect(IntakeLayout.BUTTON_X, IntakeLayout.ICON_BUTTON_EXTRACT_Y));
        specs.buttons.put("auto_io", iconRect(IntakeLayout.AUTOIO_BUTTON_X, IntakeLayout.AUTOIO_BUTTON_Y));
        specs.buttons.put("side_config", iconRect(IntakeLayout.SIDE_BUTTON_X, IntakeLayout.SIDE_BUTTON_Y));
        specs.spacers.put("upgrade_band", new Rect(IntakeLayout.RESERVED_BAND_X, IntakeLayout.RESERVED_BAND_Y,
                IntakeLayout.RESERVED_BAND_WIDTH, IntakeLayout.RESERVED_BAND_HEIGHT));
        specs.inventory = new InventorySpec(IntakeLayout.INVENTORY_LABEL_X, IntakeLayout.INVENTORY_LABEL_Y,
                IntakeLayout.INVENTORY_X, defaultInventoryRows(), IntakeLayout.HOTBAR_Y,
                IntakeLayout.INVENTORY_STEP);
        return new GuiLayout(INTAKE, IntakeLayout.WIDTH, IntakeLayout.HEIGHT, List.of(), specs, null);
    }

    private static GuiLayout machineStatusDefaults() {
        Specs specs = new Specs();
        specs.title = new Title(DYNAMIC_TEXT, 8, 6, true);
        specs.energyBar = new Rect(8, 26, 96, 8);
        specs.bars.put("mixed", new BarSpec(new Rect(8, 37, 96, 4), "mixed", "hidesMixedBuffer"));
        specs.phaseRows = new PhaseRows(new Rect(8, 44, 96, 10), 11, 5, new Rect(106, 44, 2, 55));
        specs.texts.put("status", new TextSpec("status", 8, 104));
        specs.buttons.put("toggle", iconRect(112, 18));
        specs.buttons.put("redstone", iconRect(112, 40));
        specs.buttons.put("auto_io", iconRect(132, 4));
        specs.buttons.put("side_config", iconRect(152, 4));
        specs.inventory = new InventorySpec(8, 128, 8, defaultInventoryRows(), 198, 18);
        return new GuiLayout(MACHINE_STATUS, DEFAULT_WIDTH, DEFAULT_HEIGHT, List.of(), specs, null);
    }

    private static Rect iconRect(int x, int y) {
        return new Rect(x, y, IntakeLayout.ICON_BUTTON_SIZE, IntakeLayout.ICON_BUTTON_SIZE);
    }

    private static int[] defaultInventoryRows() {
        return new int[]{IntakeLayout.INVENTORY_Y, IntakeLayout.INVENTORY_Y + IntakeLayout.INVENTORY_STEP,
                IntakeLayout.INVENTORY_Y + 2 * IntakeLayout.INVENTORY_STEP};
    }

    // ------------------------------------------------------------------
    // Resource access
    // ------------------------------------------------------------------

    private static String readResource(String id) throws IOException {
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(
                MeteorosProtocol.MODID, RESOURCE_DIRECTORY + id + ".json");
        Optional<Resource> resource = ClientResources.find(location);
        if (resource.isEmpty()) {
            throw new IOException("resource " + location + " is missing");
        }
        try (InputStream input = resource.get().open()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Client-only resource lookup, kept in a nested class so a dedicated server
     * never has to resolve {@link Minecraft} while loading this class.
     */
    private static final class ClientResources {
        private static Optional<Resource> find(ResourceLocation location) {
            Minecraft client = Minecraft.getInstance();
            if (client == null) {
                return Optional.empty();
            }
            return client.getResourceManager().getResource(location);
        }
    }

    // ------------------------------------------------------------------
    // Parsing + validation
    // ------------------------------------------------------------------

    private static GuiLayout fromJson(String id, String json, GuiLayout fallback) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        int schema = optInt(root, "schema", SCHEMA_VERSION);
        if (schema > SCHEMA_VERSION) {
            warnOnce("schema:" + id,
                    "GUI layout '{}' declares schema {}; this build understands {} and ignores newer fields",
                    id, schema, SCHEMA_VERSION);
        }
        int[] size = requiredIntArray(root, "size", 2);
        int width = size[0];
        int height = size[1];
        if (width <= 0 || height <= 0) {
            throw new LayoutException("'size' must be positive, got " + width + "x" + height);
        }

        JsonArray entries = requiredArray(root, "elements");
        List<Element> elements = new ArrayList<>();
        Set<String> actions = new LinkedHashSet<>();
        Set<String> slotRoles = new LinkedHashSet<>();
        for (int index = 0; index < entries.size(); index++) {
            JsonElement entry = entries.get(index);
            if (!entry.isJsonObject()) {
                warnOnce("element:" + id + ":" + index,
                        "GUI layout '{}': element {} is not an object, skipped", id, index);
                continue;
            }
            JsonObject element = entry.getAsJsonObject();
            String type = optString(element, "type", null);
            if (type == null || type.isEmpty()) {
                warnOnce("element-type:" + id + ":" + index,
                        "GUI layout '{}': element {} has no type, skipped", id, index);
                continue;
            }
            if (!KNOWN_TYPES.contains(type)) {
                warnOnce("type:" + id + ":" + type,
                        "GUI layout '{}': unknown element type '{}', skipped", id, type);
                continue;
            }
            validateElement(id, type, element, width, height);
            if ("icon_button".equals(type)) {
                String action = requiredString(element, "action");
                if (!actions.add(action)) {
                    throw new LayoutException("duplicate icon_button action '" + action + "'");
                }
            } else if ("slot".equals(type)) {
                String role = requiredString(element, "role");
                if (!slotRoles.add(role)) {
                    throw new LayoutException("duplicate slot role '" + role + "'");
                }
            }
            elements.add(new Element(type, element));
        }
        validateNoRowOverlap(elements);

        Specs specs = specsFromElements(id, elements);
        return new GuiLayout(id, width, height, elements, specs, fallback);
    }

    private static void validateElement(String id, String type, JsonObject element, int width, int height) {
        switch (type) {
            case "title" -> {
                int x = requiredInt(element, "x");
                int y = requiredInt(element, "y");
                requiredString(element, "text");
                requirePointInside(type, x, y, width, height);
            }
            case "text" -> {
                int x = requiredInt(element, "x");
                int y = requiredInt(element, "y");
                requiredString(element, "role");
                requirePointInside(type, x, y, width, height);
            }
            case "phase_rows" -> {
                Rect area = rect(element, 0, 0);
                int step = requiredInt(element, "step");
                int visible = requiredInt(element, "visible");
                if (step <= 0 || visible <= 0) {
                    throw new LayoutException("phase_rows needs step > 0 and visible > 0");
                }
                Rect scrollbar = rect(requiredObject(element, "scrollbar"), 0, 0);
                requireInside(type, new Rect(area.x(), area.y(), area.w(), visible * step - 1), width, height);
                requireInside(type + ".scrollbar", scrollbar, width, height);
            }
            case "bar_phase" -> {
                requiredString(element, "channel");
                normalizeHideWhen(id, optString(element, "hide_when", null));
                requireInside(type, rect(element, 0, 0), width, height);
            }
            case "bar_energy" -> requireInside(type, rect(element, 0, 0), width, height);
            case "slot" -> {
                int x = requiredInt(element, "x");
                int y = requiredInt(element, "y");
                requiredString(element, "role");
                requireInside(type, new Rect(x, y, SLOT_SIZE, SLOT_SIZE), width, height);
            }
            case "icon_button" -> {
                int x = requiredInt(element, "x");
                int y = requiredInt(element, "y");
                int size = requiredInt(element, "size");
                if (size <= 0) {
                    throw new LayoutException("icon_button needs size > 0");
                }
                requiredString(element, "action");
                requireInside(type, new Rect(x, y, size, size), width, height);
            }
            case "inventory" -> {
                validateInventory(element, width, height);
            }
            case "spacer" -> {
                requiredString(element, "role");
                requireInside(type, rect(element, 0, 0), width, height);
            }
            default -> throw new LayoutException("unhandled element type '" + type + "'");
        }
    }

    private static void validateInventory(JsonObject element, int width, int height) {
        int[] label = requiredIntArray(element, "label", 2);
        int[] rows = requiredIntArray(element, "rows", 1);
        int hotbar = requiredInt(element, "hotbar");
        int step = requiredInt(element, "step");
        if (step <= 0) {
            throw new LayoutException("inventory needs step > 0");
        }
        int gridWidth = 9 * step - 2;
        requirePointInside("inventory.label", label[0], label[1], width, height);
        for (int rowY : rows) {
            requireInside("inventory.rows", new Rect(label[0], rowY, gridWidth, SLOT_SIZE), width, height);
        }
        requireInside("inventory.hotbar", new Rect(label[0], hotbar, gridWidth, SLOT_SIZE), width, height);
    }

    /** Rule 3: the row window must not sit on top of another drawn element. */
    private static void validateNoRowOverlap(List<Element> elements) {
        Rect window = null;
        for (Element element : elements) {
            if ("phase_rows".equals(element.type())) {
                JsonObject raw = element.raw();
                int step = requiredInt(raw, "step");
                int visible = requiredInt(raw, "visible");
                window = new Rect(requiredInt(raw, "x"), requiredInt(raw, "y"),
                        requiredInt(raw, "w"), visible * step - 1);
                break;
            }
        }
        if (window == null) {
            return;
        }
        for (Element element : elements) {
            for (Rect occupied : occupiedRects(element)) {
                if (window.intersects(occupied)) {
                    throw new LayoutException("phase_rows window " + window + " overlaps "
                            + element.type() + " " + occupied);
                }
            }
        }
    }

    /** Pixel rectangles an element occupies for the overlap rule; text has none. */
    private static List<Rect> occupiedRects(Element element) {
        JsonObject raw = element.raw();
        return switch (element.type()) {
            case "bar_phase", "bar_energy", "spacer" -> List.of(rect(raw, 0, 0));
            case "slot" -> List.of(new Rect(requiredInt(raw, "x"), requiredInt(raw, "y"),
                    SLOT_SIZE, SLOT_SIZE));
            case "icon_button" -> List.of(new Rect(requiredInt(raw, "x"), requiredInt(raw, "y"),
                    requiredInt(raw, "size"), requiredInt(raw, "size")));
            case "inventory" -> inventoryRects(raw);
            default -> List.of();
        };
    }

    private static List<Rect> inventoryRects(JsonObject raw) {
        int[] label = requiredIntArray(raw, "label", 2);
        int[] rows = requiredIntArray(raw, "rows", 1);
        int hotbar = requiredInt(raw, "hotbar");
        int step = requiredInt(raw, "step");
        int gridX = label[0];
        int gridWidth = 9 * step - 2;
        List<Rect> rects = new ArrayList<>(rows.length + 1);
        for (int rowY : rows) {
            rects.add(new Rect(gridX, rowY, gridWidth, SLOT_SIZE));
        }
        rects.add(new Rect(gridX, hotbar, gridWidth, SLOT_SIZE));
        return rects;
    }

    private static Specs specsFromElements(String id, List<Element> elements) {
        Specs specs = new Specs();
        for (Element element : elements) {
            JsonObject raw = element.raw();
            switch (element.type()) {
                case "title" -> specs.title = new Title(requiredString(raw, "text"),
                        requiredInt(raw, "x"), requiredInt(raw, "y"), optBoolean(raw, "centered", false));
                case "text" -> {
                    String role = requiredString(raw, "role");
                    specs.texts.put(role, new TextSpec(role, requiredInt(raw, "x"), requiredInt(raw, "y")));
                }
                case "phase_rows" -> specs.phaseRows = new PhaseRows(rect(raw, 0, 0),
                        requiredInt(raw, "step"), requiredInt(raw, "visible"),
                        rect(requiredObject(raw, "scrollbar"), 0, 0));
                case "bar_phase" -> {
                    String channel = requiredString(raw, "channel");
                    specs.bars.put(channel, new BarSpec(rect(raw, 0, 0), channel,
                            normalizeHideWhen(id, optString(raw, "hide_when", null))));
                }
                case "bar_energy" -> specs.energyBar = rect(raw, 0, 0);
                case "slot" -> specs.slots.put(requiredString(raw, "role"),
                        new Rect(requiredInt(raw, "x"), requiredInt(raw, "y"), SLOT_SIZE, SLOT_SIZE));
                case "icon_button" -> specs.buttons.put(requiredString(raw, "action"),
                        new Rect(requiredInt(raw, "x"), requiredInt(raw, "y"),
                                requiredInt(raw, "size"), requiredInt(raw, "size")));
                case "inventory" -> {
                    int[] label = requiredIntArray(raw, "label", 2);
                    specs.inventory = new InventorySpec(label[0], label[1], label[0],
                            requiredIntArray(raw, "rows", 1), requiredInt(raw, "hotbar"),
                            requiredInt(raw, "step"));
                }
                case "spacer" -> specs.spacers.put(requiredString(raw, "role"), rect(raw, 0, 0));
                default -> {
                    // Unknown types were filtered out before this point.
                }
            }
        }
        return specs;
    }

    // ------------------------------------------------------------------
    // Accessors (always non-null; missing elements fall back to defaults)
    // ------------------------------------------------------------------

    public String id() {
        return this.id;
    }

    public int width() {
        return this.width;
    }

    public int height() {
        return this.height;
    }

    /** All elements in document order. Empty for a built-in default layout. */
    public List<Element> elements() {
        return this.elements;
    }

    /** First element of the given type, or {@code null} when neither the file nor the defaults have one. */
    public Element element(String type) {
        for (Element element : this.elements) {
            if (element.type().equals(type)) {
                return element;
            }
        }
        return this.fallback != null ? this.fallback.element(type) : null;
    }

    /** Every element of the given type, in document order. */
    public List<Element> elements(String type) {
        List<Element> found = new ArrayList<>();
        for (Element element : this.elements) {
            if (element.type().equals(type)) {
                found.add(element);
            }
        }
        if (found.isEmpty() && this.fallback != null) {
            return this.fallback.elements(type);
        }
        return found;
    }

    public Title title() {
        if (this.title != null) {
            return this.title;
        }
        if (this.fallback != null) {
            return this.fallback.title();
        }
        return new Title(DYNAMIC_TEXT, IntakeLayout.TITLE_X, IntakeLayout.TITLE_Y, false);
    }

    public PhaseRows phaseRows() {
        if (this.phaseRows != null) {
            return this.phaseRows;
        }
        if (this.fallback != null) {
            return this.fallback.phaseRows();
        }
        return new PhaseRows(new Rect(IntakeLayout.PHASE_ROW_X, IntakeLayout.PHASE_ROW_Y,
                IntakeLayout.PHASE_ROW_WIDTH, IntakeLayout.PHASE_ROW_HEIGHT),
                IntakeLayout.PHASE_ROW_STEP, IntakeLayout.PHASE_ROW_VISIBLE,
                new Rect(IntakeLayout.PHASE_SCROLLBAR_X, IntakeLayout.PHASE_SCROLLBAR_Y,
                        IntakeLayout.PHASE_SCROLLBAR_WIDTH, IntakeLayout.PHASE_SCROLLBAR_HEIGHT));
    }

    /** Bar for the given channel; a zero-sized placeholder when even the defaults lack it. */
    public BarSpec barPhase(String channel) {
        BarSpec bar = this.bars.get(channel);
        if (bar != null) {
            return bar;
        }
        if (this.fallback != null) {
            return this.fallback.barPhase(channel);
        }
        return new BarSpec(new Rect(0, 0, 0, 0), channel, null);
    }

    public Rect barEnergy() {
        if (this.energyBar != null) {
            return this.energyBar;
        }
        if (this.fallback != null) {
            return this.fallback.barEnergy();
        }
        return new Rect(0, 0, 0, 0);
    }

    /** Slot rectangle by role ({@code filter}, {@code canister}). Menus read the same map. */
    public Rect slotRect(String role) {
        Rect slot = this.slots.get(role);
        if (slot != null) {
            return slot;
        }
        if (this.fallback != null) {
            return this.fallback.slotRect(role);
        }
        return new Rect(0, 0, SLOT_SIZE, SLOT_SIZE);
    }

    /** Icon-button rectangle by action ({@code toggle}, {@code redstone}, ...). */
    public Rect iconButton(String action) {
        Rect button = this.buttons.get(action);
        if (button != null) {
            return button;
        }
        if (this.fallback != null) {
            return this.fallback.iconButton(action);
        }
        return new Rect(0, 0, 0, 0);
    }

    public InventorySpec inventory() {
        if (this.inventory != null) {
            return this.inventory;
        }
        if (this.fallback != null) {
            return this.fallback.inventory();
        }
        return new InventorySpec(8, 128, 8, new int[]{140, 158, 176}, 198, 18);
    }

    /** Free-standing text element by role (e.g. {@code status}). */
    public TextSpec text(String role) {
        TextSpec text = this.texts.get(role);
        if (text != null) {
            return text;
        }
        if (this.fallback != null) {
            return this.fallback.text(role);
        }
        return new TextSpec(role, 0, 0);
    }

    /** Spacer element by role (the reserved upgrade band and friends); never drawn. */
    public Rect spacer(String role) {
        Rect spacer = this.spacers.get(role);
        if (spacer != null) {
            return spacer;
        }
        if (this.fallback != null) {
            return this.fallback.spacer(role);
        }
        return new Rect(0, 0, 0, 0);
    }

    // ------------------------------------------------------------------
    // JSON helpers
    // ------------------------------------------------------------------

    private static void requireInside(String what, Rect rect, int width, int height) {
        if (rect.w() <= 0 || rect.h() <= 0 || rect.x() < 0 || rect.y() < 0
                || rect.x() + rect.w() > width || rect.y() + rect.h() > height) {
            throw new LayoutException(what + " is outside the " + width + "x" + height
                    + " window: " + rect);
        }
    }

    private static void requirePointInside(String what, int x, int y, int width, int height) {
        if (x < 0 || y < 0 || x > width || y > height) {
            throw new LayoutException(what + " is outside the " + width + "x" + height
                    + " window: (" + x + ", " + y + ")");
        }
    }

    private static Rect rect(JsonObject object, int defaultWidth, int defaultHeight) {
        int x = requiredInt(object, "x");
        int y = requiredInt(object, "y");
        int w = object.has("w") ? requiredInt(object, "w") : defaultWidth;
        int h = object.has("h") ? requiredInt(object, "h") : defaultHeight;
        return new Rect(x, y, w, h);
    }

    private static JsonObject requiredObject(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonObject()) {
            throw new LayoutException("missing required object '" + key + "'");
        }
        return object.getAsJsonObject(key);
    }

    private static JsonArray requiredArray(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonArray()) {
            throw new LayoutException("missing required array '" + key + "'");
        }
        return object.getAsJsonArray(key);
    }

    private static int[] requiredIntArray(JsonObject object, String key, int minLength) {
        JsonArray array = requiredArray(object, key);
        if (array.size() < minLength) {
            throw new LayoutException("'" + key + "' needs at least " + minLength + " entries");
        }
        int[] values = new int[array.size()];
        for (int index = 0; index < array.size(); index++) {
            try {
                values[index] = array.get(index).getAsInt();
            } catch (RuntimeException exception) {
                throw new LayoutException("'" + key + "' must be an int array");
            }
        }
        return values;
    }

    private static int requiredInt(JsonObject object, String key) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            throw new LayoutException("missing required int '" + key + "'");
        }
        try {
            return object.get(key).getAsInt();
        } catch (RuntimeException exception) {
            throw new LayoutException("'" + key + "' must be an int");
        }
    }

    private static int optInt(JsonObject object, String key, int defaultValue) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            return defaultValue;
        }
        try {
            return object.get(key).getAsInt();
        } catch (RuntimeException exception) {
            return defaultValue;
        }
    }

    private static String requiredString(JsonObject object, String key) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            throw new LayoutException("missing required string '" + key + "'");
        }
        try {
            String value = object.get(key).getAsString();
            if (value.isEmpty()) {
                throw new LayoutException("'" + key + "' must not be empty");
            }
            return value;
        } catch (IllegalStateException | UnsupportedOperationException exception) {
            throw new LayoutException("'" + key + "' must be a string");
        }
    }

    private static String optString(JsonObject object, String key, String defaultValue) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            return defaultValue;
        }
        try {
            return object.get(key).getAsString();
        } catch (RuntimeException exception) {
            return defaultValue;
        }
    }

    private static boolean optBoolean(JsonObject object, String key, boolean defaultValue) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            return defaultValue;
        }
        try {
            return object.get(key).getAsBoolean();
        } catch (RuntimeException exception) {
            return defaultValue;
        }
    }

    private static String normalizeHideWhen(String id, String hideWhen) {
        if (hideWhen == null || KNOWN_HIDE_WHEN.contains(hideWhen)) {
            return hideWhen;
        }
        warnOnce("hide_when:" + id + ":" + hideWhen,
                "GUI layout '{}': unknown hide_when '{}', the bar is always drawn", id, hideWhen);
        return null;
    }

    /**
     * Warns at most once per key. Logging must never break a GUI, so any failure
     * in the logging backend is swallowed: the caller always gets a layout.
     */
    private static void warnOnce(String key, String message, Object... arguments) {
        if (WARNED.add(key)) {
            try {
                MeteorosProtocol.LOGGER.warn("[gui-layout] " + message, arguments);
            } catch (Throwable ignored) {
                // Deliberately ignored: a broken logger must not break the GUI.
            }
        }
    }

    /** Mutable staging area used while a layout is being built. */
    private static final class Specs {
        private final Map<String, Rect> slots = new LinkedHashMap<>();
        private final Map<String, Rect> buttons = new LinkedHashMap<>();
        private final Map<String, BarSpec> bars = new LinkedHashMap<>();
        private final Map<String, TextSpec> texts = new LinkedHashMap<>();
        private final Map<String, Rect> spacers = new LinkedHashMap<>();
        private PhaseRows phaseRows;
        private Rect energyBar;
        private Title title;
        private InventorySpec inventory;
    }

    /** Signals an unusable document; always caught by {@link #load}. */
    private static final class LayoutException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private LayoutException(String message) {
            super(message);
        }
    }
}
