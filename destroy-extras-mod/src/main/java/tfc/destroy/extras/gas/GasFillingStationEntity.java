package tfc.destroy.extras.gas;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import tfc.destroy.bridge.electric.ProcessPowerDevice;

final class GasFillingStationEntity extends BlockEntity {
    private static final int TRANSFER_LIMIT = 25;
    private ItemStack cylinder = ItemStack.EMPTY;
    private boolean discharge;
    private ProcessPowerDevice device;

    final IFluidHandler inlet = new IFluidHandler() {
        @Override public int getTanks() { return 1; }
        @Override public FluidStack getFluidInTank(int tank) {
            return tank == 0 && cylinder.getItem() instanceof GasCylinderItem item ? item.gas(cylinder) : FluidStack.EMPTY;
        }
        @Override public int getTankCapacity(int tank) {
            return tank == 0 && cylinder.getItem() instanceof GasCylinderItem item ? item.capacity() : 0;
        }
        @Override public boolean isFluidValid(int tank, FluidStack gas) {
            return tank == 0 && !discharge && !GasTankLogic.gasSpecies(gas).isEmpty();
        }
        @Override public int fill(FluidStack gas, FluidAction action) { return receive(gas, action); }
        @Override public FluidStack drain(FluidStack gas, FluidAction action) { return FluidStack.EMPTY; }
        @Override public FluidStack drain(int amount, FluidAction action) { return FluidStack.EMPTY; }
    };

    GasFillingStationEntity(BlockPos pos, BlockState state) {
        super(GasFillingStation.ENTITY.get(), pos, state);
    }

    Direction portSide() {
        return getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite();
    }

    void insert(Player player, ItemStack held) {
        if (cylinder.isEmpty()) {
            cylinder = held.split(1);
            setChanged();
        }
        showStatus(player);
    }

    ItemStack take() {
        ItemStack removed = cylinder;
        cylinder = ItemStack.EMPTY;
        setChanged();
        return removed;
    }

    void interact(Player player) {
        if (player.isShiftKeyDown()) {
            discharge = !discharge;
            setChanged();
        } else if (!cylinder.isEmpty() && player.getInventory().add(cylinder.copy())) {
            take();
        }
        showStatus(player);
    }

    private void showStatus(Player player) {
        Component mode = Component.translatable(discharge ? "message.destroy_extras.gas_station.discharge"
            : "message.destroy_extras.gas_station.charge");
        Component vessel = cylinder.getItem() instanceof GasCylinderItem item ? item.status(cylinder)
            : Component.translatable("message.destroy_extras.gas_station.no_cylinder");
        Component power = Component.translatable(powered() ? "message.destroy_extras.gas_station.powered"
            : "message.destroy_extras.gas_station.no_power");
        player.displayClientMessage(Component.translatable("message.destroy_extras.gas_station.status", mode, vessel, power), true);
    }

    private boolean powered() {
        return device != null && device.isValid() && device.hasWorkingPower();
    }

    private int receive(FluidStack offered, IFluidHandler.FluidAction action) {
        if (discharge || !powered() || !(cylinder.getItem() instanceof GasCylinderItem item)) return 0;
        FluidStack incoming = GasTankLogic.canonicalGas(offered);
        FluidStack stored = item.gas(cylinder);
        if (incoming.isEmpty() || !stored.isEmpty() && !FluidStack.isSameFluidSameComponents(incoming, stored)) return 0;
        int accepted = Math.min(TRANSFER_LIMIT, Math.min(incoming.getAmount(), item.capacity() - stored.getAmount()));
        if (accepted > 0 && action.execute()) {
            item.setGas(cylinder, incoming, stored.getAmount() + accepted);
            setChanged();
        }
        return accepted;
    }

    void tick() {
        if (!(level instanceof ServerLevel server)) return;
        if (device == null || !device.isValid()) {
            device = DevicesSavedData.load(server).getDevice(worldPosition, ProcessPowerDevice.class);
        }
        if (device == null) return;
        device.setRating(300, 2);
        if (!(cylinder.getItem() instanceof GasCylinderItem item)) {
            device.setActive(false);
            return;
        }
        FluidStack stored = item.gas(cylinder);
        Direction side = portSide();
        BlockPos neighbor = worldPosition.relative(side);
        IFluidHandler port = server.hasChunkAt(neighbor)
            ? server.getCapability(Capabilities.FluidHandler.BLOCK, neighbor, side.getOpposite()) : null;
        device.setActive(port != null && (discharge ? !stored.isEmpty() : stored.getAmount() < item.capacity()));
        if (port == null || !powered()) return;

        if (discharge) {
            if (stored.isEmpty()) return;
            int moved = port.fill(stored.copyWithAmount(Math.min(TRANSFER_LIMIT, stored.getAmount())), IFluidHandler.FluidAction.EXECUTE);
            if (moved > 0) {
                item.setGas(cylinder, stored, stored.getAmount() - moved);
                setChanged();
            }
        } else {
            FluidStack offered = port.drain(TRANSFER_LIMIT, IFluidHandler.FluidAction.SIMULATE);
            int wanted = receive(offered, IFluidHandler.FluidAction.SIMULATE);
            if (wanted > 0) receive(port.drain(offered.copyWithAmount(wanted), IFluidHandler.FluidAction.EXECUTE),
                IFluidHandler.FluidAction.EXECUTE);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!cylinder.isEmpty()) tag.put("cylinder", cylinder.save(registries));
        tag.putBoolean("discharge", discharge);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ItemStack stored = ItemStack.parseOptional(registries, tag.getCompound("cylinder"));
        cylinder = stored.getItem() instanceof GasCylinderItem ? stored.copyWithCount(1) : ItemStack.EMPTY;
        discharge = tag.getBoolean("discharge");
    }
}
