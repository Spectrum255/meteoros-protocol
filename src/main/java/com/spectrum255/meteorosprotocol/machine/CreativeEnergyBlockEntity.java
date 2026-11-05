package com.spectrum255.meteorosprotocol.machine;

import com.spectrum255.meteorosprotocol.MeteorosProtocol;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * Creative test energy source. It stores and can extract Integer.MAX_VALUE FE,
 * and every server tick offers Integer.MAX_VALUE FE to each neighbouring
 * {@code IEnergyStorage}. Machines accept only up to their own 100,000 FE cap.
 */
public class CreativeEnergyBlockEntity extends BlockEntity implements IEnergyStorage {
    public CreativeEnergyBlockEntity(BlockPos pos, BlockState state) {
        super(MeteorosProtocol.CREATIVE_ENERGY_BLOCK_ENTITY.get(), pos, state);
    }

    public static void serverTick(ServerLevel level, BlockPos pos, BlockState state,
                                  CreativeEnergyBlockEntity source) {
        for (Direction direction : Direction.values()) {
            IEnergyStorage target = level.getCapability(
                    Capabilities.EnergyStorage.BLOCK,
                    pos.relative(direction),
                    direction.getOpposite()
            );
            if (target != null && target.canReceive()) {
                target.receiveEnergy(Integer.MAX_VALUE, false);
            }
        }
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        return 0;
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        return Math.max(0, maxExtract);
    }

    @Override
    public int getEnergyStored() {
        return Integer.MAX_VALUE;
    }

    @Override
    public int getMaxEnergyStored() {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean canExtract() {
        return true;
    }

    @Override
    public boolean canReceive() {
        return false;
    }
}
