package ee.extras.redstone;

import com.george_vi.electroenergetics.content.connector.InsulatorDevice;
import com.george_vi.electroenergetics.content.electrical_panel.*;
import com.george_vi.electroenergetics.content.electrical_panel.attachments.*;
import com.george_vi.electroenergetics.content.electrical_panel.special_interaction.*;
import com.george_vi.electroenergetics.devices.device.*;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.WireType;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import ee.extras.EeExtras;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.registries.*;
import java.util.*;

/** 16 независимых каналов 0–15 поверх декоративных (неэлектрических) соединений EE. */
public final class Signals {
    public static final int BASE = 0x6000;
    public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<InsulatorDevice>> DEVICE =
            EeExtras.DEVICES.register("signal_connector", () -> new SimulatedDeviceType<>(EeExtras.rl("signal_connector"),
                    (type, level, pos, data) -> new SignalDevice(level, pos, data, type)));
    public static final DeferredBlock<SignalConnector> CONNECTOR = EeExtras.BLOCKS.register("signal_connector",
            () -> new SignalConnector(BlockBehaviour.Properties.of().strength(1).noOcclusion()));
    public static final DeferredItem<BlockItem> CONNECTOR_ITEM = EeExtras.ITEMS.registerSimpleBlockItem(CONNECTOR);
    public static final DeferredItem<Item> SEGMENT = EeExtras.ITEMS.registerItem("signal_wire", Item::new);
    public static final DeferredItem<SignalSpool> SPOOL = EeExtras.ITEMS.registerItem("signal_spool", SignalSpool::new);
    public static final DeferredHolder<WireType, WireType> WIRE = EeExtras.WIRES.register("signal_wire", () ->
            new WireType.Builder(PartialModel.of(EeExtras.rl("block/signal_wire")))
                    .decorative().invulnerable().spoolItem(SPOOL::get).droppedItem(SEGMENT::get)
                    .maxLength(() -> 32).sag(.5f).thickness(.0625f).build());

    public static void register() { NeoForge.EVENT_BUS.register(Signals.class); }
    public static boolean reserved(int id) { return id >= BASE && id < BASE + ElectricalPanelSlot.values().length; }
    public static boolean supported(PanelAttachment a) {
        return a instanceof AnalogLeverPanelAttachment || a instanceof SteeringWheelPanelAttachment
                || a instanceof CutOffSwitchPanelAttachment || a instanceof MomentarySwitchPanelAttachment;
    }
    public static PanelAttachment attachment(Level level, InWorldNode node) {
        if (!reserved(node.id()) || !level.hasChunkAt(node.sourcePos())) return null;
        if (!(level.getBlockEntity(node.sourcePos()) instanceof ElectricalPanelBlockEntity panel)) return null;
        var a = panel.getAttachments()[node.id() - BASE];
        return supported(a) ? a : null;
    }
    public static boolean valid(Level level, InWorldNode node) {
        return level.hasChunkAt(node.sourcePos()) && (node.id() == BASE
                && level.getBlockState(node.sourcePos()).getBlock() instanceof SignalConnector
                || attachment(level, node) != null);
    }
    public static Vec3 panelPoint(Level level, InWorldNode node) {
        var a = attachment(level, node);
        return a == null ? null : a.getCenter().subtract(Vec3.atLowerCornerOf(node.sourcePos()));
    }
    public static InWorldNode hit(Level level, BlockPos pos, Direction face, Vec3 point) {
        if (level.getBlockState(pos).getBlock() instanceof SignalConnector) return new InWorldNode(BASE, pos);
        if (!(level.getBlockEntity(pos) instanceof ElectricalPanelBlockEntity panel)
                || face != panel.getBlockState().getValue(ElectricalPanelBlock.FACING)) return null;
        var slot = panel.getHoveringAttachmentIndex(point.subtract(Vec3.atLowerCornerOf(pos)));
        return slot != null && supported(panel.getAttachments()[slot.ordinal()]) ? new InWorldNode(BASE + slot.ordinal(), pos) : null;
    }
    private static int channel(PanelAttachment a) {
        if (a instanceof AnalogLeverPanelAttachment lever && lever.color != null) return lever.color.getId();
        return DyeColor.WHITE.getId();
    }
    private static int source(PanelAttachment a) {
        if (a instanceof AnalogLeverPanelAttachment lever) return lever.redstoneSignal;
        if (a instanceof SteeringWheelPanelAttachment wheel) return wheel.redstoneSignal;
        if (a instanceof CutOffSwitchPanelAttachment sw) return sw.isClosed ? 15 : 0;
        if (a instanceof MomentarySwitchPanelAttachment button) return button.closedTicks > 0 ? 15 : 0;
        return 0;
    }

