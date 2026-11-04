package ee.extras.mixin;

import dan200.computercraft.core.util.Colour;
import dan200.computercraft.shared.ModRegistry;
import dan200.computercraft.shared.util.ColourTracker;
import dan200.computercraft.shared.util.ColourUtils;
import dan200.computercraft.shared.util.DataComponentUtil;
import ee.extras.pcb.PcbItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Заменяет бумагу и редстоун в штатном рецепте CC на изготовленный магнитный носитель. */
@Pseudo
@Mixin(targets = "dan200.computercraft.shared.media.recipes.DiskRecipe", remap = false)
public abstract class DiskRecipeMixin {
    @Inject(method = "matches", at = @At("HEAD"), cancellable = true)
    private void eeExtras$matches(CraftingInput input, Level level, CallbackInfoReturnable<Boolean> cir) {
        boolean mediumFound = false;
        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack stack = input.getItem(slot);
            if (stack.isEmpty()) continue;
            if (stack.is(PcbItems.MAGNETIC_DISK_MEDIUM.get())) {
                if (mediumFound) {
                    cir.setReturnValue(false);
                    return;
                }
                mediumFound = true;
            } else if (ColourUtils.getStackColour(stack) == null) {
                cir.setReturnValue(false);
                return;
            }
        }
        cir.setReturnValue(mediumFound);
    }

    @Inject(method = "assemble", at = @At("HEAD"), cancellable = true)
    private void eeExtras$assemble(CraftingInput input, HolderLookup.Provider registries,
            CallbackInfoReturnable<ItemStack> cir) {
        ColourTracker tracker = new ColourTracker();
        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack stack = input.getItem(slot);
            if (stack.isEmpty() || stack.is(PcbItems.MAGNETIC_DISK_MEDIUM.get())) continue;
            DyeColor dye = ColourUtils.getStackColour(stack);
            if (dye != null) tracker.addColour(dye);
        }
        cir.setReturnValue(DataComponentUtil.createStack(ModRegistry.Items.DISK.get(), DataComponents.DYED_COLOR,
                new DyedItemColor(tracker.hasColour() ? tracker.getColour() : Colour.BLUE.getHex(), false)));
    }
}
