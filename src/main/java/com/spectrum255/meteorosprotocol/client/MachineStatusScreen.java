package com.spectrum255.meteorosprotocol.client;

import com.spectrum255.meteorosprotocol.MeteorosProtocol;
import com.spectrum255.meteorosprotocol.Config;
import com.spectrum255.meteorosprotocol.client.gui.GuiLayout;
import com.spectrum255.meteorosprotocol.client.gui.IconButton;
import com.spectrum255.meteorosprotocol.client.gui.MeteorosGui;
import com.spectrum255.meteorosprotocol.client.gui.PhaseRowWidget;
import com.spectrum255.meteorosprotocol.client.gui.SideConfigPanel;
import com.spectrum255.meteorosprotocol.machine.MachineBlockEntity;
import com.spectrum255.meteorosprotocol.menu.MachineStatusMenu;
import com.spectrum255.meteorosprotocol.machine.SideChannel;
import com.spectrum255.meteorosprotocol.particle.ParticleFormat;
import com.spectrum255.meteorosprotocol.particle.Phase;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared status screen for the machines that have no bespoke layout (thermal
 * generator, particle purifier, particle port, weather station, weather tank):
 * a 176 x 224 vanilla-styled panel, one FE bar, a thin mixed-buffer bar, a
 * scrolling horizontal list of <em>relevant</em> phase rows and four icon
 * buttons (enable, cycle redstone mode, auto I/O, side config). No item slots
 * are involved; only the player inventory is drawn.
 *
 * <p>Every coordinate comes from {@link GuiLayout} ({@code gui/machine_status.json});
 * the loader falls back to the built-in layout when the file is missing or
 * invalid, so this screen never fails to open.</p>
 *
 * <p>The rows follow the machine family: the generator shows only its Thermion
 * input (a bare generator has no byproduct), the purifier shows the four phases
 * it produces and consumes, the port shows every phase, and the weather blocks
 * (which have no particle logic yet) show no rows at all and a single "no
 * particle I/O" line instead. Clicking a row only pins a white highlight for
 * reading; it sends nothing to the server.</p>
 */
public class MachineStatusScreen extends AbstractContainerScreen<MachineStatusMenu> {
    private static final int TITLE_COLOR = 0xFF404040;
    private static final int FE_COLOR = 0xFFFF0000;
    /** Mixed buffer: one thin bar, hidden for the purifier (it never stores mixed for long). */
    private static final int MIXED_COLOR = 0xFF8B8B8B;
    private static final int STATUS_COLOR = 0xFF404040;

    private static final String ICON_ROOT = "textures/gui/";
    private static final ResourceLocation ICON_POWER_ON = icon("icon_power");
    private static final ResourceLocation ICON_POWER_OFF = icon("icon_power_off");
    private static final ResourceLocation ICON_REDSTONE_HIGH = icon("icon_redstone_high");
    private static final ResourceLocation ICON_REDSTONE_LOW = icon("icon_redstone_low");
    private static final ResourceLocation ICON_REDSTONE_ALWAYS = icon("icon_redstone_always");
    private static final ResourceLocation ICON_SIDE = icon("icon_side");
    private static final ResourceLocation ICON_AUTOIO_ON = icon("icon_autoio");
    private static final ResourceLocation ICON_AUTOIO_OFF = icon("icon_autoio_off");

    private static final List<Component> POWER_TOOLTIP = List.of(
            Component.translatable("gui.meteorosprotocol.intake.power.enabled"));
    private static final List<Component> POWER_OFF_TOOLTIP = List.of(
            Component.translatable("gui.meteorosprotocol.intake.power.disabled"));
    private static final List<Component> REDSTONE_HIGH_TOOLTIP = List.of(
            Component.translatable("gui.meteorosprotocol.intake.redstone.high"));
    private static final List<Component> REDSTONE_LOW_TOOLTIP = List.of(
            Component.translatable("gui.meteorosprotocol.intake.redstone.low"));
    private static final List<Component> REDSTONE_ALWAYS_TOOLTIP = List.of(
            Component.translatable("gui.meteorosprotocol.intake.redstone.always"));
    /** Row tooltip hint; falls back to English until a lang key is added. */
    private static final Component PIN_HINT = Component.translatableWithFallback(
            "gui.meteorosprotocol.machine.pin_hint", "Click to pin this row");

