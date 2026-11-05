package ee.extras.wire;

import ee.extras.cable.CableEndBlock;
import ee.extras.cable.CableKind;
import com.simibubi.create.AllShapes;
import net.createmod.catnip.math.VoxelShaper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Распределительная коробка: плоская коробка 8×3×8 пикселей на любой грани (форма — как у powergrid:cord_junction).
 * Для кабеля — как двухжильный изолятор: все узлы скрыты в одной точке на крышке, все декоративные провода и
 * двухжильные кабели, пришедшие к коробке, соединены жила к жиле.
 */
public class JunctionBoxBlock extends CableEndBlock {
    /** Как формы блоков EE (CEEShapes): лицом вверх, повороты — VoxelShaper.forDirectional */
    private static final VoxelShaper SHAPE = new AllShapes.Builder(Block.box(4, 0, 4, 12, 3, 12)).forDirectional();

    public JunctionBoxBlock(Properties properties) {
        super(properties, CableKind.DUPLEX, points());
    }

    /** Все узлы (кабель и жилы 1, 2) — в центре крышки, у лица вверх (8, 3, 8) */
    static float[][] points() {
        float[][] out = new float[CableKind.DUPLEX.conductors + 1][];
        for (int i = 0; i < out.length; i++) out[i] = new float[] {8, 3, 8};
        return out;
    }

    @Override
    public boolean isNodeAccessible(Level level, BlockPos pos, BlockState state, int id) {
        return false;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE.get(state.getValue(FACING));
    }
}
