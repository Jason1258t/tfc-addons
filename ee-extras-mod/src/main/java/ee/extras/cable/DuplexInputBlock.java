package ee.extras.cable;

import net.createmod.catnip.math.VoxelShaper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Корпус с двухжильным вводом. Клеммы скрыты внутри корпуса. */
public abstract class DuplexInputBlock extends CableEndBlock {
    /** Ввод кабеля — как у отвода фазы; все узлы в нём */
    private static final float[][] POINTS = {{8, 2, 13}, {8, 2, 13}, {8, 2, 13}};
    private static final VoxelShaper[] SHAPES = {
            VoxelShaper.forDirectional(box(0, 0, 1, 16, 6, 13), Direction.UP),
            VoxelShaper.forDirectional(box(3, 0, 0, 15, 6, 16), Direction.UP),
            VoxelShaper.forDirectional(box(0, 0, 3, 16, 6, 15), Direction.UP),
            VoxelShaper.forDirectional(box(1, 0, 0, 13, 6, 16), Direction.UP)};

    protected DuplexInputBlock(Properties properties) {
        super(properties, CableKind.DUPLEX, POINTS);
    }

    @Override
    public boolean isNodeAccessible(Level level, BlockPos pos, BlockState state, int id) {
        return false;
    }

    @Override
    public MutableComponent getNodeLabel(Level level, BlockPos pos, BlockState state, int node) {
        return Component.translatable("ee_extras.nodes.cable");
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[CableOrientation.of(state)].get(state.getValue(FACING));
    }
}
