package aero.extra;

import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

public final class TransportEngineBlockEntity extends GeneratingKineticBlockEntity {
    public static final int MAX_RPM = 128;
    private static final int ROTATION_PER_MB = 96 * 20;
    private static final float STRESS_CAPACITY_PER_RPM = 16;
    private static final ResourceLocation DIESEL = ResourceLocation.fromNamespaceAndPath("createdieselgenerators", "diesel");

    private int targetRpm = 32;
    private int outputRpm;
    // Остаток работы от уже сожжённого 1 мБ, а не жидкость. Переживает останов и сохранение мира.
    private int paidRotation;

    TransportEngineBlockEntity(BlockPos pos, BlockState state) {
        super(AeroExtra.TRANSPORT_ENGINE_BE.get(), pos, state);
    }

    public int getTargetRpm() {
        return targetRpm;
    }

    /** Серверная уставка для регулятора. Ноль останавливает двигатель, знак задаёт направление. */
    public void setTargetRpm(int rpm) {
        if (level == null || level.isClientSide) return;
        int next = Mth.clamp(rpm, -MAX_RPM, MAX_RPM);
        if (targetRpm == next) return;
        targetRpm = next;
        notifyUpdate();
    }

    @Override
    public float getGeneratedSpeed() {
        return convertToDirection(outputRpm, getBlockState().getValue(TransportEngineBlock.FACING));
    }

    @Override
    public float calculateAddedStressCapacity() {
        lastCapacityProvided = outputRpm == 0 ? 0 : STRESS_CAPACITY_PER_RPM;
        return lastCapacityProvided;
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide || isVirtual()) return;
        int rpm = suppliedRpm();
        // При переносе на подуровень Create заново собирает сеть, сохраняя уставку двигателя.
        if (rpm != outputRpm || (rpm != 0 && getTheoreticalSpeed() == 0)) {
            outputRpm = rpm;
            updateGeneratedRotation();
            notifyUpdate();
        }
    }

    private int suppliedRpm() {
        if (targetRpm == 0 || level.hasNeighborSignal(worldPosition)) return 0;
        Direction shaft = getBlockState().getValue(TransportEngineBlock.FACING);
        BlockPos inlet = worldPosition.relative(shaft.getOpposite());
        if (!level.hasChunkAt(inlet)) return 0;
        IFluidHandler source = level.getCapability(Capabilities.FluidHandler.BLOCK, inlet, shaft);
        if (source == null) return 0;

        int demand = Math.abs(targetRpm);
        if (paidRotation < demand) {
            FluidStack available = source.drain(1, FluidAction.SIMULATE);
            if (available.isEmpty() || !DIESEL.equals(BuiltInRegistries.FLUID.getKey(available.getFluid()))) return 0;
            FluidStack drained = source.drain(available, FluidAction.EXECUTE);
            if (drained.getAmount() != 1 || !FluidStack.isSameFluidSameComponents(available, drained)) return 0;
            paidRotation += ROTATION_PER_MB;
        }
        paidRotation -= demand;
        setChanged();
        return targetRpm;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("TargetRpm", targetRpm);
        tag.putInt("OutputRpm", outputRpm);
        tag.putInt("PaidRotation", paidRotation);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (tag.contains("TargetRpm")) targetRpm = Mth.clamp(tag.getInt("TargetRpm"), -MAX_RPM, MAX_RPM);
        outputRpm = Mth.clamp(tag.getInt("OutputRpm"), -MAX_RPM, MAX_RPM);
        paidRotation = Mth.clamp(tag.getInt("PaidRotation"), 0, ROTATION_PER_MB);
    }
}
