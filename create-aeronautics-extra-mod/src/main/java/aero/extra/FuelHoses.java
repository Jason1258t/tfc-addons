package aero.extra;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public final class FuelHoses extends SavedData {
    private static final Factory<FuelHoses> FACTORY = new Factory<>(FuelHoses::new, FuelHoses::load);
    private static final double MAX_SPAN = 8;
    private static final double MAX_ROUTE = 24;
    private final Map<BlockPos, List<Link>> links = new HashMap<>();

    private record Link(BlockPos a, BlockPos b, int cost) {
        BlockPos other(BlockPos pos) {
            return a.equals(pos) ? b : a;
        }

        double length() {
            return Math.sqrt(a.distSqr(b));
        }
    }

    private record Line(BlockPos end, double length) {}

    static FuelHoses get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, "aero_fuel_hoses");
    }

    static boolean endpoint(BlockState state) {
        return state.getBlock() instanceof FuelPumpBlock || state.getBlock() instanceof FuelPortBlock;
    }

    private static boolean relay(BlockState state) {
        return state.getBlock() instanceof FuelPortBlock && state.getValue(FuelPortBlock.RELAY);
    }

    private List<Link> at(BlockPos pos) {
        return links.getOrDefault(pos, List.of());
    }

    List<BlockPos> neighbors(BlockPos pos) {
        return at(pos).stream().map(link -> link.other(pos)).toList();
    }

    boolean connected(BlockPos pos) {
        return links.containsKey(pos);
    }

    private void put(Link link) {
        links.computeIfAbsent(link.a, key -> new ArrayList<>(2)).add(link);
        links.computeIfAbsent(link.b, key -> new ArrayList<>(2)).add(link);
    }

    private void removeAt(BlockPos pos, Link link) {
        List<Link> entries = links.get(pos);
        entries.remove(link);
        if (entries.isEmpty()) links.remove(pos);
    }

    private void remove(Link link) {
        removeAt(link.a, link);
        removeAt(link.b, link);
    }

    private static void sync(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return;
        BlockState state = level.getBlockState(pos);
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
    }

    int connect(ServerLevel level, BlockPos a, BlockPos b, int available) {
        if (a.equals(b)) return 0;
        Link link = new Link(a.immutable(), b.immutable(), (int) Math.ceil(Math.sqrt(a.distSqr(b))));
        if (link.length() > MAX_SPAN || link.cost > available) return 0;

        Set<BlockPos> seen = new HashSet<>();
        Line first = walk(level, a, seen, true);
        Line second = walk(level, b, seen, true);
        if (first == null || second == null || first.length + link.length() + second.length > MAX_ROUTE) return 0;
        BlockState left = level.getBlockState(first.end);
        BlockState right = level.getBlockState(second.end);
        if (!relay(left) && !relay(right) && left.getBlock() == right.getBlock()) return 0;
        if (ModList.get().isLoaded("sable") && !SableFuelGeometry.samePlot(level, a, b)) return 0;
        if (!clear(level, a, b)) return 0;

        put(available == Integer.MAX_VALUE ? new Link(link.a, link.b, 0) : link);
        setDirty();
        sync(level, a);
        sync(level, b);
        return link.cost;
    }

    // Идём по одной цепочке, проверяя доступность каждого блока и исключая циклы.
    private Line walk(ServerLevel level, BlockPos start, Set<BlockPos> seen, boolean adding) {
        BlockPos previous = null;
        BlockPos current = start;
        double length = 0;
        while (length <= MAX_ROUTE && seen.add(current) && level.hasChunkAt(current)) {
            BlockState state = level.getBlockState(current);
            if (!endpoint(state)) return null;
            List<Link> entries = at(current);
            int limit = relay(state) ? 2 : 1;
            if (entries.size() + (adding && previous == null ? 1 : 0) > limit) return null;
            Link next = null;
            for (Link candidate : entries) {
                if (!candidate.other(current).equals(previous)) next = candidate;
            }
            if (next == null) return new Line(current, length);
            previous = current;
            current = next.other(current);
            length += next.length();
        }
        return null;
    }

    IFluidHandler source(ServerLevel level, BlockPos pump) {
        Line route = walk(level, pump, new HashSet<>(), false);
        if (route == null) return null;
        BlockState state = level.getBlockState(route.end);
        return state.getBlock() instanceof FuelPortBlock ? FuelPortBlock.tank(level, route.end, state) : null;
    }

    int disconnect(ServerLevel level, BlockPos pos) {
        var removed = List.copyOf(at(pos));
        int cost = 0;
        for (Link link : removed) {
            remove(link);
            cost += link.cost;
            sync(level, link.other(pos));
        }
        if (!removed.isEmpty()) {
            setDirty();
            sync(level, pos);
        }
        return cost;
    }

    static void drop(ServerLevel level, BlockPos pos, int count) {
        if (count > 0) Block.popResource(level, pos, new ItemStack(AeroExtra.FUEL_HOSE.get(), count));
    }

    public static void move(ServerLevel from, ServerLevel to, Iterable<BlockPos> blocks,
                            java.util.function.UnaryOperator<BlockPos> transform) {
        FuelHoses old = get(from);
        if (old.links.isEmpty()) return;
        Set<BlockPos> moved = new HashSet<>();
        blocks.forEach(pos -> moved.add(pos.immutable()));
        Set<Link> affected = new HashSet<>();
        moved.forEach(pos -> affected.addAll(old.at(pos)));
        if (affected.isEmpty()) return;
        FuelHoses next = get(to);
        for (Link link : affected) old.remove(link);
        for (Link link : affected) {
            boolean moveA = moved.contains(link.a);
            boolean moveB = moved.contains(link.b);
            BlockPos a = moveA ? transform.apply(link.a) : link.a;
            BlockPos b = moveB ? transform.apply(link.b) : link.b;
            if (moveA && moveB) {
                next.put(new Link(a, b, link.cost));
            } else {
                BlockPos remaining = moveA ? link.b : link.a;
                BlockPos moving = moveA ? link.a : link.b;
                drop(from, from.hasChunkAt(remaining) ? remaining : moving, link.cost);
                sync(from, remaining);
            }
        }
        old.setDirty();
        next.setDirty();
    }

    static Vec3 curve(BlockPos a, BlockPos b, double t) {
        Vec3 start = Vec3.atCenterOf(a);
        Vec3 end = Vec3.atCenterOf(b);
        double sag = Math.min(0.45, Math.hypot(end.x - start.x, end.z - start.z) / 12);
        return start.lerp(end, t).add(0, -4 * sag * t * (1 - t), 0);
    }

    private static boolean clear(Level level, BlockPos a, BlockPos b) {
        for (int step = 0; step < 16; step++) {
            Vec3 start = curve(a, b, step / 16.0);
            Vec3 end = curve(a, b, (step + 1) / 16.0);
            boolean obstructed = BlockGetter.traverseBlocks(start, end, level, (world, pos) -> {
                if (!world.hasChunkAt(pos)) return true;
                if (pos.equals(a) || pos.equals(b)) return null;
                return world.getBlockState(pos).getCollisionShape(world, pos).clip(start, end, pos) != null ? true : null;
            }, world -> false);
            if (obstructed) return false;
        }
        return true;
    }

    private static FuelHoses load(CompoundTag tag, HolderLookup.Provider registries) {
        FuelHoses data = new FuelHoses();
        for (Tag entry : tag.getList("Links", Tag.TAG_COMPOUND)) {
            CompoundTag row = (CompoundTag) entry;
            Link link = new Link(BlockPos.of(row.getLong("A")), BlockPos.of(row.getLong("B")), row.getInt("Cost"));
            if (link.cost >= 0 && link.cost <= MAX_SPAN && link.length() > 0 && link.length() <= MAX_SPAN) data.put(link);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        for (var entry : links.entrySet()) {
            for (Link link : entry.getValue()) {
                if (!entry.getKey().equals(link.a)) continue;
                CompoundTag row = new CompoundTag();
                row.putLong("A", link.a.asLong());
                row.putLong("B", link.b.asLong());
                row.putInt("Cost", link.cost);
                entries.add(row);
            }
        }
        tag.put("Links", entries);
        return tag;
    }
}
