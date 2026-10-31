package tfc.destroy.bridge.devtest;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Только devtest: бак с сольной водой рядом с контроллером. */
public final class SourceBlock extends Block implements EntityBlock {
    public SourceBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SourceBlockEntity(pos, state);
    }
}
