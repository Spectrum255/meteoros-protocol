package com.spectrum255.meteorosprotocol.client;

import com.spectrum255.meteorosprotocol.MeteorosProtocol;
import com.spectrum255.meteorosprotocol.client.gui.IntakeLayout;
import com.spectrum255.meteorosprotocol.client.gui.MeteorosGui;
import com.spectrum255.meteorosprotocol.item.InfiniteParticleCanisterItem;
import com.spectrum255.meteorosprotocol.machine.MachineBlockEntity;
import com.spectrum255.meteorosprotocol.menu.IntakeMenu;
import com.spectrum255.meteorosprotocol.particle.Phase;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;

/**
 * Intake tower screen: 176 x 180, vanilla-styled container panel with a banded
 * mixture tank, one horizontal FE bar, six phase gauges fed by the mixed buffer,
 * two slots and three vanilla buttons. Clicking a gauge selects the phase to
 * extract; the extract button then pumps that phase into the canister in slot 1.
 * Layout constants live in {@link IntakeLayout} so the menu slots never drift.
 */
public class IntakeScreen extends AbstractContainerScreen<IntakeMenu> {
    private static final int TITLE_Y = 6;
    private static final int TITLE_COLOR = 0xFF404040;
    private static final int TICK_COLOR = 0x66000000;
    private static final int GHOST = 0x66C8D0D8;
    private static final int HOVER_WASH = 0x30FFFFFF;
    private static final int FE_COLOR = 0xFFFF0000;

    /** Vanilla button widgets: 60 x 20, stacked with a 22 px pitch. */
    private static final int BUTTON_WIDGET_WIDTH = 60;
    private static final int BUTTON_WIDGET_HEIGHT = 20;
    private static final int BUTTON_TOGGLE_WIDGET_Y = 18;
    private static final int BUTTON_REDSTONE_WIDGET_Y = 40;
    private static final int BUTTON_EXTRACT_WIDGET_Y = 62;

    private Button toggleButton;
    private Button redstoneButton;
    private Button extractButton;

    public IntakeScreen(IntakeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = IntakeLayout.WIDTH;
        this.imageHeight = IntakeLayout.HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        int buttonX = this.leftPos + IntakeLayout.BUTTON_X;
        this.toggleButton = this.addRenderableWidget(Button.builder(Component.empty(), button -> sendButton(0))
                .bounds(buttonX, this.topPos + BUTTON_TOGGLE_WIDGET_Y,
                        BUTTON_WIDGET_WIDTH, BUTTON_WIDGET_HEIGHT)
                .build());
        this.redstoneButton = this.addRenderableWidget(Button.builder(Component.empty(), button -> sendButton(1))
                .bounds(buttonX, this.topPos + BUTTON_REDSTONE_WIDGET_Y,
                        BUTTON_WIDGET_WIDTH, BUTTON_WIDGET_HEIGHT)
                .build());
        this.extractButton = this.addRenderableWidget(Button.builder(Component.empty(), button -> sendButton(8))
                .bounds(buttonX, this.topPos + BUTTON_EXTRACT_WIDGET_Y,
                        BUTTON_WIDGET_WIDTH, BUTTON_WIDGET_HEIGHT)
                .build());
        updateButtons();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateButtons();
    }

