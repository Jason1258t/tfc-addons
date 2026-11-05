package tfcstudio.betterfluids.mixin;

import com.simibubi.create.api.connectivity.ConnectivityHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tfcstudio.betterfluids.IronTankEntity;

@Mixin(value = ConnectivityHandler.class, remap = false)
abstract class TankModelConnectionsMixin {
    @Inject(method = "isConnected", at = @At("HEAD"), cancellable = true)
    private static void betterFluids$connected(BlockGetter world, BlockPos from, BlockPos to,
                                                CallbackInfoReturnable<Boolean> result) {
        if (world.getBlockEntity(from) instanceof IronTankEntity) {
            result.setReturnValue(connected(world, from, to));
        }
    }

    private static boolean connected(BlockGetter world, BlockPos from, BlockPos to) {
        for (Direction direction : Direction.values()) {
            BlockPos step = from.relative(direction);
            if (step.distManhattan(to) >= from.distManhattan(to) || !IronTankEntity.shellConnected(world, from, direction)) {
                continue;
            }
            if (step.equals(to)) {
                return true;
            }
            for (Direction second : Direction.values()) {
                if (step.relative(second).equals(to) && IronTankEntity.shellConnected(world, step, second)) {
                    return true;
                }
            }
        }
        return false;
    }
}
