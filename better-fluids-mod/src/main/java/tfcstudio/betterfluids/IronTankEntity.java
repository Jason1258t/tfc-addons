package tfcstudio.betterfluids;

import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public final class IronTankEntity extends FluidTankBlockEntity {
    public static final int CAPACITY = 14_000;
    private int disconnected;
    private boolean legacy;
    private NetworkSnapshot snapshot;
    private final TankNetwork handler = new TankNetwork(this);

    IronTankEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public int sectionCapacity() {
        return getBlockState().getBlock() instanceof IronTankBlock block ? block.section.capacity : CAPACITY;
    }

    IFluidHandler fluidHandler() {
        return handler;
    }

    @Override
    public void invalidate() {
        invalidateNetwork();
        super.invalidate();
    }

    boolean legacy() {
        return legacy;
    }

    @Override
    protected void updateConnectivity() {
        updateConnectivity = false;
        updateShell();
    }

    @Override
    public void tick() {
        if (!level.isClientSide && legacy) {
            migrate();
        }
        super.tick();
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        legacy = !tag.contains("DisconnectedFaces")
            && (!isController() || getWidth() > 1 || getHeight() > 1);
        int previous = disconnected;
        disconnected = tag.getInt("DisconnectedFaces");
        if (clientPacket && level != null && previous != disconnected) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 8);
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = worldPosition.relative(direction);
                BlockState state = level.getBlockState(neighbor);
                level.sendBlockUpdated(neighbor, state, state, 8);
            }
        }
    }

    @Override
    public void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (!legacy) {
            tag.putInt("DisconnectedFaces", disconnected);
        }
    }

    private static final class NetworkSnapshot {
        final long tick;
        final List<IronTankEntity> parts;
        boolean invalidated;
        boolean comparatorsUpdated;

        NetworkSnapshot(long tick, List<IronTankEntity> parts) {
            this.tick = tick;
            this.parts = parts;
        }
    }

    List<IronTankEntity> networkParts() {
        if (level == null || level.isClientSide || legacy || isRemoved()) {
            return connectedParts(direction -> true);
        }
        long tick = level.getGameTime();
        if (snapshot == null || snapshot.tick != tick || snapshot.invalidated) {
            var fresh = new NetworkSnapshot(tick, connectedParts(direction -> true));
            for (IronTankEntity part : fresh.parts) {
                part.snapshot = fresh;
            }
        }
        return snapshot.parts;
    }

    void updateNetworkComparators() {
        var parts = networkParts();
        if (!parts.isEmpty() && snapshot != null && !snapshot.comparatorsUpdated) {
            snapshot.comparatorsUpdated = true;
            for (IronTankEntity part : parts) {
                level.updateNeighbourForOutputSignal(part.getBlockPos(), part.getBlockState().getBlock());
            }
        }
    }

    double networkFillFraction() {
        var parts = networkParts();
        return parts.isEmpty() ? 0.0 : parts.stream().mapToLong(part -> part.getTankInventory().getFluidAmount()).sum()
            / (double) parts.stream().mapToLong(IronTankEntity::sectionCapacity).sum();
    }

    void invalidateNetwork() {
        if (snapshot != null) {
            snapshot.invalidated = true;
        }
    }

    void neighborRemoved(Direction direction) {
        int updated = disconnected & ~bit(direction);
        if (updated != disconnected) {
            disconnected = updated;
            setChanged();
            sendDataImmediately();
        }
        invalidateNetwork();
        updateShell();
        queueComparatorUpdate();
    }

    private void migrate() {
        if (!isController() || !level.hasChunksAt(worldPosition, worldPosition.offset(width - 1, 0, width - 1))) {
            return;
        }

        var parts = new ArrayList<IronTankEntity>();
        for (BlockPos pos : BlockPos.betweenClosed(worldPosition, worldPosition.offset(width - 1, height - 1, width - 1))) {
            if (!(level.getBlockEntity(pos) instanceof IronTankEntity part) || !part.legacy
                || !part.getController().equals(worldPosition)) {
                return;
            }
            parts.add(part);
        }

        FluidStack fluid = getTankInventory().getFluid().copy();
        for (IronTankEntity part : parts) {
            part.window = window;
            part.legacy = false;
            part.controller = null;
            part.width = 1;
            part.height = 1;
            int amount = Math.min(CAPACITY, fluid.getAmount());
            part.getTankInventory().setCapacity(CAPACITY);
            part.getTankInventory().setFluid(fluid.copyWithAmount(amount));
            fluid.shrink(amount);
        }
        for (IronTankEntity part : parts) {
            part.updateNeighborhood();
            part.onFluidStackChanged(part.getTankInventory().getFluid());
        }
    }

    boolean hasCutNeighbor() {
        for (Direction direction : Direction.values()) {
            BlockPos pos = worldPosition.relative(direction);
            if (level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof IronTankEntity part
                && !part.legacy && !connected(level, worldPosition, direction)) {
                return true;
            }
        }
        return false;
    }

    void preserveBoundary(Set<BlockPos> group) {
        int previous = disconnected;
        for (Direction direction : Direction.values()) {
            BlockPos pos = worldPosition.relative(direction);
            if (!group.contains(pos) && connected(level, worldPosition, direction)) {
                disconnected |= bit(direction);
            }
        }
        if (previous != disconnected) {
            connectionsChanged();
        }
    }

    void toggleConnections() {
        if (legacy) {
            return;
        }
        int attached = 0;
        for (Direction direction : Direction.values()) {
            if (connected(level, worldPosition, direction)) {
                attached |= bit(direction);
            }
        }
        disconnected = disconnected == 0 ? attached : 0;
        connectionsChanged();
    }

    private void connectionsChanged() {
        setChanged();
        sendDataImmediately();
        updateNeighborhood();
    }

    void updateNeighborhood() {
        invalidateNetwork();
        updateShell();
        queueComparatorUpdate();
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = worldPosition.relative(direction);
            if (level.hasChunkAt(neighbor) && level.getBlockEntity(neighbor) instanceof IronTankEntity part) {
                part.invalidateNetwork();
                part.updateShell();
                part.queueComparatorUpdate();
            }
        }
    }

    void queueComparatorUpdate() {
        if (!level.isClientSide) {
            level.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
        }
    }

    void updateShell() {
        if (level == null || level.isClientSide || legacy || !level.getBlockState(worldPosition).is(BetterFluids.TANK.get())) {
            return;
        }
        BlockState state = getBlockState();
        BlockState updated = state.setValue(IronTankBlock.TOP, !shellConnected(level, worldPosition, Direction.UP))
            .setValue(IronTankBlock.BOTTOM, !shellConnected(level, worldPosition, Direction.DOWN))
            .setValue(IronTankBlock.SHAPE, window ? IronTankBlock.Shape.WINDOW : IronTankBlock.Shape.PLAIN);
        if (updated != state) {
            level.setBlock(worldPosition, updated, 3);
        }
    }

    @Override
    public void setWindows(boolean open) {
        if (legacy) {
            super.setWindows(open);
        } else {
            window = open;
            updateShell();
            setChanged();
            sendData();
        }
    }

    @Override
    public void updateBoilerState() {
        // Железный резервуар хранит жидкость.
    }

    List<IronTankEntity> connectedParts(Predicate<Direction> allowed) {
        var parts = new ArrayList<IronTankEntity>();
        if (level == null || legacy || isRemoved()) {
            return parts;
        }
        var visited = new HashSet<BlockPos>();
        parts.add(this);
        visited.add(worldPosition);
        for (int index = 0; index < parts.size(); index++) {
            BlockPos pos = parts.get(index).getBlockPos();
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = pos.relative(direction);
                if (allowed.test(direction) && !visited.contains(neighbor) && level.hasChunkAt(neighbor)
                    && connected(level, pos, direction)) {
                    visited.add(neighbor);
                    parts.add((IronTankEntity) level.getBlockEntity(neighbor));
                }
            }
        }
        return parts;
    }

    public static boolean shellConnected(BlockGetter world, BlockPos pos, Direction direction) {
        return world.getBlockState(pos).is(BetterFluids.TANK.get())
            && connected(world, pos, direction)
            && world.getBlockState(pos.relative(direction)).is(BetterFluids.TANK.get());
    }

    public static boolean connected(BlockGetter world, BlockPos pos, Direction direction) {
        BlockPos neighbor = pos.relative(direction);
        if (world instanceof Level level && !level.hasChunkAt(neighbor)) {
            return false;
        }
        if (!(world.getBlockEntity(pos) instanceof IronTankEntity first)
            || !(world.getBlockEntity(neighbor) instanceof IronTankEntity second)) {
            return false;
        }
        if (first.legacy || second.legacy) {
            return first.legacy && second.legacy && first.getController().equals(second.getController());
        }
        return (first.disconnected & bit(direction)) == 0
            && (second.disconnected & bit(direction.getOpposite())) == 0;
    }

    private static int bit(Direction direction) {
        return 1 << direction.ordinal();
    }
}
