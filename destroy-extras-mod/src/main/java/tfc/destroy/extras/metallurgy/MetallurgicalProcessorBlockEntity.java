package tfc.destroy.extras.metallurgy;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.neoforged.neoforge.items.ItemStackHandler;
import tfc.destroy.bridge.electric.ProcessPowerDevice;
import tfc.destroy.extras.DestroyExtras;
import tfc.destroy.extras.recipe.ExtrasRecipeTypes;
import tfc.destroy.extras.recipe.MetallurgyRecipe;

/** Атомарный универсальный контур: обжиг, выщелачивание, очистка и электролиз задаются JSON. */
public final class MetallurgicalProcessorBlockEntity extends BlockEntity implements IHaveGoggleInformation {
    public final FluidTank input = tank();
    public final FluidTank reagent = tank();
    public final FluidTank output = tank();
    public final FluidTank secondaryOutput = tank();
    public final ItemStackHandler items = new ItemStackHandler(5) {
        @Override public boolean isItemValid(int slot, ItemStack stack) { return slot < 3; }
        @Override protected void onContentsChanged(int slot) {
            if (slot < 3) { progress = 0; recipe = null; recipeId = null; nextRecipeScan = 0; }
            setChanged();
        }
    };
    private ProcessPowerDevice device;
    private RecipeManager recipeManager;
    private RecipeHolder<MetallurgyRecipe> recipe;
    private ResourceLocation recipeId;
    private long nextRecipeScan;
    private MetallurgyStatus status = MetallurgyStatus.IDLE;
    public int progress;
    public int batches;

    public MetallurgicalProcessorBlockEntity(BlockPos pos, BlockState state) {
        super(DestroyExtras.METALLURGY_BE.get(), pos, state);
    }

    private FluidTank tank() {
        return new FluidTank(8000) {
            @Override protected void onContentsChanged() { setChanged(); }
        };
    }

    public void tick() {
        if (!(level instanceof ServerLevel server)) return;
        MetallurgyStatus previous = status;
        if (device == null || !device.isValid()) device = DevicesSavedData.load(server).getDevice(worldPosition, ProcessPowerDevice.class);
        if (device == null) { status = MetallurgyStatus.FAULT; sync(previous); return; }
        selectRecipe(server.getRecipeManager());
        if (recipe == null) {
            device.setActive(false);
            progress = 0;
            status = MetallurgyStatus.IDLE;
            sync(previous);
            return;
        }
        MetallurgyRecipe value = recipe.value();
        device.setRating(value.voltage(), value.current());
        boolean inputs = hasItems(value) && hasFluids(value);
        boolean outputs = inputs && canComplete(value);
        device.setActive(outputs);
        if (!inputs) {
            progress = 0;
            status = MetallurgyStatus.IDLE;
        } else if (!outputs) status = MetallurgyStatus.BLOCKED_OUTPUT;
        else if (!device.hasWorkingPower()) status = MetallurgyStatus.WAITING_FOR_POWER;
        else status = MetallurgyStatus.RUNNING;
        if (!outputs || !device.hasWorkingPower()) { sync(previous); return; }
        progress++;
        if (progress < value.getProcessingDuration()) { setChanged(); sync(previous); return; }
        if (!canComplete(value) || !completeFluids(value)) {
            status = MetallurgyStatus.BLOCKED_OUTPUT;
            sync(previous);
            return;
        }
        consumeItems(value);
        outputItems(value);
        damageCatalyst(value);
        progress = 0;
        batches++;
        status = MetallurgyStatus.IDLE;
        setChanged();
        sync(previous);
    }

    private boolean hasItems(MetallurgyRecipe value) {
        List<Ingredient> ingredients = value.getIngredients();
        for (int i = 0; i < ingredients.size(); i++) if (!ingredients.get(i).test(items.getStackInSlot(i))) return false;
        Item catalyst = value.catalystItem();
        return catalyst == null || items.getStackInSlot(2).is(catalyst);
    }

    private boolean hasFluids(MetallurgyRecipe value) {
        List<SizedFluidIngredient> ingredients = value.getFluidIngredients();
        FluidTank[] tanks = {input, reagent};
        for (int i = 0; i < ingredients.size(); i++) {
            SizedFluidIngredient ingredient = ingredients.get(i);
            if (tanks[i].getFluidAmount() < ingredient.amount() || !ingredient.test(tanks[i].getFluid())) return false;
        }
        return true;
    }

    private boolean canComplete(MetallurgyRecipe value) {
        if (!hasItems(value) || !hasFluids(value)) return false;
        FluidTank[] inputs = {input, reagent};
        List<SizedFluidIngredient> ingredients = value.getFluidIngredients();
        for (int i = 0; i < ingredients.size(); i++) {
            if (inputs[i].drain(ingredients.get(i).amount(), FluidTank.FluidAction.SIMULATE).getAmount() != ingredients.get(i).amount()) return false;
        }
        FluidTank[] outputs = {output, secondaryOutput};
        List<FluidStack> results = value.getFluidResults();
        for (int i = 0; i < results.size(); i++) if (outputs[i].fill(results.get(i).copy(), FluidTank.FluidAction.SIMULATE) != results.get(i).getAmount()) return false;
        List<ProcessingOutput> itemResults = value.getRollableResults();
        for (int i = 0; i < itemResults.size(); i++) if (!canInsert(3 + i, itemResults.get(i).getStack())) return false;
        return true;
    }

