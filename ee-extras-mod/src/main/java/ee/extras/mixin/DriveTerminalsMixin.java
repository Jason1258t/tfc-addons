package ee.extras.mixin;

import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import ee.extras.motor.BeltMotorDevice;
import ee.extras.motor.BeltMotorHost;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;

@Pseudo
@Mixin(targets = {"com.simibubi.create.content.kinetics.belt.BeltBlock",
        "dev.simulated_team.simulated.content.blocks.auger_shaft.AugerShaftBlock"}, remap = false)
public abstract class DriveTerminalsMixin implements ElectricalDeviceBlock<BeltMotorDevice> {
    @Override
    public SimulatedDeviceType<BeltMotorDevice> getDevice() {
        // Привод устанавливается предметом на уже существующий блок.
        return null;
    }

    @Override
    public Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) {
        Direction side = eeExtras$side(level, pos, state);
        return side == null ? Map.of() : Map.of(0, BeltMotorDevice.terminal(side, 0), 1, BeltMotorDevice.terminal(side, 1));
    }

    @Override
    public Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int node) {
        Direction side = eeExtras$side(level, pos, state);
        return side == null || node < 0 || node > 1 ? null : BeltMotorDevice.terminal(side, node);
    }

    @Unique
    private static Direction eeExtras$side(Level level, BlockPos pos, BlockState state) {
        return BeltMotorDevice.supportsDrive(state) && level.getBlockEntity(pos) instanceof BeltMotorHost host
                ? host.eeExtras$mountSide() : null;
    }
}
