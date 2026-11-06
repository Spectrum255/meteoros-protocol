package com.spectrum255.meteorosprotocol.compat;

import java.util.Map;

import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;

/**
 * Block-state to temperature lookup used by {@link TemperatureProbe}.
 *
 * <p>Values are in Kelvin. A missing entry means "not a meaningful heat source
 * or cold block" and contributes nothing to the local temperature. Fluids are
 * matched by tag first so flowing/source variants both work; lit furnaces and
 * campfires are matched through the vanilla {@code lit} property.</p>
 */
public final class TemperatureSources {
    /** Lava fluid contributes this much. */
    public static final float LAVA = 1300.0F;
    /** Lit furnace / campfire / fire. */
    public static final float HOT = 900.0F;
    /** Soul fire / soul campfire. */
    public static final float SOUL_HOT = 700.0F;

    private static final Map<Block, Float> SOURCES = Map.ofEntries(
            Map.entry(Blocks.MAGMA_BLOCK, 1000.0F),
            Map.entry(Blocks.FIRE, HOT),
            Map.entry(Blocks.SOUL_FIRE, SOUL_HOT),
            Map.entry(Blocks.WATER, 290.0F),
            Map.entry(Blocks.ICE, 265.0F),
            Map.entry(Blocks.PACKED_ICE, 265.0F),
            Map.entry(Blocks.BLUE_ICE, 240.0F),
            Map.entry(Blocks.SNOW_BLOCK, 270.0F),
            Map.entry(Blocks.SNOW, 270.0F),
            Map.entry(Blocks.POWDER_SNOW, 250.0F),
            Map.entry(Blocks.SOUL_SAND, 280.0F),
            Map.entry(Blocks.SOUL_SOIL, 280.0F));

    private TemperatureSources() {
    }

    /**
     * Temperature of the given block state in Kelvin, or {@code null} when the
     * state is not a heat source or cold block.
     */
    public static Float temperatureOf(BlockState state) {
        FluidState fluid = state.getFluidState();
        if (!fluid.isEmpty()) {
            if (fluid.is(FluidTags.LAVA)) {
                return LAVA;
            }
            if (fluid.is(FluidTags.WATER)) {
                return 290.0F;
            }
        }

        Block block = state.getBlock();
        if (block == Blocks.FURNACE || block == Blocks.BLAST_FURNACE || block == Blocks.SMOKER) {
            return isLit(state) ? HOT : null;
        }
        if (block == Blocks.CAMPFIRE) {
            return isLit(state) ? HOT : null;
        }
        if (block == Blocks.SOUL_CAMPFIRE) {
            return isLit(state) ? SOUL_HOT : null;
        }
        return SOURCES.get(block);
    }

    private static boolean isLit(BlockState state) {
        return state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT);
    }
}
