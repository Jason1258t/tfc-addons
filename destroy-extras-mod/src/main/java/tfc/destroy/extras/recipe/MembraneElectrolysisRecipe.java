package tfc.destroy.extras.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeParams;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

/**
 * Рецепт электролизёра: один жидкий вход и от одного до трёх жидких продуктов, каждый — в свой порт по роли
 * ({@link Port}). Какая реакция идёт, задаёт только datapack. Если указаны {@code salt_concentration} и
 * {@code water_concentration}, вход принимает и солёную воду TFC — она переводится в смесь Destroy с этим составом.
 */
public final class MembraneElectrolysisRecipe extends BasinRecipe {
    public static final String DEFAULT_TRANSLATION_KEY = "destroy_extras.fluid.salt_water";
    /** Старый формат без поля outputs: водород, хлор, щёлочь по порядку. */
    public static final List<Port> DEFAULT_PORTS = List.of(Port.CATHODE_GAS, Port.ANODE_GAS, Port.CATHOLYTE);

    /** Выходные порты машины по ролям электролиза. */
    public enum Port implements StringRepresentable {
        /** Газ с катода — левая верхняя труба. */
        CATHODE_GAS,
        /** Газ с анода — правая верхняя труба. */
        ANODE_GAS,
        /** Жидкий продукт (католит) — левый нижний бак. */
        CATHOLYTE;

        public static final Codec<Port> CODEC = StringRepresentable.fromEnum(Port::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private final Optional<Float> saltConcentration;
    private final Optional<Float> waterConcentration;
    private final String translationKey;
    private final List<Port> ports;
    private final boolean needsMembrane;

    public MembraneElectrolysisRecipe(ProcessingRecipeParams params, Optional<Float> saltConcentration,
        Optional<Float> waterConcentration, String translationKey, List<Port> ports, boolean needsMembrane) {
        super(ExtrasRecipeTypes.ELECTROLYSIS, params);
        this.saltConcentration = saltConcentration;
        this.waterConcentration = waterConcentration;
        this.translationKey = translationKey;
        this.ports = ports.isEmpty() ? DEFAULT_PORTS : List.copyOf(ports);
        this.needsMembrane = needsMembrane;
        if (this.processingDuration == 0) this.processingDuration = 200;
    }

    public SizedFluidIngredient input() {
        return getFluidIngredients().getFirst();
    }

    public FluidStack[] products() {
        return getFluidResults().stream().map(FluidStack::copy).toArray(FluidStack[]::new);
    }

    /** Порт каждого продукта, в порядке {@link #products()}. */
    public List<Port> ports() {
        return ports;
    }

    public boolean needsMembrane() {
        return needsMembrane;
    }

    /** Принимает ли рецепт солёную воду TFC (с переводом в смесь Destroy). */
    public boolean acceptsSaltWater() {
        return saltConcentration.isPresent() && waterConcentration.isPresent();
    }

    public float saltConcentration() {
        return saltConcentration.orElse(0.0F);
    }

    public float waterConcentration() {
        return waterConcentration.orElse(0.0F);
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
        if (getFluidIngredients().size() != 1) errors.add("Требуется ровно один жидкий вход: " + getFluidIngredients().size());
        int results = getFluidResults().size();
        if (results < 1 || results > 3) errors.add("Требуется от одного до трёх жидких выходов: " + results);
        if (ports.size() != results) errors.add("Поле outputs должно назвать порт для каждого выхода: портов " + ports.size() + ", выходов " + results);
        Set<Port> unique = ports.isEmpty() ? Set.of() : EnumSet.copyOf(ports);
        if (unique.size() != ports.size()) errors.add("Порты в outputs не должны повторяться: " + ports);
        if (!getIngredients().isEmpty() || !getRollableResults().isEmpty()) errors.add("Предметные входы и выходы не поддерживаются: входов " + getIngredients().size() + ", выходов " + getRollableResults().size());
        if (saltConcentration.isPresent() != waterConcentration.isPresent()) errors.add("salt_concentration и water_concentration задаются только вместе.");
        if (saltConcentration.orElse(1.0F) <= 0 || waterConcentration.orElse(1.0F) <= 0) errors.add("Концентрации должны быть положительными.");
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
            Codec.FLOAT.optionalFieldOf("salt_concentration").forGetter(recipe -> recipe.saltConcentration),
            Codec.FLOAT.optionalFieldOf("water_concentration").forGetter(recipe -> recipe.waterConcentration),
            Codec.STRING.optionalFieldOf("translation_key", DEFAULT_TRANSLATION_KEY)
                .forGetter(MembraneElectrolysisRecipe::translationKey),
            Port.CODEC.listOf().optionalFieldOf("outputs", List.of()).forGetter(MembraneElectrolysisRecipe::ports),
            Codec.BOOL.optionalFieldOf("membrane", true).forGetter(MembraneElectrolysisRecipe::needsMembrane)
        ).apply(instance, MembraneElectrolysisRecipe::new)).validate(recipe -> {
            List<String> errors = recipe.validate();
            return errors.isEmpty() ? DataResult.success(recipe)
                : DataResult.error(() -> String.join("\n", errors), recipe);
        });

        private static final StreamCodec<RegistryFriendlyByteBuf, MembraneElectrolysisRecipe> STREAM_CODEC =
            StreamCodec.composite(
                ProcessingRecipeParams.STREAM_CODEC, MembraneElectrolysisRecipe::getParams,
                ByteBufCodecs.optional(ByteBufCodecs.FLOAT), recipe -> recipe.saltConcentration,
                ByteBufCodecs.optional(ByteBufCodecs.FLOAT), recipe -> recipe.waterConcentration,
                ByteBufCodecs.STRING_UTF8, MembraneElectrolysisRecipe::translationKey,
                ByteBufCodecs.idMapper(i -> Port.values()[i], Port::ordinal).apply(ByteBufCodecs.list()), MembraneElectrolysisRecipe::ports,
                ByteBufCodecs.BOOL, MembraneElectrolysisRecipe::needsMembrane,
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
