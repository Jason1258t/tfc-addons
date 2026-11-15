package aero.extra.mixin;

import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Редстоун останавливает двигатель, сохраняя оставшееся время горения. */
@Pseudo
@Mixin(targets = "dev.simulated_team.simulated.content.blocks.portable_engine.PortableEngineBlockEntity", remap = false)
abstract class PortableEngineRedstoneMixin extends GeneratingKineticBlockEntity {
    @Shadow
    protected float generatedSpeed;

    protected PortableEngineRedstoneMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void aeroExtra$pauseWithRedstone(CallbackInfo callback) {
        if (level == null || isVirtual() || !level.hasNeighborSignal(worldPosition)) return;

        // Оставляем служебный тик Create, но пропускаем сгорание и розжиг, включая жидкостный миксин.
        super.tick();
        BlockState state = getBlockState();
        boolean wasRunning = generatedSpeed != 0 || state.getValue(BlockStateProperties.LIT);
        generatedSpeed = 0;
        if (!level.isClientSide && wasRunning) {
            if (state.getValue(BlockStateProperties.LIT)) {
                level.setBlock(worldPosition, state.setValue(BlockStateProperties.LIT, false), Block.UPDATE_CLIENTS);
            }
            updateGeneratedRotation();
            notifyUpdate();
        }
        callback.cancel();
    }
}
