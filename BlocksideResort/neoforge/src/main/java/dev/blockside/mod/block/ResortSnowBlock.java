package dev.blockside.mod.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Managed snow layers (groomed corduroy, icy crust). Unlike vanilla snow, the collision matches the
 * visible height exactly: groomed snow is firm, you don't sink. Rendered height = layers / 8, the same
 * height the skier and groomer sample.
 */
public class ResortSnowBlock extends Block {
    public static final MapCodec<ResortSnowBlock> CODEC = simpleCodec(ResortSnowBlock::new);
    public static final IntegerProperty LAYERS = BlockStateProperties.LAYERS;
    private static final VoxelShape[] SHAPES = new VoxelShape[9];
    static {
        for (int i = 1; i <= 8; i++) SHAPES[i] = Block.box(0, 0, 0, 16, i * 2, 16);
        SHAPES[0] = SHAPES[1];
    }

    public ResortSnowBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(LAYERS, 1));
    }

    @Override protected MapCodec<? extends Block> codec() { return CODEC; }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(LAYERS); }

    @Override protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return SHAPES[s.getValue(LAYERS)]; }

    @Override protected VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return SHAPES[s.getValue(LAYERS)]; }

    @Override protected boolean useShapeForLightOcclusion(BlockState s) { return true; }
}
