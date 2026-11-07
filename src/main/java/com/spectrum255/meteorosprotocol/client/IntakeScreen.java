package com.spectrum255.meteorosprotocol.client;

import com.spectrum255.meteorosprotocol.MeteorosProtocol;
import com.spectrum255.meteorosprotocol.Config;
import com.spectrum255.meteorosprotocol.client.gui.IconButton;
import com.spectrum255.meteorosprotocol.client.gui.IntakeLayout;
import com.spectrum255.meteorosprotocol.client.gui.MeteorosGui;
import com.spectrum255.meteorosprotocol.client.gui.PhaseRowWidget;
import com.spectrum255.meteorosprotocol.client.gui.SideConfigPanel;
import com.spectrum255.meteorosprotocol.item.InfiniteParticleCanisterItem;
import com.spectrum255.meteorosprotocol.machine.MachineBlockEntity;
import com.spectrum255.meteorosprotocol.machine.SideChannel;
import com.spectrum255.meteorosprotocol.particle.ParticleFormat;
import com.spectrum255.meteorosprotocol.particle.Phase;
import com.spectrum255.meteorosprotocol.menu.IntakeMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Intake tower screen: 176 x 180, vanilla-styled container panel with a wide
 * phase list holding one scrolling horizontal row per registered phase (six core
 * phases plus impure aetheron, so seven today), a separate mixed-buffer bar,
 * one FE bar, two slots and three 20 x 20 icon buttons.
 *
 * <p>The phase rows are real {@link PhaseRowWidget}s, so selecting a phase uses
 * vanilla widget dispatch (button id {@code 2 + phase.index()}); four rows are
 * visible at once and the mouse wheel scrolls the rest. The selected row gets a
 * 1 px white frame. The extract button pumps the selected phase into the
 * canister in slot 1, phase tank first, mixed buffer second.</p>
 */
public class IntakeScreen extends AbstractContainerScreen<IntakeMenu> {
    private static final int TITLE_Y = 6;
    private static final int TITLE_COLOR = 0xFF404040;
    private static final int GHOST = 0x66C8D0D8;
    private static final int FE_COLOR = 0xFFFF0000;
    /** The mixed buffer always uses one fixed grey; it is a summed pool, not a phase. */
    private static final int MIXED_COLOR = 0xFF8B8B8B;

    /** 20 x 20 square icon buttons on the right of the content band. */
    private static final int ICON_BUTTON_SIZE = 20;
    private static final int ICON_BUTTON_X = IntakeLayout.BUTTON_X;
    private static final int ICON_TOGGLE_Y = 18;
    private static final int ICON_REDSTONE_Y = 40;
    private static final int ICON_EXTRACT_Y = 62;

    private static final String ICON_ROOT = "textures/gui/";
    private static final ResourceLocation ICON_POWER_ON = icon("icon_power");
    private static final ResourceLocation ICON_POWER_OFF = icon("icon_power_off");
    private static final ResourceLocation ICON_REDSTONE_HIGH = icon("icon_redstone_high");
    private static final ResourceLocation ICON_REDSTONE_LOW = icon("icon_redstone_low");
    private static final ResourceLocation ICON_REDSTONE_ALWAYS = icon("icon_redstone_always");
    private static final ResourceLocation ICON_EXTRACT = icon("icon_extract");
    private static final ResourceLocation ICON_SIDE = icon("icon_side");
    private static final ResourceLocation ICON_AUTOIO_ON = icon("icon_autoio");
    private static final ResourceLocation ICON_AUTOIO_OFF = icon("icon_autoio_off");
    /** Side-config entry button sits in the top-right of the panel, above the reserved band. */
    private static final int SIDE_BUTTON_X = 152;
    private static final int SIDE_BUTTON_Y = 4;
    /** Auto-I/O toggle sits just left of the side-config button. */
    private static final int AUTOIO_BUTTON_X = 132;
    private static final int AUTOIO_BUTTON_Y = 4;

