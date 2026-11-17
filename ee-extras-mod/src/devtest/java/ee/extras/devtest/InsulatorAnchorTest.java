package ee.extras.devtest;

import com.george_vi.electroenergetics.CEEBlocks;
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
        Tension.CLAMP_BEHAVIOUR.get().interactWire(new NodeConnectionPoint(span.node1(), span.node2(), point),
                level, player, Tension.CLAMP_ITEM.toStack());
    }

    private static InWorldNode tip(InfrastructureSavedData sd, BlockPos pos) {
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

    static int power(CommandSourceStack source) {
        var level = source.getLevel();
        var sd = InfrastructureSavedData.load(level);
        DevTest.place(level, BATTERY, CEEBlocks.CREATIVE_BATTERY.getDefaultState());
        DevTest.place(level, METER, CEEBlocks.VOLTMETER.getDefaultState());
        Lines.ensure(level, BATTERY);
        Lines.ensure(level, METER);
        var copper = CEEWireTypes.COPPER.get();
        // Запас длины для отвода при переходе от свободного подвеса к анкерному.
        sd.connect(new InWorldNode(0, BATTERY), tip(sd, LEFT), WireData.ofLength(copper, 12));
        sd.connect(tip(sd, RIGHT), new InWorldNode(0, METER), WireData.ofLength(copper, 12));
        DevTest.wire(level, BATTERY, 1, METER, 1);
        return 1;
    }

    static int voltage(CommandSourceStack source) {
        String volts = Extras.vm(DevicesSavedData.load(source.getLevel()), METER);
        boolean powered = volts.equals("300.0") || volts.equals("300,0");
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
