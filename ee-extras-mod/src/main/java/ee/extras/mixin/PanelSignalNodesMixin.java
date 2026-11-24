package ee.extras.mixin;

import com.george_vi.electroenergetics.content.electrical_panel.*;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import ee.extras.redstone.Signals;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.*;

@Mixin(value = ElectricalPanelBlock.class, remap = false)
public class PanelSignalNodesMixin {
    @Inject(method = "getNodePositions", at = @At("RETURN"), cancellable = true, require = 1)
    private void signalNodes(Level level, BlockPos pos, BlockState state, CallbackInfoReturnable<Map<Integer, Vec3>> result) {
        if (!(level.getBlockEntity(pos) instanceof ElectricalPanelBlockEntity panel)) return;
        var nodes = new HashMap<>(result.getReturnValue());
        for (var a : panel.getAttachments()) if (Signals.supported(a))
            nodes.put(Signals.BASE + a.slot.ordinal(), a.getCenter().subtract(Vec3.atLowerCornerOf(pos)));
        result.setReturnValue(nodes);
    }
    @Inject(method = "getNodePosition", at = @At("HEAD"), cancellable = true, require = 1)
    private void signalPoint(Level level, BlockPos pos, BlockState state, int id, CallbackInfoReturnable<Vec3> result) {
        if (Signals.reserved(id)) result.setReturnValue(Signals.panelPoint(level, new InWorldNode(id, pos)));
    }
}
