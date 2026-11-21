package ee.extras.devtest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.CEEItems;
import com.george_vi.electroenergetics.CEEDataComponents;
import com.george_vi.electroenergetics.content.wire_spool.WireSpoolItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import com.george_vi.electroenergetics.CEEWireTypes;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.content.wire.attachments.AttachmentRemovalWireInteractionBehaviour;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.foundation.nodes.NodeConnectionPoint;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.george_vi.electroenergetics.simulation.infrastructure.WireData;
import com.george_vi.electroenergetics.simulation.infrastructure.detached_nodes.DetachedNodeType;
import com.simibubi.create.AllItems;
import ee.extras.lines.LineWire;
import ee.extras.lines.Tension;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

final class InsulatorAnchorTest {
    private static final BlockPos LEFT = new BlockPos(3, -51, 8);
    private static final BlockPos RIGHT = LEFT.offset(18, 2, 8);
    private static final BlockPos BATTERY = LEFT.below(7);
    private static final BlockPos METER = RIGHT.below(7);
    private static final BlockPos LOAD = METER.east(2);
    private static final BlockPos SHORT_LEFT = LEFT.offset(0, 0, 16);
    private static final BlockPos SHORT_RIGHT = SHORT_LEFT.offset(4, 0, 0);

    static InWorldNodeConnection build(ServerLevel level, BlockPos left, BlockPos right) {
        var sd = InfrastructureSavedData.load(level);
        InWorldNode a = hang(level, sd, left, false);
        InWorldNode b = hang(level, sd, right, true);
        sd.connect(a, b, LineWire.ACSR.type.get());
        return new InWorldNodeConnection(a, b);
    }