    private final List<PhaseRowWidget> phaseRows = new ArrayList<>();
    /** First visible phase row; clamped to {@code [0, size - 4]}. */
    private int phaseScroll;
    private final SideConfigPanel sidePanel = new SideConfigPanel();
    private IconButton toggleButton;
    private IconButton redstoneButton;
    private IconButton extractButton;
    private IconButton sideButton;
    private IconButton autoIoButton;

    public IntakeScreen(IntakeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = IntakeLayout.WIDTH;
        this.imageHeight = IntakeLayout.HEIGHT;
    }

    private static ResourceLocation icon(String name) {
        return ResourceLocation.fromNamespaceAndPath(MeteorosProtocol.MODID, ICON_ROOT + name + ".png");
    }

    @Override
    protected void init() {
        super.init();
        int buttonX = this.leftPos + ICON_BUTTON_X;
        this.toggleButton = this.addRenderableWidget(new IconButton(buttonX, this.topPos + ICON_TOGGLE_Y,
                ICON_BUTTON_SIZE, ICON_BUTTON_SIZE, this::powerIcon, this::powerTooltip, button -> sendButton(0)));
        this.redstoneButton = this.addRenderableWidget(new IconButton(buttonX, this.topPos + ICON_REDSTONE_Y,
                ICON_BUTTON_SIZE, ICON_BUTTON_SIZE, this::redstoneIcon, this::redstoneTooltip,
                button -> sendButton(1)));
        this.extractButton = this.addRenderableWidget(new IconButton(buttonX, this.topPos + ICON_EXTRACT_Y,
                ICON_BUTTON_SIZE, ICON_BUTTON_SIZE, ICON_EXTRACT, this::extractTooltip,
                button -> sendButton(this.menu.extractButtonId())));
        this.sideButton = this.addRenderableWidget(new IconButton(this.leftPos + SIDE_BUTTON_X,
                this.topPos + SIDE_BUTTON_Y, ICON_BUTTON_SIZE, ICON_BUTTON_SIZE, ICON_SIDE,
                this::sideTooltip, button -> this.sidePanel.toggle()));
        this.autoIoButton = this.addRenderableWidget(new IconButton(this.leftPos + AUTOIO_BUTTON_X,
                this.topPos + AUTOIO_BUTTON_Y, ICON_BUTTON_SIZE, ICON_BUTTON_SIZE, this::autoIoIcon,
                this::autoIoTooltip, button -> sendButton(this.menu.autoIoButtonId())));
        this.sidePanel.setItemAvailable(true);

        this.phaseRows.clear();
        this.phaseScroll = 0;
        int count = Phase.all().size();
        int rowX = this.leftPos + IntakeLayout.PHASE_ROW_X;
        for (int i = 0; i < count; i++) {
            final int index = i;
            PhaseRowWidget row = this.addRenderableWidget(new PhaseRowWidget(
                    rowX,
                    this.topPos + IntakeLayout.PHASE_ROW_Y,
                    IntakeLayout.PHASE_ROW_WIDTH,
                    IntakeLayout.PHASE_ROW_HEIGHT,
                    index,
                    () -> phaseFill(index),
                    () -> Phase.all().get(index).argb(),
                    () -> Phase.all().get(index).darkArgb(),
                    () -> phaseRowText(index),
                    () -> this.menu.canisterPhaseIndex() == index,
                    id -> sendButton(2 + id)));
            this.phaseRows.add(row);
        }
        updateRowLayout();

        updateWidgets();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateWidgets();
        updateRowLayout();
    }

    /** Keeps the extract button enabled state in sync; tooltips are live suppliers. */
    private void updateWidgets() {
        if (this.extractButton != null) {
            this.extractButton.active = extractReady();
        }
    }

    private float phaseFill(int index) {
        Phase phase = Phase.all().get(index);
        long capacity = phase.tankCapacity();
        if (capacity <= 0L) {
            return 0.0F;
        }
        return (float) ((double) this.menu.phaseAmount(index) / (double) capacity);
    }

