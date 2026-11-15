package aero.extra;

import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class TransportEngineBlock extends DirectionalKineticBlock implements IBE<TransportEngineBlockEntity> {
    TransportEngineBlock(Properties properties) {
        super(properties);
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(FACING).getAxis();
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face == state.getValue(FACING);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof TransportEngineBlockEntity engine) {
            int current = engine.getTargetRpm();
            int next = player.isShiftKeyDown() || current >= TransportEngineBlockEntity.MAX_RPM
                    ? 0 : (Math.max(0, current) / 32 + 1) * 32;
            engine.setTargetRpm(next);
            player.displayClientMessage(Component.translatable("create_aeronautics_extra.transport_engine.target_rpm", next), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public Class<TransportEngineBlockEntity> getBlockEntityClass() {
        return TransportEngineBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends TransportEngineBlockEntity> getBlockEntityType() {
        return AeroExtra.TRANSPORT_ENGINE_BE.get();
    }
}
