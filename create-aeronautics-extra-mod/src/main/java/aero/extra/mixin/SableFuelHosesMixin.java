package aero.extra.mixin;

import aero.extra.FuelHoses;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper.AssemblyTransform;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "dev.ryanhcode.sable.api.SubLevelAssemblyHelper", remap = false)
abstract class SableFuelHosesMixin {
    @Inject(method = "moveBlocks", at = @At("HEAD"))
    private static void moveHoses(ServerLevel level, AssemblyTransform transform, Iterable<BlockPos> blocks,
                                  CallbackInfo callback) {
        FuelHoses.move(level, transform.getLevel(), blocks, transform::apply);
    }
}