    /** Geometry of this screen, loaded from {@code gui/machine_status.json} with a built-in fallback. */
    private final GuiLayout layout;
    private IconButton toggleButton;
    private IconButton redstoneButton;
    private IconButton sideButton;
    private IconButton autoIoButton;
    private final SideConfigPanel sidePanel = new SideConfigPanel();
    /** One widget per registry phase (indexed by {@code Phase.index()}); visibility follows the machine kind. */
    private final List<PhaseRowWidget> phaseRows = new ArrayList<>();
    /** First visible row in the relevant-phase list. */
    private int phaseScroll;
    /** Purely visual highlight; never sent to the server. */
    private int pinnedIndex = -1;

    public MachineStatusScreen(MachineStatusMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.layout = GuiLayout.load(GuiLayout.MACHINE_STATUS);
        this.imageWidth = this.layout.width();
        this.imageHeight = this.layout.height();
    }

    private static ResourceLocation icon(String name) {
        return ResourceLocation.fromNamespaceAndPath(MeteorosProtocol.MODID, ICON_ROOT + name + ".png");
    }

    @Override
    protected void init() {
        super.init();
        GuiLayout.Rect toggleRect = this.layout.iconButton("toggle");
        GuiLayout.Rect redstoneRect = this.layout.iconButton("redstone");
        GuiLayout.Rect sideRect = this.layout.iconButton("side_config");
        GuiLayout.Rect autoIoRect = this.layout.iconButton("auto_io");
        this.toggleButton = this.addRenderableWidget(new IconButton(
                this.leftPos + toggleRect.x(), this.topPos + toggleRect.y(),
                toggleRect.w(), toggleRect.h(), this::powerIcon, this::powerTooltip,
                button -> sendButton(0)));
        this.redstoneButton = this.addRenderableWidget(new IconButton(
                this.leftPos + redstoneRect.x(), this.topPos + redstoneRect.y(),
                redstoneRect.w(), redstoneRect.h(), this::redstoneIcon, this::redstoneTooltip,
                button -> sendButton(1)));
        this.sideButton = this.addRenderableWidget(new IconButton(
                this.leftPos + sideRect.x(), this.topPos + sideRect.y(),
                sideRect.w(), sideRect.h(), ICON_SIDE, this::sideTooltip,
                button -> this.sidePanel.toggle()));
        this.autoIoButton = this.addRenderableWidget(new IconButton(
                this.leftPos + autoIoRect.x(), this.topPos + autoIoRect.y(),
                autoIoRect.w(), autoIoRect.h(), this::autoIoIcon, this::autoIoTooltip,
                button -> sendButton(this.menu.autoIoButtonId())));
        // Only the intake tower has item slots; this screen never shows them.
        this.sidePanel.setItemAvailable(false);

        this.phaseRows.clear();
        this.phaseScroll = 0;
        GuiLayout.PhaseRows rows = this.layout.phaseRows();
        for (Phase phase : Phase.all()) {
            final int index = phase.index();
            PhaseRowWidget row = this.addRenderableWidget(new PhaseRowWidget(
                    this.leftPos + rows.area().x(),
                    this.topPos + rowY(),
                    rows.area().w(),
                    rows.area().h(),
                    index,
                    () -> tankFill(index),
                    phase::argb,
                    phase::darkArgb,
                    () -> this.pinnedIndex == index,
                    selected -> this.pinnedIndex = selected));
            this.phaseRows.add(row);
        }
        updateRows();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateRows();
    }

