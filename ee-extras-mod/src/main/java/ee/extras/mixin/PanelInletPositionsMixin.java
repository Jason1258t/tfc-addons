package ee.extras.mixin;

import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelBlockEntity;

import ee.extras.panel.PanelPorts;

import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;

@Mixin(value = ElectricalPanelBlockEntity.class, remap = false)
public abstract class PanelInletPositionsMixin {
    @Inject(method = "getNodePositions", at = @At("RETURN"), cancellable = true, require = 1)
    private void inletPositions(CallbackInfoReturnable<Map<Integer, Vec3>> result) {
        var panel = (ElectricalPanelBlockEntity) (Object) this;
        var positions = new HashMap<>(result.getReturnValue());
        for (var id : positions.keySet()) {
            var point = PanelPorts.inletNode(panel, id);
            if (point != null) positions.put(id, point);
        }
        result.setReturnValue(positions);
    }
}
