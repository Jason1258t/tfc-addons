package tfc.destroy.extras.electrolyzer;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemStackHandler;
import tfc.destroy.bridge.electric.ProcessPowerDevice;
import tfc.destroy.bridge.fluid.AtomicFluidOperation;
import tfc.destroy.bridge.fluid.SaltWaterAdapter;
import tfc.destroy.extras.DestroyExtras;
import tfc.destroy.extras.recipe.ExtrasRecipeTypes;
import tfc.destroy.extras.recipe.MembraneElectrolysisRecipe;

/** Production-контроллер: рецепт берётся из RecipeManager, все изменения выполняются на серверном потоке. */
public final class MembraneElectrolyzerBlockEntity extends BlockEntity {
    public static final int INPUT_CAPACITY = 8000;
    public static final int MIXTURE_CAPACITY = 2000;
    public static final int OUTPUT_CAPACITY = 4000;
    public static final int MEMBRANE_OPERATIONS = 64;

    public final FluidTank input = tank(INPUT_CAPACITY, SaltWaterAdapter::isSaltWater);
    public final FluidTank mixture = tank(MIXTURE_CAPACITY, stack -> true);
    public final FluidTank hydrogen = tank(OUTPUT_CAPACITY, stack -> true);
    public final FluidTank chlorine = tank(OUTPUT_CAPACITY, stack -> true);
    public final FluidTank sodiumHydroxide = tank(OUTPUT_CAPACITY, stack -> true);
    public final FluidTank[] outputs = {hydrogen, chlorine, sodiumHydroxide};
    public final ItemStackHandler membrane = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.is(DestroyExtras.MEMBRANE_ITEM.get()) && stack.getDamageValue() < stack.getMaxDamage();
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    private ProcessPowerDevice device;
    private RecipeManager recipeManager;
    private RecipeHolder<MembraneElectrolysisRecipe> recipe;
    private ResourceLocation recipeId;
    private ElectrolyzerStatus status = ElectrolyzerStatus.IDLE;
    public int progress;
    public int batches;

    public MembraneElectrolyzerBlockEntity(BlockPos pos, BlockState state) {
        super(DestroyExtras.ELECTROLYZER_BE.get(), pos, state);
    }

    private FluidTank tank(int capacity, Predicate<FluidStack> validator) {
        return new FluidTank(capacity, validator) {
            @Override
            protected void onContentsChanged() {
                setChanged();
            }
        };
    }

    public void tick() {
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (device == null || !device.isValid()) {
            device = DevicesSavedData.load(serverLevel).getDevice(worldPosition, ProcessPowerDevice.class);
        }
        selectRecipe(serverLevel.getRecipeManager());
        if (device == null) {
            status = ElectrolyzerStatus.FAULT;
            return;
        }
        if (recipe == null) {
            device.setActive(false);
            status = mixture.isEmpty() ? ElectrolyzerStatus.IDLE : ElectrolyzerStatus.FAULT;
            return;
        }

        adaptInput();
        MembraneElectrolysisRecipe value = recipe.value();
        FluidStack consumed = mixture.getFluid().copyWithAmount(value.input().amount());
        FluidStack[] products = value.products();
        boolean hasBatch = mixture.getFluidAmount() >= value.input().amount();
        boolean ready = hasBatch && AtomicFluidOperation.canExecute(mixture, consumed, outputs, products);
        device.setActive(ready && hasUsableMembrane());

        if (!hasUsableMembrane()) {
            status = ElectrolyzerStatus.FAULT;
        } else if (hasBatch && !ready) {
            status = ElectrolyzerStatus.BLOCKED_OUTPUT;
        } else if (!hasBatch) {
            status = ElectrolyzerStatus.IDLE;
        } else if (!device.hasWorkingPower()) {
            status = ElectrolyzerStatus.WAITING_FOR_POWER;
        } else {
            status = ElectrolyzerStatus.RUNNING;
        }
        if (!ready || !hasUsableMembrane() || !device.hasWorkingPower()) return;

        progress++;
        if (progress < value.getProcessingDuration()) {
            setChanged();
            return;
        }
        if (!AtomicFluidOperation.execute(mixture, consumed, outputs, products)) {
            throw new IllegalStateException("Проверенная операция электролиза не выполнилась");
        }
        ItemStack stack = membrane.getStackInSlot(0);
        stack.setDamageValue(Math.min(stack.getMaxDamage(), stack.getDamageValue() + 1));
        membrane.setStackInSlot(0, stack);
        progress = 0;
        batches++;
        status = ElectrolyzerStatus.IDLE;
        setChanged();
    }