    /**
     * Top edge of the row window. When the mixed bar is hidden the layout has no
     * dedicated {@code hide_when} gap field, so the reserved gap is derived from
     * the JSON itself: {@code phase_rows.y - (bar_phase.y + bar_phase.h)} — 3 px
     * for the shipped layout, which is exactly the old hand-tuned shift.
     */
    private int rowY() {
        GuiLayout.PhaseRows rows = this.layout.phaseRows();
        GuiLayout.BarSpec mixed = this.layout.barPhase("mixed");
        if (!mixed.hiddenBy("hidesMixedBuffer") || !this.menu.hidesMixedBuffer()) {
            return rows.area().y();
        }
        int gap = rows.area().y() - (mixed.rect().y() + mixed.rect().h());
        return rows.area().y() - Math.max(0, gap);
    }

    private float tankFill(int index) {
        Phase phase = Phase.all().get(index);
        long capacity = phase.tankCapacity();
        if (capacity <= 0L) {
            return 0.0F;
        }
        return (float) Math.min(1.0, (double) this.menu.tankAmount(index) / (double) capacity);
    }

    /**
     * Phases the status screen lists: a machine always shows the tanks it owns
     * (even when empty), while a container — the particle port, kind generic —
     * only shows phases that actually hold particles, plus the pinned row.
     */
    private List<Phase> displayedPhases() {
        List<Phase> owned = this.menu.relevantPhases();
        List<Phase> shown = new ArrayList<>();
        for (Phase phase : owned) {
            if (this.menu.kind() != MachineStatusMenu.KIND_GENERIC
                    || this.menu.tankAmount(phase.index()) > 0L
                    || phase.index() == this.pinnedIndex) {
                shown.add(phase);
            }
        }
        shown.sort((left, right) -> {
            int leftSeq = this.menu.arrivalSeq(left.index());
            int rightSeq = this.menu.arrivalSeq(right.index());
            if (leftSeq != rightSeq) {
                if (leftSeq < 0) {
                    return 1;
                }
                if (rightSeq < 0) {
                    return -1;
                }
                return Integer.compare(leftSeq, rightSeq);
            }
            return Integer.compare(left.index(), right.index());
        });
        return shown;
    }

    /** Positions the row widgets for the current scroll offset and hides rows outside the window. */
    private void updateRows() {
        List<Phase> phases = displayedPhases();
        int total = phases.size();
        GuiLayout.PhaseRows rows = this.layout.phaseRows();
        int maxScroll = Math.max(0, total - rows.visible());
        this.phaseScroll = Mth.clamp(this.phaseScroll, 0, maxScroll);
        for (PhaseRowWidget row : this.phaseRows) {
            row.visible = false;
        }
        int top = this.topPos + rowY();
        for (int position = 0; position < total; position++) {
            int slot = position - this.phaseScroll;
            if (slot < 0 || slot >= rows.visible()) {
                continue;
            }
            Phase phase = phases.get(position);
            PhaseRowWidget row = this.phaseRows.get(phase.index());
            row.visible = true;
            row.setY(top + slot * rows.step());
        }
    }

