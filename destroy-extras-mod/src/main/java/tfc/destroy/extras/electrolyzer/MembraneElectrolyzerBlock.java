package tfc.destroy.extras.electrolyzer;

import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.base.SimpleElectricalDeviceBlock;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import tfc.destroy.bridge.electric.BridgeDevices;
import tfc.destroy.bridge.electric.ProcessPowerDevice;

/** Два EE-контакта; жидкостные стороны задаются capability блока-сущности. */
public final class MembraneElectrolyzerBlock extends SimpleElectricalDeviceBlock<ProcessPowerDevice> implements EntityBlock {
    private static final Map<Integer, Vec3> NODES = Map.of(
        ProcessPowerDevice.PLUS, new Vec3(0.4, 0.5, 0.5),
        ProcessPowerDevice.MINUS, new Vec3(0.6, 0.5, 0.5));

    public MembraneElectrolyzerBlock(Properties properties) {
        super(properties);
    }

    @Override
    public SimulatedDeviceType<ProcessPowerDevice> getDevice() {
        return BridgeDevices.PROCESS_POWER.get();
    }

    @Override
    public Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) {
        return NODES;
    }

    @Override
    public Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int node) {
        return NODES.get(node);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MembraneElectrolyzerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : (l, p, s, entity) -> {
            if (entity instanceof MembraneElectrolyzerBlockEntity electrolyzer) electrolyzer.tick();
        };
    }
}
