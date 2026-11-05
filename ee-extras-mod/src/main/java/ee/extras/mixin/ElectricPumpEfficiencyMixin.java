package ee.extras.mixin;

import com.george_vi.electroenergetics.content.electric_pump.ElectricPumpBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ElectricPumpBlockEntity.class, remap = false)
public abstract class ElectricPumpEfficiencyMixin {
    @Unique
    private static final double eeExtras$EFFICIENCY_FACTOR = 2.4;

    // Симуляция использует это сопротивление, а обороты EE по-прежнему рассчитывает по исходному конфигу.
    @Inject(method = "getResistance", at = @At("RETURN"), cancellable = true, require = 1)
    private void eeExtras$reduceElectricalLoad(CallbackInfoReturnable<Double> result) {
        result.setReturnValue(result.getReturnValueD() * eeExtras$EFFICIENCY_FACTOR);
    }

    @ModifyArg(method = "addToGoggleTooltip", at = @At(value = "INVOKE",
            target = "Ljava/lang/Math;round(D)J"), index = 0, require = 1)
    private double eeExtras$showActualPower(double watts) {
        return watts / eeExtras$EFFICIENCY_FACTOR;
    }
}