    private void selectRecipe(RecipeManager manager) {
        if (recipeManager != manager) {
            recipeManager = manager;
            recipe = null;
        }
        if (recipe != null) return;
        for (RecipeHolder<MembraneElectrolysisRecipe> candidate : manager.getAllRecipesFor(ExtrasRecipeTypes.ELECTROLYSIS_TYPE.get())) {
            if (recipeId != null && !candidate.id().equals(recipeId)) continue;
            MembraneElectrolysisRecipe value = candidate.value();
            if (!mixture.isEmpty() && value.input().test(mixture.getFluid())) {
                setRecipe(candidate);
                return;
            }
            FluidStack adapted = adaptedInput(value);
            if (!adapted.isEmpty() && value.input().test(adapted)) {
                setRecipe(candidate);
                return;
            }
        }
    }

    private void setRecipe(RecipeHolder<MembraneElectrolysisRecipe> value) {
        if (recipeId != null && !recipeId.equals(value.id())) progress = 0;
        recipe = value;
        recipeId = value.id();
    }

    private FluidStack adaptedInput(MembraneElectrolysisRecipe value) {
        if (input.getFluidAmount() < value.input().amount()) return FluidStack.EMPTY;
        return SaltWaterAdapter.toDestroyMixture(input.getFluid().copyWithAmount(value.input().amount()),
            value.saltConcentration(), value.waterConcentration(), value.translationKey());
    }

    private void adaptInput() {
        if (recipe == null) return;
        MembraneElectrolysisRecipe value = recipe.value();
        FluidStack adapted = adaptedInput(value);
        if (adapted.isEmpty()) return;
        FluidStack consumed = input.getFluid().copyWithAmount(value.input().amount());
        AtomicFluidOperation.execute(input, consumed, new FluidTank[] {mixture}, new FluidStack[] {adapted});
    }

    public boolean hasUsableMembrane() {
        ItemStack stack = membrane.getStackInSlot(0);
        return stack.is(DestroyExtras.MEMBRANE_ITEM.get()) && stack.getDamageValue() < stack.getMaxDamage();
    }

    public int remainingMembraneOperations() {
        ItemStack stack = membrane.getStackInSlot(0);
        return hasUsableMembrane() ? stack.getMaxDamage() - stack.getDamageValue() : 0;
    }

    public ElectrolyzerStatus status() {
        return status;
    }

    public ProcessPowerDevice device() {
        return device;
    }

    public ResourceLocation recipeId() {
        return recipeId;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Input", input.writeToNBT(registries, new CompoundTag()));
        tag.put("Mixture", mixture.writeToNBT(registries, new CompoundTag()));
        tag.put("Hydrogen", hydrogen.writeToNBT(registries, new CompoundTag()));
        tag.put("Chlorine", chlorine.writeToNBT(registries, new CompoundTag()));
        tag.put("SodiumHydroxide", sodiumHydroxide.writeToNBT(registries, new CompoundTag()));
        tag.put("Membrane", membrane.serializeNBT(registries));
        tag.putInt("Progress", progress);
        tag.putInt("Batches", batches);
        tag.putString("Status", status.id());
        if (recipeId != null) tag.putString("Recipe", recipeId.toString());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        input.readFromNBT(registries, tag.getCompound("Input"));
        mixture.readFromNBT(registries, tag.getCompound("Mixture"));
        hydrogen.readFromNBT(registries, tag.getCompound("Hydrogen"));
        chlorine.readFromNBT(registries, tag.getCompound("Chlorine"));
        sodiumHydroxide.readFromNBT(registries, tag.getCompound("SodiumHydroxide"));
        membrane.deserializeNBT(registries, tag.getCompound("Membrane"));
        progress = tag.getInt("Progress");
        batches = tag.getInt("Batches");
        recipeId = tag.contains("Recipe") ? ResourceLocation.tryParse(tag.getString("Recipe")) : null;
        recipe = null;
        recipeManager = null;
        device = null;
        status = ElectrolyzerStatus.IDLE;
    }
}
