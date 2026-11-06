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
     * Six hollow phase-tank bars. The old tank frame is gone: the bars themselves
     * fill the whole former tank area (7..72), each 10 px wide with an 11 px step,
     * so the 1 px gaps between bars are covered by the hit boxes.
     */
    public static final int PHASE_BAR_COUNT = 6;
    public static final int PHASE_BAR_X = 7;
    public static final int PHASE_BAR_Y = 19;
    public static final int PHASE_BAR_WIDTH = 10;
    public static final int PHASE_BAR_HEIGHT = 40;
    public static final int PHASE_BAR_STEP = 11;
    /** Hit boxes span the full step, so adjacent bars never leave a dead 1 px gap. */
    public static final int PHASE_BAR_HIT_WIDTH = 11;

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

    /**
     * RESERVED BAND — do not fill, do not shrink the window.
     *
     * <p>The strip from roughly x = 134..168, y = 18..84 (right of the icon buttons,
     * above the inventory label) is intentionally empty. The user wants machine
     * upgrade slots there once the upgrade system exists ("空位置放置机器升级槽").
     * Keep the 176 x 180 window and leave this band free so upgrades can be added
     * without redoing this layout; when they land, put 18 x 18 slots here and read
     * their positions from this class like every other control.
     */
    public static final int RESERVED_BAND_X = 134;
    public static final int RESERVED_BAND_Y = 18;
    public static final int RESERVED_BAND_WIDTH = 34;
    public static final int RESERVED_BAND_HEIGHT = 66;

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
