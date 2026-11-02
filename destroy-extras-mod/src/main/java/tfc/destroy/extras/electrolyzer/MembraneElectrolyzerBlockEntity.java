package tfc.destroy.extras.electrolyzer;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
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
public final class MembraneElectrolyzerBlockEntity extends BlockEntity implements IHaveGoggleInformation {
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
    private boolean formed;
    private float pumpRpm;
    private long nextStructureCheck;
    private long nextRecipeScan;
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
        ElectrolyzerStatus previousStatus = status;
        refreshStructure(serverLevel);
        if (device == null || !device.isValid()) {
            device = DevicesSavedData.load(serverLevel).getDevice(worldPosition, ProcessPowerDevice.class);
        }
        if (device == null) {
            status = ElectrolyzerStatus.FAULT;
            syncStatus(previousStatus);
            return;
        }
        if (!formed) {
            device.setActive(false);
            status = ElectrolyzerStatus.FAULT;
            syncStatus(previousStatus);
            return;
        }
        selectRecipe(serverLevel.getRecipeManager());
        if (recipe == null) {
            device.setActive(false);
            status = mixture.isEmpty() ? ElectrolyzerStatus.IDLE : ElectrolyzerStatus.FAULT;
            syncStatus(previousStatus);
            return;
        }

        adaptInput();
        MembraneElectrolysisRecipe value = recipe.value();
        FluidStack consumed = mixture.getFluid().copyWithAmount(value.input().amount());
        FluidStack[] products = value.products();
        boolean hasBatch = mixture.getFluidAmount() >= value.input().amount();
        boolean ready = hasBatch && AtomicFluidOperation.canExecute(mixture, consumed, outputs, products);
        boolean hasPump = pumpRpm >= ElectrolyzerStructure.MINIMUM_PUMP_RPM;
        device.setActive(ready && hasUsableMembrane() && hasPump);

        if (!hasUsableMembrane() || !hasPump) {
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
        if (!ready || !hasUsableMembrane() || !hasPump || !device.hasWorkingPower()) {
            syncStatus(previousStatus);
            return;
        }

        progress++;
        if (progress < value.getProcessingDuration()) {
            setChanged();
            syncStatus(previousStatus);
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
        syncStatus(previousStatus);
    }

    private void refreshStructure(ServerLevel level) {
        if (level.getGameTime() < nextStructureCheck) return;
        nextStructureCheck = level.getGameTime() + 20;
        formed = ElectrolyzerStructure.hasShell(level, worldPosition);
        pumpRpm = formed ? ElectrolyzerStructure.pumpRpm(level, worldPosition) : 0.0F;
    }

    private void syncStatus(ElectrolyzerStatus previousStatus) {
        if (status != previousStatus && level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    private void selectRecipe(RecipeManager manager) {
        if (recipeManager != manager) {
            recipeManager = manager;
            recipe = null;
            nextRecipeScan = 0;
        }
        if (recipe != null || level == null || level.getGameTime() < nextRecipeScan) return;
        nextRecipeScan = level.getGameTime() + 20;
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

    public boolean formed() {
        return formed;
    }

    public float pumpRpm() {
        return pumpRpm;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.translatable("goggles.destroy_extras.electrolyzer.status", status.text()));
        tooltip.add(Component.translatable("goggles.destroy_extras.electrolyzer.structure",
            Component.translatable(formed ? "goggles.destroy_extras.formed" : "goggles.destroy_extras.not_formed")));
        tooltip.add(Component.translatable("goggles.destroy_extras.electrolyzer.pump", String.format(java.util.Locale.ROOT, "%.0f", pumpRpm)));
        tooltip.add(Component.translatable("goggles.destroy_extras.electrolyzer.membrane", remainingMembraneOperations()));
        return true;
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        CompoundTag data = saveCustomOnly(level.registryAccess());
        BlockEntity.addEntityType(data, getType());
        builder.set(DataComponents.BLOCK_ENTITY_DATA, net.minecraft.world.item.component.CustomData.of(data));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
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
        tag.putBoolean("Formed", formed);
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
        formed = tag.getBoolean("Formed");
        nextStructureCheck = 0;
        nextRecipeScan = 0;
        recipeId = tag.contains("Recipe") ? ResourceLocation.tryParse(tag.getString("Recipe")) : null;
        recipe = null;
        recipeManager = null;
        device = null;
        status = ElectrolyzerStatus.IDLE;
    }
}