    /** 2 px wheel-only scrollbar; drawn in the background so it never blocks the rows. */
    private void drawScrollbar(GuiGraphics graphics, int x, int y) {
        GuiLayout.PhaseRows rows = this.layout.phaseRows();
        int total = displayedPhases().size();
        if (total <= rows.visible()) {
            return;
        }
        GuiLayout.Rect scrollbar = rows.scrollbar();
        int barX = x + scrollbar.x();
        int barY = y + rowY();
        int barHeight = scrollbar.h();
        graphics.fill(barX, barY, barX + scrollbar.w(), barY + barHeight, MeteorosGui.SLOT_DARK);
        int thumbHeight = Math.max(6, barHeight * rows.visible() / total);
        int maxScroll = total - rows.visible();
        int thumbY = barY + (int) Math.round((double) (barHeight - thumbHeight) * this.phaseScroll / maxScroll);
        graphics.fill(barX, thumbY, barX + scrollbar.w(), thumbY + thumbHeight, MeteorosGui.SLOT_BODY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        MeteorosGui.panel(graphics, x, y, this.imageWidth, this.imageHeight);

        GuiLayout.Rect energyRect = this.layout.barEnergy();
        MeteorosGui.barHWithText(graphics, this.font, x + energyRect.x(), y + energyRect.y(),
                energyRect.w(), energyRect.h(),
                (float) this.menu.energy() / MachineStatusMenu.ENERGY_CAPACITY, FE_COLOR, 0xFF555555,
                Component.literal(ParticleFormat.formatFE(this.menu.energy()) + " / "
                        + ParticleFormat.formatFE(MachineStatusMenu.ENERGY_CAPACITY)));

        GuiLayout.BarSpec mixed = this.layout.barPhase("mixed");
        if (!mixed.hiddenBy("hidesMixedBuffer") || !this.menu.hidesMixedBuffer()) {
            GuiLayout.Rect mixedRect = mixed.rect();
            MeteorosGui.phaseBarH(graphics, x + mixedRect.x(), y + mixedRect.y(),
                    mixedRect.w(), mixedRect.h(),
                    (float) ((double) this.menu.mixedTotal() / (double) MachineBlockEntity.MIXED_CAPACITY),
                    MIXED_COLOR, 0xFF555555);
        }
        drawScrollbar(graphics, x, y);

        GuiLayout.InventorySpec inventory = this.layout.inventory();
        int[] inventoryRowY = inventory.rows();
        for (int row = 0; row < inventoryRowY.length; row++) {
            for (int col = 0; col < 9; col++) {
                int slotX = x + inventory.gridX() + col * inventory.step();
                int slotY = y + inventoryRowY[row];
                MeteorosGui.slot(graphics, slotX, slotY, inside(mouseX, mouseY, slotX, slotY, 16, 16));
            }
        }
        for (int col = 0; col < 9; col++) {
            int slotX = x + inventory.gridX() + col * inventory.step();
            int slotY = y + inventory.hotbar();
            MeteorosGui.slot(graphics, slotX, slotY, inside(mouseX, mouseY, slotX, slotY, 16, 16));
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        drawTitle(graphics);

        GuiLayout.PhaseRows rows = this.layout.phaseRows();
        if (displayedPhases().isEmpty()) {
            graphics.drawString(this.font,
                    Component.translatable("gui.meteorosprotocol.machine.no_particles"),
                    rows.area().x(), rows.area().y(), MeteorosGui.TEXT_DIM, false);
        }

        GuiLayout.TextSpec status = this.layout.text("status");
        Component state = Component.translatable(this.menu.active()
                ? "gui.meteorosprotocol.machine.working"
                : "gui.meteorosprotocol.machine.idle");
        graphics.drawString(this.font, state, status.x(), status.y(), STATUS_COLOR, false);

        GuiLayout.InventorySpec inventory = this.layout.inventory();
        MeteorosGui.label(graphics, this.font, this.playerInventoryTitle,
                inventory.labelX(), inventory.labelY());
    }

    /** Draws the panel title at the layout position, centred when the layout asks for it. */
    private void drawTitle(GuiGraphics graphics) {
        GuiLayout.Title title = this.layout.title();
        Component text = GuiLayout.DYNAMIC_TEXT.equals(title.text())
                ? this.title
                : Component.translatable(title.text());
        int titleX = title.centered()
                ? (this.imageWidth - this.font.width(text)) / 2
                : title.x();
        graphics.drawString(this.font, text, titleX, title.y(), TITLE_COLOR, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (this.sidePanel.visible()) {
            // AbstractContainerScreen queues the hovered item's tooltip for the
            // end of Screen.renderWithTooltip, i.e. after render() returns. Drop
            // it so it cannot paint over the overlay or the panel's own tooltip.
            this.clearTooltipForNextRenderPass();
            // Slot items are batched through the GUI buffer source; flush them now so
            // the opaque overlay is submitted after their draw calls instead of being
            // painted underneath a later batch flush.
            graphics.flush();
            // Opaque backdrop over the whole container window so the overlay covers
            // the slot grid and the inventory instead of sitting behind them.
            graphics.fill(this.leftPos - 1, this.topPos - 1,
                    this.leftPos + this.imageWidth + 1, this.topPos + this.imageHeight + 1,
                    0xFF101010);
            this.sidePanel.render(graphics, this.font, this.leftPos, this.topPos,
                    sideMasks(), mouseX, mouseY);
            this.sidePanel.renderTooltip(graphics, this.font, this.leftPos, this.topPos,
                    sideMasks(), mouseX, mouseY);
            return;
        }
        // Flush batched slot items before drawing our own tooltips, otherwise the
        // deferred batch can end up painted above them.
        graphics.flush();
        this.renderTooltip(graphics, mouseX, mouseY);
        drawTooltips(graphics, mouseX, mouseY);
        // Submit our tooltip before the screen returns: the phase-row labels are batched
        // separately and must not be flushed on top of the tooltip afterwards.
        graphics.flush();
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        // The side-config overlay owns the screen while it is open: never queue an
        // item tooltip that would be rendered after our overlay.
        if (this.sidePanel.visible()) {
            this.clearTooltipForNextRenderPass();
            return;
        }
        super.renderTooltip(graphics, mouseX, mouseY);
    }

    private int[] sideMasks() {
        SideChannel[] channels = SideChannel.values();
        int[] masks = new int[channels.length];
        for (int index = 0; index < channels.length; index++) {
            masks[index] = this.menu.sideMask(channels[index]);
        }
        return masks;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.sidePanel.visible() && this.sidePanel.mouseClicked(mouseX, mouseY, button,
                this.leftPos, this.topPos, sideMasks(), this::sendButton)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.sidePanel.visible()) {
            return true;
        }
        GuiLayout.PhaseRows rows = this.layout.phaseRows();
        if (inside(mouseX, mouseY, this.leftPos + rows.area().x(), this.topPos + rowY(),
                rows.area().w() + rows.scrollbar().w() + 2, rows.windowHeight())) {
            int total = displayedPhases().size();
            int maxScroll = Math.max(0, total - rows.visible());
            if (maxScroll > 0) {
                int direction = scrollY > 0.0D ? -1 : 1;
                int next = Mth.clamp(this.phaseScroll + direction, 0, maxScroll);
                if (next != this.phaseScroll) {
                    this.phaseScroll = next;
                    updateRows();
                }
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 && this.sidePanel.visible()) {
            this.sidePanel.close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private List<Component> sideTooltip() {
        return List.of(Component.translatable("gui.meteorosprotocol.side.open"));
    }

    private ResourceLocation autoIoIcon() {
        return this.menu.autoIo() ? ICON_AUTOIO_ON : ICON_AUTOIO_OFF;
    }

    private List<Component> autoIoTooltip() {
        return List.of(
                Component.translatable(this.menu.autoIo()
                        ? "gui.meteorosprotocol.autoio.on"
                        : "gui.meteorosprotocol.autoio.off"),
                Component.translatable("gui.meteorosprotocol.autoio.hint"));
    }

    private ResourceLocation powerIcon() {
        return this.menu.enabled() ? ICON_POWER_ON : ICON_POWER_OFF;
    }

    private ResourceLocation redstoneIcon() {
        return switch (this.menu.redstoneMode()) {
            case 0 -> ICON_REDSTONE_HIGH;
            case 1 -> ICON_REDSTONE_LOW;
            default -> ICON_REDSTONE_ALWAYS;
        };
    }

    private List<Component> powerTooltip() {
        return this.menu.enabled() ? POWER_TOOLTIP : POWER_OFF_TOOLTIP;
    }

    private List<Component> redstoneTooltip() {
        return switch (this.menu.redstoneMode()) {
            case 0 -> REDSTONE_HIGH_TOOLTIP;
            case 1 -> REDSTONE_LOW_TOOLTIP;
            default -> REDSTONE_ALWAYS_TOOLTIP;
        };
    }

    private void drawTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        if (this.hoveredSlot != null && this.hoveredSlot.hasItem()) {
            return;
        }
        if (this.toggleButton != null && this.toggleButton.isHovered()) {
            MeteorosGui.tooltip(graphics, this.font, powerTooltip(), mouseX, mouseY, 0xFF9A9A9A);
            return;
        }
        if (this.redstoneButton != null && this.redstoneButton.isHovered()) {
            MeteorosGui.tooltip(graphics, this.font, redstoneTooltip(), mouseX, mouseY, 0xFF9A9A9A);
            return;
        }
        if (this.sideButton != null && this.sideButton.isHovered()) {
            MeteorosGui.tooltip(graphics, this.font, sideTooltip(), mouseX, mouseY, 0xFF9A9A9A);
            return;
        }
        if (this.autoIoButton != null && this.autoIoButton.isHovered()) {
            MeteorosGui.tooltip(graphics, this.font, autoIoTooltip(), mouseX, mouseY, 0xFF9A9A9A);
            return;
        }
        PhaseRowWidget hovered = hoveredRow();
        if (hovered != null) {
            drawPhaseTooltip(graphics, hovered.index(), mouseX, mouseY);
            return;
        }
        int x = this.leftPos;
        int y = this.topPos;
        GuiLayout.BarSpec mixed = this.layout.barPhase("mixed");
        GuiLayout.Rect mixedRect = mixed.rect();
        if ((!mixed.hiddenBy("hidesMixedBuffer") || !this.menu.hidesMixedBuffer())
                && inside(mouseX, mouseY, x + mixedRect.x(), y + mixedRect.y(),
                        mixedRect.w(), mixedRect.h())) {
            String line = Component.translatable("gui.meteorosprotocol.intake.mixed").getString()
                    + ": " + ParticleFormat.formatPlain(this.menu.mixedTotal(), "mPU")
                    + " / " + ParticleFormat.formatPlain(MachineBlockEntity.MIXED_CAPACITY, "mPU");
            MeteorosGui.tooltip(graphics, this.font, Component.literal(line), mouseX, mouseY);
            return;
        }
        GuiLayout.Rect energyRect = this.layout.barEnergy();
        if (inside(mouseX, mouseY, x + energyRect.x(), y + energyRect.y(),
                energyRect.w(), energyRect.h())) {
            String line = "FE " + ParticleFormat.formatPlain(this.menu.energy(), "FE")
                    + " / " + ParticleFormat.formatPlain(MachineStatusMenu.ENERGY_CAPACITY, "FE");
            MeteorosGui.tooltip(graphics, this.font, Component.literal(line), mouseX, mouseY);
        }
    }

    private PhaseRowWidget hoveredRow() {
        for (PhaseRowWidget row : this.phaseRows) {
            if (row.visible && row.isHovered()) {
                return row;
            }
        }
        return null;
    }

    /** Phase name, amount / capacity, tank percentage, mixed amount (unless hidden) and the pin hint. */
    private void drawPhaseTooltip(GuiGraphics graphics, int index, int mouseX, int mouseY) {
        Phase phase = Phase.all().get(index);
        long stored = this.menu.tankAmount(index);
        long capacity = phase.tankCapacity();
        Component header = Component.literal(phase.displayName().getString()
                + ": " + ParticleFormat.formatPlain(stored, "mPU")
                + " / " + ParticleFormat.formatPlain(capacity, "mPU"));
        double percent = capacity <= 0L ? 0.0D : Math.min(100.0D, stored * 100.0D / capacity);
        Component percentLine = Component.literal(String.format(java.util.Locale.ROOT, "%.1f%%", percent));
        List<Component> lines = new ArrayList<>();
        lines.add(header);
        lines.add(percentLine);
        if (!this.menu.hidesMixedBuffer() && Config.showMixedInPhaseTooltip()) {
            lines.add(Component.literal("mixed: " + ParticleFormat.formatPlain(
                    this.menu.mixedAmount(index), "mPU")));
        }
        lines.add(PIN_HINT);
        MeteorosGui.tooltip(graphics, this.font, lines, mouseX, mouseY, 0xFF9A9A9A);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private void sendButton(int id) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
        }
    }
}
