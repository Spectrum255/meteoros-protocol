package com.spectrum255.meteorosprotocol.item;

import com.spectrum255.meteorosprotocol.machine.MachineBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Rotates any machine in the {@code meteorosprotocol:rotatable} tag. */
public class WrenchItem extends Item {
    public static final TagKey<Block> ROTATABLE = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("meteorosprotocol", "rotatable"));

    public WrenchItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        if (!state.is(ROTATABLE) || !state.hasProperty(MachineBlock.FACING)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        Direction next = state.getValue(MachineBlock.FACING).getClockWise();
        level.setBlock(pos, state.setValue(MachineBlock.FACING, next), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.BLOCKS, 0.8F, 1.2F);
        return InteractionResult.CONSUME;
    }
}
