package ee.extras.machine;

import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

interface MotorMachineBlock extends ElectricalDeviceBlock<MotorMachineDevice> {
    default Direction terminalFace(BlockState state) {
        return Direction.UP;
    }

    default Direction terminalRow(BlockState state) {
        return Direction.EAST;
    }

    @Override
    default Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) {
        return Map.of(0, terminal(state, 0), 1, terminal(state, 1));
    }

    @Override
    default Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int node) {
        return node == 0 || node == 1 ? terminal(state, node) : null;
    }

    private Vec3 terminal(BlockState state, int node) {
        Vec3 outward = Vec3.atLowerCornerOf(terminalFace(state).getNormal()).scale(10.0 / 16);
        Vec3 offset = Vec3.atLowerCornerOf(terminalRow(state).getNormal()).scale((node == 0 ? -3.0 : 3.0) / 16);
        return new Vec3(0.5, 0.5, 0.5).add(outward).add(offset);
    }
}
