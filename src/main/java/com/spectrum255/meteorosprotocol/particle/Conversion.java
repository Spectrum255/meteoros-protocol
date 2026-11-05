package com.spectrum255.meteorosprotocol.particle;

import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/**
 * A particle conversion recipe. Amounts are milli-PU (mPU, 1 PU = 1000 mPU)
 * and must satisfy {@link MassBalance}.
 */
public record Conversion(ResourceLocation id, Map<Phase, Long> inputs, Map<Phase, Long> outputs,
                         Map<Phase, Long> waste, int energyIn, int energyOut, int ticks) {
    /**
     * v2 thermal generator: 10 PU Thermion becomes 10 PU Cryon plus 1000 FE
     * every 20 ticks. It intentionally produces no Aetheron (that was the
     * retired v1 recipe).
     */
    public static final Conversion GENERATOR_THERMION = new Conversion(
            ResourceLocation.fromNamespaceAndPath("meteorosprotocol", "generator_thermion"),
            Map.of(Phase.THERMION, 10_000L),
            Map.of(Phase.CRYON, 10_000L),
            Map.of(),
            0,
            1000,
            20
    );

    public void validate() {
        if (ticks <= 0 || energyIn < 0 || energyOut < 0) {
            throw new IllegalArgumentException("invalid conversion");
        }
        long in = inputs.values().stream().mapToLong(Long::longValue).sum();
        long out = outputs.values().stream().mapToLong(Long::longValue).sum();
        long wasteAmount = waste.values().stream().mapToLong(Long::longValue).sum();
        MassBalance.require(in, out, wasteAmount);
        for (long value : inputs.values()) {
            if (value < 0L) {
                throw new IllegalArgumentException("negative input");
            }
        }
        for (long value : outputs.values()) {
            if (value < 0L) {
                throw new IllegalArgumentException("negative output");
            }
        }
        for (long value : waste.values()) {
            if (value < 0L) {
                throw new IllegalArgumentException("negative waste");
            }
        }
    }
}
