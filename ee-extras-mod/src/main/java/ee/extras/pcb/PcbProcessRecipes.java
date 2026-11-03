package ee.extras.pcb;

import com.simibubi.create.foundation.recipe.IRecipeTypeInfo;
import ee.extras.EeExtras;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Регистрация промышленных операций без привязки к JEI. */
public final class PcbProcessRecipes {
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
        DeferredRegister.create(Registries.RECIPE_SERIALIZER, EeExtras.MOD_ID);
    private static final DeferredRegister<RecipeType<?>> TYPES =
        DeferredRegister.create(Registries.RECIPE_TYPE, EeExtras.MOD_ID);

    public static final ResourceLocation PCB_PROCESS_ID = EeExtras.rl("pcb_process");
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<PcbProcessRecipe>> PCB_PROCESS_SERIALIZER =
        SERIALIZERS.register("pcb_process", PcbProcessRecipe.Serializer::new);
    public static final DeferredHolder<RecipeType<?>, RecipeType<PcbProcessRecipe>> PCB_PROCESS_TYPE =
        TYPES.register("pcb_process", () -> RecipeType.simple(PCB_PROCESS_ID));

    public static final IRecipeTypeInfo PCB_PROCESS = new IRecipeTypeInfo() {
        @Override
        public ResourceLocation getId() {
            return PCB_PROCESS_ID;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T extends RecipeSerializer<?>> T getSerializer() {
            return (T) PCB_PROCESS_SERIALIZER.get();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <I extends RecipeInput, R extends Recipe<I>> RecipeType<R> getType() {
            return (RecipeType<R>) PCB_PROCESS_TYPE.get();
        }
    };

    private PcbProcessRecipes() {}

    public static void register(IEventBus modBus) {
        SERIALIZERS.register(modBus);
        TYPES.register(modBus);
    }
}
