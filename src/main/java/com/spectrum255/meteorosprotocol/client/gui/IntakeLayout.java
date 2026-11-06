package com.spectrum255.meteorosprotocol.client.gui;

/**
 * Single source of truth for the intake tower GUI layout (176 x 180).
 * Both {@code IntakeScreen} (drawing) and {@code IntakeMenu} (slot positions)
 * read these constants, so the click areas can never drift from the art.
 */
public final class IntakeLayout {
    public static final int WIDTH = 176;
    public static final int HEIGHT = 180;

    /** Title text top edge; 8 px font, no shadow, colour 0xFF404040. */
    public static final int TITLE_Y = 6;

    /** Mixture tank outer frame (inner 24 x 38 is the fill area). */
    public static final int TANK_X = 7;
    public static final int TANK_Y = 19;
    public static final int TANK_WIDTH = 26;
    public static final int TANK_HEIGHT = 40;

    /** Horizontal FE bar outer frame (inner 58 x 4). */
    public static final int FE_X = 8;
    public static final int FE_Y = 70;
    public static final int FE_WIDTH = 60;
    public static final int FE_HEIGHT = 6;

    /** Item slot positions (top-left of the 16 x 16 item cell). */
    public static final int FILTER_SLOT_X = 76;
    public static final int FILTER_SLOT_Y = 20;
    public static final int LOAD_SLOT_X = 76;
    public static final int LOAD_SLOT_Y = 44;

    /** Three 60 x 16 buttons on the right of the content band. */
    public static final int BUTTON_X = 112;
    public static final int BUTTON_WIDTH = 60;
    public static final int BUTTON_HEIGHT = 16;
    public static final int BUTTON_TOGGLE_Y = 19;
    public static final int BUTTON_REDSTONE_Y = 43;
    /** Extract button: pumps the selected phase from the mixed buffer into slot 1. */
    public static final int BUTTON_EXTRACT_Y = 67;

    /** Player inventory label and 3 x 9 grid + hotbar. */
    public static final int INVENTORY_LABEL_X = 8;
    public static final int INVENTORY_LABEL_Y = 86;
    public static final int INVENTORY_X = 8;
    public static final int INVENTORY_Y = 98;
    public static final int INVENTORY_STEP = 18;
    public static final int HOTBAR_Y = 156;

    private IntakeLayout() {
    }
}
