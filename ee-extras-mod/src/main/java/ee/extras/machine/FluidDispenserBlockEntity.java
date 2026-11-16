package ee.extras.machine;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import ee.extras.EeExtras;
import java.util.List;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public final class FluidDispenserBlockEntity extends SmartBlockEntity {
    private static final int CAPACITY = 1000;
    private final FluidTank tank = new FluidTank(CAPACITY) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    private ScrollValueBehaviour dose;
    private FilteringBehaviour filter;
    private boolean wasPowered;

    public FluidDispenserBlockEntity(BlockPos pos, BlockState state) {
        super(EeExtras.FLUID_DISPENSER_BE.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        dose = new ScrollValueBehaviour(Component.translatable("ee_extras.fluid_dispenser.dose"), this,
                new ControlSlot(5)).between(1, CAPACITY);
        dose.value = 250;
        dose.withFormatter(value -> Component.translatable("ee_extras.fluid_dispenser.amount", value).getString());
        filter = new FilteringBehaviour(this, new ControlSlot(11)).forFluids();
        behaviours.add(dose);
        behaviours.add(filter);
    }

    public IFluidHandler input(Direction side) {
        return side == Direction.UP || side == null ? tank : null;
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;
        boolean powered = level.hasNeighborSignal(worldPosition);
        if (powered == wasPowered) return;
        wasPowered = powered;
        setChanged();
        if (powered) deliverDose();
    }

    private void deliverDose() {
        FluidStack fluid = tank.getFluid();
        int amount = dose.getValue();
        if (fluid.getAmount() < amount || !filter.test(fluid)) return;

        BlockPos target = worldPosition.below();
        if (!level.isLoaded(target)) return;
        IFluidHandler output = level.getCapability(Capabilities.FluidHandler.BLOCK, target, Direction.UP);
        if (output == null) return;
        FluidStack portion = fluid.copyWithAmount(amount);
        if (output.fill(portion, FluidAction.SIMULATE) != amount) return;

        int delivered = output.fill(portion, FluidAction.EXECUTE);
        if (delivered > 0) tank.drain(delivered, FluidAction.EXECUTE);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.put("Tank", tank.writeToNBT(registries, new CompoundTag()));
        tag.putBoolean("WasPowered", wasPowered);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        tank.readFromNBT(registries, tag.getCompound("Tank"));
        wasPowered = tag.getBoolean("WasPowered");
    }

    private static final class ControlSlot extends ValueBoxTransform.Sided {
        private final int x;

        ControlSlot(int x) {
            this.x = x;
            scale = 0.36f;
        }

        @Override
        protected Vec3 getSouthLocation() {
            return VecHelper.voxelSpace(x, 10, 15.5);
        }

        @Override
        protected boolean isSideActive(BlockState state, Direction direction) {
            return direction == state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        }
    }
}
