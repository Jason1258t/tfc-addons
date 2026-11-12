package tfc.destroy.extras.electrolyzer;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * Мембранная ячейка — центральный блок переднего ряда электролизёра и место удара молотом IE. Сама ничего не делает:
 * всю работу ведёт собранный мультиблок {@link ElectrolyzerLogic}. Лицевая грань (люк мембраны) смотрит на игрока.
 */
public final class MembraneElectrolyzerBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<MembraneElectrolyzerBlock> CODEC = simpleCodec(MembraneElectrolyzerBlock::new);

    public MembraneElectrolyzerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }
}
