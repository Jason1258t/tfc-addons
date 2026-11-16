package ee.extras.mixin;

import com.george_vi.electroenergetics.config.CEEConfigs;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.Direction;
import ee.extras.motor.BeltMotorDevice;
import ee.extras.motor.BeltMotorHost;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = KineticBlockEntity.class, remap = false)
public abstract class BeltMotorKineticsMixin implements BeltMotorHost {
    @Shadow protected float stress;
    @Shadow protected float capacity;
    @Shadow protected float lastCapacityProvided;
    @Unique private float eeExtras$motorRpm;
    @Unique private float eeExtras$motorCapacity;
    @Unique private BeltMotorDevice eeExtras$device;
    @Unique private double eeExtras$voltage;
    @Unique private boolean eeExtras$powerMeasured;
    @Unique private boolean eeExtras$restoreMotor = true;
    @Unique private Direction eeExtras$mountSide;

    @Inject(method = "write", at = @At("TAIL"), require = 1)
    private void eeExtras$writeMotor(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket,
                                     CallbackInfo ci) {
        if (eeExtras$mountSide != null) tag.putString(BeltMotorHost.MOUNT_SIDE_TAG, eeExtras$mountSide.getSerializedName());
        if (!clientPacket && eeExtras$motorRpm != 0) {
            tag.putFloat("EeExtrasDriveRpm", eeExtras$motorRpm);
            tag.putFloat("EeExtrasDriveCapacity", eeExtras$motorCapacity);
        }
    }

    @Inject(method = "read", at = @At("TAIL"), require = 1)
    private void eeExtras$readMotor(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket,
                                    CallbackInfo ci) {
        eeExtras$mountSide = Direction.byName(tag.getString(BeltMotorHost.MOUNT_SIDE_TAG));
        if (!clientPacket && BeltMotorDevice.supportsDrive(eeExtras$kinetic().getBlockState())) {
            // При загрузке Create вычитает ранее сохранённую мощность только у распознанных источников.
            eeExtras$motorRpm = tag.getFloat("EeExtrasDriveRpm");
            eeExtras$motorCapacity = tag.getFloat("EeExtrasDriveCapacity");
            eeExtras$restoreMotor = true;
        }
    }

    @Unique
    private KineticBlockEntity eeExtras$kinetic() {
        return (KineticBlockEntity) (Object) this;
    }

    @Override
    public Direction eeExtras$mountSide() {
        return eeExtras$mountSide;
    }

    @Override
    public void eeExtras$mountSide(Direction side) {
        if (eeExtras$mountSide == side) return;
        eeExtras$mountSide = side;
        eeExtras$kinetic().notifyUpdate();
    }

    @Override
    public void eeExtras$readMotorPower(BeltMotorDevice device, double rmsVoltage) {
        eeExtras$device = device;
        eeExtras$powerMeasured = true;
        eeExtras$voltage = rmsVoltage;
    }

    @Inject(method = "tick", at = @At("TAIL"), require = 1)
    private void eeExtras$tickMotor(CallbackInfo ci) {
        KineticBlockEntity kinetic = eeExtras$kinetic();
        if (!(kinetic.getLevel() instanceof ServerLevel server)) return;
        if (eeExtras$restoreMotor) {
            eeExtras$restoreMotor = false;
            if (!BeltMotorDevice.supportsDrive(kinetic.getBlockState())) return;
            if (eeExtras$device == null) {
                eeExtras$device = DevicesSavedData.load(server).getDevice(kinetic.getBlockPos(), BeltMotorDevice.class);
                eeExtras$mountSide(eeExtras$device == null ? null : eeExtras$device.mountSide());
                if (eeExtras$device == null && eeExtras$motorRpm != 0) eeExtras$drive(0, 0);
                // Существующая сеть ждёт первого измерения после загрузки проводов.
                return;
            }
        }
        if (eeExtras$device == null) return;
        if (eeExtras$device.isValid() && eeExtras$device.shouldRemove(kinetic.getBlockState(), kinetic.getBlockState())) {
            DevicesSavedData.load(server).removeDevice(kinetic.getBlockPos());
        }
        if (!eeExtras$device.isValid()) {
            eeExtras$device = null;
            eeExtras$drive(0, 0);
            eeExtras$mountSide(null);
            return;
        }
        if (!eeExtras$powerMeasured) return;
        float rpm = !kinetic.hasSource() && !server.hasNeighborSignal(kinetic.getBlockPos())
                && eeExtras$voltage > CEEConfigs.server().voltageValues.motorMinVoltage.get() ? eeExtras$device.rpm() : 0;
        eeExtras$drive(rpm, BeltMotorDevice.MODEL.capacityPerRpm(eeExtras$voltage, rpm));
    }

    @Override
    public float eeExtras$networkStress() {
        return stress;
    }

    @Override
    public float eeExtras$networkCapacity() {
        return capacity;
    }

    @Override
    public void eeExtras$drive(float rpm, float capacityPerRpm) {
        KineticBlockEntity kinetic = eeExtras$kinetic();
        if (rpm != 0 && !BeltMotorDevice.supportsDrive(kinetic.getBlockState())) return;
        if (rpm == eeExtras$motorRpm && capacityPerRpm == eeExtras$motorCapacity
                && (rpm == 0 || kinetic.hasNetwork())) return;

        if (kinetic.hasSource() || (rpm == eeExtras$motorRpm && kinetic.hasNetwork())) {
            eeExtras$motorRpm = rpm;
            eeExtras$motorCapacity = capacityPerRpm;
            if (kinetic.hasNetwork()) kinetic.getOrCreateNetwork().updateCapacityFor(kinetic, kinetic.calculateAddedStressCapacity());
            kinetic.setChanged();
            return;
        }

        float previousSpeed = kinetic.getSpeed();
        kinetic.detachKinetics();
        if (rpm == 0) kinetic.setNetwork(null);
        eeExtras$motorRpm = rpm;
        eeExtras$motorCapacity = capacityPerRpm;
        kinetic.setSpeed(rpm);
        if (rpm != 0) {
            kinetic.setNetwork(kinetic.getBlockPos().asLong());
            kinetic.attachKinetics();
        }
        kinetic.onSpeedChanged(previousSpeed);
        kinetic.notifyUpdate();
    }

    @Inject(method = "getGeneratedSpeed", at = @At("HEAD"), cancellable = true, require = 1)
    private void eeExtras$generatedSpeed(CallbackInfoReturnable<Float> result) {
        if (eeExtras$motorRpm != 0) result.setReturnValue(eeExtras$motorRpm);
    }

    @Inject(method = "calculateAddedStressCapacity", at = @At("HEAD"), cancellable = true, require = 1)
    private void eeExtras$addedCapacity(CallbackInfoReturnable<Float> result) {
        if (eeExtras$motorRpm != 0) {
            lastCapacityProvided = eeExtras$motorCapacity;
            result.setReturnValue(eeExtras$motorCapacity);
        }
    }
}
