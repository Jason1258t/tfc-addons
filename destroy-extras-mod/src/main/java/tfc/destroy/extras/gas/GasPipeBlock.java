package tfc.destroy.extras.gas;

import com.mojang.serialization.MapCodec;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import petrolpark.mc.destroy.DestroyDataComponents;

public final class GasPipeBlock extends PipeBlock {
    public static final MapCodec<GasPipeBlock> CODEC = simpleCodec(GasPipeBlock::new);
    private static final int TRANSFER_LIMIT = 250;
    private static final int MAX_PIPES = 256;
    private static final Direction[] DIRECTIONS = Direction.values();
    // Жидкостный мод может вернуть поток в нашу трубу через собственную сеть.
    private static final ThreadLocal<Boolean> TRANSFERRING = new ThreadLocal<>();

    public GasPipeBlock(Properties properties) {
        super(1.5f / 16, properties);
        BlockState state = stateDefinition.any();
        for (var property : PROPERTY_BY_DIRECTION.values()) state = state.setValue(property, false);
        registerDefaultState(state);
    }

    @Override
    protected MapCodec<GasPipeBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        for (var property : PROPERTY_BY_DIRECTION.values()) builder.add(property);
    }

    private BlockState connection(BlockState state, LevelAccessor level, BlockPos pos, Direction side) {
        BlockPos neighbor = pos.relative(side);
        if (!level.hasChunkAt(neighbor)) return state;
        BlockState target = level.getBlockState(neighbor);
        boolean connected = target.getBlock() instanceof GasPipeBlock
            ? target.is(this)
            : level instanceof Level world && world.getCapability(Capabilities.FluidHandler.BLOCK, neighbor, side.getOpposite()) != null;
        return state.setValue(PROPERTY_BY_DIRECTION.get(side), connected);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        for (Direction side : DIRECTIONS) state = connection(state, context.getLevel(), context.getClickedPos(), side);
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction side, BlockState neighbor, LevelAccessor level,
        BlockPos pos, BlockPos neighborPos) {
        return connection(state, level, pos, side);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return reposition(state, rotation::rotate);
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return reposition(state, mirror::mirror);
    }

    private BlockState reposition(BlockState state, java.util.function.UnaryOperator<Direction> transform) {
        BlockState result = state;
        for (Direction side : DIRECTIONS) {
            result = result.setValue(PROPERTY_BY_DIRECTION.get(transform.apply(side)), state.getValue(PROPERTY_BY_DIRECTION.get(side)));
        }
        return result;
    }

    private boolean accepts(FluidStack stack) {
        String expected = this == GasPipes.HYDROGEN.get() ? "destroy:hydrogen" : "destroy:chlorine";
        return expected.equals(GasTankLogic.gasSpecies(stack));
    }

    IFluidHandler handler(Level level, BlockPos pos, Direction from) {
        return new Port(level, pos.immutable(), from);
    }

    /** Автоматическая выдача только в выделенную газовую линию. */
    public static void pushIntoPipe(Level level, BlockPos sourcePos, Direction toward, IFluidHandler source) {
        pushIntoPipe(level, sourcePos, toward, source, null);
    }

    static void pushIntoPipe(Level level, BlockPos sourcePos, Direction toward, IFluidHandler source, BlockPos blockedReceiver) {
        BlockPos pipePos = sourcePos.relative(toward);
        if (!level.hasChunkAt(pipePos) || !(level.getBlockState(pipePos).getBlock() instanceof GasPipeBlock pipe)) return;
        FluidStack gas = source.drain(TRANSFER_LIMIT, FluidAction.SIMULATE);
        if (!pipe.accepts(gas)) return;
        int moved = pipe.transfer(level, pipePos, toward.getOpposite(), gas, FluidAction.EXECUTE, blockedReceiver);
        if (moved > 0) source.drain(gas.copyWithAmount(moved), FluidAction.EXECUTE);
    }

    private int route(Level level, BlockPos inlet, Direction from, FluidStack gas, FluidAction action, BlockPos blockedReceiver) {
        BlockPos source = from == null ? null : inlet.relative(from);
        var visited = new HashSet<BlockPos>();
        var queue = new ArrayDeque<BlockPos>();
        var receivers = new LinkedHashSet<Receiver>();
        visited.add(inlet);
        queue.add(inlet);
        while (!queue.isEmpty()) {
            BlockPos pipe = queue.removeFirst();
            for (Direction side : DIRECTIONS) {
                BlockPos target = pipe.relative(side);
                if (target.equals(source) || target.equals(blockedReceiver) || !level.hasChunkAt(target)) continue;
                BlockState targetState = level.getBlockState(target);
                if (targetState.getBlock() instanceof GasPipeBlock next) {
                    if (next == this && visited.add(target)) {
                        if (visited.size() > MAX_PIPES) return 0;
                        queue.add(target);
                    }
                } else if (!targetState.isAir()) {
                    receivers.add(new Receiver(target, side.getOpposite()));
                }
            }
        }

        FluidStack offer = gas.copyWithAmount(Math.min(gas.getAmount(), TRANSFER_LIMIT));
        for (Receiver receiver : receivers) {
            var handler = level.getCapability(Capabilities.FluidHandler.BLOCK, receiver.pos(), receiver.side());
            if (handler == null) continue;
            int delivered = handler.fill(offer, action);
            if (delivered > 0) return delivered;
        }
        return 0;
    }

    private int transfer(Level level, BlockPos pos, Direction from, FluidStack resource, FluidAction action, BlockPos blockedReceiver) {
        if (level.isClientSide || TRANSFERRING.get() != null || !accepts(resource)
            || !level.hasChunkAt(pos) || !level.getBlockState(pos).is(GasPipeBlock.this)) return 0;
        TRANSFERRING.set(true);
        try {
            FluidStack gas = resource.copy();
            var mixture = resource.get(DestroyDataComponents.MIXTURE).copy();
            mixture.getList("Contents", Tag.TAG_COMPOUND).getCompound(0).putFloat("Gaseous", 1);
            gas.set(DestroyDataComponents.MIXTURE, mixture);
            return route(level, pos, from, gas, action, blockedReceiver);
        } finally {
            TRANSFERRING.remove();
        }
    }

    private record Receiver(BlockPos pos, Direction side) {}

    private final class Port implements IFluidHandler {
        private final Level level;
        private final BlockPos pos;
        private final Direction from;

        Port(Level level, BlockPos pos, Direction from) {
            this.level = level;
            this.pos = pos;
            this.from = from;
        }

        @Override public int getTanks() { return 1; }
        @Override public FluidStack getFluidInTank(int tank) { return FluidStack.EMPTY; }
        @Override public int getTankCapacity(int tank) { return TRANSFER_LIMIT; }
        @Override public boolean isFluidValid(int tank, FluidStack stack) { return tank == 0 && accepts(stack); }
        @Override public FluidStack drain(FluidStack stack, FluidAction action) { return FluidStack.EMPTY; }
        @Override public FluidStack drain(int amount, FluidAction action) { return FluidStack.EMPTY; }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return transfer(level, pos, from, resource, action, null);
        }
    }
}
