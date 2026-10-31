package tfc.destroy.extras.recipe;

import com.simibubi.create.foundation.recipe.IRecipeTypeInfo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import tfc.destroy.extras.DestroyExtras;

/** Типы машин Extras не подменяют штатные рецепты Destroy. */
public final class ExtrasRecipeTypes {
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
        DeferredRegister.create(Registries.RECIPE_SERIALIZER, DestroyExtras.MOD_ID);
    private static final DeferredRegister<RecipeType<?>> TYPES =
        DeferredRegister.create(Registries.RECIPE_TYPE, DestroyExtras.MOD_ID);

    public static final ResourceLocation ELECTROLYSIS_ID = DestroyExtras.rl("electrolysis");
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<MembraneElectrolysisRecipe>> ELECTROLYSIS_SERIALIZER =
        SERIALIZERS.register("electrolysis", MembraneElectrolysisRecipe.Serializer::new);
    public static final DeferredHolder<RecipeType<?>, RecipeType<MembraneElectrolysisRecipe>> ELECTROLYSIS_TYPE =
        TYPES.register("electrolysis", () -> RecipeType.simple(ELECTROLYSIS_ID));

    public static final IRecipeTypeInfo ELECTROLYSIS = new IRecipeTypeInfo() {
        @Override
        public ResourceLocation getId() {
            return ELECTROLYSIS_ID;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T extends RecipeSerializer<?>> T getSerializer() {
            return (T) ELECTROLYSIS_SERIALIZER.get();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <I extends RecipeInput, R extends Recipe<I>> RecipeType<R> getType() {
            return (RecipeType<R>) ELECTROLYSIS_TYPE.get();
        }
    };

    private ExtrasRecipeTypes() {}

    public static void register(IEventBus modBus) {
        SERIALIZERS.register(modBus);
        TYPES.register(modBus);
    }
}