    private static InWorldNode hang(ServerLevel level, InfrastructureSavedData sd, BlockPos pos, boolean colored) {
        DevTest.place(level, pos, CEEBlocks.CONNECTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.DOWN));
        Lines.ensure(level, pos);
        var top = sd.getNodesAt(pos).getFirst().node;
        var tip = sd.createDetachedNode(DetachedNodeType.PHYSICS_UNINITIALIZED, sd.getNodePosition(top).add(0, -4.125, 0)).node;
        var glass = colored ? CEEWireTypes.COLORED_GLASS_INSULATOR[DyeColor.LIGHT_BLUE.ordinal()].get()
                : CEEWireTypes.GLASS_INSULATOR.get();
        sd.connect(top, tip, WireData.ofLength(glass, 4));
        return tip;
    }

    static void clamp(ServerLevel level, InWorldNodeConnection span, Player player, float point) {
        var sd = InfrastructureSavedData.load(level);
        var stack = new ItemStack(Tension.CLAMP_ITEM.get(), 2);
        for (var tip : new InWorldNode[] { span.node1(), span.node2() }) {
            var glass = sd.getConnections(tip).stream()
                    .filter(c -> ee.extras.lines.InsulatorAnchors.isGlass(sd.getConnectionData(c).wireType())).findFirst().orElseThrow();
            Tension.CLAMP_BEHAVIOUR.get().interactWire(new NodeConnectionPoint(glass.node1(), glass.node2(), 0.5f),
                    level, player, stack);
        }
    }

    static InWorldNode tip(InfrastructureSavedData sd, BlockPos pos) {
        InWorldNode top = sd.getNodesAt(pos).getFirst().node;
        var suspension = sd.getConnections(top).getFirst();
        return top.equals(suspension.node1()) ? suspension.node2() : suspension.node1();
    }

    static boolean shape(ServerLevel level, BlockPos left, BlockPos right, boolean anchored) {
        var sd = InfrastructureSavedData.load(level);
        Vec3 a = sd.getNodePosition(sd.getNodesAt(left).getFirst().node);
        Vec3 b = sd.getNodePosition(sd.getNodesAt(right).getFirst().node);
        Vec3 tipA = sd.getNodePosition(tip(sd, left));
        Vec3 tipB = sd.getNodePosition(tip(sd, right));
        Vec3 horizontal = b.subtract(a).multiply(1, 0, 1);
        double reach = anchored ? Math.min(Math.sqrt(12), (horizontal.length() - 1.5) / 2) : 0;
        Vec3 offset = horizontal.normalize().scale(reach);
        Vec3 drop = new Vec3(0, -Math.sqrt(16 - reach * reach), 0);
        // Даже штатный пролёт слегка подтягивает свободные концы друг к другу.
        double tolerance = anchored ? 0.18 : 1.1;
        return tipA.distanceTo(a.add(offset).add(drop)) < tolerance
                && tipB.distanceTo(b.subtract(offset).add(drop)) < tolerance;
    }

    static int build(CommandSourceStack source) {
        build(source.getLevel(), LEFT, RIGHT);
        build(source.getLevel(), SHORT_LEFT, SHORT_RIGHT);
        return 1;
    }

    static int clamp(CommandSourceStack source) {
        var level = source.getLevel();
        var player = FakePlayerFactory.getMinecraft(level);
        var sd = InfrastructureSavedData.load(level);
        var longSpan = new InWorldNodeConnection(tip(sd, LEFT), tip(sd, RIGHT));
        var shortSpan = new InWorldNodeConnection(tip(sd, SHORT_LEFT), tip(sd, SHORT_RIGHT));
        clamp(level, longSpan, player, 0.95f);
        clamp(level, longSpan, player, 0.05f);
        clamp(level, longSpan, player, 0.05f);
        clamp(level, shortSpan, player, 0.05f);
        clamp(level, shortSpan, player, 0.95f);
        return 1;
    }

    static int check(CommandSourceStack source, boolean anchored) {
        var level = source.getLevel();
        var sd = InfrastructureSavedData.load(level);
        boolean good = true;
        for (BlockPos left : new BlockPos[] { LEFT, SHORT_LEFT }) {
            BlockPos right = left.equals(LEFT) ? RIGHT : SHORT_RIGHT;
            var span = new InWorldNodeConnection(tip(sd, left), tip(sd, right));
            var wire = sd.getConnectionData(span);
            double distance = sd.getNodePosition(span.node1()).distanceTo(sd.getNodePosition(span.node2()));
            double length = sd.getNodePosition(sd.getNodesAt(left).getFirst().node)
                    .distanceTo(sd.getNodePosition(sd.getNodesAt(right).getFirst().node));
            boolean passed = wire != null && wire.attachments().size() == (anchored ? 2 : 0)
                    && shape(level, left, right, anchored)
                    && Math.abs(wire.length - length) < 0.02
                    && (!anchored || wire.getSag(distance) < wire.getSag() * 0.31);
            good &= passed;
            source.sendSuccess(() -> Component.literal((passed ? "ПРОЛЁТ ОК " : "ПРОЛЁТ ОШИБКА ")
                    + left.toShortString() + ": расстояние " + distance + ", провод " + wire), false);
        }
        return good ? 1 : 0;
    }

    private static void select(ServerLevel level, InWorldNode tip, Player player, ItemStack stack) {
        var sd = InfrastructureSavedData.load(level);
        var glass = sd.getConnections(tip).stream()
                .filter(c -> ee.extras.lines.InsulatorAnchors.isGlass(sd.getConnectionData(c).wireType())).findFirst().orElseThrow();
        Tension.CLAMP_BEHAVIOUR.get().interactWire(new NodeConnectionPoint(glass.node1(), glass.node2(), 0.5f),
                level, player, stack);
    }

    static int selection(CommandSourceStack source) {
        var level = source.getLevel();
        var sd = InfrastructureSavedData.load(level);
        var player = FakePlayerFactory.getMinecraft(level);
        var connection = build(level, new BlockPos(3, -51, 35), new BlockPos(21, -51, 35));
        var stack = new ItemStack(Tension.CLAMP_ITEM.get(), 2);
        select(level, connection.node1(), player, stack);
        boolean first = connection.node1().equals(stack.get(CEEDataComponents.SELECTED_NODE))
                && stack.getCount() == 2 && sd.getConnectionData(connection).attachments().isEmpty();
        select(level, connection.node1(), player, stack);
        boolean cancel = !stack.has(CEEDataComponents.SELECTED_NODE) && stack.getCount() == 2;
        sd.removeConnectionNoDrops(connection);
        select(level, connection.node1(), player, stack);
        select(level, connection.node2(), player, stack);
        boolean unconnected = !sd.isConnected(connection.node1(), connection.node2()) && stack.getCount() == 2;
        sd.connect(connection.node1(), connection.node2(), CEEWireTypes.COPPER.get());
        select(level, connection.node2(), player, stack);
        boolean unsupported = sd.getConnectionData(connection).attachments().isEmpty() && stack.getCount() == 2;
        sd.removeConnectionNoDrops(connection);
        sd.connect(connection.node1(), connection.node2(), LineWire.ACSR.type.get());
        stack.setCount(1);
        select(level, connection.node2(), player, stack);
        boolean insufficient = sd.getConnectionData(connection).attachments().isEmpty() && stack.getCount() == 1;
        stack.setCount(2);
        select(level, connection.node2(), player, stack);
        boolean success = sd.getConnectionData(connection).attachments().size() == 2 && stack.isEmpty();
        boolean ok = first && cancel && unconnected && unsupported && insufficient && success;
        String result = "ВЫБОР ГИРЛЯНД " + (ok ? "ОК" : "ОШИБКА") + " первая=" + first + " отмена=" + cancel
                + " нет связи=" + unconnected + " неподходящий провод=" + unsupported
                + " мало зажимов=" + insufficient + " установка=" + success;
        source.sendSuccess(() -> Component.literal(result), false);
        return ok ? 1 : 0;
    }

    private static final BlockPos CHAIN_A = new BlockPos(3, -51, 52);
    private static final BlockPos CHAIN_B = CHAIN_A.offset(18, 0, 0);
    private static final BlockPos CHAIN_C = CHAIN_B.offset(2, 0, 0);
    private static final BlockPos CHAIN_D = CHAIN_C.offset(18, 0, 0);

    static int chainBuild(CommandSourceStack source) {
        var level = source.getLevel();
        var sd = InfrastructureSavedData.load(level);
        var first = build(level, CHAIN_A, CHAIN_B);
        var second = build(level, CHAIN_C, CHAIN_D);
        sd.connect(tip(sd, CHAIN_B), tip(sd, CHAIN_C), CEEWireTypes.COPPER.get());
        var player = FakePlayerFactory.getMinecraft(level);
        clamp(level, first, player, 0.5f);
        clamp(level, second, player, 0.5f);
        return 1;
    }

    static int chainCheck(CommandSourceStack source) {
        var level = source.getLevel();
        var sd = InfrastructureSavedData.load(level);
        var a = tip(sd, CHAIN_A);
        var b = tip(sd, CHAIN_B);
        var c = tip(sd, CHAIN_C);
        var d = tip(sd, CHAIN_D);
        var first = new InWorldNodeConnection(a, b);
        var second = new InWorldNodeConnection(c, d);
        var jumper = new InWorldNodeConnection(b, c);
        boolean geometry = shape(level, CHAIN_A, CHAIN_B, true) && shape(level, CHAIN_C, CHAIN_D, true);
        boolean anchored = sd.getConnectionData(first).attachments().size() == 2
                && sd.getConnectionData(second).attachments().size() == 2;
        var player = FakePlayerFactory.getMinecraft(level);
        // Даже перемычку из натягиваемого материала нельзя закрепить поверх занятого наконечника.
        sd.removeConnectionNoDrops(jumper);
        sd.connect(b, c, LineWire.ACSR.type.get());
        var stack = new ItemStack(Tension.CLAMP_ITEM.get(), 2);
        select(level, b, player, stack);
        select(level, c, player, stack);
        boolean refused = stack.getCount() == 2 && sd.getConnectionData(jumper).attachments().isEmpty();
        var third = hang(level, sd, CHAIN_A.offset(0, 0, 16), false);
        var competing = new InWorldNodeConnection(b, third);
        sd.connect(b, third, LineWire.ACSR.type.get());
        clamp(level, competing, player, 0.5f);
        boolean competingRefused = sd.getConnectionData(competing).attachments().isEmpty();
        sd.removeConnectionNoDrops(competing);
        sd.removeConnectionNoDrops(jumper);
        sd.connect(b, c, CEEWireTypes.COPPER.get());
        boolean ok = geometry && anchored && refused && competingRefused;
        source.sendSuccess(() -> Component.literal("ЦЕПОЧКА " + (ok ? "ОК" : "ОШИБКА")
                + " геометрия=" + geometry + " анкеры=" + anchored + " перемычка отклонена=" + refused
                + " занятый конец=" + competingRefused), false);
        return ok ? 1 : 0;
    }

    static int chainRelease(CommandSourceStack source) {
        var level = source.getLevel();
        var sd = InfrastructureSavedData.load(level);
        var first = new InWorldNodeConnection(tip(sd, CHAIN_A), tip(sd, CHAIN_B));
        var remove = new AttachmentRemovalWireInteractionBehaviour();
        var clicks = sd.getConnectionData(first).attachments().stream().map(a -> a.getFirst()).toList();
        for (float click : clicks) {
            remove.interactWire(new NodeConnectionPoint(first.node1(), first.node2(), click), level,
                    FakePlayerFactory.getMinecraft(level), AllItems.WRENCH.asStack());
        }
        return 1;
    }

    static int chainReleased(CommandSourceStack source) {
        var level = source.getLevel();
        var sd = InfrastructureSavedData.load(level);
        var first = new InWorldNodeConnection(tip(sd, CHAIN_A), tip(sd, CHAIN_B));
        var second = new InWorldNodeConnection(tip(sd, CHAIN_C), tip(sd, CHAIN_D));
        boolean ok = sd.getConnectionData(first).attachments().isEmpty()
                && sd.getConnectionData(second).attachments().size() == 2
                && shape(level, CHAIN_C, CHAIN_D, true);
        source.sendSuccess(() -> Component.literal("НЕЗАВИСИМОЕ СНЯТИЕ " + (ok ? "ОК" : "ОШИБКА")), false);
        return ok ? 1 : 0;
    }

    static int power(CommandSourceStack source) {
        var level = source.getLevel();
        var sd = InfrastructureSavedData.load(level);
        DevTest.place(level, BATTERY, CEEBlocks.CREATIVE_BATTERY.getDefaultState());
        DevTest.place(level, METER, CEEBlocks.VOLTMETER.getDefaultState());
        Lines.ensure(level, BATTERY);
        Lines.ensure(level, METER);
        DevTest.place(level, LOAD, CEEBlocks.RESISTIVE_HEATER.getDefaultState());
        Lines.ensure(level, LOAD);
        var player = FakePlayerFactory.getMinecraft(level);
        var spool = CEEItems.COPPER_WIRE_SPOOL.asStack();
        var item = (WireSpoolItem) spool.getItem();
        player.setItemInHand(InteractionHand.MAIN_HAND, spool);
        item.interactDetachedNode(tip(sd, LEFT), player, level, spool);
        Vec3 batteryNode = new InWorldNode(0, BATTERY).getPosition(level);
        item.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(batteryNode, Direction.UP, BATTERY, false)));
        boolean input = sd.isConnected(tip(sd, LEFT), new InWorldNode(0, BATTERY));
        spool = CEEItems.COPPER_WIRE_SPOOL.asStack();
        player.setItemInHand(InteractionHand.MAIN_HAND, spool);
        Vec3 meterNode = new InWorldNode(0, METER).getPosition(level);
        item.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(meterNode, Direction.UP, METER, false)));
        item.interactDetachedNode(tip(sd, RIGHT), player, level, spool);
        boolean output = sd.isConnected(tip(sd, RIGHT), new InWorldNode(0, METER));
        boolean back = DevTest.spoolWire(player, CEEItems.COPPER_WIRE_SPOOL.asStack(), BATTERY, 1, METER, 1);
        boolean load = DevTest.spoolWire(player, CEEItems.COPPER_WIRE_SPOOL.asStack(), METER, 0, LOAD, 0)
                & DevTest.spoolWire(player, CEEItems.COPPER_WIRE_SPOOL.asStack(), METER, 1, LOAD, 1);
        source.sendSuccess(() -> Component.literal("ОТВОДЫ КАТУШКОЙ " + input + "/" + output + "/" + back + "/нагрузка=" + load), false);
        return 1;
    }

    static int voltage(CommandSourceStack source) {
        String volts = Extras.vm(DevicesSavedData.load(source.getLevel()), METER);
        double value = Double.parseDouble(volts.replace(',', '.'));
        boolean powered = value > 290 && value <= 300.1;
        source.sendSuccess(() -> Component.literal((powered ? "ПИТАНИЕ ОК " : "ПИТАНИЕ ОШИБКА ") + volts + " В"), false);
        return powered ? 1 : 0;
    }

    static int release(CommandSourceStack source) {
        var level = source.getLevel();
        var sd = InfrastructureSavedData.load(level);
        var player = FakePlayerFactory.getMinecraft(level);
        var wrench = AllItems.WRENCH.asStack();
        var remove = new AttachmentRemovalWireInteractionBehaviour();
        for (BlockPos left : new BlockPos[] { LEFT, SHORT_LEFT }) {
            var span = new InWorldNodeConnection(tip(sd, left), tip(sd, left.equals(LEFT) ? RIGHT : SHORT_RIGHT));
            var clicks = sd.getConnectionData(span).attachments().stream().map(a -> a.getFirst()).toList();
            for (float click : clicks) {
                remove.interactWire(new NodeConnectionPoint(span.node1(), span.node2(), click), level, player, wrench);
            }
        }
        return 1;
    }
}
