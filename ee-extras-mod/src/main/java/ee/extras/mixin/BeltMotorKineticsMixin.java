package ee.extras.mixin;

import com.george_vi.electroenergetics.config.CEEConfigs;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
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
    @Unique private boolean eeExtras$restoreMotor;

    @Inject(method = "write", at = @At("TAIL"), require = 1)
    private void eeExtras$writeMotor(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket,
                                     CallbackInfo ci) {
        if (!clientPacket && eeExtras$motorRpm != 0) {
            tag.putFloat("EeExtrasDriveRpm", eeExtras$motorRpm);
            tag.putFloat("EeExtrasDriveCapacity", eeExtras$motorCapacity);
        }
    }

    @Inject(method = "read", at = @At("TAIL"), require = 1)
    private void eeExtras$readMotor(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket,
                                    CallbackInfo ci) {
        if (!clientPacket && (Object) this instanceof BeltBlockEntity) {
            // При загрузке Create вычитает ранее сохранённую мощность только у распознанных источников.
            eeExtras$motorRpm = tag.getFloat("EeExtrasDriveRpm");
            eeExtras$motorCapacity = tag.getFloat("EeExtrasDriveCapacity");
            eeExtras$restoreMotor = eeExtras$motorRpm != 0;
        }
    }

    @Override
    public void eeExtras$readMotorPower(BeltMotorDevice device, double rmsVoltage) {
        eeExtras$device = device;
        eeExtras$voltage = rmsVoltage;
    }

    @Inject(method = "tick", at = @At("TAIL"), require = 1)
    private void eeExtras$tickMotor(CallbackInfo ci) {
        if (eeExtras$restoreMotor && (Object) this instanceof BeltBlockEntity belt
                && belt.getLevel() instanceof ServerLevel server) {
            eeExtras$restoreMotor = false;
            if (eeExtras$device == null) {
                eeExtras$device = DevicesSavedData.load(server).getDevice(belt.getBlockPos(), BeltMotorDevice.class);
                if (eeExtras$device == null) eeExtras$drive(0, 0);
                // Первое измерение EE придёт после восстановления проводов и сети Create.
                return;
            }
        }
        if (eeExtras$device == null) return;
        if (!eeExtras$device.isValid()) {
            eeExtras$device = null;
            eeExtras$drive(0, 0);
            return;
        }
        float rpm = eeExtras$voltage > CEEConfigs.server().voltageValues.motorMinVoltage.get() ? 32 : 0;
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
        if (!((Object) this instanceof BeltBlockEntity belt)) return;
        if (rpm == eeExtras$motorRpm && capacityPerRpm == eeExtras$motorCapacity && (rpm == 0 || belt.hasNetwork())) return;

        if (rpm == eeExtras$motorRpm && belt.hasNetwork()) {
            eeExtras$motorCapacity = capacityPerRpm;
            belt.getOrCreateNetwork().updateCapacityFor(belt, belt.calculateAddedStressCapacity());
            belt.setChanged();
            return;
        }

        float previousSpeed = belt.getSpeed();
        if (eeExtras$motorRpm != 0 || !belt.hasSource()) {
            // Пока старые обороты видны сети, Create может убрать запись источника и отключить его ветку.
            belt.setNetwork(null);
            eeExtras$motorRpm = 0;
            belt.detachKinetics();
            belt.setSpeed(0);
        }
        eeExtras$motorRpm = rpm;
        eeExtras$motorCapacity = capacityPerRpm;
        if (rpm != 0 && !belt.hasSource()) {
            belt.setSpeed(rpm);
            belt.setNetwork(belt.getBlockPos().asLong());
        } else if (rpm != 0 && belt.hasNetwork()) {
            belt.getOrCreateNetwork().updateCapacityFor(belt, belt.calculateAddedStressCapacity());
        }
        belt.attachKinetics();
        belt.onSpeedChanged(previousSpeed);
        belt.sendData();
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