    /** Keeps the vanilla button captions and the extract-enabled state in sync. */
    private void updateButtons() {
        if (this.toggleButton != null) {
            boolean work = this.menu.enabled();
            this.toggleButton.setMessage(Component.translatable(
                    work ? "gui.meteorosprotocol.intake.work.on" : "gui.meteorosprotocol.intake.work.off"));
        }
        if (this.redstoneButton != null) {
            String redstoneKey = switch (this.menu.redstoneMode()) {
                case 1 -> "gui.meteorosprotocol.intake.redstone.always";
                default -> "gui.meteorosprotocol.intake.redstone.control";
            };
            this.redstoneButton.setMessage(Component.translatable(redstoneKey));
        }
        if (this.extractButton != null) {
            this.extractButton.setMessage(Component.translatable("gui.meteorosprotocol.intake.extract",
                    selectedPhase().displayName()));
            this.extractButton.active = extractReady();
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        MeteorosGui.panel(graphics, x, y, IntakeLayout.WIDTH, IntakeLayout.HEIGHT);

        drawMixtureTank(graphics, x + IntakeLayout.TANK_X, y + IntakeLayout.TANK_Y);

        for (int index = 0; index < Phase.all().size(); index++) {
            drawPhaseStrip(graphics,
                    x + IntakeLayout.PHASE_X + index * IntakeLayout.PHASE_STEP,
                    y + IntakeLayout.PHASE_Y, index, mouseX, mouseY);
        }

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

    private void drawMixtureTank(GuiGraphics graphics, int x, int y) {
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
    }

    private void drawPhaseStrip(GuiGraphics graphics, int x, int y, int index, int mouseX, int mouseY) {
        Phase phase = Phase.all().get(index);
        long amount = this.menu.mixedAmount(index);
        float level = (float) Math.min(1.0, (double) amount / MachineBlockEntity.MIXED_CAPACITY);
        MeteorosGui.barV(graphics, x, y, IntakeLayout.PHASE_WIDTH, IntakeLayout.PHASE_HEIGHT,
                level, phase.argb());
        if (index == this.menu.canisterPhaseIndex()) {
            drawSelectedFrame(graphics, x, y);
        }
        if (inside(mouseX, mouseY, x, y, IntakeLayout.PHASE_WIDTH, IntakeLayout.PHASE_HEIGHT)) {
            graphics.fill(x, y, x + IntakeLayout.PHASE_WIDTH, y + IntakeLayout.PHASE_HEIGHT, HOVER_WASH);
        }
    }

    /** 1 px white frame around the gauge chosen as the extraction phase. */
    private static void drawSelectedFrame(GuiGraphics graphics, int x, int y) {
        int left = x - 1;
        int top = y - 1;
        int right = x + IntakeLayout.PHASE_WIDTH + 1;
        int bottom = y + IntakeLayout.PHASE_HEIGHT + 1;
        graphics.fill(left, top, right, top + 1, 0xFFFFFFFF);
        graphics.fill(left, bottom - 1, right, bottom, 0xFFFFFFFF);
        graphics.fill(left, top, left + 1, bottom, 0xFFFFFFFF);
        graphics.fill(right - 1, top, right, bottom, 0xFFFFFFFF);
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
        return accepts && this.menu.mixedAmount(this.menu.canisterPhaseIndex()) > 0L;
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
        int phase = phaseAt(mouseX, mouseY);
        if (phase >= 0) {
            drawPhaseTooltip(graphics, phase, mouseX, mouseY);
            return;
        }
        int x = this.leftPos;
        int y = this.topPos;
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

    /** Phase name, mixed-buffer amount and capacity share, plus the selection hint. */
    private void drawPhaseTooltip(GuiGraphics graphics, int index, int mouseX, int mouseY) {
        Phase value = Phase.all().get(index);
        long amount = this.menu.mixedAmount(index);
        double percent = amount * 100.0 / MachineBlockEntity.MIXED_CAPACITY;
        Component name = value.displayName();
        Component line = Component.literal(name.getString()
                + ": " + String.format(Locale.ROOT, "%,d", amount) + " mPU ("
                + String.format(Locale.ROOT, "%.1f", percent) + "%)");
        Component hint = Component.translatable("gui.meteorosprotocol.intake.select_hint");
        MeteorosGui.tooltip(graphics, this.font, List.of(line, hint), mouseX, mouseY, 0xFF9A9A9A);
    }

    private int phaseAt(int mouseX, int mouseY) {
        for (int index = 0; index < Phase.all().size(); index++) {
            int barX = this.leftPos + IntakeLayout.PHASE_X + index * IntakeLayout.PHASE_STEP;
            if (inside(mouseX, mouseY, barX, this.topPos + IntakeLayout.PHASE_Y,
                    IntakeLayout.PHASE_WIDTH, IntakeLayout.PHASE_HEIGHT)) {
                return index;
            }
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Vanilla buttons get the first chance; the phase bars stay hand-hit-tested.
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0) {
            int phase = phaseAt((int) mouseX, (int) mouseY);
            if (phase >= 0) {
                sendButton(2 + phase);
                return true;
            }
        }
        return false;
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
