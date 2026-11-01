package tfc.destroy.extras.compat.jei;

import com.simibubi.create.compat.jei.category.BasinCategory;
import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.resources.ResourceLocation;
import tfc.destroy.extras.DestroyExtras;
import tfc.destroy.extras.recipe.ExtrasRecipeTypes;

@JeiPlugin
public final class DestroyExtrasJei implements IModPlugin {
    private CreateRecipeCategory<BasinRecipe> electrolysis;
    private CreateRecipeCategory<BasinRecipe> etching;

    @Override
    public ResourceLocation getPluginUid() {
        return DestroyExtras.rl("jei");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        electrolysis = new CreateRecipeCategory.Builder<>(BasinRecipe.class)
            .addTypedRecipes(ExtrasRecipeTypes.ELECTROLYSIS)
            .itemIcon(DestroyExtras.ELECTROLYZER_BLOCK.get())
            .catalyst(DestroyExtras.ELECTROLYZER_BLOCK::get)
            .emptyBackground(177, 103)
            .build(DestroyExtras.rl("electrolysis"), info -> new BasinCategory(info, false));
        etching = new CreateRecipeCategory.Builder<>(BasinRecipe.class)
            .addTypedRecipes(ExtrasRecipeTypes.ETCHING)
            .itemIcon(DestroyExtras.ETCHING_BATH_BLOCK.get())
            .catalyst(DestroyExtras.ETCHING_BATH_BLOCK::get)
            .emptyBackground(177, 103)
            .build(DestroyExtras.rl("etching"), info -> new BasinCategory(info, false));
        registration.addRecipeCategories(electrolysis, etching);
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        if (electrolysis != null) electrolysis.registerRecipes(registration);
        if (etching != null) etching.registerRecipes(registration);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        if (electrolysis != null) electrolysis.registerCatalysts(registration);
        if (etching != null) etching.registerCatalysts(registration);
    }
}
