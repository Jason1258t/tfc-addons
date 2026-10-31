package tfc.destroy.extras.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeParams;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

/** Одна солёная вода и три независимых жидких продукта; состав входа задаёт datapack. */
public final class MembraneElectrolysisRecipe extends BasinRecipe {
    public static final String DEFAULT_TRANSLATION_KEY = "destroy_extras.fluid.salt_water";

    private final float saltConcentration;
    private final float waterConcentration;
    private final String translationKey;

    public MembraneElectrolysisRecipe(ProcessingRecipeParams params, float saltConcentration,
        float waterConcentration, String translationKey) {
        super(ExtrasRecipeTypes.ELECTROLYSIS, params);
        this.saltConcentration = saltConcentration;
        this.waterConcentration = waterConcentration;
        this.translationKey = translationKey;
        if (this.processingDuration == 0) this.processingDuration = 200;
    }

    public SizedFluidIngredient input() {
        return getFluidIngredients().getFirst();
    }

    public FluidStack[] products() {
        return getFluidResults().stream().map(FluidStack::copy).toArray(FluidStack[]::new);
    }

    public float saltConcentration() {
        return saltConcentration;
    }

    public float waterConcentration() {
        return waterConcentration;
    }

    public String translationKey() {
        return translationKey;
    }

    @Override
    protected int getMaxFluidInputCount() {
        return 1;
    }

    @Override
    protected int getMaxFluidOutputCount() {
        return 3;
    }

    @Override
    protected boolean canRequireHeat() {
        return false;
    }

    @Override
    public List<String> validate() {
        List<String> errors = super.validate();
        if (getFluidIngredients().size() != 1) errors.add("Требуется ровно один жидкий вход солёной воды: " + getFluidIngredients().size());
        if (getFluidResults().size() != 3) errors.add("Требуются ровно три жидких выхода: " + getFluidResults().size());
        if (!getIngredients().isEmpty() || !getRollableResults().isEmpty()) errors.add("Предметные входы и выходы не поддерживаются: входов " + getIngredients().size() + ", выходов " + getRollableResults().size());
        if (saltConcentration <= 0 || waterConcentration <= 0) errors.add("Концентрации должны быть положительными.");
        return errors;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ExtrasRecipeTypes.ELECTROLYSIS_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ExtrasRecipeTypes.ELECTROLYSIS_TYPE.get();
    }

    public static final class Serializer implements RecipeSerializer<MembraneElectrolysisRecipe> {
        private static final MapCodec<MembraneElectrolysisRecipe> CODEC = RecordCodecBuilder.<MembraneElectrolysisRecipe>mapCodec(instance -> instance.group(
            ProcessingRecipeParams.CODEC.forGetter(MembraneElectrolysisRecipe::getParams),
            Codec.FLOAT.fieldOf("salt_concentration").forGetter(MembraneElectrolysisRecipe::saltConcentration),
            Codec.FLOAT.fieldOf("water_concentration").forGetter(MembraneElectrolysisRecipe::waterConcentration),
            Codec.STRING.optionalFieldOf("translation_key", DEFAULT_TRANSLATION_KEY)
                .forGetter(MembraneElectrolysisRecipe::translationKey)
        ).apply(instance, (ProcessingRecipeParams params, Float salt, Float water, String key) ->
            new MembraneElectrolysisRecipe(params, salt, water, key))).validate(recipe -> {
            List<String> errors = recipe.validate();
            return errors.isEmpty() ? DataResult.success(recipe)
                : DataResult.error(() -> String.join("\n", errors), recipe);
        });

        private static final StreamCodec<RegistryFriendlyByteBuf, MembraneElectrolysisRecipe> STREAM_CODEC =
            StreamCodec.composite(
                ProcessingRecipeParams.STREAM_CODEC, MembraneElectrolysisRecipe::getParams,
                ByteBufCodecs.FLOAT, MembraneElectrolysisRecipe::saltConcentration,
                ByteBufCodecs.FLOAT, MembraneElectrolysisRecipe::waterConcentration,
                ByteBufCodecs.STRING_UTF8, MembraneElectrolysisRecipe::translationKey,
                MembraneElectrolysisRecipe::new);

        @Override
        public MapCodec<MembraneElectrolysisRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, MembraneElectrolysisRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
