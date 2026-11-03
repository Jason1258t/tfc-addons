package ee.extras.pcb;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import java.util.List;
import java.util.Locale;
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
import net.neoforged.neoforge.items.ItemStackHandler;

/** Общий производственный цикл: поиск JSON, пауза без нужной мощности и атомарная выдача одной партии. */
public final class PcbMachineBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    public final ItemStackHandler items = new ItemStackHandler(4) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot >= 0 && slot < kind().inputSlots();
        }

        @Override
        protected void onContentsChanged(int slot) {
            if (slot < 3) {
                progress = 0;
                recipe = null;
                recipeId = null;
                nextRecipeScan = 0;
            }
            setChanged();
        }
    };

    private PcbMachineDevice device;
    private RecipeManager recipeManager;
    private RecipeHolder<PcbProcessRecipe> recipe;
    private ResourceLocation recipeId;
    private long nextRecipeScan;
    private PcbMachineStatus status = PcbMachineStatus.IDLE;
    public int progress;
    public int batches;

    public PcbMachineBlockEntity(BlockPos pos, BlockState state) {
        super(PcbItems.PCB_MACHINE_BE.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    public PcbMachineKind kind() {
        return ((PcbMachineBlock) getBlockState().getBlock()).kind();
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level instanceof ServerLevel serverLevel)) return;
        PcbMachineStatus previousStatus = status;
        device();
        selectRecipe(serverLevel.getRecipeManager());
        if (recipe == null) {
            setDeviceLoad(0, 1, false);
            progress = 0;
            status = invalidDesign() ? PcbMachineStatus.INVALID_DESIGN : PcbMachineStatus.IDLE;
            syncStatus(previousStatus);
            return;
        }

        PcbProcessRecipe value = recipe.value();
        ItemStack result = value.result(topologySource());
        if (!canOutput(result)) {
            setDeviceLoad(0, 1, false);
            status = PcbMachineStatus.BLOCKED_OUTPUT;
            syncStatus(previousStatus);
            return;
        }

        setDeviceLoad(value.voltage(), value.current(), true);
        if (device == null || !device.hasWorkingPower()) {
            status = PcbMachineStatus.NO_POWER;
            setChanged();
            syncStatus(previousStatus);
            return;
        }

        status = PcbMachineStatus.RUNNING;
        progress++;
        if (progress < value.getProcessingDuration()) {
            setChanged();
            syncStatus(previousStatus);
            return;
        }

        // Все проверки и расход выполняются в одном серверном тике, поэтому неполная партия невозможна.
        if (!matches(value) || !canOutput(result)) {
            progress = 0;
            status = PcbMachineStatus.BLOCKED_OUTPUT;
            syncStatus(previousStatus);
            return;
        }
        ResourceLocation completedRecipe = recipeId;
        items.extractItem(0, 1, false);
        if (kind() == PcbMachineKind.METALLIZATION) items.extractItem(1, 1, false);
        if (kind() == PcbMachineKind.PHOTOLITHOGRAPHY) items.extractItem(2, 1, false);
        output(result);
        recipeId = completedRecipe;
        progress = 0;
        batches++;
        status = PcbMachineStatus.IDLE;
        setChanged();
        syncStatus(previousStatus);
    }

    private void setDeviceLoad(double voltage, double current, boolean active) {
        if (device != null) device.setLoad(voltage, current, active);
    }

    public PcbMachineDevice device() {
        if ((device == null || !device.isValid()) && level instanceof ServerLevel serverLevel)
            device = DevicesSavedData.load(serverLevel).getDevice(worldPosition, PcbMachineDevice.class);
        return device;
    }

    private void selectRecipe(RecipeManager manager) {
        if (recipeManager != manager) {
            recipeManager = manager;
            recipe = null;
            nextRecipeScan = 0;
        }
        if (recipe != null || level == null || level.getGameTime() < nextRecipeScan) return;
        nextRecipeScan = level.getGameTime() + 20;
        for (RecipeHolder<PcbProcessRecipe> candidate : manager.getAllRecipesFor(PcbProcessRecipes.PCB_PROCESS_TYPE.get())) {
            if (recipeId != null && !candidate.id().equals(recipeId)) continue;
            if (matches(candidate.value())) {
                if (recipeId != null && !recipeId.equals(candidate.id())) progress = 0;
                recipe = candidate;
                recipeId = candidate.id();
                return;
            }
        }
    }

    private boolean matches(PcbProcessRecipe value) {
        if (!value.machine().equals(kind().id()) || value.kind().inputSlots() > items.getSlots()) return false;
        for (int slot = 0; slot < value.kind().inputSlots(); slot++) {
            if (!value.ingredient(slot).test(items.getStackInSlot(slot))) return false;
        }
        if (kind() == PcbMachineKind.METALLIZATION) {
            CompoundTag topology = items.getStackInSlot(0).get(PcbItems.PCB_TOPOLOGY.get());
            return topology != null && PcbTopology.level(topology) == value.level() && PcbTopology.validate(topology) == null;
        }
        return validDesign(items.getStackInSlot(1), value.level());
    }

    private static boolean validDesign(ItemStack stack, int level) {
        if (!stack.is(PcbItems.PCB_DESIGN.get())) return false;
        CompoundTag topology = stack.get(PcbItems.PCB_TOPOLOGY.get());
        return topology != null && topology.getBoolean("Validated")
            && PcbTopology.level(topology) == level && PcbTopology.validate(topology) == null;
    }

    private boolean invalidDesign() {
        if (kind() == PcbMachineKind.PHOTOLITHOGRAPHY)
            return !items.getStackInSlot(1).isEmpty() && !validDesign(items.getStackInSlot(1), 2)
                && !validDesign(items.getStackInSlot(1), 3);
        CompoundTag topology = items.getStackInSlot(0).get(PcbItems.PCB_TOPOLOGY.get());
        return topology != null && PcbTopology.validate(topology) != null;
    }

    private ItemStack topologySource() {
        return items.getStackInSlot(kind() == PcbMachineKind.PHOTOLITHOGRAPHY ? 1 : 0);
    }

    private boolean canOutput(ItemStack result) {
        if (result.isEmpty()) return false;
        ItemStack existing = items.getStackInSlot(3);
        return existing.isEmpty() || ItemStack.isSameItemSameComponents(existing, result)
            && existing.getCount() + result.getCount() <= result.getMaxStackSize();
    }

    private void output(ItemStack result) {
        ItemStack existing = items.getStackInSlot(3);
        if (existing.isEmpty()) items.setStackInSlot(3, result);
        else existing.grow(result.getCount());
    }

    private void syncStatus(PcbMachineStatus previousStatus) {
        if (status != previousStatus && level != null)
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public PcbMachineStatus status() {
        return status;
    }

    public ResourceLocation recipeId() {
        return recipeId;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.translatable("goggles.ee_extras.pcb_machine.status", status.text()));
        if (recipe != null) {
            PcbProcessRecipe value = recipe.value();
            tooltip.add(Component.translatable("goggles.ee_extras.pcb_machine.progress",
                progress, value.getProcessingDuration(), value.level()));
            tooltip.add(Component.translatable("goggles.ee_extras.pcb_machine.required_power",
                String.format(Locale.ROOT, "%.0f", value.voltage()), String.format(Locale.ROOT, "%.1f", value.current())));
        }
        if (device != null) tooltip.add(Component.translatable("goggles.ee_extras.pcb_machine.measured_power",
            String.format(Locale.ROOT, "%.1f", device.measuredVoltage()),
            String.format(Locale.ROOT, "%.2f", device.measuredCurrent())));
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
        tag.put("Items", items.serializeNBT(registries));
        tag.putInt("Progress", progress);
        tag.putInt("Batches", batches);
        tag.putString("Status", status.id());
        if (recipeId != null) tag.putString("Recipe", recipeId.toString());
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        items.deserializeNBT(registries, tag.getCompound("Items"));
        progress = tag.getInt("Progress");
        batches = tag.getInt("Batches");
        recipeId = tag.contains("Recipe") ? ResourceLocation.tryParse(tag.getString("Recipe")) : null;
        recipe = null;
        recipeManager = null;
        status = PcbMachineStatus.IDLE;
    }
}
