package ee.extras.mixin;

import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.george_vi.electroenergetics.simulation.infrastructure.detached_nodes.DetachedNodeEntity;
import com.george_vi.electroenergetics.simulation.infrastructure.detached_nodes.DetachedNodeHelper;
import ee.extras.lines.InsulatorAnchors;
import java.util.Objects;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DetachedNodeEntity.class, remap = false)
public abstract class DetachedNodeAnchorMixin {
    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lcom/george_vi/electroenergetics/simulation/infrastructure/InfrastructureSavedData;load(Lnet/minecraft/server/level/ServerLevel;)Lcom/george_vi/electroenergetics/simulation/infrastructure/InfrastructureSavedData;"),
            cancellable = true, require = 1)
    private void eeExtras$anchorTip(CallbackInfo ci) {
        DetachedNodeEntity entity = (DetachedNodeEntity) (Object) this;
        if (!(entity.level() instanceof ServerLevel level)) return;
        var sd = InfrastructureSavedData.load(level);
        var tip = sd.getNodeData(DetachedNodeHelper.getFromId(entity.detachedNodeID));
        if (tip != null && Objects.equals(tip.detachedNodeEntityId, entity.getUUID())
                && InsulatorAnchors.positionTip(sd, tip, entity)) ci.cancel();
    }
}
