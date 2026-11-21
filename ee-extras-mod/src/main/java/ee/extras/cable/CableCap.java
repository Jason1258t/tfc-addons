package ee.extras.cable;

import com.george_vi.electroenergetics.content.bundled_wire.BundledWireTerminationBlock;
import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.simibubi.create.AllSoundEvents;

import ee.extras.EeExtras;
import ee.extras.PowerBlock;
import ee.extras.converter.BidiConverterBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/** Крышка закрывает конкретную электрическую пару; вход и выход одного прибора независимы. */
public class CableCap extends SavedData {
    static final String NAME = "ee_extras_cable_caps";
    static final int CHECK_TICKS = 10;

    public record Cap(BlockPos pos, int first, int second) {
        public int[] contacts() {
            return new int[] {first, second};
        }

        public boolean contains(int node) {
            return node == first || node == second;
        }

        CableEnd end() {
            return new CableEnd(pos, CableKind.DUPLEX, false, first, contacts());
        }
    }

    final Set<Cap> caps = new HashSet<>();
    private final Set<BlockPos> legacy = new HashSet<>();

    public static CableCap get(ServerLevel level) {
        var data =
                level.getDataStorage()
                        .computeIfAbsent(new Factory<>(CableCap::new, CableCap::load, null), NAME);
        data.migrate(level);
        return data;
    }

    void migrate(ServerLevel level) {
        for (var it = legacy.iterator(); it.hasNext(); ) {
            var pos = it.next();
            if (!level.isLoaded(pos)) continue;
            int[] c = contacts(level, pos, level.getBlockState(pos));
            if (c != null && c.length == 2) {
                caps.add(new Cap(pos, c[0], c[1]));
                it.remove();
                setDirty();
            }
        }
    }

    public boolean has(BlockPos pos) {
        return legacy.contains(pos) || caps.stream().anyMatch(c -> c.pos.equals(pos));
    }

    public static List<Cap> all(Level level, BlockPos pos) {
        return level instanceof ServerLevel sl
                ? get(sl).caps.stream().filter(c -> c.pos.equals(pos)).toList()
                : CableCapRender.at(level, pos);
    }

    public static boolean covered(Level level, BlockPos pos, int node) {
        return all(level, pos).stream().anyMatch(c -> c.contains(node));
    }