    /** Row label: short phase name plus the tank amount, e.g. {@code "T 12.3 kPU"}. */
    private Component phaseRowText(int index) {
        Phase phase = Phase.all().get(index);
        return Component.literal(phase.shortName().getString() + " "
                + ParticleFormat.formatMPU(this.menu.phaseAmount(index)));
    }

    /** Phases currently listed: non-empty tanks plus the selected phase, oldest arrival first. */
    private List<Phase> displayedPhases() {
        int selected = this.menu.canisterPhaseIndex();
        List<Phase> shown = new ArrayList<>();
        for (Phase phase : Phase.all()) {
            if (this.menu.phaseAmount(phase.index()) > 0L || phase.index() == selected) {
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
    private void updateRowLayout() {
        List<Phase> phases = displayedPhases();
        int total = phases.size();
        int maxScroll = Math.max(0, total - IntakeLayout.PHASE_ROW_VISIBLE);
        this.phaseScroll = Mth.clamp(this.phaseScroll, 0, maxScroll);
        for (PhaseRowWidget row : this.phaseRows) {
            row.visible = false;
        }
        int top = this.topPos + IntakeLayout.PHASE_ROW_Y;
        for (int position = 0; position < total; position++) {
            int slot = position - this.phaseScroll;
            if (slot < 0 || slot >= IntakeLayout.PHASE_ROW_VISIBLE) {
                continue;
            }
            PhaseRowWidget row = this.phaseRows.get(phases.get(position).index());
            row.visible = true;
            row.setY(top + slot * IntakeLayout.PHASE_ROW_STEP);
        }
    }

    /** 2 px wheel-only scrollbar; drawn in the background so it never blocks the rows. */
    private void drawPhaseScrollbar(GuiGraphics graphics, int x, int y) {
        int total = displayedPhases().size();
        if (total <= IntakeLayout.PHASE_ROW_VISIBLE) {
            return;
        }
        int barX = x + IntakeLayout.PHASE_SCROLLBAR_X;
        int barY = y + IntakeLayout.PHASE_SCROLLBAR_Y;
        int barWidth = IntakeLayout.PHASE_SCROLLBAR_WIDTH;
        int barHeight = IntakeLayout.PHASE_SCROLLBAR_HEIGHT;
        graphics.fill(barX, barY, barX + barWidth, barY + barHeight, MeteorosGui.SLOT_DARK);
        int thumbHeight = Math.max(6, barHeight * IntakeLayout.PHASE_ROW_VISIBLE / total);
        int maxScroll = total - IntakeLayout.PHASE_ROW_VISIBLE;
        int thumbY = barY + (int) Math.round((double) (barHeight - thumbHeight) * this.phaseScroll / maxScroll);
        graphics.fill(barX, thumbY, barX + barWidth, thumbY + thumbHeight, MeteorosGui.SLOT_BODY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        MeteorosGui.panel(graphics, x, y, IntakeLayout.WIDTH, IntakeLayout.HEIGHT);

        MeteorosGui.phaseBarH(graphics, x + IntakeLayout.MIXED_BAR_X, y + IntakeLayout.MIXED_BAR_Y,
                IntakeLayout.MIXED_BAR_WIDTH, IntakeLayout.MIXED_BAR_HEIGHT,
                (float) ((double) this.menu.mixedTotal() / (double) MachineBlockEntity.MIXED_CAPACITY),
                MIXED_COLOR, 0xFF555555);

        drawPhaseScrollbar(graphics, x, y);
        if (displayedPhases().isEmpty()) {
            graphics.drawString(this.font,
                    Component.translatable("gui.meteorosprotocol.intake.empty"),
                    x + IntakeLayout.PHASE_ROW_X, y + IntakeLayout.PHASE_ROW_Y, 0xFF808080, false);
        }

        MeteorosGui.barHWithText(graphics, this.font, x + IntakeLayout.FE_X, y + IntakeLayout.FE_Y,
                IntakeLayout.FE_WIDTH, IntakeLayout.FE_HEIGHT,
                (float) ((double) this.menu.energy() / (double) IntakeMenu.ENERGY_CAPACITY),
                FE_COLOR, 0xFF555555,
                Component.literal(ParticleFormat.formatFE(this.menu.energy()) + " / "
                        + ParticleFormat.formatFE(IntakeMenu.ENERGY_CAPACITY)));

        boolean filterHovered = inside(mouseX, mouseY,
                x + IntakeLayout.FILTER_SLOT_X, y + IntakeLayout.FILTER_SLOT_Y, 16, 16);
        MeteorosGui.slot(graphics, x + IntakeLayout.FILTER_SLOT_X, y + IntakeLayout.FILTER_SLOT_Y, filterHovered);
        boolean loadHovered = inside(mouseX, mouseY,
                x + IntakeLayout.LOAD_SLOT_X, y + IntakeLayout.LOAD_SLOT_Y, 16, 16);
        MeteorosGui.slot(graphics, x + IntakeLayout.LOAD_SLOT_X, y + IntakeLayout.LOAD_SLOT_Y, loadHovered);

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slotX = x + IntakeLayout.INVENTORY_X + col * IntakeLayout.INVENTORY_STEP;
                int slotY = y + IntakeLayout.INVENTORY_Y + row * IntakeLayout.INVENTORY_STEP;
                MeteorosGui.slot(graphics, slotX, slotY, inside(mouseX, mouseY, slotX, slotY, 16, 16));
            }
        }
        for (int col = 0; col < 9; col++) {
            int slotX = x + IntakeLayout.INVENTORY_X + col * IntakeLayout.INVENTORY_STEP;
            int slotY = y + IntakeLayout.HOTBAR_Y;
            MeteorosGui.slot(graphics, slotX, slotY, inside(mouseX, mouseY, slotX, slotY, 16, 16));
        }

        if (this.menu.getSlot(0).getItem().isEmpty()) {
            drawGhostFilter(graphics, x + IntakeLayout.FILTER_SLOT_X, y + IntakeLayout.FILTER_SLOT_Y);
        }
    }

    /** Phase currently selected as the extraction target, clamped for safety. */
    private Phase selectedPhase() {
        int index = Mth.clamp(this.menu.canisterPhaseIndex(), 0, Math.max(0, Phase.all().size() - 1));
        return Phase.all().get(index);
    }

    /**
     * Client-side preview of the server guard: slot 1 must hold a canister that
     * can still accept particles, and the selected phase must have material in
     * either its phase tank or the mixed buffer.
     */
    private boolean extractReady() {
        ItemStack canister = this.menu.getSlot(1).getItem();
        boolean accepts = canister.is(MeteorosProtocol.CANISTER.get())
                || (canister.is(MeteorosProtocol.INFINITE_PARTICLE_CANISTER.get())
                && !InfiniteParticleCanisterItem.hasPhase(canister));
        int selected = Mth.clamp(this.menu.canisterPhaseIndex(), 0, Math.max(0, Phase.all().size() - 1));
        return accepts
                && (this.menu.phaseAmount(selected) > 0L || this.menu.mixedAmount(selected) > 0L);
    }

    /** Six-socket filter ghost: a horizontal cartridge outline, lit at low alpha. */
    private static void drawGhostFilter(GuiGraphics graphics, int x, int y) {
        graphics.fill(x + 1, y + 4, x + 15, y + 5, GHOST);
        graphics.fill(x + 1, y + 11, x + 15, y + 12, GHOST);
        graphics.fill(x + 1, y + 4, x + 2, y + 12, GHOST);
        graphics.fill(x + 14, y + 4, x + 15, y + 12, GHOST);
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 3; col++) {
                int socketX = x + 3 + col * 3;
                int socketY = y + 6 + row * 3;
                graphics.fill(socketX, socketY, socketX + 2, socketY + 2, GHOST);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // Vanilla container title position: 8 px from the panel's left edge.
        graphics.drawString(this.font, this.title, 8, TITLE_Y, TITLE_COLOR, false);
        MeteorosGui.label(graphics, this.font, this.playerInventoryTitle,
                IntakeLayout.INVENTORY_LABEL_X, IntakeLayout.INVENTORY_LABEL_Y);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (this.sidePanel.visible()) {
            // AbstractContainerScreen queues the hovered item's tooltip through
            // setTooltipForNextRenderPass; Screen.renderWithTooltip draws it
            // AFTER render() returns, which would paint it on top of our overlay.
            // Drop the queued tooltip so only the panel (and its own tooltip) are
            // visible while the overlay is open.
            this.clearTooltipForNextRenderPass();
            // Slot items are batched through the GUI buffer source; flush them now so
            // the opaque overlay is submitted after their draw calls instead of being
            // painted underneath a later batch flush.
            graphics.flush();
            // Opaque backdrop over the whole container window: the overlay must sit
            // above the slot grid and the player inventory, not underneath them.
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
        // Submit our tooltip before the screen returns: the item icons are batched as well
        // and must not be flushed on top of the tooltip afterwards.
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
        if (inside(mouseX, mouseY, this.leftPos + IntakeLayout.PHASE_ROW_X,
                this.topPos + IntakeLayout.PHASE_ROW_Y,
                IntakeLayout.PHASE_ROW_WIDTH + IntakeLayout.PHASE_SCROLLBAR_WIDTH + 2,
                IntakeLayout.PHASE_ROW_WINDOW_HEIGHT)) {
            int total = displayedPhases().size();
            int maxScroll = Math.max(0, total - IntakeLayout.PHASE_ROW_VISIBLE);
            if (maxScroll > 0) {
                int direction = scrollY > 0.0D ? -1 : 1;
                int next = Mth.clamp(this.phaseScroll + direction, 0, maxScroll);
                if (next != this.phaseScroll) {
                    this.phaseScroll = next;
                    updateRowLayout();
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

    private void drawTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        // Vanilla already draws the hovered item's tooltip; never stack ours on top.
        if (this.hoveredSlot != null && this.hoveredSlot.hasItem()) {
            return;
        }
        for (PhaseRowWidget row : this.phaseRows) {
            if (row.visible && row.isHovered()) {
                drawPhaseTooltip(graphics, row.index(), mouseX, mouseY);
                return;
            }
        }
        if (drawWidgetTooltips(graphics, mouseX, mouseY)) {
            return;
        }

        int x = this.leftPos;
        int y = this.topPos;
        if (inside(mouseX, mouseY, x + IntakeLayout.MIXED_BAR_X, y + IntakeLayout.MIXED_BAR_Y,
                IntakeLayout.MIXED_BAR_WIDTH, IntakeLayout.MIXED_BAR_HEIGHT)) {
            String line = Component.translatable("gui.meteorosprotocol.intake.mixed").getString()
                    + ": " + ParticleFormat.formatPlain(this.menu.mixedTotal(), "mPU")
                    + " / " + ParticleFormat.formatPlain(MachineBlockEntity.MIXED_CAPACITY, "mPU");
            MeteorosGui.tooltip(graphics, this.font, Component.literal(line), mouseX, mouseY);
            return;
        }
        if (inside(mouseX, mouseY, x + IntakeLayout.FE_X, y + IntakeLayout.FE_Y,
                IntakeLayout.FE_WIDTH, IntakeLayout.FE_HEIGHT)) {
            String line = "FE " + ParticleFormat.formatPlain(this.menu.energy(), "FE") + " / "
                    + ParticleFormat.formatPlain(IntakeMenu.ENERGY_CAPACITY, "FE");
            MeteorosGui.tooltip(graphics, this.font, Component.literal(line), mouseX, mouseY);
            return;
        }
        if (inside(mouseX, mouseY, x + IntakeLayout.FILTER_SLOT_X, y + IntakeLayout.FILTER_SLOT_Y, 16, 16)) {
            if (this.menu.getSlot(0).getItem().isEmpty()) {
                MeteorosGui.tooltip(graphics, this.font,
                        Component.translatable("gui.meteorosprotocol.intake.filter"), mouseX, mouseY);
            }
            return;
        }
        if (inside(mouseX, mouseY, x + IntakeLayout.LOAD_SLOT_X, y + IntakeLayout.LOAD_SLOT_Y, 16, 16)) {
            if (this.menu.getSlot(1).getItem().isEmpty()) {
                MeteorosGui.tooltip(graphics, this.font,
                        Component.translatable("gui.meteorosprotocol.intake.loading"), mouseX, mouseY);
            }
        }
    }

    /** Widget tooltips for the three icon buttons and the side-config entry. */
    private boolean drawWidgetTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        if (this.toggleButton != null && this.toggleButton.isHovered()) {
            MeteorosGui.tooltip(graphics, this.font, this.toggleButton.tooltipLines(), mouseX, mouseY,
                    0xFF9A9A9A);
            return true;
        }
        if (this.redstoneButton != null && this.redstoneButton.isHovered()) {
            MeteorosGui.tooltip(graphics, this.font, this.redstoneButton.tooltipLines(), mouseX, mouseY,
                    0xFF9A9A9A);
            return true;
        }
        if (this.extractButton != null && this.extractButton.isHovered()) {
            MeteorosGui.tooltip(graphics, this.font, this.extractButton.tooltipLines(), mouseX, mouseY,
                    0xFF9A9A9A);
            return true;
        }
        if (this.sideButton != null && this.sideButton.isHovered()) {
            MeteorosGui.tooltip(graphics, this.font, this.sideButton.tooltipLines(), mouseX, mouseY,
                    0xFF9A9A9A);
            return true;
        }
        if (this.autoIoButton != null && this.autoIoButton.isHovered()) {
            MeteorosGui.tooltip(graphics, this.font, this.autoIoButton.tooltipLines(), mouseX, mouseY,
                    0xFF9A9A9A);
            return true;
        }
        return false;
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

    private List<Component> sideTooltip() {
        return List.of(Component.translatable("gui.meteorosprotocol.side.open"));
    }

    /** Phase name, phase-tank fill, mixed-buffer amount and the selection hint. */
    private void drawPhaseTooltip(GuiGraphics graphics, int index, int mouseX, int mouseY) {
        Phase value = Phase.all().get(index);
        long stored = this.menu.phaseAmount(index);
        long capacity = value.tankCapacity();
        Component tankLine = Component.literal(value.displayName().getString()
                + ": " + ParticleFormat.formatPlain(stored, "mPU")
                + " / " + ParticleFormat.formatPlain(capacity, "mPU"));
        Component mixedLine = Component.literal(
                Component.translatable("gui.meteorosprotocol.intake.mixed").getString()
                        + ": " + ParticleFormat.formatPlain(this.menu.mixedAmount(index), "mPU"));
        Component hint = Component.translatable("gui.meteorosprotocol.intake.select_hint");
        List<Component> lines = new ArrayList<>();
        lines.add(tankLine);
        if (Config.showMixedInPhaseTooltip()) {
            lines.add(mixedLine);
        }
        lines.add(hint);
        MeteorosGui.tooltip(graphics, this.font, lines, mouseX, mouseY, 0xFF9A9A9A);
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
        return List.of(Component.translatable(this.menu.enabled()
                ? "gui.meteorosprotocol.intake.power.enabled"
                : "gui.meteorosprotocol.intake.power.disabled"));
    }

    private List<Component> redstoneTooltip() {
        String key = switch (this.menu.redstoneMode()) {
            case 0 -> "gui.meteorosprotocol.intake.redstone.high";
            case 1 -> "gui.meteorosprotocol.intake.redstone.low";
            default -> "gui.meteorosprotocol.intake.redstone.always";
        };
        return List.of(Component.translatable(key));
    }

    private List<Component> extractTooltip() {
        return List.of(
                Component.translatable("gui.meteorosprotocol.intake.extract", selectedPhase().displayName()),
                Component.translatable("gui.meteorosprotocol.intake.extract_hint"));
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
