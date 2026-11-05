package com.spectrum255.meteorosprotocol.machine;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.BaseEntityBlock;

/** Particle intake tower; inherited FACING selects the display side. */
public class IntakeBlock extends MachineBlock {
    public IntakeBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(IntakeBlock::new);
    }
}
