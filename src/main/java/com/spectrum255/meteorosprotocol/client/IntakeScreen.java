package com.spectrum255.meteorosprotocol.client;

import com.spectrum255.meteorosprotocol.MeteorosProtocol;
import com.spectrum255.meteorosprotocol.client.gui.IconButton;
import com.spectrum255.meteorosprotocol.client.gui.IntakeLayout;
import com.spectrum255.meteorosprotocol.client.gui.MeteorosGui;
import com.spectrum255.meteorosprotocol.item.InfiniteParticleCanisterItem;
import com.spectrum255.meteorosprotocol.machine.MachineBlockEntity;
import com.spectrum255.meteorosprotocol.menu.IntakeMenu;
import com.spectrum255.meteorosprotocol.particle.Phase;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;

/**
 * Intake tower screen: 176 x 180, vanilla-styled container panel with a banded
 * mixture tank, one horizontal FE bar, two slots and three 20 x 20 icon buttons.
 *
 * <p>The tank itself is the phase selector: clicking a colour band selects that
 * phase for extraction (button id {@code 2 + phase.index()}), the selected band
 * gets a white frame and a hovered band is washed with white. The three icon
 * buttons keep their own vanilla widget dispatch, and the extract button pumps
 * the selected phase into the canister in slot 1.
 */
public class IntakeScreen extends AbstractContainerScreen<IntakeMenu> {
    private static final int TITLE_Y = 6;
    private static final int TITLE_COLOR = 0xFF404040;
    private static final int TICK_COLOR = 0x66000000;
    private static final int GHOST = 0x66C8D0D8;
    private static final int FE_COLOR = 0xFFFF0000;

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

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if (handled || button != 0) {
            return handled;
        }
        int phase = tankPhaseAt(mouseX, mouseY);
        if (phase >= 0) {
            sendButton(2 + phase);
            return true;
        }
        return false;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        MeteorosGui.panel(graphics, x, y, IntakeLayout.WIDTH, IntakeLayout.HEIGHT);

        drawMixtureTank(graphics, x + IntakeLayout.TANK_X, y + IntakeLayout.TANK_Y, mouseX, mouseY);

        MeteorosGui.barH(graphics, x + IntakeLayout.FE_X, y + IntakeLayout.FE_Y,
                IntakeLayout.FE_WIDTH, IntakeLayout.FE_HEIGHT,
                (float) this.menu.energy() / IntakeMenu.ENERGY_CAPACITY, FE_COLOR);

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

    private void drawMixtureTank(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        int width = IntakeLayout.TANK_WIDTH;
        int height = IntakeLayout.TANK_HEIGHT;
        graphics.fill(x, y, x + width, y + height, MeteorosGui.SLOT_DARK);

        int count = Phase.all().size();
        long[] amounts = new long[count];
        int[] colors = new int[count];
        for (int index = 0; index < count; index++) {
            amounts[index] = this.menu.mixedAmount(index);
            colors[index] = Phase.all().get(index).argb();
        }
        int innerX = x + 1;
        int innerY = y + 1;
        int innerW = width - 2;
        int innerH = height - 2;
        graphics.fill(innerX, innerY, innerX + innerW, innerY + innerH, MeteorosGui.SLOT_BODY);
        MeteorosGui.stripV(graphics, innerX, innerY, innerW, innerH,
                amounts, this.menu.mixedTotal(), colors);

        // 25/50/75% tick marks, like a fluid gauge.
        for (int tick = 1; tick <= 3; tick++) {
            int tickY = innerY + innerH - innerH * tick / 4;
            graphics.fill(innerX, tickY, innerX + innerW, tickY + 1, TICK_COLOR);
        }

        int hovered = tankPhaseAt(mouseX, mouseY);
        if (hovered >= 0) {
            int[] bounds = segmentBounds(hovered, innerY, innerH);
            if (bounds != null) {
                graphics.fill(innerX, bounds[0], innerX + innerW, bounds[1], 0x30FFFFFF);
            }
        }

        int selected = Mth.clamp(this.menu.canisterPhaseIndex(), 0, Math.max(0, count - 1));
        int[] selectedBounds = segmentBounds(selected, innerY, innerH);
        if (selectedBounds != null) {
            drawSegmentFrame(graphics, innerX, selectedBounds[0], innerW, selectedBounds[1]);
        }
    }

    /** 1 px white frame around the selected colour band. */
    private static void drawSegmentFrame(GuiGraphics graphics, int x, int top, int width, int bottom) {
        graphics.fill(x, top, x + width, top + 1, MeteorosGui.SELECTED_FRAME);
        graphics.fill(x, bottom - 1, x + width, bottom, MeteorosGui.SELECTED_FRAME);
        graphics.fill(x, top, x + 1, bottom, MeteorosGui.SELECTED_FRAME);
        graphics.fill(x + width - 1, top, x + width, bottom, MeteorosGui.SELECTED_FRAME);
    }

