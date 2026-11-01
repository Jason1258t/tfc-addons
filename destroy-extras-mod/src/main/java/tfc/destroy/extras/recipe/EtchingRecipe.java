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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

/** Узкий рецепт ванны: топология берётся из фактической маски, а не фиксируется в JSON. */
public final class EtchingRecipe extends BasinRecipe {
    private final float copperMoles;

    public EtchingRecipe(ProcessingRecipeParams params, float copperMoles) {
        super(ExtrasRecipeTypes.ETCHING, params);
        this.copperMoles = copperMoles;
        if (this.processingDuration == 0) this.processingDuration = 400;
    }

    public Ingredient blank() {
        return getIngredients().get(0);
    }

    public Ingredient mask() {
        return getIngredients().get(1);
    }

    public SizedFluidIngredient input() {
        return getFluidIngredients().getFirst();
    }

    public ItemStack output() {
        return getRollableResults().getFirst().getStack();
    }

    public ItemStack result(ItemStack mask) {
        ItemStack output = output();
        return mask.transmuteCopy(output.getItem(), output.getCount());
    }

    public float copperMoles() {
        return copperMoles;
    }

    @Override
    protected int getMaxInputCount() {
        return 2;
    }

    @Override
    protected int getMaxOutputCount() {
        return 1;
    }

    @Override
    protected int getMaxFluidInputCount() {
        return 1;
    }

    @Override
    protected int getMaxFluidOutputCount() {
        return 1;
    }

    @Override
    protected boolean canRequireHeat() {
        return false;
    }

    @Override
    public List<String> validate() {
        List<String> errors = super.validate();
        if (getIngredients().size() != 2) errors.add("Требуются заготовка и маска: " + getIngredients().size());
        if (getFluidIngredients().size() != 1) errors.add("Требуется один вход FeCl₃: " + getFluidIngredients().size());
        if (getRollableResults().size() != 1 || output().getCount() != 1) errors.add("Требуется одна плата на выходе");
        if (getFluidResults().size() != 1) errors.add("Требуется показательный выход отработанного раствора для JEI");
        if (copperMoles <= 0) errors.add("Количество меди должно быть положительным");
        return errors;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ExtrasRecipeTypes.ETCHING_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ExtrasRecipeTypes.ETCHING_TYPE.get();
    }

    public static final class Serializer implements RecipeSerializer<EtchingRecipe> {
        private static final MapCodec<EtchingRecipe> CODEC = RecordCodecBuilder.<EtchingRecipe>mapCodec(instance -> instance.group(
            ProcessingRecipeParams.CODEC.forGetter(EtchingRecipe::getParams),
            Codec.FLOAT.fieldOf("copper_moles").forGetter(EtchingRecipe::copperMoles)
        ).apply(instance, EtchingRecipe::new)).validate(recipe -> {
            List<String> errors = recipe.validate();
            return errors.isEmpty() ? DataResult.success(recipe)
                : DataResult.error(() -> String.join("\n", errors), recipe);
        });

        private static final StreamCodec<RegistryFriendlyByteBuf, EtchingRecipe> STREAM_CODEC = StreamCodec.composite(
            ProcessingRecipeParams.STREAM_CODEC, EtchingRecipe::getParams,
            ByteBufCodecs.FLOAT, EtchingRecipe::copperMoles,
            EtchingRecipe::new);

        @Override
        public MapCodec<EtchingRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, EtchingRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
