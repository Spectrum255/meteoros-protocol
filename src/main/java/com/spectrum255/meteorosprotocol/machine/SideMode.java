package com.spectrum255.meteorosprotocol.machine;

/**
 * Per-face particle I/O role. Stored as two bits per face in
 * {@link MachineBlockEntity}'s side masks.
 */
public enum SideMode {
    /** Nothing may be pushed to or pulled from this face. */
    NONE,
    /** Only allow pulling particles from the neighbour on this face. */
    IN,
    /** Only allow pushing particles to the neighbour on this face. */
    OUT,
    /** Both directions are allowed (the tick logic picks one when needed). */
    BOTH;

    private static final SideMode[] VALUES = values();

    public int id() {
        return ordinal();
    }

    public static SideMode byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : NONE;
    }

    public SideMode next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    public SideMode previous() {
        return VALUES[(ordinal() + VALUES.length - 1) % VALUES.length];
    }
}
