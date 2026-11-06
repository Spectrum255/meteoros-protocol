package com.spectrum255.meteorosprotocol.client;

import com.spectrum255.meteorosprotocol.MeteorosProtocol;
import com.spectrum255.meteorosprotocol.client.gui.IconButton;
import com.spectrum255.meteorosprotocol.client.gui.MeteorosGui;
import com.spectrum255.meteorosprotocol.menu.MachineStatusMenu;
import com.spectrum255.meteorosprotocol.particle.Phase;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;
import java.util.Locale;

/**
 * Shared status screen for the machines that have no bespoke layout (thermal
 * generator, phase separator, weather station, weather tank): 176 x 180
 * vanilla-styled panel, one FE bar, a vertical mixed-buffer tank, one vertical
 * bar per phase tank, two status lines and two icon buttons (enable, cycle
 * redstone mode). No item slots are involved; only the player inventory is
 * drawn.
 */
public class MachineStatusScreen extends AbstractContainerScreen<MachineStatusMenu> {
    private static final int WIDTH = 176;
    private static final int HEIGHT = 180;
    private static final int TITLE_Y = 6;
    private static final int TITLE_COLOR = 0xFF404040;

    private static final int FE_COLOR = 0xFFFF0000;
    private static final int FE_X = 8;
    private static final int FE_Y = 24;
    private static final int FE_WIDTH = 96;
    private static final int FE_HEIGHT = 8;

    private static final int TANK_X = 8;
    private static final int TANK_Y = 38;
    private static final int TANK_WIDTH = 26;
    private static final int TANK_HEIGHT = 26;

    private static final int PHASE_X = 44;
    private static final int PHASE_Y = 38;
    private static final int PHASE_WIDTH = 6;
    private static final int PHASE_HEIGHT = 26;
    private static final int PHASE_STEP = 8;

    private static final int STATUS_X = 8;
    private static final int STATUS_Y = 66;
    private static final int REDSTONE_Y = 76;
    private static final int STATUS_COLOR = 0xFF404040;

    private static final int BUTTON_X = 112;
    private static final int BUTTON_SIZE = 20;
    private static final int BUTTON_TOGGLE_Y = 18;
    private static final int BUTTON_REDSTONE_Y = 40;

    private static final int TICK_COLOR = 0x66000000;

    private static final String ICON_ROOT = "textures/gui/";
    private static final ResourceLocation ICON_POWER_ON = icon("icon_power");
    private static final ResourceLocation ICON_POWER_OFF = icon("icon_power_off");
    private static final ResourceLocation ICON_REDSTONE_HIGH = icon("icon_redstone_high");
    private static final ResourceLocation ICON_REDSTONE_LOW = icon("icon_redstone_low");
    private static final ResourceLocation ICON_REDSTONE_ALWAYS = icon("icon_redstone_always");

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

    private IconButton toggleButton;
    private IconButton redstoneButton;

