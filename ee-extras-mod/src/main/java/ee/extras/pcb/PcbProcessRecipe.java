package ee.extras.pcb;

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

/** JSON-операция фотолитографии или металлизации; топология копируется из фактического входа. */
public final class PcbProcessRecipe extends BasinRecipe {
    private final String machine;
    private final int level;
    private final double voltage;
    private final double current;

    public PcbProcessRecipe(ProcessingRecipeParams params, String machine, int level, double voltage, double current) {
        super(PcbProcessRecipes.PCB_PROCESS, params);
        this.machine = machine;
        this.level = level;
        this.voltage = voltage;
        this.current = current;
        if (processingDuration == 0) processingDuration = 200;
    }

    public String machine() {
        return machine;
    }

    public PcbMachineKind kind() {
        return PcbMachineKind.byId(machine);
    }

    public int level() {
        return level;
    }

    public double voltage() {
        return voltage;
    }

    public double current() {
        return current;
    }

    public ItemStack output() {
        return getRollableResults().getFirst().getStack();
    }

    public ItemStack result(ItemStack topologySource) {
        ItemStack output = output();
        return topologySource.transmuteCopy(output.getItem(), output.getCount());
    }

    public Ingredient ingredient(int slot) {
        return getIngredients().get(slot);
    }

    @Override
    protected int getMaxInputCount() {
        return 3;
    }

    @Override
    protected int getMaxOutputCount() {
        return 1;
    }

    @Override
    protected int getMaxFluidInputCount() {
        return 0;
    }

    @Override
    protected int getMaxFluidOutputCount() {
        return 0;
    }

    @Override
    protected boolean canRequireHeat() {
        return false;
    }

    @Override
    public List<String> validate() {
        List<String> errors = super.validate();
        boolean knownMachine = machine.equals(PcbMachineKind.PHOTOLITHOGRAPHY.id())
            || machine.equals(PcbMachineKind.METALLIZATION.id());
        if (!knownMachine) errors.add("Неизвестная машина: " + machine);
        int expectedInputs = machine.equals(PcbMachineKind.PHOTOLITHOGRAPHY.id()) ? 3 : 2;
        if (getIngredients().size() != expectedInputs) errors.add("Неверное число предметных входов: " + getIngredients().size());
        if (level != 2 && level != 3) errors.add("Промышленный процесс поддерживает только L2/L3");
        if (voltage <= 0 || current <= 0) errors.add("Напряжение и ток должны быть положительными");
        if (getRollableResults().size() != 1 || output().getCount() != 1) errors.add("Требуется один предмет на выходе");
        return errors;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return PcbProcessRecipes.PCB_PROCESS_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return PcbProcessRecipes.PCB_PROCESS_TYPE.get();
    }

    public static final class Serializer implements RecipeSerializer<PcbProcessRecipe> {
        private static final MapCodec<PcbProcessRecipe> CODEC = RecordCodecBuilder.<PcbProcessRecipe>mapCodec(instance -> instance.group(
            ProcessingRecipeParams.CODEC.forGetter(PcbProcessRecipe::getParams),
            Codec.STRING.fieldOf("machine").forGetter(PcbProcessRecipe::machine),
            Codec.INT.fieldOf("level").forGetter(PcbProcessRecipe::level),
            Codec.DOUBLE.fieldOf("voltage").forGetter(PcbProcessRecipe::voltage),
            Codec.DOUBLE.fieldOf("current").forGetter(PcbProcessRecipe::current)
        ).apply(instance, PcbProcessRecipe::new)).validate(recipe -> {
            List<String> errors = recipe.validate();
            return errors.isEmpty() ? DataResult.success(recipe)
                : DataResult.error(() -> String.join("\n", errors), recipe);
        });

        private static final StreamCodec<RegistryFriendlyByteBuf, PcbProcessRecipe> STREAM_CODEC = StreamCodec.composite(
            ProcessingRecipeParams.STREAM_CODEC, PcbProcessRecipe::getParams,
            ByteBufCodecs.STRING_UTF8, PcbProcessRecipe::machine,
            ByteBufCodecs.INT, PcbProcessRecipe::level,
            ByteBufCodecs.DOUBLE, PcbProcessRecipe::voltage,
            ByteBufCodecs.DOUBLE, PcbProcessRecipe::current,
            PcbProcessRecipe::new);

        @Override
        public MapCodec<PcbProcessRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, PcbProcessRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
