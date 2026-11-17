package ee.extras.mixin;

import com.george_vi.electroenergetics.simulation.infrastructure.WireData;
import ee.extras.lines.InsulatorAnchors;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = WireData.class, remap = false)
public abstract class AnchoredWireSagMixin {
    @Inject(method = "getSag(D)F", at = @At("HEAD"), cancellable = true, require = 1)
    private void eeExtras$anchorSag(double distance, CallbackInfoReturnable<Float> cir) {
        WireData wire = (WireData) (Object) this;
        if (InsulatorAnchors.hasAnchor(wire)) cir.setReturnValue(InsulatorAnchors.sag(wire, distance));
    }
}
