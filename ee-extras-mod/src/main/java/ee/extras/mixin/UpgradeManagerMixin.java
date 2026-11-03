package ee.extras.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dan200.computercraft.api.upgrades.UpgradeBase;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Учитывает новые стандартные компоненты предметов в штатных улучшениях CC, сохраняя явные компоненты стека. */
@Pseudo
@Mixin(targets = "dan200.computercraft.impl.UpgradeManager", remap = false)
public abstract class UpgradeManagerMixin {
    @WrapOperation(method = "lambda$get$0", at = @At(value = "INVOKE",
            target = "Ldan200/computercraft/api/upgrades/UpgradeBase;isItemSuitable(Lnet/minecraft/world/item/ItemStack;)Z"),
            require = 1)
    private static boolean eeExtras$isItemSuitable(UpgradeBase upgrade, ItemStack stack, Operation<Boolean> original) {
        try {
            if (upgrade.getClass().getMethod("isItemSuitable", ItemStack.class).getDeclaringClass() != UpgradeBase.class) {
                return original.call(upgrade, stack);
            }
        } catch (NoSuchMethodException e) {
            return original.call(upgrade, stack);
        }
        ItemStack craftingStack = upgrade.getCraftingItem();
        if (!craftingStack.is(stack.getItem())) return false;
        PatchedDataComponentMap normalized = PatchedDataComponentMap.fromPatch(stack.getPrototype(),
                craftingStack.getComponentsPatch());
        return normalized.equals(stack.getComponents());
    }
}
