package ee.extras.mixin;

import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelBlock;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;

import ee.extras.panel.PanelPorts;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ElectricalPanelBlock.class, remap = false)
public abstract class PanelCableNodesMixin {
    @Inject(method = "getNodePosition", at = @At("RETURN"), cancellable = true, require = 1)
    private void inletPosition(
            Level level,
            BlockPos pos,
            BlockState state,
            int id,
            CallbackInfoReturnable<Vec3> result) {
        if (level.getBlockEntity(pos)
                instanceof
                com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelBlockEntity
                        panel) {
            var point = PanelPorts.inletNode(panel, id);
            if (point != null) result.setReturnValue(point);
        }
    }

    /**
     * Переопределяет метод интерфейса EE: оболочка есть в сети, но на неё нельзя повесить обычный
     * провод.
     */
    public boolean isNodeAccessible(Level level, BlockPos pos, BlockState state, int id) {
        return !ee.extras.redstone.Signals.reserved(id) && PanelPorts.resolve(level, new InWorldNode(id, pos)) == null;
    }
}
