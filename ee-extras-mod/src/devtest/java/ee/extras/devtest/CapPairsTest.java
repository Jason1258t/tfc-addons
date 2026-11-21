package ee.extras.devtest;

import com.george_vi.electroenergetics.CEEItems;
import com.george_vi.electroenergetics.foundation.base.DirectionalRolledDeviceBlock;
import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;

import ee.extras.EeExtras;
import ee.extras.cable.*;
import ee.extras.wire.WireEnd;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

final class CapPairsTest {
    private static final BlockPos BIDI = new BlockPos(2, -60, 40);
    private static final BlockPos A = BIDI.east(6), B = A.south(4);

    static int build(CommandSourceStack src) {
        var level = src.getLevel();
        int placed = 0, index = 0;
        var blocks =
                new net.minecraft.world.level.block.Block[] {
                    EeExtras.CONVERTER.get(),
                    EeExtras.SINGLE_PHASE.block.get(),
                    EeExtras.THREE_PHASE.block.get(),
                    EeExtras.SINGLE_PHASE_INVERTER.block.get(),
                    EeExtras.THREE_PHASE_INVERTER.block.get()
                };
        for (var block : blocks)
            for (var face : Direction.values())
                for (boolean roll : new boolean[] {false, true}) {
                    var pos = new BlockPos(2 + (index % 8) * 4, -58, 2 + (index / 8) * 4);
                    index++;
                    var state =
                            block.defaultBlockState()
                                    .setValue(DirectionalRolledDeviceBlock.FACING, face)
                                    .setValue(DirectionalRolledDeviceBlock.ROLL, roll);
                    DevTest.place(level, pos, state);
                    var groups = CableCap.groups(level, pos, state);
                    for (var pair : groups) {
                        var hit = hit(level, pos, pair[0]);
                        var stack = new ItemStack(EeExtras.CABLE_CAP.get(), 64);
                        var player = CapTest.player(level, stack);
                        require(
                                player.gameMode
                                        .useItemOn(
                                                player,
                                                level,
                                                stack,
                                                InteractionHand.MAIN_HAND,
                                                hit)
                                        .consumesAction(),
                                "установка " + pos);
                        var end = CableEnd.hit(level, hit);
                        require(
                                end != null
                                        && end.conductor(1).id() == pair[0]
                                        && end.conductor(2).id() == pair[1],
                                "выбор пары " + pos);
                        var geometry = CableCapGeometry.of(level, pos, state, pair);
                        require(
                                geometry.outward().dot(Vec3.atLowerCornerOf(face.getNormal()))
                                        > .999,
                                "нормаль " + face + "/" + roll);
                        var wireEnd = new WireEnd.Block(pos, pair[0]);
                        require(
                                WireEnd.load(wireEnd.save()).nodes(level, 2).getFirst().id()
                                        == pair[0],
                                "порт поверхностного провода");
                        placed++;
                    }
                    if (groups.size() == 1) {
                        require(
                                CableEnd.hit(level, hit(level, pos, 0)) == null,
                                "крышка не закрывает трёхфазный вход");
                        var stack = new ItemStack(EeExtras.CABLE_CAP.get(), 4);
                        var player = CapTest.player(level, stack);
                        player.gameMode.useItemOn(
                                player,
                                level,
                                stack,
                                InteractionHand.MAIN_HAND,
                                hit(level, pos, 0));
                        require(stack.getCount() == 4, "не расходовать крышку на три фазы");
                    }
                }
        var legacy = new BlockPos(2, -60, 46);
        DevTest.place(
                level,
                legacy,
                com.george_vi.electroenergetics.CEEBlocks.RESISTIVE_HEATER.getDefaultState());
        CapMigrationProbe.check(level, legacy);
        DevTest.place(
                level,
                BIDI,
                EeExtras.CONVERTER
                        .get()
                        .defaultBlockState()
                        .setValue(DirectionalRolledDeviceBlock.FACING, Direction.UP));
        DevTest.place(level, A, Extras.eeTermination());
        DevTest.place(level, B, Extras.eeTermination());
        for (int node : new int[] {0, 2}) {
            var cap = new ItemStack(EeExtras.CABLE_CAP.get(), 4);
            var player = CapTest.player(level, cap);
            player.gameMode.useItemOn(
                    player, level, cap, InteractionHand.MAIN_HAND, hit(level, BIDI, node));
            if (node == 0) {
                var ordinary = new ItemStack(CEEItems.COPPER_WIRE_SPOOL.get(), 4);
                player = CapTest.player(level, ordinary);
                player.gameMode.useItemOn(
                        player, level, ordinary, InteractionHand.MAIN_HAND, hit(level, BIDI, 2));
                var selected =
                        ordinary.get(
                                com.george_vi.electroenergetics.CEEDataComponents.SELECTED_NODE);
                require(
                        selected != null && selected.id() == 2,
                        "свободная пара принимает обычный провод");
                ee.extras.interaction.WireCancel.clear(ordinary);
                player.gameMode.useItemOn(
                        player, level, ordinary, InteractionHand.MAIN_HAND, hit(level, BIDI, 0));
                require(
                        !ordinary.has(
                                com.george_vi.electroenergetics.CEEDataComponents.SELECTED_NODE),
                        "закрытая пара не принимает обычный провод");
            }
            var spool = new ItemStack(CEEItems.DUPLEX_WIRE_SPOOL.get(), 4);
            player = CapTest.player(level, spool);
            player.gameMode.useItemOn(
                    player, level, spool, InteractionHand.MAIN_HAND, hit(level, BIDI, node));
            var target = node == 0 ? A : B;
            player.gameMode.useItemOn(
                    player,
                    level,
                    spool,
                    InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(target), Direction.UP, target, false));
            require(spool.getCount() == 3, "провод от конкретной пары");
        }
        require(placed == 96, "установлены все 96 крышек");
        src.sendSuccess(
                () ->
                        Component.literal(
                                "ПАРЫ BUILD PASS: 96 крышек, 60 блоков, все 12 ориентаций,"
                                        + " трёхфазный вход открыт"),
                false);
        return check(src);
    }

    static int check(CommandSourceStack src) {
        var level = src.getLevel();
        require(CableCap.all(level, BIDI).size() == 2, "обе крышки сохранены");
        var sd = InfrastructureSavedData.load(level);
        for (int node : new int[] {0, 2}) {
            var end = CableEnd.resolve(level, new InWorldNode(node, BIDI));
            require(end != null && end.conductor(1).id() == node, "разрешение сохранённого порта");
            var other =
                    CableEnd.of(level, node == 0 ? A : B, level.getBlockState(node == 0 ? A : B));
            require(
                    sd.isConnected(end.conductor(1), other.conductor(1))
                            && sd.isConnected(end.conductor(2), other.conductor(2)),
                    "жилы не перепутаны");
        }
        src.sendSuccess(
                () ->
                        Component.literal(
                                "ПАРЫ CHECK PASS: вход и выход независимо подключены и сохранены"),
                false);
        return 1;
    }

    static int take(CommandSourceStack src) {
        var level = src.getLevel();
        var player = CapTest.player(level, ItemStack.EMPTY);
        player.setShiftKeyDown(true);
        player.gameMode.useItemOn(
                player, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit(level, BIDI, 0));
        require(
                CableCap.all(level, BIDI).size() == 1
                        && CableEnd.resolve(level, new InWorldNode(0, BIDI)) == null,
                "снята только входная крышка");
        var output = CableEnd.resolve(level, new InWorldNode(2, BIDI));
        var other = CableEnd.of(level, B, level.getBlockState(B));
        require(
                output != null
                        && InfrastructureSavedData.load(level)
                                .isConnected(output.conductor(1), other.conductor(1)),
                "выходной кабель остался");
        src.sendSuccess(
                () ->
                        Component.literal(
                                "ПАРЫ TAKE PASS: снятие входа сохранило выход и его кабель"),
                false);
        return 1;
    }

    private static BlockHitResult hit(ServerLevel level, BlockPos pos, int node) {
        var state = level.getBlockState(pos);
        var block = (ElectricalDeviceBlock<?>) state.getBlock();
        return new BlockHitResult(
                Vec3.atLowerCornerOf(pos).add(block.getNodePosition(level, pos, state, node)),
                state.getValue(DirectionalRolledDeviceBlock.FACING),
                pos,
                false);
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new IllegalStateException("ПАРЫ FAIL: " + message);
    }

    private CapPairsTest() {}
}
