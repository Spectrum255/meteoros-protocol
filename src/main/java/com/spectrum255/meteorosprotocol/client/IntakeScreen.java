package com.spectrum255.meteorosprotocol.client;

import com.spectrum255.meteorosprotocol.MeteorosProtocol;
import com.spectrum255.meteorosprotocol.client.gui.IconButton;
import com.spectrum255.meteorosprotocol.client.gui.IntakeLayout;
import com.spectrum255.meteorosprotocol.client.gui.MeteorosGui;
import com.spectrum255.meteorosprotocol.client.gui.PhaseBarWidget;
import com.spectrum255.meteorosprotocol.item.InfiniteParticleCanisterItem;
import com.spectrum255.meteorosprotocol.machine.MachineBlockEntity;
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
import java.util.Locale;

/**
 * Intake tower screen: 176 x 180, vanilla-styled container panel with a wide
 * phase-tank frame holding six vertical tank bars, a separate mixed-buffer bar,
 * one FE bar, two slots and three 20 x 20 icon buttons.
 *
 * <p>The six phase bars are real {@link PhaseBarWidget}s, so selecting a phase
 * uses vanilla widget dispatch (button id {@code 2 + phase.index()}); the
 * selected bar gets a 2 px white frame. The extract button pumps the selected
 * phase into the canister in slot 1, phase tank first, mixed buffer second.</p>
 */
public class IntakeScreen extends AbstractContainerScreen<IntakeMenu> {
    private static final int TITLE_Y = 6;
    private static final int TITLE_COLOR = 0xFF404040;
    private static final int GHOST = 0x66C8D0D8;
    private static final int FE_COLOR = 0xFFFF0000;
    private static final int MIXED_EMPTY_COLOR = 0xFF555555;

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

