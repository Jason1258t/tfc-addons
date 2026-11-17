package tfc.destroy.extras.gas;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

final class GasCylinderEntity extends BlockEntity {
    private final GasCylinderItem item;
    private final GasTankLogic.State storage;
    private boolean discharge;

    GasCylinderEntity(BlockPos pos, BlockState state) {
        super(GasFillingStation.CYLINDER_ENTITY.get(), pos, state);
        item = (GasCylinderItem) state.getBlock().asItem();
        storage = new GasTankLogic.State(item.capacity(), this::setChanged);
    }

    IFluidHandler port() {
        return discharge ? storage.out : storage.in;
    }

    void toggleMode() {
        discharge = !discharge;
        setChanged();
        if (level != null) level.invalidateCapabilities(worldPosition);
    }

    Component status() {
        var mode = Component.translatable(discharge ? "message.destroy_extras.gas_station.discharge"
            : "message.destroy_extras.gas_station.charge");
        return Component.translatable("message.destroy_extras.gas_cylinder.status", mode, item.status(storage.tank.getFluid()));
    }

    void tick() {
        if (discharge && level != null && !storage.tank.isEmpty() && level.getGameTime() % 10 == 0) {
            GasPipeBlock.pushIntoPipe(level, worldPosition, Direction.UP, storage.out);
        }
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        storage.tank.setFluid(item.gas(input.get(GasFillingStation.GAS.get())));
        setChanged();
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        FluidStack gas = storage.tank.getFluid();
        if (!gas.isEmpty()) {
            builder.set(GasFillingStation.GAS.get(), GasCylinderItem.Contents.of(gas));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        storage.writeSaveNBT(tag, registries);
        tag.putBoolean("discharge", discharge);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        storage.readSaveNBT(tag, registries);
        discharge = tag.getBoolean("discharge");
    }
}
