package com.spectrum255.meteorosprotocol.machine;

/**
 * A resource channel a machine face can be configured for: the shared mixed
 * buffer, the per-phase tanks, the FE buffer and the item slots.
 *
 * <p>The declaration order is the bit-field order used by
 * {@link MachineBlockEntity}'s side masks and by the menu data slots, so
 * {@code MIXED} and {@code PHASE} must stay at ordinals 0 and 1 to keep existing
 * saves readable.</p>
 */
public enum SideChannel {
    MIXED,
    PHASE,
    ENERGY,
    ITEM
}
