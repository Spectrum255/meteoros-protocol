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

    /**
     * Phase-tank frame. The inner 63 x 38 px area holds six vertical phase bars;
     * the frame was widened from 26 px so the old six-column selector is gone and
     * the tank uses that space instead.
     */
    public static final int TANK_X = 7;
    public static final int TANK_Y = 19;
    public static final int TANK_WIDTH = 65;
    public static final int TANK_HEIGHT = 40;

    /** Six vertical phase-tank bars inside the tank (8 px wide, 2 px gap). */
    public static final int PHASE_BAR_COUNT = 6;
    public static final int PHASE_BAR_X = 9;
    public static final int PHASE_BAR_Y = 21;
    public static final int PHASE_BAR_WIDTH = 8;
    public static final int PHASE_BAR_HEIGHT = 36;
    public static final int PHASE_BAR_STEP = 10;
    /** The widget hit box is 2 px wider than the visible bar (1 px on each side). */
    public static final int PHASE_BAR_HIT_WIDTH = 10;

    /** Mixed-buffer bar below the tank (proportion of MIXED_CAPACITY). */
    public static final int MIXED_BAR_X = 8;
    public static final int MIXED_BAR_Y = 63;
    public static final int MIXED_BAR_WIDTH = 60;
    public static final int MIXED_BAR_HEIGHT = 6;

    /** Horizontal FE bar outer frame (inner 58 x 4). */
    public static final int FE_X = 8;
    public static final int FE_Y = 72;
    public static final int FE_WIDTH = 60;
    public static final int FE_HEIGHT = 6;

    /** Item slot positions (top-left of the 16 x 16 item cell). */
    public static final int FILTER_SLOT_X = 76;
    public static final int FILTER_SLOT_Y = 20;
    public static final int LOAD_SLOT_X = 76;
    public static final int LOAD_SLOT_Y = 44;

    /** Three 20 x 20 icon buttons on the right of the content band. */
    public static final int BUTTON_X = 112;
    public static final int BUTTON_WIDTH = 60;
    public static final int BUTTON_HEIGHT = 16;
    public static final int BUTTON_TOGGLE_Y = 19;
    public static final int BUTTON_REDSTONE_Y = 43;
    /** Extract button: pumps the selected phase into the canister in slot 1. */
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
