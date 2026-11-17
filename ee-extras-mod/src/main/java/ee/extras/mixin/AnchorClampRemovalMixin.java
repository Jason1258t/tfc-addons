package ee.extras.mixin;

import com.george_vi.electroenergetics.content.wire.attachments.AttachmentRemovalWireInteractionBehaviour;
import com.george_vi.electroenergetics.foundation.nodes.NodeConnectionPoint;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import ee.extras.lines.InsulatorAnchors;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Снятие насадки в EE меняет список на месте. Само обновление провода не помечает данные для записи. */
@Mixin(value = AttachmentRemovalWireInteractionBehaviour.class, remap = false)
public abstract class AnchorClampRemovalMixin {
    @Inject(method = "interactWire", at = @At("HEAD"), cancellable = true, require = 1)
    private void eeExtras$saveAnchorRemoval(NodeConnectionPoint point, Level level, Player player, ItemStack stack,
                                            CallbackInfo ci) {
        if (!(level instanceof ServerLevel server)) return;
        var sd = InfrastructureSavedData.load(server);
        var wire = sd.getConnectionData(point.connection());
        if (wire == null) {
            ci.cancel();
        } else if (InsulatorAnchors.hasAnchor(wire)) {
            sd.setDirty();
        }
    }
}
