package ee.extras.machine;

import com.george_vi.electroenergetics.content.electric_pump.ElectricPumpBlockEntity;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
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
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/** Две независимые сети труб заканчиваются на дозирующей камере: сквозного потока нет. */
public final class MeteringPumpBlockEntity extends ElectricPumpBlockEntity {
    private static final int CAPACITY = 1000;
    private final FluidTank chamber = new FluidTank(CAPACITY) {
        @Override protected void onContentsChanged() { syncCycle(); }
    };
    private ScrollValueBehaviour dose;
    private FilteringBehaviour filter;
    private boolean wasPowered;
    private int cycleDose;
    private boolean dispensing;
    private boolean pressureDirty;
    private final IFluidHandler inlet = new Port(true);
    private final IFluidHandler outlet = new Port(false);

    public MeteringPumpBlockEntity(BlockPos pos, BlockState state) {
        super(EeExtras.METERING_PUMP_BE.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        behaviours.removeIf(behaviour -> behaviour instanceof FluidTransportBehaviour);
        behaviours.add(new FluidTransportBehaviour(this) {
            @Override public boolean canHaveFlowToward(BlockState state, Direction side) {
                return isSideAccessible(side);
            }
            @Override public void tick() {
                // Давление создаёт штатная помпа, но жидкость через её корпус не проходит.
                // Соседние трубы видят вместо сквозной сети два ограниченных capability-порта.
                for (Direction side : Direction.values()) {
                    var connection = getConnection(side);
                    if (connection == null) continue;
                    boolean pull = side != getFront();
                    connection.getPressure().set(pull, Math.abs(getSpeed()));
                    connection.getPressure().set(!pull, 0f);
                }
            }
            @Override public FluidStack getProvidedOutwardFluid(Direction side) {
                return FluidStack.EMPTY;
            }
        });
        dose = new ScrollValueBehaviour(Component.translatable("ee_extras.fluid_dispenser.dose"), this,
                new ControlSlot(5)).between(1, CAPACITY);
        dose.value = 250;
        dose.withFormatter(value -> Component.translatable("ee_extras.fluid_dispenser.amount", value).getString());
        filter = new FilteringBehaviour(this, new ControlSlot(11)).forFluids();
        behaviours.add(dose);
        behaviours.add(filter);
    }

    private void syncCycle() {
        setChanged();
        if (level != null && !level.isClientSide) sendData();
    }

    public IFluidHandler port(Direction side) {
        if (side == null) return null;
        return side == getFront() ? outlet : side == getFront().getOpposite() ? inlet : null;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean sneaking) {
        super.addToGoggleTooltip(tooltip, sneaking);
        tooltip.add(Component.translatable("ee_extras.metering_pump.dose", dose.getValue()));
        tooltip.add(Component.translatable("ee_extras.metering_pump." +
                (cycleDose == 0 ? "ready" : dispensing ? "dispensing" : "filling"),
                chamber.getFluidAmount(), cycleDose));
        return true;
    }

    public boolean startDose() {
        if (cycleDose != 0 || super.getSpeed() == 0) return false;
        cycleDose = dose.getValue();
        dispensing = false;
        syncCycle();
        updatePressureChange();
        return true;
    }

    @Override public float getSpeed() {
        return cycleDose == 0 ? 0 : super.getSpeed();
    }

    @Override public void tick() {
        // Сброс сети нельзя выполнять внутри drain: Create ещё передаёт извлечённую жидкость получателю.
        if (pressureDirty && level != null && !level.isClientSide) {
            pressureDirty = false;
            updatePressureChange();
        }
        super.tick();
        if (level == null || level.isClientSide) return;
        boolean powered = level.hasNeighborSignal(worldPosition);
        if (powered != wasPowered) {
            wasPowered = powered;
            syncCycle();
            if (powered) startDose();
        }
        if (cycleDose == 0 || super.getSpeed() == 0) return;
        Direction direction = getFront();
        BlockPos adjacent = worldPosition.relative(dispensing ? direction : direction.getOpposite());
        if (!level.isLoaded(adjacent)) return;
        // Прямое подключение к баку работает и без промежуточного блока трубы.
        IFluidHandler other = level.getCapability(Capabilities.FluidHandler.BLOCK, adjacent,
                dispensing ? direction.getOpposite() : direction);
        if (other == null || other == inlet || other == outlet) return;
        if (dispensing) {
            FluidStack offered = outlet.drain(CAPACITY, FluidAction.SIMULATE);
            int accepted = other.fill(offered, FluidAction.SIMULATE);
            if (accepted > 0) {
                int filled = other.fill(offered.copyWithAmount(accepted), FluidAction.EXECUTE);
                outlet.drain(filled, FluidAction.EXECUTE);
            }
        } else {
            for (int i = 0; i < other.getTanks(); i++) {
                FluidStack available = other.getFluidInTank(i);
                int accepted = inlet.fill(available, FluidAction.SIMULATE);
                if (accepted == 0) continue;
                FluidStack moved = other.drain(available.copyWithAmount(accepted), FluidAction.EXECUTE);
                inlet.fill(moved, FluidAction.EXECUTE);
                break;
            }
        }
    }

    @Override protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean packet) {
        super.write(tag, registries, packet);
        tag.put("MeteringChamber", chamber.writeToNBT(registries, new CompoundTag()));
        tag.putBoolean("MeteringSignal", wasPowered);
        tag.putInt("MeteringDose", cycleDose);
        tag.putBoolean("MeteringDispensing", dispensing);
    }