    @SubscribeEvent public static void configure(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel().getBlockState(event.getPos()).getBlock() instanceof SignalConnector)) return;
        var stack = event.getItemStack();
        boolean dye = stack.getItem() instanceof DyeItem;
        boolean inspect = stack.isEmpty();
        boolean wrench = com.simibubi.create.AllItems.WRENCH.isIn(stack)
                || net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals("immersiveengineering:screwdriver");
        if (!dye && !wrench && !inspect) return;
        if (event.getEntity().isShiftKeyDown() && wrench) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!(event.getLevel() instanceof ServerLevel sl) || !event.getEntity().mayBuild()) return;
        var data = DevicesSavedData.load(sl);
        var device = data.getDevice(event.getPos(), SignalDevice.class);
        if (device == null) return;
        if (dye) device.channel = ((DyeItem) stack.getItem()).getDyeColor().getId();
        else if (wrench) device.output = !device.output;
        if (!inspect) { device.signal = 0; device.needsNotify = true; }
        data.setDirty();
        sl.updateNeighborsAt(event.getPos(), CONNECTOR.get());
        sl.updateNeighborsAt(event.getPos().relative(sl.getBlockState(event.getPos()).getValue(SignalConnector.FACING).getOpposite()), CONNECTOR.get());
        event.getEntity().displayClientMessage(Component.translatable("ee_extras.signal.config",
                Component.translatable(device.output ? "ee_extras.signal.output" : "ee_extras.signal.input"),
                Component.translatable("color.minecraft." + DyeColor.byId(device.channel).getName())), true);
    }

    @SubscribeEvent public static void tick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var data = InfrastructureSavedData.load(level);
        var devices = DevicesSavedData.load(level);
        Set<InWorldNode> visited = new HashSet<>();
        // Только сигнальные контакты; обычные провода и силовые устройства не участвуют.
        for (var start : new ArrayList<>(data.getNodes())) {
            if (!reserved(start.id()) || visited.contains(start) || !valid(level, start)) continue;
            var queue = new ArrayDeque<InWorldNode>();
            var outputs = new ArrayList<SignalDevice>();
            int[] values = new int[16];
            queue.add(start);
            visited.add(start);
            while (!queue.isEmpty()) {
                var node = queue.remove();
                var device = devices.getDevice(node.sourcePos(), SignalDevice.class);
                if (device != null && level.getBlockState(node.sourcePos()).getBlock() instanceof SignalConnector) {
                    if (device.output) outputs.add(device);
                    else {
                        int power = 0;
                        for (var side : Direction.values()) {
                            var neighbor = node.sourcePos().relative(side);
                            if (level.hasChunkAt(neighbor) && !(level.getBlockState(neighbor).getBlock() instanceof SignalConnector))
                                power = Math.max(power, level.getSignal(neighbor, side));
                        }
                        values[device.channel] = Math.max(values[device.channel], power);
                    }
                } else {
                    var a = attachment(level, node);
                    if (a != null) values[channel(a)] = Math.max(values[channel(a)], Math.clamp(source(a), 0, 15));
                }
                for (var connection : data.getConnections(node)) {
                    var wire = data.getConnectionData(connection);
                    var other = connection.node1().equals(node) ? connection.node2() : connection.node1();
                    if (wire != null && wire.wireType() == WIRE.get() && valid(level, other) && visited.add(other)) queue.add(other);
                }
            }
            // Двухфазное обновление: сначала все входы, затем выходы компоненты.
            for (var output : outputs) {
                int power = values[output.channel];
                if (output.signal == power && !output.needsNotify) continue;
                output.needsNotify = false;
                output.signal = power;
                level.updateNeighborsAt(output.pos, CONNECTOR.get());
                var state = level.getBlockState(output.pos);
                level.updateNeighborsAt(output.pos.relative(state.getValue(SignalConnector.FACING).getOpposite()), CONNECTOR.get());
            }
        }
    }
    private Signals() {}
}
