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
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
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
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;

public final class PcbCircuitBlock extends SimpleElectricalDeviceBlock<PcbCircuitDevice> implements IBE<PcbCircuitBlockEntity> {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final BooleanProperty CUSTOM = BooleanProperty.create("custom");
    public static final BooleanProperty BURNT = BooleanProperty.create("burnt");
    public static final IntegerProperty OUTPUT = IntegerProperty.create("output", 0, 15);
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    private static final VoxelShape[] SHAPES = java.util.Arrays.stream(Direction.values())
            .map(face -> mountedShape(Block.box(0, 0, 0, 16, 2, 16), face))
            .toArray(VoxelShape[]::new);
    private static final NodeConfigurator NODES = new NodeConfigurator.Builder()
            .add(4, 2, 4).add(12, 2, 4).add(12, 2, 12).add(4, 2, 12).simple();

    public PcbCircuitBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(LIT, false).setValue(OUTPUT, 0).setValue(BURNT, false).setValue(CUSTOM, false).setValue(FACING, Direction.UP));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    static Vec3 mountedPoint(Vec3 point, Direction face) {
        double x = 0.125 + 0.75 * point.x;
        double y = point.y;
        double z = 0.125 + 0.75 * point.z;
        return switch (face) {
            case UP -> new Vec3(x, y, z);
            case DOWN -> new Vec3(x, 1 - y, 1 - z);
            case NORTH -> new Vec3(x, z, 1 - y);
            case SOUTH -> new Vec3(1 - x, z, y);
            case EAST -> new Vec3(y, z, x);
            case WEST -> new Vec3(1 - y, z, 1 - x);
        };
    }

    static Direction mountedDirection(Direction local, Direction face) {
        Vec3 vector = mountedPoint(Vec3.atLowerCornerOf(local.getNormal()), face)
                .subtract(mountedPoint(Vec3.ZERO, face));
        return Direction.getNearest(vector.x, vector.y, vector.z);
    }

    static Vec3 localPoint(Vec3 hit, Direction face) {
        Vec3 origin = mountedPoint(Vec3.ZERO, face);
        Vec3 delta = hit.subtract(origin);
        Vec3 right = mountedPoint(new Vec3(1, 0, 0), face).subtract(origin);
        Vec3 back = mountedPoint(new Vec3(0, 0, 1), face).subtract(origin);
        return new Vec3(delta.dot(right) / right.lengthSqr(), 0, delta.dot(back) / back.lengthSqr());
    }

    static int hitCell(Vec3 hit, Direction face) {
        Vec3 point = localPoint(hit, face);
        int x = (int) Math.floor(point.x * PcbTopology.SIZE);
        int z = (int) Math.floor(point.z * PcbTopology.SIZE);
        return x < 0 || z < 0 || x >= PcbTopology.SIZE || z >= PcbTopology.SIZE ? -1 : z * PcbTopology.SIZE + x;
    }

    static int sliderPosition(Vec3 point, int index, int definition) {
        int anchor = index % PcbTopology.CELLS;
        double x = (point.x * PcbTopology.SIZE - anchor % PcbTopology.SIZE) / PcbFootprint.width(definition);
        double z = (point.z * PcbTopology.SIZE - anchor / PcbTopology.SIZE) / PcbFootprint.height(definition);
        double value = switch (PcbWiring.rotation(definition)) { case 1 -> 1 - x; case 2 -> 1 - z; case 3 -> x; default -> z; };
        return (int) Math.clamp(Math.round(value * 100), 0, 100);
    }

    static net.minecraft.world.phys.AABB contactBounds(int cell, Direction face) {
        Vec3 first = mountedPoint(new Vec3(cell % 16 / 16.0, 1.0/16, cell / 16 / 16.0), face);
        Vec3 last = mountedPoint(new Vec3((cell % 16 + 1) / 16.0, 2.0/16, (cell / 16 + 1) / 16.0), face);
        return new net.minecraft.world.phys.AABB(first, last);
    }

    @Override
    public float getNodeSize(Level level, BlockPos pos, BlockState state, int node) {
        if (state.getValue(CUSTOM) && level.getBlockEntity(pos) instanceof PcbCircuitBlockEntity board) {
            int[] contacts = board.positionedContacts();
            if (contacts != null && node >= 0 && node < contacts.length) return .75f/16;
        }
        return .25f;
    }

    static VoxelShape mountedShape(VoxelShape local, Direction face) {
        VoxelShape result = Shapes.empty();
        for (var box : local.toAabbs()) {
            Vec3 a = mountedPoint(new Vec3(box.minX, box.minY, box.minZ), face);
            Vec3 b = mountedPoint(new Vec3(box.maxX, box.maxY, box.maxZ), face);
            result = Shapes.or(result, Shapes.box(Math.min(a.x, b.x), Math.min(a.y, b.y), Math.min(a.z, b.z),
                    Math.max(a.x, b.x), Math.max(a.y, b.y), Math.max(a.z, b.z)));
        }
        return result;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(CUSTOM) && level.getBlockEntity(pos) instanceof PcbCircuitBlockEntity board)
            return board.shape();
        return SHAPES[state.getValue(FACING).ordinal()];
    }

    @Override
    public SimulatedDeviceType<PcbCircuitDevice> getDevice() {
        return PcbItems.CIRCUIT_DEVICE.get();
    }

    @Override
    public Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) {
        if (level.getBlockEntity(pos) instanceof PcbCircuitBlockEntity board) {
            int[] contacts = board.positionedContacts();
            if (contacts != null) {
                Map<Integer, Vec3> nodes = new java.util.LinkedHashMap<>();
                for (int id = 0; id < contacts.length; id++) {
                    int cell = contacts[id];
                    nodes.put(id, mountedPoint(new Vec3((cell % PcbTopology.SIZE + 0.5) / PcbTopology.SIZE,
                            2.0 / 16, (cell / PcbTopology.SIZE + 0.5) / PcbTopology.SIZE), state.getValue(FACING)));
                }
                PcbPowerInlet.addNodes(board, state, nodes);
                return nodes;
            }
        }
        Map<Integer, Vec3> nodes = new java.util.LinkedHashMap<>();
        NODES.getNodes(Direction.UP).forEach((id, point) -> nodes.put(id, mountedPoint(point, state.getValue(FACING))));
        return nodes;
    }

    @Override
    public boolean isNodeAccessible(Level level, BlockPos pos, BlockState state, int node) {
        return node < PcbPowerInlet.BASE;
    }

    @Override
    public Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int node) {
        return getNodePositions(level, pos, state).get(node);
    }

    @Override
    public MutableComponent getNodeLabel(Level level, BlockPos pos, BlockState state, int node) {
        if (node >= PcbPowerInlet.BASE) return Component.literal(node == PcbPowerInlet.BASE ? "Ввод бортового трансформатора" : "Первичная жила " + (node-PcbPowerInlet.BASE));
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
        entity.setBoard(stack);
        int[] contacts = entity.positionedContacts();
        if (contacts == null) {
            device.setCircuit(parts, PcbCircuitDevice.Rating.values()[boardLevel - 1]);
        } else {
            device.setCircuit(parts, PcbCircuitDevice.Rating.values()[boardLevel - 1], contacts.length);
            level.setBlock(pos, state.setValue(CUSTOM, true), Block.UPDATE_ALL);
            ensureNodesExist(server, pos, level.getBlockState(pos));
        }
        device.restoreFuses(topology.getLong("BlownFuses"));
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder loot) {
        if (loot.getOptionalParameter(LootContextParams.BLOCK_ENTITY)
                instanceof PcbCircuitBlockEntity entity) {
            ItemStack board = entity.board();
            if (!board.isEmpty()) {
                var drops = new java.util.ArrayList<ItemStack>();
                drops.add(state.getValue(BURNT) ? board.transmuteCopy(PcbItems.BURNT_BOARD.get(), 1) : board);
                entity.addCapDrops(drops);
                return drops;
            }
        }
        return List.of();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LIT, OUTPUT, BURNT, CUSTOM, FACING);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(PcbItems.BUTTON_CAP.get()) && !stack.is(PcbItems.SWITCH_CAP.get())) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!(level.getBlockEntity(pos) instanceof PcbCircuitBlockEntity board))
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        int cell = hitCell(hit.getLocation().subtract(Vec3.atLowerCornerOf(pos)), state.getValue(FACING));
        int index = board.controlAt(cell);
        if (state.getValue(BURNT) || index < 0 || !board.acceptsCap(index) || board.hasCap(cell)
                || (stack.is(PcbItems.SWITCH_CAP.get()) != board.isSwitch(index))) return ItemInteractionResult.FAIL;
        if (!level.isClientSide) {
            board.addCap(cell);
            stack.consume(1, player);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (state.getValue(BURNT)) return InteractionResult.PASS;
        if (state.getValue(CUSTOM) && level.getBlockEntity(pos) instanceof PcbCircuitBlockEntity board) {
            int cell = hitCell(hit.getLocation().subtract(Vec3.atLowerCornerOf(pos)), state.getValue(FACING));
            int index = board.controlAt(cell);
            if (index < 0) return InteractionResult.PASS;
            if (level instanceof ServerLevel server) {
                var device = DevicesSavedData.load(server).getDevice(pos, PcbCircuitDevice.class);
                if (device != null) {
                    if (board.adjustable(index)) {
                        int definition = board.topology().getIntArray(PcbWiring.KEY)[index];
                        int value = sliderPosition(localPoint(hit.getLocation().subtract(Vec3.atLowerCornerOf(pos)), state.getValue(FACING)), index, definition);
                        boolean selector = PcbModules.get(definition).kind() == PcbCircuitDevice.Kind.THREE_POSITION_SWITCH;
                        if (selector) value = PcbCircuitDevice.selectorPosition(value);
                        if (device.adjustComponent(board.componentOrdinal(index), value)) {
                            String label = selector ? (value == 0 ? "контакт I" : value == 100 ? "контакт II" : "центр, цепи разомкнуты") : value + " %";
                            player.displayClientMessage(Component.literal("Положение: " + label), true);
                        }
                    } else {
                        String message = device.interactComponent(board.componentOrdinal(index), level.getGameTime(), player.isShiftKeyDown());
                        if (message != null) player.displayClientMessage(Component.literal(message), true);
                    }
                }
            }
        } else if (level instanceof ServerLevel server) {
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
        return direction == mountedDirection(Direction.EAST, state.getValue(FACING)) ? state.getValue(OUTPUT) : 0;
    }
}