    @Override protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean packet) {
        super.read(tag, registries, packet);
        chamber.readFromNBT(registries, tag.getCompound("MeteringChamber"));
        wasPowered = tag.getBoolean("MeteringSignal");
        cycleDose = tag.getInt("MeteringDose");
        dispensing = tag.getBoolean("MeteringDispensing");
    }

    private final class Port implements IFluidHandler {
        private final boolean input;
        Port(boolean input) { this.input = input; }
        @Override public int getTanks() { return 1; }
        @Override public FluidStack getFluidInTank(int tank) { return chamber.getFluid().copy(); }
        @Override public int getTankCapacity(int tank) { return CAPACITY; }
        @Override public boolean isFluidValid(int tank, FluidStack stack) { return filter != null && filter.test(stack); }
        @Override public int fill(FluidStack stack, FluidAction action) {
            if (!input || cycleDose == 0 || dispensing || getSpeed() == 0 || !isFluidValid(0, stack)) return 0;
            int remaining = cycleDose - chamber.getFluidAmount();
            int accepted = chamber.fill(stack.copyWithAmount(Math.min(remaining, stack.getAmount())), action);
            if (action.execute() && chamber.getFluidAmount() == cycleDose) {
                dispensing = true;
                syncCycle();
            }
            return accepted;
        }
        @Override public FluidStack drain(FluidStack stack, FluidAction action) {
            return FluidStack.isSameFluidSameComponents(stack, chamber.getFluid()) ? drain(stack.getAmount(), action) : FluidStack.EMPTY;
        }
        @Override public FluidStack drain(int amount, FluidAction action) {
            if (input || !dispensing || getSpeed() == 0) return FluidStack.EMPTY;
            FluidStack result = chamber.drain(amount, action);
            if (action.execute() && chamber.isEmpty()) {
                cycleDose = 0;
                dispensing = false;
                syncCycle();
                pressureDirty = true;
            }
            return result;
        }
    }

    private static final class ControlSlot extends ValueBoxTransform.Sided {
        private final int x;
        ControlSlot(int x) { this.x = x; scale = 0.36f; }
        @Override protected Vec3 getSouthLocation() { return VecHelper.voxelSpace(x, 10, 15.5); }
        @Override protected boolean isSideActive(BlockState state, Direction side) {
            return side == (state.getValue(MeteringPumpBlock.FACING).getAxis().isHorizontal() ? Direction.UP : state.getValue(MeteringPumpBlock.FACING) == Direction.DOWN ? Direction.NORTH : Direction.SOUTH);
        }
    }
}
