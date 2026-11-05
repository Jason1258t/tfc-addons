package ee.extras.pcb;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.base.SimpleElectricalDeviceBlock;
import com.george_vi.electroenergetics.foundation.nodes.NodeConfigurator;
import com.simibubi.create.foundation.block.IBE;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class PcbCircuitBlock extends SimpleElectricalDeviceBlock<PcbCircuitDevice> implements IBE<PcbCircuitBlockEntity> {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final BooleanProperty BURNT = BooleanProperty.create("burnt");
    public static final IntegerProperty OUTPUT = IntegerProperty.create("output", 0, 15);
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 4, 16);
    private static final NodeConfigurator NODES = new NodeConfigurator.Builder()
            .add(4, 4, 4).add(12, 4, 4).add(12, 4, 12).add(4, 4, 12).simple();

    public PcbCircuitBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(LIT, false).setValue(OUTPUT, 0).setValue(BURNT, false));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
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
    public MutableComponent getNodeLabel(Level level, BlockPos pos, BlockState state, int node) {
        return Component.translatable("ee_extras.pcb.terminal", node + 1);
    }

    @Override
    public Class<PcbCircuitBlockEntity> getBlockEntityClass() {
        return PcbCircuitBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends PcbCircuitBlockEntity> getBlockEntityType() {
        return PcbItems.CIRCUIT_BE.get();
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!(level instanceof ServerLevel server)
                || !(level.getBlockEntity(pos) instanceof PcbCircuitBlockEntity entity)) return;
        var topology = stack.get(PcbItems.PCB_TOPOLOGY.get());
        var device = DevicesSavedData.load(server).getDevice(pos, PcbCircuitDevice.class);
        int boardLevel = PcbBoardItem.level(stack);
        if (topology == null || device == null || boardLevel == 0) return;
        var parts = PcbWiring.compile(topology);
        if (parts == null) return;
        device.setCircuit(parts, PcbCircuitDevice.Rating.values()[boardLevel - 1]);
        entity.setBoard(stack);
        device.restoreFuses(topology.getLong("BlownFuses"));
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder loot) {
        if (loot.getOptionalParameter(LootContextParams.BLOCK_ENTITY)
                instanceof PcbCircuitBlockEntity entity) {
            ItemStack board = entity.board();
            if (!board.isEmpty()) return List.of(state.getValue(BURNT)
                    ? board.transmuteCopy(PcbItems.BURNT_BOARD.get(), 1) : board);
        }
        return List.of();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LIT, OUTPUT, BURNT);
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
        return direction == Direction.EAST ? state.getValue(OUTPUT) : 0;
    }
}
