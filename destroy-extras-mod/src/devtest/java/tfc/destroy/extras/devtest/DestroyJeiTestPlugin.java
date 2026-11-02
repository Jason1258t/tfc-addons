package tfc.destroy.extras.devtest;

import com.simibubi.create.compat.jei.CreateJEI;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.resources.ResourceLocation;
import petrolpark.mc.destroy.Destroy;
import petrolpark.mc.destroy.DestroyRecipeTypes;
import petrolpark.mc.destroy.compat.jei.DestroyJEI;
import petrolpark.mc.destroy.content.processing.centrifuge.CentrifugationRecipe;

/** Подключает штатную центрифугу Destroy в изолированном клиентском прогоне. */
@JeiPlugin
public final class DestroyJeiTestPlugin extends DestroyJEI {
    private RecipeType<CentrifugationRecipe> centrifugation;

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath("destroy_extras_devtest", "destroy_jei");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        super.registerCategories(registration);
        for (RecipeType<?> type : MIXTURE_APPLICABLE_RECIPE_TYPES.keySet()) {
            if (type.getUid().equals(Destroy.asResource("centrifugation"))) {
                centrifugation = (RecipeType<CentrifugationRecipe>) type;
            }
        }
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        // Остальные категории не нужны проверке; штатный показ электролиза падает из-за дисков Destroy.
        registration.addRecipes(centrifugation,
            CreateJEI.getTypedRecipes(DestroyRecipeTypes.CENTRIFUGATION.getType()).stream()
                .map(holder -> (CentrifugationRecipe) holder.value()).toList());
    }
}
