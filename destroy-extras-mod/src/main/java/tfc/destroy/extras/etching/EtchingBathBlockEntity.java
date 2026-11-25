package tfc.destroy.extras.etching;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.kinetics.mixer.MechanicalMixerBlockEntity;
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
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.minecraft.world.level.material.Fluids;
import petrolpark.mc.destroy.chemistry.legacy.LegacyMixture;
import petrolpark.mc.destroy.chemistry.legacy.index.DestroyMolecules;
import petrolpark.mc.destroy.chemistry.minecraft.MixtureFluid;
import tfc.destroy.bridge.fluid.AtomicFluidOperation;
import tfc.destroy.bridge.fluid.HydrochloricAcidAdapter;
import tfc.destroy.extras.DestroyExtras;
import tfc.destroy.extras.recipe.EtchingRecipe;
import tfc.destroy.extras.recipe.ExtrasRecipeTypes;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import petrolpark.mc.destroy.core.fluid.GeniusFluidTankBehaviour.GeniusFluidTank;

/** Травление без EE/FE: длительность и расход задаёт JSON, завершение атомарно для предметов и жидкости. */
public final class EtchingBathBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    public static final int INPUT_CAPACITY = 4000;
    public static final int SPENT_CAPACITY = 4000;
    public static final float MINIMUM_MIXER_RPM = 16.0F;

    public final FluidTank input = tank(INPUT_CAPACITY, stack -> true, true);
    public final FluidTank spent = tank(SPENT_CAPACITY, stack -> true, false);
    public final FluidTank hydrochloricAcid = tank(INPUT_CAPACITY, EtchingChemistry::isPureAcidGas, false);
    public final FluidTank water = tank(INPUT_CAPACITY,
        stack -> stack.getFluid() == Fluids.WATER, false);
    public static final int[] BOARD_SLOTS = {0, 3, 4, 5};
    private int activeSlot;
    public final ItemStackHandler items = new ItemStackHandler(6) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (level == null || slot == 2) return false;
            for (RecipeHolder<EtchingRecipe> holder : level.getRecipeManager().getAllRecipesFor(ExtrasRecipeTypes.ETCHING_TYPE.get())) {
                if ((slot == 1 ? holder.value().mask() : holder.value().blank()).test(stack)) return true;
            }
            return false;
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot == 2 ? 64 : 1;
        }

        @Override
        protected void onContentsChanged(int slot) {
            if (slot != 2) {
                progress = 0;
                recipe = null;
                recipeId = null;
                nextRecipeScan = 0;
            }
            changed();
        }
    };

    public final IItemHandler automation = new IItemHandler() {
        public int getSlots() {
            return items.getSlots();
        }
        public ItemStack getStackInSlot(int slot) {
            return items.getStackInSlot(slot);
        }
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return items.insertItem(slot, stack, simulate);
        }
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot == 1 || items.isItemValid(slot, items.getStackInSlot(slot)) ? ItemStack.EMPTY
                : items.extractItem(slot, amount, simulate);
        }
        public int getSlotLimit(int slot) {
            return items.getSlotLimit(slot);
        }
        public boolean isItemValid(int slot, ItemStack stack) {
            return items.isItemValid(slot, stack);
        }
    };

    /** Все стороны принимают раствор; откачка начинает с отдельного бака отработки. Старые баки доступны для возврата. */
    public final IFluidHandler fluids = new IFluidHandler() {
        private FluidTank tankAt(int index) { return switch (index) { case 0 -> spent; case 1 -> input; case 2 -> hydrochloricAcid; default -> water; }; }
        public int getTanks() {
            return 4;
        }
        public FluidStack getFluidInTank(int tank) {
            return tankAt(tank).getFluid();
        }
        public int getTankCapacity(int tank) {
            return tankAt(tank).getCapacity();
        }
        public boolean isFluidValid(int tank, FluidStack stack) {
            return tank == 1 && input.isFluidValid(stack);
        }
        public int fill(FluidStack stack, FluidAction action) {
            return input.fill(stack, action);
        }
        public FluidStack drain(FluidStack stack, FluidAction action) {
            for (int i = 0; i < getTanks(); i++) {
                FluidStack result = tankAt(i).drain(stack, action);
                if (!result.isEmpty()) return result;
            }
            return FluidStack.EMPTY;
        }
        public FluidStack drain(int amount, FluidAction action) {
            for (int i = 0; i < getTanks(); i++) {
                FluidStack result = tankAt(i).drain(amount, action);
                if (!result.isEmpty()) return result;
            }
            return FluidStack.EMPTY;
        }
    };

    private RecipeManager recipeManager;
    private RecipeHolder<EtchingRecipe> recipe;
    private ResourceLocation recipeId;
    private EtchingStatus status = EtchingStatus.IDLE;
    private long nextRecipeScan;
    private float mixerRpm;
    public int progress;
    public int batches;

    public EtchingBathBlockEntity(BlockPos pos, BlockState state) {
        super(DestroyExtras.ETCHING_BATH_BE.get(), pos, state);
    }

    private FluidTank tank(int capacity, Predicate<FluidStack> validator, boolean affectsRecipe) {
        return new GeniusFluidTank(capacity, stack -> {}) {
            @Override
            public boolean isFluidValid(FluidStack stack) {
                return validator.test(stack);
            }

            @Override
            public int fill(FluidStack stack, FluidAction action) {
                return isFluidValid(stack) ? super.fill(stack, action) : 0;
            }
            @Override
            protected void onContentsChanged() {
                if (affectsRecipe) {
                    recipe = null;
                    nextRecipeScan = 0;
                }
                changed();
            }
        };
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) sendData();
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level instanceof ServerLevel serverLevel)) return;
        EtchingStatus previousStatus = status;
        mixerRpm = serverLevel.getBlockEntity(worldPosition.above()) instanceof MechanicalMixerBlockEntity mixer
            ? Math.abs(mixer.getSpeed()) : 0.0F;
        absorbAcid();
        selectRecipe(serverLevel.getRecipeManager());
        if (recipe == null) {
            progress = 0;
            status = EtchingStatus.IDLE;
            syncStatus(previousStatus);
            return;
        }

        EtchingRecipe value = recipe.value();
        ItemStack blank = items.getStackInSlot(activeSlot);
        ItemStack mask = items.getStackInSlot(1);
        int amount = value.input().amount();
        FluidStack consumed = input.getFluid().copyWithAmount(amount);
        FluidStack spentResult = EtchingChemistry.spentEtchant(consumed, amount, value.copperMoles());
        boolean hasItems = value.validItems(blank, mask);
        boolean hasFluid = value.input().test(consumed) && !spentResult.isEmpty();
        ItemStack result = hasItems ? value.result(mask) : ItemStack.EMPTY;
        boolean hasOutputSpace = canOutput(result);
        boolean ready = hasItems && hasFluid && hasOutputSpace
            && AtomicFluidOperation.canExecute(input, consumed, new FluidTank[] {spent}, new FluidStack[] {spentResult});

        if (!hasItems || !hasFluid) {
            if (!hasItems || input.getFluidAmount() < amount) progress = 0;
            status = EtchingStatus.IDLE;
            syncStatus(previousStatus);
            return;
        }
        if (!ready) {
            status = EtchingStatus.BLOCKED_OUTPUT;
            syncStatus(previousStatus);
            return;
        }

        status = EtchingStatus.RUNNING;
        progress += mixerRpm >= MINIMUM_MIXER_RPM ? 2 : 1;
        if (progress < value.getProcessingDuration()) {
            setChanged();
            syncStatus(previousStatus);
            return;
        }
        if (!value.validItems(blank, mask) || !canOutput(result)
            || !AtomicFluidOperation.execute(input, consumed, new FluidTank[] {spent}, new FluidStack[] {spentResult})) {
            status = EtchingStatus.BLOCKED_OUTPUT;
            syncStatus(previousStatus);
            return;
        }
        ResourceLocation completedRecipe = recipeId;
        items.extractItem(activeSlot, 1, false);
        if (value.industrialLevel() == 0) items.extractItem(1, 1, false);
        if (items.getStackInSlot(activeSlot).isEmpty()) items.setStackInSlot(activeSlot, result);
        else output(result);
        recipeId = completedRecipe;
        progress = 0;
        batches++;
        status = EtchingStatus.IDLE;
        setChanged();
        syncStatus(previousStatus);
    }

    private void syncStatus(EtchingStatus previousStatus) {
        if (status != previousStatus && level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    private boolean canOutput(ItemStack result) {
        if (result.isEmpty()) return false;
        if (items.getStackInSlot(activeSlot).getCount() == 1) return true;
        ItemStack existing = items.getStackInSlot(2);
        return existing.isEmpty() || ItemStack.isSameItemSameComponents(existing, result)
            && existing.getCount() + result.getCount() <= result.getMaxStackSize();
    }

    private void output(ItemStack result) {
        ItemStack existing = items.getStackInSlot(2);
        if (existing.isEmpty()) {
            items.setStackInSlot(2, result);
        } else {
            existing.grow(result.getCount());
            setChanged();
        }
    }

    /** Survival-граница M3: 25 мБ чистого HCl + 250 мБ воды атомарно дают 275 мБ кислоты. */
    private void absorbAcid() {
        if (hydrochloricAcid.getFluidAmount() < 25 || water.getFluidAmount() < 250) return;
        FluidStack waterStack = water.getFluid().copyWithAmount(250);
        if (waterStack.getFluid() == Fluids.WATER) {
            waterStack = MixtureFluid.of(250, new LegacyMixture().addMolecule(DestroyMolecules.WATER,
                DestroyMolecules.WATER.getPureConcentration()));
        }
        FluidStack absorbed = HydrochloricAcidAdapter.absorb(
            hydrochloricAcid.getFluid().copyWithAmount(25), waterStack);
        if (absorbed.isEmpty() || input.fill(absorbed, FluidTank.FluidAction.SIMULATE) != absorbed.getAmount()) return;
        FluidStack acidDrain = hydrochloricAcid.drain(25, FluidTank.FluidAction.SIMULATE);
        FluidStack waterDrain = water.drain(250, FluidTank.FluidAction.SIMULATE);
        if (acidDrain.getAmount() != 25 || waterDrain.getAmount() != 250) return;
        hydrochloricAcid.drain(25, FluidTank.FluidAction.EXECUTE);
        water.drain(250, FluidTank.FluidAction.EXECUTE);
        input.fill(absorbed, FluidTank.FluidAction.EXECUTE);
        setChanged();
    }

    private void selectRecipe(RecipeManager manager) {
        if (recipeManager != manager) {
            recipeManager = manager;
            recipe = null;
            nextRecipeScan = 0;
        }
        if (recipe != null || level == null || level.getGameTime() < nextRecipeScan) return;
        nextRecipeScan = level.getGameTime() + 20;
        for (int slot : BOARD_SLOTS) {
            for (RecipeHolder<EtchingRecipe> candidate : manager.getAllRecipesFor(ExtrasRecipeTypes.ETCHING_TYPE.get())) {
                EtchingRecipe value = candidate.value();
                if (value.validItems(items.getStackInSlot(slot), items.getStackInSlot(1))
                    && value.input().test(input.getFluid())
                    && EtchingChemistry.canEtch(input.getFluid().copyWithAmount(value.input().amount()),
                        value.input().amount(), value.copperMoles()) && input.getFluidAmount() >= value.input().amount()) {
                    if (activeSlot != slot) progress = 0;
                    activeSlot = slot;
                    setRecipe(candidate);
                    return;
                }
            }
        }
    }

    private void setRecipe(RecipeHolder<EtchingRecipe> value) {
        if (recipeId != null && !recipeId.equals(value.id())) progress = 0;
        recipe = value;
        recipeId = value.id();
    }

    public EtchingStatus status() {
        return status;
    }

    public ResourceLocation recipeId() {
        return recipeId;
    }

    public float mixerRpm() {
        return mixerRpm;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.translatable("goggles.destroy_extras.etching.status", status.text()));
        tooltip.add(Component.translatable("goggles.destroy_extras.etching.mixer",
            String.format(java.util.Locale.ROOT, "%.0f", mixerRpm)));
        tooltip.add(Component.translatable("goggles.destroy_extras.etching.input", input.getFluidAmount(), INPUT_CAPACITY));
        tooltip.add(Component.translatable("goggles.destroy_extras.etching.spent", spent.getFluidAmount(), SPENT_CAPACITY));
        int count = 0;
        for (int slot : BOARD_SLOTS) if (!items.getStackInSlot(slot).isEmpty()) count++;
        tooltip.add(Component.translatable("goggles.destroy_extras.etching.boards", count, items.getStackInSlot(1).getHoverName()));
        return true;
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
    protected void collectImplicitComponents(DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        CompoundTag data = saveCustomOnly(level.registryAccess());
        BlockEntity.addEntityType(data, getType());
        builder.set(DataComponents.BLOCK_ENTITY_DATA, net.minecraft.world.item.component.CustomData.of(data));
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.put("Input", input.writeToNBT(registries, new CompoundTag()));
        tag.put("Spent", spent.writeToNBT(registries, new CompoundTag()));
        tag.put("HydrochloricAcid", hydrochloricAcid.writeToNBT(registries, new CompoundTag()));
        tag.put("Water", water.writeToNBT(registries, new CompoundTag()));
        tag.put("Items", items.serializeNBT(registries));
        tag.putInt("ActiveSlot", activeSlot);
        tag.putInt("Progress", progress);
        tag.putInt("Batches", batches);
        tag.putString("Status", status.id());
        if (recipeId != null) tag.putString("Recipe", recipeId.toString());
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        input.readFromNBT(registries, tag.getCompound("Input"));
        spent.readFromNBT(registries, tag.getCompound("Spent"));
        hydrochloricAcid.readFromNBT(registries, tag.getCompound("HydrochloricAcid"));
        water.readFromNBT(registries, tag.getCompound("Water"));
        CompoundTag inventory = tag.getCompound("Items").copy();
        inventory.putInt("Size", 6);
        items.deserializeNBT(registries, inventory);
        progress = tag.getInt("Progress");
        batches = tag.getInt("Batches");
        recipeId = tag.contains("Recipe") ? ResourceLocation.tryParse(tag.getString("Recipe")) : null;
        recipe = null;
        recipeManager = null;
        activeSlot = tag.getInt("ActiveSlot");
        if (activeSlot < 0 || activeSlot >= items.getSlots() || activeSlot == 1 || activeSlot == 2) activeSlot = 0;
        status = EtchingStatus.IDLE;
        if (clientPacket) {
            for (EtchingStatus candidate : EtchingStatus.values()) if (candidate.id().equals(tag.getString("Status"))) status = candidate;
        }
    }
}
