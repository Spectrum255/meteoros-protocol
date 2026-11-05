package com.spectrum255.meteorosprotocol.client;

import com.spectrum255.meteorosprotocol.client.gui.MeteorosGui;
import com.spectrum255.meteorosprotocol.machine.CreativeParticleBlockEntity;
import com.spectrum255.meteorosprotocol.menu.InfiniteParticleCellMenu;
import com.spectrum255.meteorosprotocol.particle.Phase;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

/**
 * Vanilla-styled face configurator for the creative particle cell. The main page
 * lists the six faces with a mode button and (for output faces) a phase button;
 * clicking the phase button opens a scrollable picker built from vanilla buttons.
 * Phase colours come from {@link Phase#argb()} everywhere.
 */
public class InfiniteParticleCellScreen extends AbstractContainerScreen<InfiniteParticleCellMenu> {
    private static final int WIDTH = 176;
    private static final int HEIGHT = 166;
    private static final int TITLE_Y = 6;

    private static final int MAIN_ROW_Y = 24;
    private static final int MAIN_ROW_H = 19;
    private static final int MAIN_BUTTON_H = 16;
    private static final int SIDE_LABEL_X = 8;
    private static final int MODE_X = 30;
    private static final int MODE_W = 50;
    private static final int PHASE_X = 84;
    private static final int PHASE_W = 54;
    private static final int SWATCH_X = 142;
    private static final int SWATCH_SIZE = 8;

    private static final int SUB_ROW_Y = 28;
    private static final int SUB_ROW_H = 18;
    private static final int SUB_BUTTON_X = 12;
    private static final int SUB_BUTTON_W = 124;
    private static final int SUB_BUTTON_H = 16;
    private static final int SUB_VISIBLE_ROWS = 6;
    private static final int BACK_X = 12;
    private static final int BACK_Y = 140;
    private static final int BACK_W = 54;
    private static final int BACK_H = 16;
    private static final int SCROLL_X = 148;
    private static final int SCROLL_Y = 26;
    private static final int SCROLL_W = 10;
    private static final int SCROLL_H = 108;
    private static final int SCROLL_HINT_Y = 158;

    private int activeSide = -1;
    private int scroll;
    private boolean draggingScrollbar;
    private boolean rebuildPending;
    private final Button[] modeButtons = new Button[InfiniteParticleCellMenu.SIDE_COUNT];
    private final Button[] phaseButtons = new Button[InfiniteParticleCellMenu.SIDE_COUNT];

    public InfiniteParticleCellScreen(InfiniteParticleCellMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = WIDTH;
        this.imageHeight = HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        if (this.activeSide < 0) {
            buildMainPage();
        } else {
            buildPickerPage();
        }
    }

    private void buildMainPage() {
        for (int side = 0; side < InfiniteParticleCellMenu.SIDE_COUNT; side++) {
            final int face = side;
            int rowY = this.topPos + MAIN_ROW_Y + side * MAIN_ROW_H;
            this.modeButtons[face] = this.addRenderableWidget(
                    Button.builder(modeComponent(face), button -> sendButton(face))
                            .bounds(this.leftPos + MODE_X, rowY, MODE_W, MAIN_BUTTON_H)
                            .build());
            boolean output = this.menu.sideMode(face) == CreativeParticleBlockEntity.SideMode.OUTPUT;
            this.phaseButtons[face] = Button.builder(phaseComponent(face, output), button -> openPicker(face))
                    .bounds(this.leftPos + PHASE_X, rowY, PHASE_W, MAIN_BUTTON_H)
                    .build();
            this.phaseButtons[face].active = output;
            this.addRenderableWidget(this.phaseButtons[face]);
        }
    }

