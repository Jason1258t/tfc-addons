package ee.extras.pcb;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.base.SimpleElectricalDeviceBlock;
import com.george_vi.electroenergetics.foundation.nodes.NodeConfigurator;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class PcbCircuitBlock extends SimpleElectricalDeviceBlock<PcbCircuitDevice> {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final BooleanProperty OUTPUT = BooleanProperty.create("output");
    private static final NodeConfigurator NODES = new NodeConfigurator.Builder()
            .add(4, 16, 4).add(12, 16, 4).add(12, 16, 12).add(4, 16, 12).simple();

    public PcbCircuitBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(LIT, false).setValue(OUTPUT, false));
    }

    @Override
    public SimulatedDeviceType<PcbCircuitDevice> getDevice() {
        return PcbItems.CIRCUIT_DEVICE.get();
    }

    @Override
    public Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) {
        return NODES.getNodes(Direction.UP);
    }

    @Override
    public Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int node) {
        return NODES.getNodePos(Direction.UP, node);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LIT, OUTPUT);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server) {
            var device = DevicesSavedData.load(server).getDevice(pos, PcbCircuitDevice.class);
            if (device != null) device.interact(player.isShiftKeyDown(), level.getGameTime());
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(OUTPUT) && direction == Direction.EAST ? 15 : 0;
    }
}
