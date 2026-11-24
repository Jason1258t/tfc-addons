package ee.extras.machine;

import com.george_vi.electroenergetics.content.electric_pump.ElectricPumpBlock;
import com.simibubi.create.content.fluids.pump.PumpBlockEntity;
import ee.extras.EeExtras;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class MeteringPumpBlock extends ElectricPumpBlock {
    public MeteringPumpBlock(Properties properties) { super(properties); }

    @Override
    public BlockEntityType<? extends PumpBlockEntity> getBlockEntityType() {
        return EeExtras.METERING_PUMP_BE.get();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        // Панель Create остаётся доступна для прокрутки дозы и настройки фильтра.
        if (player.isShiftKeyDown()) return InteractionResult.PASS;
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof MeteringPumpBlockEntity pump) pump.startDose();
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
