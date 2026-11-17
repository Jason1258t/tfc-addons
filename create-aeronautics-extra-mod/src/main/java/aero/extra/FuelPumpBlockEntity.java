package aero.extra;

import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public final class FuelPumpBlockEntity extends FuelEndpointBlockEntity {
    private static final ResourceLocation PORTABLE = ResourceLocation.parse("simulated:portable_engine");
    private static final ResourceLocation CDG = ResourceLocation.parse("createdieselgenerators:diesel_engine_tile_entity");
    private static final TagKey<Fluid> HOSE_FUELS = TagKey.create(Registries.FLUID, AeroExtra.rl("fuel_hose"));
    private static final int RATE = 50;
    private static final int PRIME_TICKS = 40;
    // Остаток сохраняется, если приёмник изменил ответ после симуляции перелива.
    private final FluidTank recovery = new FluidTank(RATE) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    private long primeUntil;

    FuelPumpBlockEntity(BlockPos pos, BlockState state) {
        super(AeroExtra.FUEL_PUMP_BE.get(), pos, state);
    }

    public void prime() {
        if (level != null && !level.isClientSide) primeUntil = level.getGameTime() + PRIME_TICKS;
    }

    private Direction facing() {
        return getBlockState().getValue(FuelPumpBlock.FACING);
    }

    private BlockEntity engine() {
        BlockPos pos = worldPosition.relative(facing());
        return level.hasChunkAt(pos) ? level.getBlockEntity(pos) : null;
    }

    private boolean driven(BlockEntity engine) {
        return !level.hasNeighborSignal(worldPosition) && (primeUntil > level.getGameTime()
                || engine instanceof GeneratingKineticBlockEntity kinetic
                && kinetic.getGeneratedSpeed() != 0 && kinetic.getSpeed() != 0);
    }

    private FuelHoses hoses() {
        return FuelHoses.get((ServerLevel) level);
    }

    private IFluidHandler source() {
        FuelHoses hoses = hoses();
        if (hoses.connected(worldPosition)) return hoses.source((ServerLevel) level, worldPosition);
        Direction output = facing();
        BlockPos pos = worldPosition.relative(output.getOpposite());
        if (!level.hasChunkAt(pos) || level.getBlockEntity(pos) instanceof FuelPumpBlockEntity) return null;
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof FuelPortBlock && state.getValue(FuelPortBlock.FACING) != output) {
            return FuelPortBlock.tank(level, pos, state);
        }
        return level.getCapability(Capabilities.FluidHandler.BLOCK, pos, output);
    }

    void tick() {
        if (level.isClientSide) return;
        BlockEntity engine = engine();
        if (engine == null || !driven(engine)) return;
        ResourceLocation kind = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(engine.getType());
        boolean cdg = CDG.equals(kind) && ModList.get().isLoaded("createdieselgenerators");
        if (!cdg && !PORTABLE.equals(kind)) return;
        IFluidHandler destination = level.getCapability(Capabilities.FluidHandler.BLOCK,
                engine.getBlockPos(), facing().getOpposite());
        if (destination == null) return;

        if (!recovery.getFluid().isEmpty()) {
            deliver(destination, cdg);
            return;
        }
        IFluidHandler source = source();
        if (source == null || source == destination) return;
        FluidStack available = source.drain(RATE, IFluidHandler.FluidAction.SIMULATE);
        if (!canDeliver(available, destination, cdg) || !allowedAtInlet(available)) return;
        int capacity = destination.fill(available, IFluidHandler.FluidAction.SIMULATE);
        if (capacity <= 0) return;
        recovery.setFluid(source.drain(available.copyWithAmount(capacity), IFluidHandler.FluidAction.EXECUTE));
        setChanged();
        deliver(destination, cdg);
    }

    private boolean allowedAtInlet(FluidStack stack) {
        return !hoses().connected(worldPosition) || stack.getFluid().is(HOSE_FUELS);
    }

    private boolean canDeliver(FluidStack stack, IFluidHandler destination, boolean cdg) {
        FluidStack stored = destination.getFluidInTank(0);
        return !stack.isEmpty() && (stored.isEmpty() || FluidStack.isSameFluidSameComponents(stored, stack))
                && (!cdg || CdgFuel.accepts(level, stack));
    }

    private void deliver(IFluidHandler destination, boolean cdg) {
        FluidStack stack = recovery.getFluid();
        if (canDeliver(stack, destination, cdg)) {
            recovery.drain(destination.fill(stack.copy(), IFluidHandler.FluidAction.EXECUTE), IFluidHandler.FluidAction.EXECUTE);
        }
    }

    boolean supplies(TransportEngineBlockEntity target) {
        return engine() == target && driven(target) && (TransportEngineBlockEntity.acceptsFuel(recovery.getFluid())
                || source() != null);
    }

    IFluidHandler engineOutlet(Direction side) {
        return side == facing() ? outlet : null;
    }

    private final IFluidHandler outlet = new IFluidHandler() {
        @Override public int getTanks() { return 1; }
        @Override public FluidStack getFluidInTank(int tank) { return FluidStack.EMPTY; }
        @Override public int getTankCapacity(int tank) { return RATE; }
        @Override public boolean isFluidValid(int tank, FluidStack stack) { return false; }
        @Override public int fill(FluidStack stack, FluidAction action) { return 0; }

        @Override
        public FluidStack drain(FluidStack request, FluidAction action) {
            if (request.isEmpty() || !TransportEngineBlockEntity.acceptsFuel(request)
                    || level == null || level.isClientSide || isRemoved()) return FluidStack.EMPTY;
            BlockEntity target = engine();
            if (!(target instanceof TransportEngineBlockEntity) || !driven(target)) return FluidStack.EMPTY;
            if (!recovery.getFluid().isEmpty()) return recovery.drain(request, action);
            IFluidHandler source = source();
            if (source == null || !allowedAtInlet(request)) return FluidStack.EMPTY;
            return source.drain(request.copyWithAmount(Math.min(RATE, request.getAmount())), action);
        }

        @Override
        public FluidStack drain(int amount, FluidAction action) {
            FluidStack diesel = TransportEngineBlockEntity.dieselStack(Math.min(RATE, amount));
            return drain(diesel, action);
        }
    };

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Recovery", recovery.writeToNBT(registries, new CompoundTag()));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        recovery.readFromNBT(registries, tag.getCompound("Recovery"));
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (!recovery.getFluid().isEmpty()) components.set(AeroExtra.PUMP_FUEL.get(), recovery.getFluid().copy());
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput components) {
        super.applyImplicitComponents(components);
        recovery.setFluid(components.getOrDefault(AeroExtra.PUMP_FUEL.get(), FluidStack.EMPTY).copy());
    }
}
