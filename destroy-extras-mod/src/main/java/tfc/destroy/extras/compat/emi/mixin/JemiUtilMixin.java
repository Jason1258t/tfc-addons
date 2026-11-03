package tfc.destroy.extras.compat.emi.mixin;

import dev.emi.emi.jemi.JemiUtil;
import java.util.Set;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** EMI не запускает JEI-плагин мода, у которого есть свой EMI-плагин; у Destroy тот показывает только реакции. */
@Mixin(value = JemiUtil.class, remap = false)
public class JemiUtilMixin {
    @Inject(method = "getHandledMods", at = @At("RETURN"))
    private static void showDestroyCategories(CallbackInfoReturnable<Set<String>> cir) {
        cir.getReturnValue().remove("destroy");
    }
}
