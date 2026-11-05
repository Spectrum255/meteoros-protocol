package com.spectrum255.meteorosprotocol.particle;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public interface ParticleField { FieldSample sample(ServerLevel level, BlockPos pos); }
