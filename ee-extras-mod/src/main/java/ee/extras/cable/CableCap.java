package ee.extras.cable;

import com.george_vi.electroenergetics.content.bundled_wire.BundledWireTerminationBlock;
import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.simibubi.create.AllSoundEvents;
import ee.extras.EeExtras;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Крышка на прибор EE с двумя контактами — модификатор прибора, а не блок: в мире хранится только список приборов с
 * крышкой. Прибор с крышкой — конец двухжильного кабеля: жила 1 — на контакт 1, жила 2 — на контакт 2, оболочка
 * (декоративный провод) — на отдельную пару контактов между крышками. Узлы — те же, к которым EE подключает обычные провода, новой электрики нет.
 *
 * Какой прибор подходит — по коду EE (ElectricalDeviceBlock 1.1.2): у блока ровно два доступных узла
 * (getNodePositions, isNodeAccessible); контакт 1 — с меньшим номером. Соединители кабеля (BundledWireTerminationBlock,
 * CableJunction) — не приборы. Раз в CHECK_TICKS тиков сверка: прибор сломан или заменён — крышка выпадает на его месте,
 * кабель EE уже убрал вместе с узлами прибора (CableData дочищает остальное).
 */
public class CableCap extends SavedData {
    static final String NAME = "ee_extras_cable_caps";
    static final int CHECK_TICKS = 10;

    final Set<BlockPos> caps = new HashSet<>();

    public static CableCap get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(CableCap::new, CableCap::load, null), NAME);
    }

    public boolean has(BlockPos pos) {
        return caps.contains(pos);
    }

    /** Контакты блока для проводов по возрастанию номеров; null — не прибор EE или сам конец кабеля */
    public static int[] contacts(Level level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof ElectricalDeviceBlock<?> d) || state.getBlock() instanceof BundledWireTerminationBlock
                || state.getBlock() instanceof CableJunction) return null;
        Map<Integer, Vec3> nodes = d.getNodePositions(level, pos, state);
        if (nodes == null) return null;
        return nodes.keySet().stream().filter(id -> d.isNodeAccessible(level, pos, state, id))
                .mapToInt(Integer::intValue).sorted().toArray();
    }

    /** Прибор EE ровно с двумя контактами — на него можно поставить крышку */
    public static boolean isTwoContact(Level level, BlockPos pos, BlockState state) {
        int[] c = contacts(level, pos, state);
        return c != null && c.length == 2;
    }

    /** Прибор с крышкой как конец кабеля; null — крышки нет или у прибора уже не два контакта */
    static boolean present(Level level, BlockPos pos) {
        return level instanceof ServerLevel server ? get(server).has(pos) : CableCapRender.has(level, pos);
    }

    static CableEnd end(Level level, BlockPos pos, BlockState state) {
        if (!present(level, pos)) return null;
        int[] c = contacts(level, pos, state);
        if (c == null || c.length != 2) return null;
        return new CableEnd(pos, CableKind.DUPLEX, false, c[0], c);
    }

    /** Крышкой по прибору: поставить, если подходит, иначе — понятный отказ */
    public static InteractionResult put(Level level, BlockPos pos, Player player, ItemStack stack) {
        BlockState state = level.getBlockState(pos);
        int[] c = contacts(level, pos, state);
        if (!(level instanceof ServerLevel sl))
            return c != null && c.length == 2 && !present(level, pos) && !CableClient.hasWires(pos, c)
                    ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        if (c == null) return CableLaying.deny(level, player, pos, "cap.not_device");
        if (c.length != 2) return deny(level, player, pos, Component.translatable("ee_extras.cable.cap.contacts", c.length));
        CableCap caps = get(sl);
        if (caps.has(pos)) return CableLaying.deny(level, player, pos, "cap.already");
        ((ElectricalDeviceBlock<?>) state.getBlock()).ensureNodesExist(sl, pos, state);
        InfrastructureSavedData sd = InfrastructureSavedData.load(sl);
        for (int id : c)
            if (!sd.getConnections(new InWorldNode(id, pos)).isEmpty()) return CableLaying.deny(level, player, pos, "cap.busy");

        caps.caps.add(pos);
        caps.setDirty();
        CableCapSync.changed(sl, pos);
        if (player == null || !player.isCreative()) stack.shrink(1);
        AllSoundEvents.WRENCH_ROTATE.playOnServer(level, pos);
        if (player != null)
            player.displayClientMessage(Component.translatable("ee_extras.cable.cap.placed", label(sl, pos, state, c, 0), label(sl, pos, state, c, 1)), true);
        return InteractionResult.SUCCESS;
    }

    /** Подпись контакта i: своя подпись EE («+», «фаза»…), а если у обоих одинаковая — «контакт 1», «контакт 2» */
    static MutableComponent label(Level level, BlockPos pos, BlockState state, int[] c, int i) {
        ElectricalDeviceBlock<?> d = (ElectricalDeviceBlock<?>) state.getBlock();
        MutableComponent a = d.getNodeLabel(level, pos, state, c[0]);
        MutableComponent b = d.getNodeLabel(level, pos, state, c[1]);
        if (a.getString().equals(b.getString())) return Component.translatable("ee_extras.cable.cap.contact", i + 1);
        return i == 0 ? a : b;
    }

    /**
     * Снять крышку (Shift + клик пустой рукой по прибору): крышка и катушки кабелей — игроку, кабели к прибору убраны.
     * false — крышки нет.
     */
    public static boolean take(ServerLevel level, BlockPos pos, Player player) {
        CableCap caps = get(level);
        if (!caps.caps.remove(pos)) return false;
        caps.setDirty();
        CableCapSync.changed(level, pos);
        int cables = CableData.get(level).removeAt(level, pos);
        if (!player.isCreative()) {
            player.getInventory().placeItemBackInInventory(new ItemStack(EeExtras.CABLE_CAP.get()));
            if (cables > 0) player.getInventory().placeItemBackInInventory(new ItemStack(CableWires.spool(CableKind.DUPLEX), cables));
        }
        AllSoundEvents.WRENCH_REMOVE.playOnServer(level, pos);
        player.displayClientMessage(Component.translatable("ee_extras.cable.cap.removed"), true);
        return true;
    }

    /** Сверка: у прибора с крышкой пропал прибор (или у него уже не два контакта) — крышка выпадает */
    void check(ServerLevel level) {
        if (caps.isEmpty()) return;
        boolean changed = false;
        for (Iterator<BlockPos> it = caps.iterator(); it.hasNext(); ) {
            BlockPos pos = it.next();
            if (!level.isLoaded(pos)) continue;
            int[] c = contacts(level, pos, level.getBlockState(pos));
            if (c != null && c.length == 2) continue;
            it.remove();
            changed = true;
            CableCapSync.changed(level, pos);
            CableData.get(level).removeAt(level, pos);
            Block.popResource(level, pos, new ItemStack(EeExtras.CABLE_CAP.get()));
        }
        if (changed) setDirty();
    }

    static InteractionResult deny(Level level, Player player, BlockPos pos, Component message) {
        AllSoundEvents.DENY.playOnServer(level, pos);
        if (player != null) player.displayClientMessage(message, true);
        return InteractionResult.FAIL;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("Caps", new LongArrayTag(caps.stream().mapToLong(BlockPos::asLong).toArray()));
        return tag;
    }

    static CableCap load(CompoundTag tag, HolderLookup.Provider registries) {
        CableCap d = new CableCap();
        for (long p : tag.getLongArray("Caps")) d.caps.add(BlockPos.of(p));
        return d;
    }
}