    private boolean canInsert(int slot, ItemStack stack) {
        ItemStack existing = items.getStackInSlot(slot);
        return existing.isEmpty() || ItemStack.isSameItemSameComponents(existing, stack)
            && existing.getCount() + stack.getCount() <= stack.getMaxStackSize();
    }

    private boolean completeFluids(MetallurgyRecipe value) {
        FluidTank[] inputs = {input, reagent};
        List<SizedFluidIngredient> ingredients = value.getFluidIngredients();
        for (int i = 0; i < ingredients.size(); i++) inputs[i].drain(ingredients.get(i).amount(), FluidTank.FluidAction.EXECUTE);
        FluidTank[] outputs = {output, secondaryOutput};
        List<FluidStack> results = value.getFluidResults();
        for (int i = 0; i < results.size(); i++) outputs[i].fill(results.get(i).copy(), FluidTank.FluidAction.EXECUTE);
        return true;
    }

    private void consumeItems(MetallurgyRecipe value) {
        for (int i = 0; i < value.getIngredients().size(); i++) items.extractItem(i, 1, false);
    }

    private void outputItems(MetallurgyRecipe value) {
        List<ProcessingOutput> results = value.getRollableResults();
        for (int i = 0; i < results.size(); i++) {
            ItemStack stack = results.get(i).getStack().copy();
            ItemStack existing = items.getStackInSlot(3 + i);
            if (existing.isEmpty()) items.setStackInSlot(3 + i, stack);
            else { existing.grow(stack.getCount()); items.setStackInSlot(3 + i, existing); }
        }
    }

    private void damageCatalyst(MetallurgyRecipe value) {
        ItemStack stack = items.getStackInSlot(2);
        if (value.catalystItem() != null && stack.isDamageableItem()) {
            stack.setDamageValue(Math.min(stack.getMaxDamage(), stack.getDamageValue() + 1));
            items.setStackInSlot(2, stack);
        }
    }

    private void selectRecipe(RecipeManager manager) {
        if (recipeManager != manager) { recipeManager = manager; recipe = null; nextRecipeScan = 0; }
        if (recipe != null || level == null || level.getGameTime() < nextRecipeScan) return;
        nextRecipeScan = level.getGameTime() + 20;
        RecipeHolder<MetallurgyRecipe> best = null;
        for (RecipeHolder<MetallurgyRecipe> candidate : manager.getAllRecipesFor(ExtrasRecipeTypes.METALLURGY_TYPE.get())) {
            if (recipeId != null && !candidate.id().equals(recipeId)) continue;
            if (!hasItems(candidate.value()) || !hasFluids(candidate.value())) continue;
            if (best == null || candidate.value().priority() > best.value().priority()) best = candidate;
        }
        if (best != null) setRecipe(best);
    }

    private void setRecipe(RecipeHolder<MetallurgyRecipe> value) {
        if (recipeId != null && !recipeId.equals(value.id())) progress = 0;
        recipe = value;
        recipeId = value.id();
    }

    private void sync(MetallurgyStatus previous) {
        if (status != previous && level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public MetallurgyStatus status() { return status; }
    public ResourceLocation recipeId() { return recipeId; }
    public ProcessPowerDevice device() { return device; }

    @Override public boolean addToGoggleTooltip(List<Component> tooltip, boolean sneaking) {
        tooltip.add(Component.translatable("goggles.destroy_extras.metallurgy.status", status.text()));
        if (recipe != null) tooltip.add(Component.translatable("goggles.destroy_extras.metallurgy.power", recipe.value().voltage(), recipe.value().current()));
        return true;
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveCustomOnly(registries); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override protected void collectImplicitComponents(DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        CompoundTag data = saveCustomOnly(level.registryAccess());
        BlockEntity.addEntityType(data, getType());
        builder.set(DataComponents.BLOCK_ENTITY_DATA, net.minecraft.world.item.component.CustomData.of(data));
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Input", input.writeToNBT(registries, new CompoundTag()));
        tag.put("Reagent", reagent.writeToNBT(registries, new CompoundTag()));
        tag.put("Output", output.writeToNBT(registries, new CompoundTag()));
        tag.put("SecondaryOutput", secondaryOutput.writeToNBT(registries, new CompoundTag()));
        tag.put("Items", items.serializeNBT(registries));
        tag.putInt("Progress", progress);
        tag.putInt("Batches", batches);
        tag.putString("Status", status.id());
        if (recipeId != null) tag.putString("Recipe", recipeId.toString());
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        input.readFromNBT(registries, tag.getCompound("Input"));
        reagent.readFromNBT(registries, tag.getCompound("Reagent"));
        output.readFromNBT(registries, tag.getCompound("Output"));
        secondaryOutput.readFromNBT(registries, tag.getCompound("SecondaryOutput"));
        items.deserializeNBT(registries, tag.getCompound("Items"));
        progress = tag.getInt("Progress");
        batches = tag.getInt("Batches");
        recipeId = tag.contains("Recipe") ? ResourceLocation.tryParse(tag.getString("Recipe")) : null;
        recipe = null;
        recipeManager = null;
        device = null;
        status = MetallurgyStatus.IDLE;
    }
}
