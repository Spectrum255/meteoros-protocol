package com.spectrum255.meteorosprotocol.particle;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/**
 * NBT persistence for particle tanks and single-phase stacks.
 *
 * <p>Internally all amounts are milli-PU (mPU, 1 PU = 1000 mPU). New data
 * carries a {@code unit="mPU"} marker inside the {@code Particles} compound.
 * Legacy data (no marker) was written in whole PU, so it is multiplied by
 * 1000 on load. Legacy data may also contain the retired {@code voltix} key;
 * that key is ignored, leaving V+ and V- at zero rather than guessing a side.</p>
 *
 * <p>Mixed buffers and per-phase tanks share the same compound shape, so the
 * save/load helpers are overloaded for {@link MixedStorage} and
 * {@link PhaseStorage}. Machines store the two compounds under separate keys
 * ({@code MixedParticles} / {@code PhaseParticles}).</p>
 */
public final class ParticleNbt {
    private static final String PARTICLES = "Particles";
    private static final String UNIT = "unit";
    private static final String UNIT_MPU = "mPU";

    private ParticleNbt() {
    }

    public static CompoundTag save(MixedStorage storage) {
        CompoundTag root = new CompoundTag();
        CompoundTag particles = new CompoundTag();
        particles.putString(UNIT, UNIT_MPU);
        for (Phase phase : Phase.all()) {
            particles.putLong(phase.id(), Math.max(0L, storage.getAmount(phase)));
        }
        root.put(PARTICLES, particles);
        return root;
    }

    public static CompoundTag save(PhaseStorage storage) {
        CompoundTag root = new CompoundTag();
        CompoundTag particles = new CompoundTag();
        particles.putString(UNIT, UNIT_MPU);
        for (Phase phase : Phase.all()) {
            particles.putLong(phase.id(), Math.max(0L, storage.getAmount(phase)));
        }
        root.put(PARTICLES, particles);
        return root;
    }

    public static void load(MixedStorage storage, CompoundTag tag) {
        CompoundTag particles = tag.getCompound(PARTICLES);
        boolean legacyPu = !UNIT_MPU.equals(particles.getString(UNIT));
        for (Phase phase : Phase.all()) {
            long target = readAmount(particles, phase, legacyPu);
            long current = storage.getAmount(phase);
            if (target > current) {
                storage.insert(phase, target - current, false);
            } else if (current > target) {
                storage.extract(phase, current - target, false);
            }
        }
    }

    public static void load(PhaseStorage storage, CompoundTag tag) {
        CompoundTag particles = tag.getCompound(PARTICLES);
        boolean legacyPu = !UNIT_MPU.equals(particles.getString(UNIT));
        for (Phase phase : Phase.all()) {
            long target = readAmount(particles, phase, legacyPu);
            long current = storage.getAmount(phase);
            if (target > current) {
                storage.insert(phase, target - current, false);
            } else if (current > target) {
                storage.extract(phase, current - target, false);
            }
        }
    }

    /**
     * Reads one phase amount. New saves use the full registry id; saves written
     * before the phase registry rework used the bare path (for example
     * {@code "thermion"}), so that form is accepted as a fallback.
     */
    public static long readAmount(CompoundTag particles, Phase phase, boolean legacyPu) {
        long amount = readNumeric(particles, phase.id());
        if (amount == Long.MIN_VALUE) {
            amount = readNumeric(particles, phase.key().getPath());
        }
        if (amount == Long.MIN_VALUE) {
            return 0L;
        }
        amount = Math.max(0L, amount);
        return legacyPu ? amount * 1000L : amount;
    }

    /** {@link Long#MIN_VALUE} means "key not present". */
    private static long readNumeric(CompoundTag particles, String key) {
        if (particles.contains(key, Tag.TAG_LONG)) {
            return particles.getLong(key);
        }
        if (particles.contains(key, Tag.TAG_INT)) {
            return particles.getInt(key);
        }
        return Long.MIN_VALUE;
    }

    public static CompoundTag saveStack(ParticleStack stack) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Phase", stack.phase().id());
        tag.putLong("Amount", stack.amount());
        tag.putString("Unit", UNIT_MPU);
        return tag;
    }

    /**
     * Loads a single-phase stack. An unknown phase id (including the retired
     * {@code "voltix"}) returns an empty {@link Phase#AETHERON} stack instead
     * of silently assigning the amount to one of the two charge phases.
     */
    public static ParticleStack loadStack(CompoundTag tag) {
        Phase phase = Phase.byId(tag.getString("Phase"));
        long amount = Math.max(0L, tag.getLong("Amount"));
        if (!UNIT_MPU.equals(tag.getString("Unit"))) {
            // Legacy stacks were saved in whole PU.
            amount *= 1000L;
        }
        if (phase == null) {
            return new ParticleStack(Phase.AETHERON, 0L);
        }
        return new ParticleStack(phase, amount);
    }
}
