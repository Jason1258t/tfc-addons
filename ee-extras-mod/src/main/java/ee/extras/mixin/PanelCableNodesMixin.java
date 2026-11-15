package ee.extras.mixin;

import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelBlock;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import ee.extras.panel.PanelPorts;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = ElectricalPanelBlock.class, remap = false)
public abstract class PanelCableNodesMixin {
    /** Переопределяет метод интерфейса EE: оболочка есть в сети, но на неё нельзя повесить обычный провод. */
    public boolean isNodeAccessible(Level level, BlockPos pos, BlockState state, int id) {
        return PanelPorts.resolve(level, new InWorldNode(id, pos)) == null;
    }
}
