package tfc.destroy.extras.devtest;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Большой резервуар только для нагрузочного стенда. */
final class TestSinkBlock extends Block implements EntityBlock {
    TestSinkBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TestSinkBlockEntity(pos, state);
    }
}