    private void buildPickerPage() {
        java.util.Arrays.fill(this.modeButtons, null);
        java.util.Arrays.fill(this.phaseButtons, null);
        int phaseCount = Phase.all().size();
        this.scroll = Mth.clamp(this.scroll, 0, maxScroll());
        int visibleCount = Math.min(SUB_VISIBLE_ROWS, Math.max(0, phaseCount - this.scroll));
        for (int visible = 0; visible < visibleCount; visible++) {
            int phaseIndex = this.scroll + visible;
            int rowY = this.topPos + SUB_ROW_Y + visible * SUB_ROW_H;
            this.addRenderableWidget(Button.builder(Phase.all().get(phaseIndex).displayName(),
                            button -> choosePhase(phaseIndex))
                    .bounds(this.leftPos + SUB_BUTTON_X, rowY, SUB_BUTTON_W, SUB_BUTTON_H)
                    .build());
        }
        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.meteorosprotocol.cell.back"),
                        button -> closePicker())
                .bounds(this.leftPos + BACK_X, this.topPos + BACK_Y, BACK_W, BACK_H)
                .build());
    }

    private void openPicker(int side) {
        this.activeSide = side;
        this.scroll = 0;
        this.draggingScrollbar = false;
        this.rebuildPending = true;
    }

    private void closePicker() {
        this.activeSide = -1;
        this.draggingScrollbar = false;
        this.rebuildPending = true;
    }

    private void choosePhase(int phaseIndex) {
        sendButton(InfiniteParticleCellMenu.PHASE_BUTTON_BASE
                + this.activeSide * Phase.all().size()
                + phaseIndex);
        this.activeSide = -1;
        this.rebuildPending = true;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateMainButtons();
    }

    /** Refreshes the main-page captions after the server flips a face mode. */
    private void updateMainButtons() {
        if (this.activeSide >= 0) {
            return;
        }
        for (int side = 0; side < InfiniteParticleCellMenu.SIDE_COUNT; side++) {
            if (this.modeButtons[side] != null) {
                this.modeButtons[side].setMessage(modeComponent(side));
            }
            if (this.phaseButtons[side] != null) {
                boolean output = this.menu.sideMode(side) == CreativeParticleBlockEntity.SideMode.OUTPUT;
                this.phaseButtons[side].active = output;
                this.phaseButtons[side].setMessage(phaseComponent(side, output));
            }
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        MeteorosGui.panel(graphics, x, y, WIDTH, HEIGHT);

        if (this.activeSide < 0) {
            for (int side = 0; side < InfiniteParticleCellMenu.SIDE_COUNT; side++) {
                int rowY = y + MAIN_ROW_Y + side * MAIN_ROW_H;
                if (inside(mouseX, mouseY, x + 4, rowY - 1, WIDTH - 8, MAIN_ROW_H - 3)) {
                    graphics.fill(x + 4, rowY - 1, x + WIDTH - 4, rowY + MAIN_ROW_H - 4,
                            MeteorosGui.ROW_HIGHLIGHT);
                }
                if (this.menu.sideMode(side) == CreativeParticleBlockEntity.SideMode.OUTPUT) {
                    graphics.fill(x + SWATCH_X, rowY + 4, x + SWATCH_X + SWATCH_SIZE, rowY + 4 + SWATCH_SIZE,
                            this.menu.outputPhase(side).argb());
                }
            }
            return;
        }

        int visibleCount = Math.min(SUB_VISIBLE_ROWS, Math.max(0, Phase.all().size() - this.scroll));
        for (int visible = 0; visible < visibleCount; visible++) {
            int phaseIndex = this.scroll + visible;
            int rowY = y + SUB_ROW_Y + visible * SUB_ROW_H;
            graphics.fill(x + 8, rowY + 2, x + 11, rowY + SUB_BUTTON_H - 2,
                    Phase.all().get(phaseIndex).argb());
        }
        graphics.fill(x + SCROLL_X, y + SCROLL_Y, x + SCROLL_X + SCROLL_W, y + SCROLL_Y + SCROLL_H,
                MeteorosGui.SLOT_BODY);
        int thumbY = y + thumbY();
        int thumbHeight = thumbHeight();
        graphics.fill(x + SCROLL_X, thumbY, x + SCROLL_X + SCROLL_W, thumbY + thumbHeight,
                MeteorosGui.SLOT_DARK);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        if (this.activeSide < 0) {
            graphics.drawString(this.font,
                    Component.translatable("gui.meteorosprotocol.cell.title"),
                    8, TITLE_Y, MeteorosGui.LABEL_TEXT, false);
            for (int side = 0; side < InfiniteParticleCellMenu.SIDE_COUNT; side++) {
                int rowY = MAIN_ROW_Y + side * MAIN_ROW_H;
                graphics.drawString(this.font, sideName(side), SIDE_LABEL_X, rowY + 4,
                        MeteorosGui.LABEL_TEXT, false);
            }
            return;
        }

        graphics.drawString(this.font,
                Component.translatable("gui.meteorosprotocol.cell.select_phase"),
                8, TITLE_Y, MeteorosGui.LABEL_TEXT, false);
        graphics.drawString(this.font,
                Component.translatable("gui.meteorosprotocol.cell.scroll_hint"),
                8, SCROLL_HINT_Y, MeteorosGui.TEXT_DIM, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Widgets are never rebuilt while a click handler is running; page changes
        // only set the flag and the rebuild happens here, before the next frame.
        if (this.rebuildPending) {
            this.rebuildPending = false;
            this.rebuildWidgets();
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        if (this.activeSide >= 0) {
            drawSelectedFrame(graphics);
        }
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    /** White frame around the phase button that is currently assigned to the face. */
    private void drawSelectedFrame(GuiGraphics graphics) {
        int selected = this.menu.outputPhase(this.activeSide).index();
        int visible = selected - this.scroll;
        if (visible < 0 || visible >= Math.min(SUB_VISIBLE_ROWS, Phase.all().size())) {
            return;
        }
        int left = this.leftPos + SUB_BUTTON_X - 1;
        int top = this.topPos + SUB_ROW_Y + visible * SUB_ROW_H - 1;
        int width = SUB_BUTTON_W + 2;
        int height = SUB_BUTTON_H + 2;
        graphics.fill(left, top, left + width, top + 1, MeteorosGui.SELECTED_FRAME);
        graphics.fill(left, top + height - 1, left + width, top + height, MeteorosGui.SELECTED_FRAME);
        graphics.fill(left, top, left + 1, top + height, MeteorosGui.SELECTED_FRAME);
        graphics.fill(left + width - 1, top, left + width, top + height, MeteorosGui.SELECTED_FRAME);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != 0 || this.activeSide < 0) {
            return false;
        }
        if (inside(mouseX, mouseY, this.leftPos + SCROLL_X, this.topPos + SCROLL_Y, SCROLL_W, SCROLL_H)) {
            int thumbY = this.topPos + thumbY();
            int thumbHeight = thumbHeight();
            if (mouseY >= thumbY && mouseY < thumbY + thumbHeight) {
                this.draggingScrollbar = true;
            } else if (mouseY < thumbY) {
                setScroll(this.scroll - SUB_VISIBLE_ROWS);
            } else {
                setScroll(this.scroll + SUB_VISIBLE_ROWS);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.draggingScrollbar && this.activeSide >= 0) {
            int maxScroll = maxScroll();
            if (maxScroll > 0) {
                int track = SCROLL_H - thumbHeight();
                if (track > 0) {
                    double ratio = (mouseY - this.topPos - SCROLL_Y - thumbHeight() / 2.0) / track;
                    setScroll((int) Math.round(Math.max(0.0, Math.min(1.0, ratio)) * maxScroll));
                }
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.draggingScrollbar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.activeSide >= 0) {
            if (scrollY > 0) {
                setScroll(this.scroll - 1);
            } else if (scrollY < 0) {
                setScroll(this.scroll + 1);
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void setScroll(int value) {
        int clamped = Mth.clamp(value, 0, maxScroll());
        if (clamped == this.scroll) {
            return;
        }
        this.scroll = clamped;
        this.rebuildPending = true;
    }

    private int maxScroll() {
        return Math.max(0, Phase.all().size() - SUB_VISIBLE_ROWS);
    }

    private int thumbHeight() {
        int total = Math.max(1, Phase.all().size());
        return Math.max(18, SCROLL_H * SUB_VISIBLE_ROWS / total);
    }

    private int thumbY() {
        int maxScroll = maxScroll();
        if (maxScroll <= 0) {
            return SCROLL_Y;
        }
        int track = SCROLL_H - thumbHeight();
        return SCROLL_Y + (int) Math.round((double) track * this.scroll / maxScroll);
    }

    private Component modeComponent(int side) {
        return Component.translatable(modeKey(this.menu.sideMode(side)));
    }

    private Component phaseComponent(int side, boolean output) {
        return output ? this.menu.outputPhase(side).displayName() : Component.literal("-");
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static Component sideName(int side) {
        return Component.translatable("gui.meteorosprotocol.cell.side."
                + CreativeParticleBlockEntity.SIDES[side].getSerializedName());
    }

    private static String modeKey(CreativeParticleBlockEntity.SideMode mode) {
        return switch (mode) {
            case INPUT -> "gui.meteorosprotocol.cell.mode.input";
            case OUTPUT -> "gui.meteorosprotocol.cell.mode.output";
            default -> "gui.meteorosprotocol.cell.mode.off";
        };
    }

    private void sendButton(int id) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
        }
    }
}
