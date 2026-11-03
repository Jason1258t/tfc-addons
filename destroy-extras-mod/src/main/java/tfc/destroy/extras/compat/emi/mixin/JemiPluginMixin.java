package tfc.destroy.extras.compat.emi.mixin;

import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.jemi.JemiPlugin;
import dev.emi.emi.jemi.JemiStack;
import java.util.Set;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import petrolpark.mc.destroy.chemistry.legacy.LegacySpecies;

/** Переносит категории Destroy из JEI в EMI; молекулы в списке уже есть от родного плагина и не дублируются. */
@Mixin(value = JemiPlugin.class, remap = false)
public class JemiPluginMixin {
    @ModifyVariable(method = "register(Ldev/emi/emi/api/EmiRegistry;)V", at = @At("STORE"), ordinal = 0)
    private Set<String> showDestroyCategories(Set<String> handled) {
        handled.remove("destroy");
        return handled;
    }

    @Inject(method = "register(Ldev/emi/emi/api/EmiRegistry;)V", at = @At("TAIL"))
    private void noDuplicateMolecules(EmiRegistry registry, CallbackInfo ci) {
        registry.removeEmiStacks(s -> s instanceof JemiStack<?> stack && stack.ingredient instanceof LegacySpecies);
    }
}
