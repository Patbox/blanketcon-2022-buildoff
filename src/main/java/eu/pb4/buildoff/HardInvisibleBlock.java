package eu.pb4.buildoff;

import eu.pb4.polymer.api.block.PolymerBlock;
import net.minecraft.block.*;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;

public class HardInvisibleBlock extends Block implements PolymerBlock  {
    public HardInvisibleBlock(Settings settings) {
        super(settings);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return context instanceof EntityShapeContext entityShapeContext && entityShapeContext.getEntity() instanceof ServerPlayerEntity ? VoxelShapes.empty() : VoxelShapes.fullCube();
    }

    @Override
    public PistonBehavior getPistonBehavior(BlockState state) {
        return PistonBehavior.BLOCK;
    }

    @Override
    public Block getPolymerBlock(BlockState state) {
        return Blocks.AIR;
    }
}