    private final List<PhaseBarWidget> phaseBars = new ArrayList<>();
    private IconButton toggleButton;
    private IconButton redstoneButton;
    private IconButton extractButton;

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
                ICON_BUTTON_SIZE, ICON_BUTTON_SIZE, ICON_EXTRACT, this::extractTooltip, button -> sendButton(8)));

        this.phaseBars.clear();
        int count = Math.min(IntakeLayout.PHASE_BAR_COUNT, Phase.all().size());
        for (int i = 0; i < count; i++) {
            final int index = i;
            PhaseBarWidget bar = this.addRenderableWidget(new PhaseBarWidget(
                    this.leftPos + IntakeLayout.PHASE_BAR_X + i * IntakeLayout.PHASE_BAR_STEP,
                    this.topPos + IntakeLayout.PHASE_BAR_Y,
                    index,
                    () -> phaseFill(index),
                    () -> Phase.all().get(index).argb(),
                    () -> this.menu.canisterPhaseIndex() == index,
                    id -> sendButton(2 + id)));
            this.phaseBars.add(bar);
        }

        updateWidgets();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateWidgets();
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

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        MeteorosGui.panel(graphics, x, y, IntakeLayout.WIDTH, IntakeLayout.HEIGHT);

        drawTankFrame(graphics, x + IntakeLayout.TANK_X, y + IntakeLayout.TANK_Y);

        MeteorosGui.barH(graphics, x + IntakeLayout.MIXED_BAR_X, y + IntakeLayout.MIXED_BAR_Y,
                IntakeLayout.MIXED_BAR_WIDTH, IntakeLayout.MIXED_BAR_HEIGHT,
                (float) ((double) this.menu.mixedTotal() / (double) MachineBlockEntity.MIXED_CAPACITY),
                mixedBufferColor());

        MeteorosGui.barH(graphics, x + IntakeLayout.FE_X, y + IntakeLayout.FE_Y,
                IntakeLayout.FE_WIDTH, IntakeLayout.FE_HEIGHT,
                (float) ((double) this.menu.energy() / (double) IntakeMenu.ENERGY_CAPACITY), FE_COLOR);

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

    /** Recessed frame behind the six phase bars; the bars themselves are widgets. */
    private static void drawTankFrame(GuiGraphics graphics, int x, int y) {
        int width = IntakeLayout.TANK_WIDTH;
        int height = IntakeLayout.TANK_HEIGHT;
        graphics.fill(x, y, x + width, y + height, MeteorosGui.SLOT_DARK);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, MeteorosGui.SLOT_BODY);
    }

    /** Colour of the mixed-buffer bar: the phase holding the most mPU. */
    private int mixedBufferColor() {
        int count = Math.min(Phase.all().size(), IntakeLayout.PHASE_BAR_COUNT);
        int best = -1;
        long bestAmount = 0L;
        for (int i = 0; i < count; i++) {
            long amount = this.menu.mixedAmount(i);
            if (amount > bestAmount) {
                bestAmount = amount;
                best = i;
            }
        }
        return best < 0 ? MIXED_EMPTY_COLOR : Phase.all().get(best).argb();
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
        this.renderTooltip(graphics, mouseX, mouseY);
        drawTooltips(graphics, mouseX, mouseY);
    }

    private void drawTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        // Vanilla already draws the hovered item's tooltip; never stack ours on top.
        if (this.hoveredSlot != null && this.hoveredSlot.hasItem()) {
            return;
        }
        for (PhaseBarWidget bar : this.phaseBars) {
            if (bar.isHovered()) {
                drawPhaseTooltip(graphics, bar.index(), mouseX, mouseY);
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
                    + ": " + String.format(Locale.ROOT, "%,d", this.menu.mixedTotal())
                    + " / 1,000,000 mPU";
            MeteorosGui.tooltip(graphics, this.font, Component.literal(line), mouseX, mouseY);
            return;
        }
        if (inside(mouseX, mouseY, x + IntakeLayout.FE_X, y + IntakeLayout.FE_Y,
                IntakeLayout.FE_WIDTH, IntakeLayout.FE_HEIGHT)) {
            String line = "FE " + String.format(Locale.ROOT, "%,d", this.menu.energy()) + " / "
                    + String.format(Locale.ROOT, "%,d", IntakeMenu.ENERGY_CAPACITY);
            MeteorosGui.tooltip(graphics, this.font, Component.literal(line), mouseX, mouseY);
            return;
        }
        if (inside(mouseX, mouseY, x + IntakeLayout.TANK_X, y + IntakeLayout.TANK_Y,
                IntakeLayout.TANK_WIDTH, IntakeLayout.TANK_HEIGHT)) {
            String line = Component.translatable("gui.meteorosprotocol.intake.mixed").getString()
                    + ": " + String.format(Locale.ROOT, "%,d", this.menu.mixedTotal())
                    + " / 1,000,000 mPU";
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

    /** Widget tooltips for the three icon buttons. */
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
        return false;
    }

    /** Phase name, phase-tank fill, mixed-buffer amount and the selection hint. */
    private void drawPhaseTooltip(GuiGraphics graphics, int index, int mouseX, int mouseY) {
        Phase value = Phase.all().get(index);
        long stored = this.menu.phaseAmount(index);
        long capacity = value.tankCapacity();
        Component tankLine = Component.literal(value.displayName().getString()
                + ": " + String.format(Locale.ROOT, "%,d", stored)
                + " / " + String.format(Locale.ROOT, "%,d", capacity) + " mPU");
        Component mixedLine = Component.literal(
                Component.translatable("gui.meteorosprotocol.intake.mixed").getString()
                        + ": " + String.format(Locale.ROOT, "%,d", this.menu.mixedAmount(index)) + " mPU");
        Component hint = Component.translatable("gui.meteorosprotocol.intake.select_hint");
        MeteorosGui.tooltip(graphics, this.font, List.of(tankLine, mixedLine, hint), mouseX, mouseY,
                0xFF9A9A9A);
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