    public MachineStatusScreen(MachineStatusMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = WIDTH;
        this.imageHeight = HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        int buttonX = this.leftPos + BUTTON_X;
        this.toggleButton = this.addRenderableWidget(new IconButton(buttonX, this.topPos + BUTTON_TOGGLE_Y,
                BUTTON_SIZE, BUTTON_SIZE, this::powerIcon, this::powerTooltip, button -> sendButton(0)));
        this.redstoneButton = this.addRenderableWidget(new IconButton(buttonX, this.topPos + BUTTON_REDSTONE_Y,
                BUTTON_SIZE, BUTTON_SIZE, this::redstoneIcon, this::redstoneTooltip, button -> sendButton(1)));
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

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        MeteorosGui.panel(graphics, x, y, WIDTH, HEIGHT);

        MeteorosGui.barH(graphics, x + FE_X, y + FE_Y, FE_WIDTH, FE_HEIGHT,
                (float) this.menu.energy() / MachineStatusMenu.ENERGY_CAPACITY, FE_COLOR);

        drawMixtureTank(graphics, x + TANK_X, y + TANK_Y);
        for (int index = 0; index < Phase.all().size(); index++) {
            drawPhaseTank(graphics,
                    x + PHASE_X + index * PHASE_STEP, y + PHASE_Y, index, mouseX, mouseY);
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slotX = x + 8 + col * 18;
                int slotY = y + 98 + row * 18;
                MeteorosGui.slot(graphics, slotX, slotY, inside(mouseX, mouseY, slotX, slotY, 16, 16));
            }
        }
        for (int col = 0; col < 9; col++) {
            int slotX = x + 8 + col * 18;
            int slotY = y + 156;
            MeteorosGui.slot(graphics, slotX, slotY, inside(mouseX, mouseY, slotX, slotY, 16, 16));
        }
    }

    private void drawMixtureTank(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + TANK_WIDTH, y + TANK_HEIGHT, MeteorosGui.SLOT_DARK);

        int count = Phase.all().size();
        long[] amounts = new long[count];
        int[] colors = new int[count];
        for (int index = 0; index < count; index++) {
            amounts[index] = this.menu.mixedAmount(index);
            colors[index] = Phase.all().get(index).argb();
        }
        int innerX = x + 1;
        int innerY = y + 1;
        int innerW = TANK_WIDTH - 2;
        int innerH = TANK_HEIGHT - 2;
        graphics.fill(innerX, innerY, innerX + innerW, innerY + innerH, MeteorosGui.SLOT_BODY);
        MeteorosGui.stripV(graphics, innerX, innerY, innerW, innerH,
                amounts, this.menu.mixedTotal(), colors);

        for (int tick = 1; tick <= 3; tick++) {
            int tickY = innerY + innerH - innerH * tick / 4;
            graphics.fill(innerX, tickY, innerX + innerW, tickY + 1, TICK_COLOR);
        }
    }

    private void drawPhaseTank(GuiGraphics graphics, int x, int y, int index, int mouseX, int mouseY) {
        Phase phase = Phase.all().get(index);
        long amount = this.menu.tankAmount(index);
        float level = phase.tankCapacity() <= 0L
                ? 0f
                : (float) Math.min(1.0, (double) amount / phase.tankCapacity());
        MeteorosGui.barV(graphics, x, y, PHASE_WIDTH, PHASE_HEIGHT, level, phase.argb());
        if (inside(mouseX, mouseY, x, y, PHASE_WIDTH, PHASE_HEIGHT)) {
            graphics.fill(x, y, x + PHASE_WIDTH, y + PHASE_HEIGHT, 0x30FFFFFF);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, 8, TITLE_Y, TITLE_COLOR, false);

        Component state = Component.translatable(this.menu.active()
                ? "gui.meteorosprotocol.machine.working"
                : "gui.meteorosprotocol.machine.idle");
        graphics.drawString(this.font, state, STATUS_X, STATUS_Y, STATUS_COLOR, false);

        String redstoneKey = switch (this.menu.redstoneMode()) {
            case 0 -> "gui.meteorosprotocol.intake.redstone.high";
            case 1 -> "gui.meteorosprotocol.intake.redstone.low";
            default -> "gui.meteorosprotocol.intake.redstone.always";
        };
        graphics.drawString(this.font, Component.translatable(redstoneKey), STATUS_X, REDSTONE_Y, STATUS_COLOR, false);

        MeteorosGui.label(graphics, this.font, this.playerInventoryTitle, 8, 86);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
        drawTooltips(graphics, mouseX, mouseY);
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
        int x = this.leftPos;
        int y = this.topPos;
        int phase = phaseAt(mouseX, mouseY);
        if (phase >= 0) {
            Phase value = Phase.all().get(phase);
            Component header = Component.literal(value.displayName().getString()
                    + ": " + String.format(Locale.ROOT, "%,d", this.menu.tankAmount(phase))
                    + " / " + String.format(Locale.ROOT, "%,d", value.tankCapacity()) + " mPU");
            Component mixed = Component.literal(String.format(Locale.ROOT, "mixed: %,d mPU",
                    this.menu.mixedAmount(phase)));
            MeteorosGui.tooltip(graphics, this.font, List.of(header, mixed), mouseX, mouseY, 0xFF9A9A9A);
            return;
        }
        if (inside(mouseX, mouseY, x + TANK_X, y + TANK_Y, TANK_WIDTH, TANK_HEIGHT)) {
            String line = Component.translatable("gui.meteorosprotocol.intake.mixed").getString()
                    + ": " + String.format(Locale.ROOT, "%,d", this.menu.mixedTotal())
                    + " / 1,000,000 mPU";
            MeteorosGui.tooltip(graphics, this.font, Component.literal(line), mouseX, mouseY);
            return;
        }
        if (inside(mouseX, mouseY, x + FE_X, y + FE_Y, FE_WIDTH, FE_HEIGHT)) {
            String line = "FE " + String.format(Locale.ROOT, "%,d", this.menu.energy())
                    + " / " + String.format(Locale.ROOT, "%,d", MachineStatusMenu.ENERGY_CAPACITY);
            MeteorosGui.tooltip(graphics, this.font, Component.literal(line), mouseX, mouseY);
        }
    }

    private int phaseAt(int mouseX, int mouseY) {
        for (int index = 0; index < Phase.all().size(); index++) {
            int barX = this.leftPos + PHASE_X + index * PHASE_STEP;
            if (inside(mouseX, mouseY, barX, this.topPos + PHASE_Y, PHASE_WIDTH, PHASE_HEIGHT)) {
                return index;
            }
        }
        return -1;
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static ResourceLocation icon(String name) {
        return ResourceLocation.fromNamespaceAndPath(MeteorosProtocol.MODID, ICON_ROOT + name + ".png");
    }

    private void sendButton(int id) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
        }
    }
}
