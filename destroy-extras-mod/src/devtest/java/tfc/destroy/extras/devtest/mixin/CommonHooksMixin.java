package tfc.destroy.extras.devtest.mixin;

import net.neoforged.neoforge.common.CommonHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** В релизной игре проверка компонентов выключена; иначе стенд падает на дисках Destroy при показе штатного электролиза. */
@Mixin(value = CommonHooks.class, remap = false)
public class CommonHooksMixin {
    @Inject(method = "validateComponent", at = @At("HEAD"), cancellable = true)
    private static void asInRelease(Object component, CallbackInfo ci) {
        ci.cancel();
    }
}
