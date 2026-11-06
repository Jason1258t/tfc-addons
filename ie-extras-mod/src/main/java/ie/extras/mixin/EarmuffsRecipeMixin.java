package ie.extras.mixin;

import blusunrize.immersiveengineering.common.crafting.EarmuffsRecipe;
import blusunrize.immersiveengineering.common.items.components.AttachedItem;
import blusunrize.immersiveengineering.common.register.IEDataComponents;
import blusunrize.immersiveengineering.common.register.IEItems;
import com.simibubi.create.AllItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EarmuffsRecipe.class)
abstract class EarmuffsRecipeMixin {
    @Inject(method = "matches(Lnet/minecraft/world/item/crafting/CraftingInput;Lnet/minecraft/world/level/Level;)Z",
            at = @At("HEAD"), cancellable = true)
    private void ieExtras$matchesGoggles(CraftingInput input, Level level, CallbackInfoReturnable<Boolean> result) {
        if (!ieExtras$withEarmuffs(input).isEmpty()) result.setReturnValue(true);
    }

    @Inject(method = "assemble(Lnet/minecraft/world/item/crafting/CraftingInput;Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/item/ItemStack;",
            at = @At("HEAD"), cancellable = true)
    private void ieExtras$assembleGoggles(CraftingInput input, HolderLookup.Provider registries,
            CallbackInfoReturnable<ItemStack> result) {
        ItemStack assembled = ieExtras$withEarmuffs(input);
        if (!assembled.isEmpty()) result.setReturnValue(assembled);
    }

    // IE распознаёт крепление по тому же компоненту, что у обычных шлемов.
    // При разборке штатный getRemainingItems возвращает сохранённые наушники.
    @Unique
    private static ItemStack ieExtras$withEarmuffs(CraftingInput input) {
        if (input.ingredientCount() < 1 || input.ingredientCount() > 2) return ItemStack.EMPTY;
        ItemStack goggles = ItemStack.EMPTY;
        ItemStack earmuffs = ItemStack.EMPTY;
        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack stack = input.getItem(slot);
            if (stack.isEmpty()) continue;
            if (goggles.isEmpty() && stack.is(AllItems.GOGGLES.asItem())) goggles = stack;
            else if (earmuffs.isEmpty() && stack.is(IEItems.Misc.EARMUFFS.asItem())) earmuffs = stack;
            else return ItemStack.EMPTY;
        }
        if (goggles.isEmpty()) return ItemStack.EMPTY;
        boolean removing = earmuffs.isEmpty();
        if (goggles.has(IEDataComponents.CONTAINED_EARMUFF) != removing) return ItemStack.EMPTY;
        ItemStack output = goggles.copyWithCount(1);
        if (earmuffs.isEmpty()) output.remove(IEDataComponents.CONTAINED_EARMUFF);
        else output.set(IEDataComponents.CONTAINED_EARMUFF, new AttachedItem(earmuffs.copyWithCount(1)));
        return output;
    }
}
