package ee.extras.mixin;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDevice;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = DevicesSavedData.class, remap = false)
public abstract class OptionalElectricalDeviceMixin {
    @Inject(method = "addDevice", at = @At("HEAD"), cancellable = true, require = 1)
    private void eeExtras$skipEmptyMount(SimulatedDeviceType<?> type, BlockPos pos, CompoundTag data,
                                         CallbackInfoReturnable<SimulatedDevice> result) {
        // EE вызывает addDevice для каждого DeviceBlock при установке. У навесного привода свой монтаж.
        if (type == null) result.setReturnValue(null);
    }
}