    /**
     * Absolute top/bottom rows of the colour band that belongs to {@code index},
     * computed with exactly the same proportional stacking as
     * {@link MeteorosGui#stripV} (segments grow upwards from the bottom, the last
     * non-empty phase absorbs the rounding remainder).
     *
     * @return {@code [top, bottom]} in screen coordinates, or {@code null} when
     *         the phase has no visible band
     */
    private int[] segmentBounds(int index, int innerY, int innerH) {
        int count = Phase.all().size();
        if (index < 0 || index >= count) {
            return null;
        }
        long total = this.menu.mixedTotal();
        if (total <= 0L) {
            return null;
        }
        long[] amounts = new long[count];
        int last = -1;
        for (int i = 0; i < count; i++) {
            amounts[i] = Math.max(0L, this.menu.mixedAmount(i));
            if (amounts[i] > 0L) {
                last = i;
            }
        }
        if (last < 0) {
            return null;
        }
        int used = 0;
        for (int i = 0; i <= last; i++) {
            long amount = amounts[i];
            if (amount <= 0L) {
                continue;
            }
            int seg = i == last
                    ? innerH - used
                    : (int) Math.round((double) amount / total * innerH);
            seg = Math.max(0, Math.min(seg, innerH - used));
            if (seg > 0) {
                int bottom = innerY + innerH - used;
                int top = bottom - seg;
                if (i == index) {
                    return new int[]{top, bottom};
                }
                used += seg;
            }
        }
        return null;
    }

    /**
     * Phase band under the cursor. Uses the same geometry as {@link #segmentBounds}
     * so the band that is highlighted is always the band that gets selected.
     */
    private int tankPhaseAt(double mouseX, double mouseY) {
        int x = this.leftPos + IntakeLayout.TANK_X;
        int y = this.topPos + IntakeLayout.TANK_Y;
        int innerX = x + 1;
        int innerY = y + 1;
        int innerW = IntakeLayout.TANK_WIDTH - 2;
        int innerH = IntakeLayout.TANK_HEIGHT - 2;
        if (!inside(mouseX, mouseY, innerX, innerY, innerW, innerH)) {
            return -1;
        }
        long total = this.menu.mixedTotal();
        if (total <= 0L) {
            return -1;
        }
        int count = Phase.all().size();
        long[] amounts = new long[count];
        int last = -1;
        for (int i = 0; i < count; i++) {
            amounts[i] = Math.max(0L, this.menu.mixedAmount(i));
            if (amounts[i] > 0L) {
                last = i;
            }
        }
        if (last < 0) {
            return -1;
        }
        int used = 0;
        for (int i = 0; i <= last; i++) {
            long amount = amounts[i];
            if (amount <= 0L) {
                continue;
            }
            int seg = i == last
                    ? innerH - used
                    : (int) Math.round((double) amount / total * innerH);
            seg = Math.max(0, Math.min(seg, innerH - used));
            if (seg > 0) {
                int bottom = innerY + innerH - used;
                int top = bottom - seg;
                if (mouseY >= top && mouseY < bottom) {
                    return i;
                }
                used += seg;
            }
        }
        return -1;
    }

    /** Phase currently selected as the extraction target, clamped for safety. */
    private Phase selectedPhase() {
        int index = Mth.clamp(this.menu.canisterPhaseIndex(), 0, Math.max(0, Phase.all().size() - 1));
        return Phase.all().get(index);
    }

    /**
     * Client-side preview of the server guard: slot 1 must hold a canister that
     * can still accept particles, and the selected phase must have material.
     */
    private boolean extractReady() {
        ItemStack canister = this.menu.getSlot(1).getItem();
        boolean accepts = canister.is(MeteorosProtocol.CANISTER.get())
                || (canister.is(MeteorosProtocol.INFINITE_PARTICLE_CANISTER.get())
                && !InfiniteParticleCanisterItem.hasPhase(canister));
        int selected = Mth.clamp(this.menu.canisterPhaseIndex(), 0, Math.max(0, Phase.all().size() - 1));
        return accepts && this.menu.mixedAmount(selected) > 0L;
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
        if (drawWidgetTooltips(graphics, mouseX, mouseY)) {
            return;
        }

        int x = this.leftPos;
        int y = this.topPos;
        int tankPhase = tankPhaseAt(mouseX, mouseY);
        if (tankPhase >= 0) {
            drawPhaseTooltip(graphics, tankPhase, mouseX, mouseY);
            return;
        }
        if (inside(mouseX, mouseY, x + IntakeLayout.TANK_X, y + IntakeLayout.TANK_Y,
                IntakeLayout.TANK_WIDTH, IntakeLayout.TANK_HEIGHT)) {
            String line = Component.translatable("gui.meteorosprotocol.intake.mixed").getString()
                    + ": " + String.format("%,d", this.menu.mixedTotal()) + " / 1,000,000 mPU";
            MeteorosGui.tooltip(graphics, this.font, Component.literal(line), mouseX, mouseY);
            return;
        }
        if (inside(mouseX, mouseY, x + IntakeLayout.FE_X, y + IntakeLayout.FE_Y,
                IntakeLayout.FE_WIDTH, IntakeLayout.FE_HEIGHT)) {
            String line = "FE " + String.format("%,d", this.menu.energy()) + " / "
                    + String.format("%,d", IntakeMenu.ENERGY_CAPACITY);
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

    /** Phase name, mixed-buffer amount and capacity share, plus the selection hint. */
    private void drawPhaseTooltip(GuiGraphics graphics, int index, int mouseX, int mouseY) {
        Phase value = Phase.all().get(index);
        long amount = this.menu.mixedAmount(index);
        double percent = amount * 100.0 / MachineBlockEntity.MIXED_CAPACITY;
        Component line = Component.literal(value.displayName().getString()
                + ": " + String.format(Locale.ROOT, "%,d", amount) + " mPU ("
                + String.format(Locale.ROOT, "%.1f", percent) + "%)");
        Component hint = Component.translatable("gui.meteorosprotocol.intake.select_hint");
        MeteorosGui.tooltip(graphics, this.font, List.of(line, hint), mouseX, mouseY, 0xFF9A9A9A);
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
