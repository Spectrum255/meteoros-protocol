package com.spectrum255.meteorosprotocol.machine;

import com.mojang.serialization.MapCodec;
import com.spectrum255.meteorosprotocol.particle.ParticleCapabilities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Six-way particle pipe. Connection flags are stored as block state properties and
 * drive a multipart block model (core + one arm per connected face), mirroring the
 * approach used by Pipez. The block entity is still the shared machine block entity;
 * only the transport logic gates on the visible connections.
 */
public class ParticlePipeBlock extends MachineBlock {
    private static final Map<Direction, BooleanProperty> CONNECTIONS;
    private static final Map<Direction, VoxelShape> ARM_SHAPES;
    private static final VoxelShape CORE_SHAPE = Block.box(5.0, 5.0, 5.0, 11.0, 11.0, 11.0);

    static {
        EnumMap<Direction, BooleanProperty> connections = new EnumMap<>(Direction.class);
        EnumMap<Direction, VoxelShape> arms = new EnumMap<>(Direction.class);
        for (Direction direction : Direction.values()) {
            connections.put(direction, BooleanProperty.create(direction.getName()));
            arms.put(direction, switch (direction) {
                case NORTH -> Block.box(5.0, 5.0, 0.0, 11.0, 11.0, 5.0);
                case SOUTH -> Block.box(5.0, 5.0, 11.0, 11.0, 11.0, 16.0);
                case WEST -> Block.box(0.0, 5.0, 5.0, 5.0, 11.0, 11.0);
                case EAST -> Block.box(11.0, 5.0, 5.0, 16.0, 11.0, 11.0);
                case UP -> Block.box(5.0, 11.0, 5.0, 11.0, 16.0, 11.0);
                case DOWN -> Block.box(5.0, 0.0, 5.0, 11.0, 5.0, 11.0);
            });
        }
        CONNECTIONS = Collections.unmodifiableMap(connections);
        ARM_SHAPES = Collections.unmodifiableMap(arms);
    }

    public ParticlePipeBlock(Properties properties) {
        super(properties);
        BlockState state = this.stateDefinition.any().setValue(FACING, Direction.NORTH);
        for (BooleanProperty property : CONNECTIONS.values()) {
            state = state.setValue(property, false);
        }
        this.registerDefaultState(state);
    }

    @Override
    public MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(ParticlePipeBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CONNECTIONS.values().toArray(BooleanProperty[]::new));
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state == null ? null : connectState(context.getLevel(), context.getClickedPos(), state);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (level instanceof Level realLevel) {
            return connectState(realLevel, pos, state);
        }
        return state;
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                BlockPos fromPos, boolean isMoving) {
        BlockState connected = connectState(level, pos, state);
        if (connected != state) {
            level.setBlock(pos, connected, Block.UPDATE_CLIENTS);
        }
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
    }

    private static BlockState connectState(Level level, BlockPos pos, BlockState state) {
        for (Direction direction : Direction.values()) {
            state = state.setValue(CONNECTIONS.get(direction), canConnectTo(level, pos, direction));
        }
        return state;
    }

    private static boolean canConnectTo(Level level, BlockPos pos, Direction direction) {
        BlockPos neighbor = pos.relative(direction);
        if (level.getBlockState(neighbor).getBlock() instanceof ParticlePipeBlock) {
            return true;
        }
        return level.getCapability(ParticleCapabilities.MIXED_STORAGE, neighbor, direction.getOpposite()) != null;
    }

    /** Used by the block entity transport loop to ignore visually disconnected faces. */
    public static boolean isConnected(BlockState state, Direction direction) {
        return state.getBlock() instanceof ParticlePipeBlock
                && state.hasProperty(CONNECTIONS.get(direction))
                && state.getValue(CONNECTIONS.get(direction));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = CORE_SHAPE;
        for (Direction direction : Direction.values()) {
            if (isConnected(state, direction)) {
                shape = Shapes.or(shape, ARM_SHAPES.get(direction));
            }
        }
        return shape;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return CORE_SHAPE;
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }
}
