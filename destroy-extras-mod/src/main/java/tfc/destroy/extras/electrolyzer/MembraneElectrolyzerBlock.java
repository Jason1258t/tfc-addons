package tfc.destroy.extras.electrolyzer;

import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.base.SimpleElectricalDeviceBlock;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.Vec3;
import tfc.destroy.bridge.electric.BridgeDevices;
import tfc.destroy.bridge.electric.ProcessPowerDevice;

/** Два EE-контакта; жидкостные стороны задаются capability блока-сущности и поворачиваются вместе с блоком. */
public final class MembraneElectrolyzerBlock extends SimpleElectricalDeviceBlock<ProcessPowerDevice> implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final Vec3 PLUS_LOCAL = new Vec3(0.4, 0.5, 0.5);
    private static final Vec3 MINUS_LOCAL = new Vec3(0.6, 0.5, 0.5);

    public MembraneElectrolyzerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState getRotatedBlockState(BlockState state, Direction axis) {
        return state.setValue(FACING, state.getValue(FACING).getClockWise());
    }

    @Override
    public SimulatedDeviceType<ProcessPowerDevice> getDevice() {
        return BridgeDevices.PROCESS_POWER.get();
    }

    @Override
    public Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) {
        Map<Integer, Vec3> nodes = new HashMap<>();
        Direction facing = state.getValue(FACING);
        nodes.put(ProcessPowerDevice.PLUS, ElectrolyzerStructure.node(facing, PLUS_LOCAL));
        nodes.put(ProcessPowerDevice.MINUS, ElectrolyzerStructure.node(facing, MINUS_LOCAL));
        return nodes;
    }

    @Override
    public Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int node) {
        Direction facing = state.getValue(FACING);
        return ElectrolyzerStructure.node(facing, node == ProcessPowerDevice.PLUS ? PLUS_LOCAL : MINUS_LOCAL);
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
