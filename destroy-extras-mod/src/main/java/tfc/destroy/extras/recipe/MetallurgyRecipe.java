package tfc.destroy.extras.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeParams;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;

/** Универсальная партия металлургического процессора; состав и номиналы EE задаёт datapack. */
public final class MetallurgyRecipe extends BasinRecipe {
    private final int voltage;
    private final float current;
    private final String catalyst;
    private final int priority;

    public MetallurgyRecipe(ProcessingRecipeParams params, int voltage, float current, String catalyst, int priority) {
        super(ExtrasRecipeTypes.METALLURGY, params);
        this.voltage = voltage;
        this.current = current;
        this.catalyst = catalyst;
        this.priority = priority;
        if (processingDuration == 0) processingDuration = 200;
    }

    public int voltage() { return voltage; }
    public float current() { return current; }
    public String catalyst() { return catalyst; }
    public int priority() { return priority; }
    public Item catalystItem() {
        return catalyst.isEmpty() ? null : BuiltInRegistries.ITEM.get(ResourceLocation.parse(catalyst));
    }

    @Override protected int getMaxInputCount() { return 2; }
    @Override protected int getMaxOutputCount() { return 2; }
    @Override protected int getMaxFluidInputCount() { return 2; }
    @Override protected int getMaxFluidOutputCount() { return 2; }
    @Override protected boolean canRequireHeat() { return false; }

    @Override
    public List<String> validate() {
        List<String> errors = super.validate();
        if (voltage != 240 && voltage != 600) errors.add("Допустимы только EE 240/600 В");
        if (current <= 0 || current > 32) errors.add("Ток должен быть в диапазоне 0–32 А");
        if (!catalyst.isEmpty()) {
            ResourceLocation id = ResourceLocation.tryParse(catalyst);
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) errors.add("Неизвестный катализатор: " + catalyst);
        }
        return errors;
    }

    @Override public RecipeSerializer<?> getSerializer() { return ExtrasRecipeTypes.METALLURGY_SERIALIZER.get(); }
    @Override public RecipeType<?> getType() { return ExtrasRecipeTypes.METALLURGY_TYPE.get(); }

    public static final class Serializer implements RecipeSerializer<MetallurgyRecipe> {
        private static final MapCodec<MetallurgyRecipe> CODEC = RecordCodecBuilder.<MetallurgyRecipe>mapCodec(i -> i.group(
            ProcessingRecipeParams.CODEC.forGetter(MetallurgyRecipe::getParams),
            Codec.INT.optionalFieldOf("voltage", 240).forGetter(MetallurgyRecipe::voltage),
            Codec.FLOAT.optionalFieldOf("current", 4.0F).forGetter(MetallurgyRecipe::current),
            Codec.STRING.optionalFieldOf("catalyst", "").forGetter(MetallurgyRecipe::catalyst),
            Codec.INT.optionalFieldOf("priority", 0).forGetter(MetallurgyRecipe::priority)
        ).apply(i, MetallurgyRecipe::new)).validate(r -> {
            List<String> errors = r.validate();
            return errors.isEmpty() ? DataResult.success(r) : DataResult.error(() -> String.join("\n", errors), r);
        });
        private static final StreamCodec<RegistryFriendlyByteBuf, MetallurgyRecipe> STREAM = StreamCodec.composite(
            ProcessingRecipeParams.STREAM_CODEC, MetallurgyRecipe::getParams,
            ByteBufCodecs.INT, MetallurgyRecipe::voltage,
            ByteBufCodecs.FLOAT, MetallurgyRecipe::current,
            ByteBufCodecs.STRING_UTF8, MetallurgyRecipe::catalyst,
            ByteBufCodecs.INT, MetallurgyRecipe::priority,
            MetallurgyRecipe::new);
        @Override public MapCodec<MetallurgyRecipe> codec() { return CODEC; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, MetallurgyRecipe> streamCodec() { return STREAM; }
    }
}