    public static int[] contacts(Level level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof ElectricalDeviceBlock<?> d)
                || state.getBlock() instanceof BundledWireTerminationBlock
                || state.getBlock() instanceof CableJunction) return null;
        var nodes = d.getNodePositions(level, pos, state);
        return nodes == null
                ? null
                : nodes.keySet().stream()
                        .filter(id -> d.isNodeAccessible(level, pos, state, id))
                        .mapToInt(Integer::intValue)
                        .sorted()
                        .toArray();
    }

    /** Трёхфазную группу не делим: крышка подходит только к полноценной двухконтактной стороне. */
    public static List<int[]> groups(Level level, BlockPos pos, BlockState state) {
        int[] c = contacts(level, pos, state);
        if (c == null) return List.of();
        if (c.length == 2) return List.of(c);
        if (state.getBlock() instanceof BidiConverterBlock)
            return List.of(new int[] {0, 1}, new int[] {2, 3});
        if (state.getBlock() instanceof PowerBlock<?> block) {
            var layout = block.layout();
            if (layout.inputs() == 2 && c.length == 4)
                return List.of(new int[] {0, 1}, new int[] {2, 3});
            if (layout.inputs() == 3 && c.length == 5) return List.of(new int[] {3, 4});
        }
        return List.of();
    }

    public static boolean isTwoContact(Level level, BlockPos pos, BlockState state) {
        return !groups(level, pos, state).isEmpty();
    }

    static boolean present(Level level, BlockPos pos) {
        return !all(level, pos).isEmpty();
    }

    static CableEnd end(Level level, BlockPos pos, BlockState state) {
        var entries = all(level, pos);
        return entries.size() == 1 ? entries.getFirst().end() : null;
    }

    public static CableEnd resolve(Level level, InWorldNode node) {
        if (!level.isLoaded(node.sourcePos())) return null;
        return all(level, node.sourcePos()).stream()
                .filter(c -> c.contains(node.id()))
                .findFirst()
                .map(Cap::end)
                .orElse(null);
    }

    private static int[] nearest(Level level, BlockPos pos, Vec3 worldHit) {
        var state = level.getBlockState(pos);
        var groups = groups(level, pos, state);
        if (groups.isEmpty()) return null;
        var device = (ElectricalDeviceBlock<?>) state.getBlock();
        var hit = worldHit.subtract(Vec3.atLowerCornerOf(pos));
        int node = -1;
        double distance = Double.POSITIVE_INFINITY;
        for (var entry : device.getNodePositions(level, pos, state).entrySet()) {
            if (!device.isNodeAccessible(level, pos, state, entry.getKey())) continue;
            double d = hit.distanceToSqr(entry.getValue());
            if (d < distance) {
                distance = d;
                node = entry.getKey();
            }
        }
        for (var pair : groups) if (pair[0] == node || pair[1] == node) return pair;
        return null;
    }

    public static CableEnd hit(Level level, BlockHitResult hit) {
        int[] c = nearest(level, hit.getBlockPos(), hit.getLocation());
        if (c == null) return null;
        var cap = new Cap(hit.getBlockPos(), c[0], c[1]);
        return all(level, hit.getBlockPos()).contains(cap) ? cap.end() : null;
    }

    public static InteractionResult put(Level level, BlockPos pos, Player player, ItemStack stack) {
        return put(level, pos, player, stack, Vec3.atCenterOf(pos));
    }

    public static InteractionResult put(
            Level level, BlockPos pos, Player player, ItemStack stack, Vec3 hit) {
        var state = level.getBlockState(pos);
        int[] c = nearest(level, pos, hit);
        if (c == null)
            return CableLaying.deny(
                    level,
                    player,
                    pos,
                    contacts(level, pos, state) == null ? "cap.not_device" : "cap.no_pair");
        var cap = new Cap(pos.immutable(), c[0], c[1]);
        if (all(level, pos).contains(cap))
            return CableLaying.deny(level, player, pos, "cap.already");
        if (!(level instanceof ServerLevel sl))
            return CableClient.hasWires(pos, c)
                    ? InteractionResult.FAIL
                    : InteractionResult.SUCCESS;
        ((ElectricalDeviceBlock<?>) state.getBlock()).ensureNodesExist(sl, pos, state);
        var sd = InfrastructureSavedData.load(sl);
        for (int id : c)
            if (!sd.getConnections(new InWorldNode(id, pos)).isEmpty())
                return CableLaying.deny(level, player, pos, "cap.busy");
        var data = get(sl);
        data.caps.add(cap);
        data.setDirty();
        CableCapSync.changed(sl, pos);
        if (player == null || !player.isCreative()) stack.shrink(1);
        AllSoundEvents.WRENCH_ROTATE.playOnServer(level, pos);
        if (player != null)
            player.displayClientMessage(
                    Component.translatable(
                            "ee_extras.cable.cap.placed",
                            label(sl, pos, state, c, 0),
                            label(sl, pos, state, c, 1)),
                    true);
        return InteractionResult.SUCCESS;
    }

    static MutableComponent label(Level level, BlockPos pos, BlockState state, int[] c, int i) {
        var d = (ElectricalDeviceBlock<?>) state.getBlock();
        var a = d.getNodeLabel(level, pos, state, c[0]);
        var b = d.getNodeLabel(level, pos, state, c[1]);
        return a.getString().equals(b.getString())
                ? Component.translatable("ee_extras.cable.cap.contact", i + 1)
                : i == 0 ? a : b;
    }

    public static boolean take(ServerLevel level, BlockPos pos, Player player) {
        return take(level, pos, player, Vec3.atCenterOf(pos));
    }

    public static boolean take(ServerLevel level, BlockPos pos, Player player, Vec3 hit) {
        int[] c = nearest(level, pos, hit);
        if (c == null) return false;
        var data = get(level);
        var cap = new Cap(pos, c[0], c[1]);
        if (!data.caps.remove(cap)) return false;
        data.setDirty();
        CableCapSync.changed(level, pos);
        int cables = CableData.get(level).removeAt(level, pos, c);
        if (!player.isCreative()) {
            player.getInventory().placeItemBackInInventory(new ItemStack(EeExtras.CABLE_CAP.get()));
            if (cables > 0)
                player.getInventory()
                        .placeItemBackInInventory(
                                new ItemStack(CableWires.spool(CableKind.DUPLEX), cables));
        }
        AllSoundEvents.WRENCH_REMOVE.playOnServer(level, pos);
        player.displayClientMessage(Component.translatable("ee_extras.cable.cap.removed"), true);
        return true;
    }

    void check(ServerLevel level) {
        for (var it = legacy.iterator(); it.hasNext(); ) {
            var pos = it.next();
            if (!level.isLoaded(pos)) continue;
            it.remove();
            setDirty();
            CableData.get(level).removeAt(level, pos);
            CableCapSync.changed(level, pos);
            Block.popResource(level, pos, new ItemStack(EeExtras.CABLE_CAP.get()));
        }
        for (var it = caps.iterator(); it.hasNext(); ) {
            var cap = it.next();
            if (!level.isLoaded(cap.pos)) continue;
            boolean valid =
                    groups(level, cap.pos, level.getBlockState(cap.pos)).stream()
                            .anyMatch(c -> c[0] == cap.first && c[1] == cap.second);
            if (valid) continue;
            it.remove();
            setDirty();
            CableData.get(level).removeAt(level, cap.pos, cap.contacts());
            CableCapSync.changed(level, cap.pos);
            Block.popResource(level, cap.pos, new ItemStack(EeExtras.CABLE_CAP.get()));
        }
    }

    static InteractionResult deny(Level level, Player player, BlockPos pos, Component message) {
        AllSoundEvents.DENY.playOnServer(level, pos);
        if (player != null) player.displayClientMessage(message, true);
        return InteractionResult.FAIL;
    }

    static ListTag encode(Collection<Cap> caps) {
        var list = new ListTag();
        for (var cap : caps) {
            var t = new CompoundTag();
            t.putLong("Pos", cap.pos.asLong());
            t.putInt("First", cap.first);
            t.putInt("Second", cap.second);
            list.add(t);
        }
        return list;
    }

    static Set<Cap> decode(CompoundTag tag) {
        Set<Cap> result = new HashSet<>();
        for (var raw : tag.getList("Pairs", Tag.TAG_COMPOUND)) {
            var t = (CompoundTag) raw;
            result.add(
                    new Cap(BlockPos.of(t.getLong("Pos")), t.getInt("First"), t.getInt("Second")));
        }
        return result;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("Pairs", encode(caps));
        tag.putLongArray("Caps", legacy.stream().mapToLong(BlockPos::asLong).toArray());
        return tag;
    }

    static CableCap load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new CableCap();
        data.caps.addAll(decode(tag));
        for (long p : tag.getLongArray("Caps")) data.legacy.add(BlockPos.of(p));
        return data;
    }
}
