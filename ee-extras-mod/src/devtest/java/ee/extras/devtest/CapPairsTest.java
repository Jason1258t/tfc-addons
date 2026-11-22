package ee.extras.devtest;

import com.george_vi.electroenergetics.CEEItems;
import com.george_vi.electroenergetics.foundation.base.DirectionalRolledDeviceBlock;
import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.simibubi.create.AllItems;

import ee.extras.EeExtras;
import ee.extras.cable.*;
import ee.extras.wire.WireEnd;
import ee.extras.wire.WireEvents;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

final class CapPairsTest {
    private static final BlockPos BIDI = new BlockPos(2, -60, 40);
    private static final BlockPos A = BIDI.east(6), B = A.south(4);
    private static final BlockPos TOOL_BIDI = new BlockPos(20, -60, 40);
    private static final BlockPos TOOL_A = TOOL_BIDI.east(6), TOOL_B = TOOL_A.south(4);
    private static final BlockPos FREE = new BlockPos(20, -60, 48);

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

    static int tools(CommandSourceStack src) {
        var level = src.getLevel();
        DevTest.place(
                level,
                TOOL_BIDI,
                EeExtras.CONVERTER
                        .get()
                        .defaultBlockState()
                        .setValue(DirectionalRolledDeviceBlock.FACING, Direction.UP));
        DevTest.place(level, TOOL_A, Extras.eeTermination());
        DevTest.place(level, TOOL_B, Extras.eeTermination());

        for (int node : new int[] {0, 2}) {
            var cap = new ItemStack(EeExtras.CABLE_CAP.get(), 4);
            var player = CapTest.player(level, cap);
            require(
                    player.gameMode
                            .useItemOn(
                                    player,
                                    level,
                                    cap,
                                    InteractionHand.MAIN_HAND,
                                    hit(level, TOOL_BIDI, node))
                            .consumesAction(),
                    "установка крышки " + node);
            var spool = new ItemStack(CEEItems.DUPLEX_WIRE_SPOOL.get(), 4);
            player = CapTest.player(level, spool);
            player.gameMode.useItemOn(
                    player, level, spool, InteractionHand.MAIN_HAND, hit(level, TOOL_BIDI, node));
            var target = node == 0 ? TOOL_A : TOOL_B;
            player.gameMode.useItemOn(
                    player,
                    level,
                    spool,
                    InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(target), Direction.UP, target, false));
            require(spool.getCount() == 3, "кабель к крышке " + node);
        }
        require(CableCap.all(level, TOOL_BIDI).size() == 2, "две крышки перед снятием");

        var cutters = new ItemStack(
                BuiltInRegistries.ITEM.get(ResourceLocation.parse("immersiveengineering:wirecutter")));
        require(cutters.getItem() != Items.AIR, "доступны кусачки IE");
        require(cutters.is(WireEvents.CUTTERS), "кусачки IE в теге EE");
        var cutterPlayer = CapTest.player(level, cutters);
        require(
                cutterPlayer.gameMode
                        .useItemOn(
                                cutterPlayer,
                                level,
                                cutters,
                                InteractionHand.MAIN_HAND,
                                hit(level, TOOL_BIDI, 0))
                        .consumesAction(),
                "кусачки снимают крышку");
        require(cutters.getCount() == 1, "кусачки не расходуются");
        require(CableCap.all(level, TOOL_BIDI).size() == 1, "кусачками снята только входная крышка");
        require(CableEnd.resolve(level, new InWorldNode(0, TOOL_BIDI)) == null, "входной порт закрыт");
        require(
                CableEnd.resolve(level, new InWorldNode(2, TOOL_BIDI)) != null,
                "выходная крышка не затронута");
        var sd = InfrastructureSavedData.load(level);
        var inputOther = CableEnd.of(level, TOOL_A, level.getBlockState(TOOL_A));
        var outputOther = CableEnd.of(level, TOOL_B, level.getBlockState(TOOL_B));
        require(inputOther != null && outputOther != null, "концы кабелей на месте");
        require(
                !sd.isConnected(new InWorldNode(0, TOOL_BIDI), inputOther.conductor(1)),
                "кабель снятой крышки удалён");
        require(
                sd.isConnected(new InWorldNode(2, TOOL_BIDI), outputOther.conductor(1)),
                "кабель второй крышки остался");
        require(
                count(cutterPlayer.getInventory().items, EeExtras.CABLE_CAP.get()) == 1,
                "снятая крышка возвращена");
        require(
                count(cutterPlayer.getInventory().items, CEEItems.DUPLEX_WIRE_SPOOL.get()) == 1,
                "катушка снятого кабеля возвращена");

        var wrench = AllItems.WRENCH.asStack();
        var wrenchPlayer = CapTest.player(level, wrench);
        var before = level.getBlockState(TOOL_BIDI);
        require(
                wrenchPlayer.gameMode
                        .useItemOn(
                                wrenchPlayer,
                                level,
                                wrench,
                                InteractionHand.MAIN_HAND,
                                hit(level, TOOL_BIDI, 2))
                        .consumesAction(),
                "ключ снимает крышку");
        require(wrench.getCount() == 1, "ключ не расходуется");
        require(CableCap.all(level, TOOL_BIDI).size() == 0, "ключом снята выходная крышка");
        require(CableEnd.resolve(level, new InWorldNode(2, TOOL_BIDI)) == null, "выходной порт закрыт");
        require(
                !sd.isConnected(new InWorldNode(2, TOOL_BIDI), outputOther.conductor(1)),
                "кабель снятой крышки удалён");
        var after = level.getBlockState(TOOL_BIDI);
        require(
                after.getValue(DirectionalRolledDeviceBlock.ROLL)
                        == before.getValue(DirectionalRolledDeviceBlock.ROLL),
                "ключ не повернул прибор вместо снятия крышки");
        require(
                count(wrenchPlayer.getInventory().items, EeExtras.CABLE_CAP.get()) == 1,
                "крышка возвращена после ключа");
        require(
                count(wrenchPlayer.getInventory().items, CEEItems.DUPLEX_WIRE_SPOOL.get()) == 1,
                "катушка возвращена после ключа");

        DevTest.place(level, FREE, EeExtras.SINGLE_PHASE.block.get().defaultBlockState());
        var freeBefore = level.getBlockState(FREE);
        var freePlayer = CapTest.player(level, AllItems.WRENCH.asStack());
        var freeEvent = new PlayerInteractEvent.RightClickBlock(
                freePlayer, InteractionHand.MAIN_HAND, FREE, hit(level, FREE, 0));
        require(!NeoForge.EVENT_BUS.post(freeEvent).isCanceled(), "ключ не отменён без крышки");
        var freeBlock = (DirectionalRolledDeviceBlock<?>) freeBefore.getBlock();
        var rotated = freeBlock.getRotatedBlockState(freeBefore, freeBefore.getValue(DirectionalRolledDeviceBlock.FACING));
        require(
                rotated.getValue(DirectionalRolledDeviceBlock.ROLL)
                        != freeBefore.getValue(DirectionalRolledDeviceBlock.ROLL),
                "штатный поворот свободного прибора доступен");

        src.sendSuccess(
                () ->
                        Component.literal(
                                "ПАРЫ TOOLS PASS: крышки сняты кусачками IE и ключом Create, кабели убраны, свободный прибор ключом поворачивается"),
                false);
        return 1;
    }

    static int breakBidi(CommandSourceStack src) {
        var level = src.getLevel();
        var pos = new BlockPos(28, -60, 40);
        DevTest.place(level, pos, EeExtras.CONVERTER.get().defaultBlockState());
        var result = CableCap.put(level, pos, null, new ItemStack(EeExtras.CABLE_CAP.get()));
        require(result.consumesAction(), "крышка на преобразователе");
        var cap = CableCap.all(level, pos).getFirst();
        require(
                CableCapGeometry.of(level, pos, level.getBlockState(pos), cap.contacts()) != null,
                "геометрия живой крышки");
        require(level.destroyBlock(pos, true), "преобразователь сломан");
        var state = level.getBlockState(pos);
        require(state.isAir(), "на месте преобразователя воздух");
        require(
                CableCapGeometry.of(level, pos, state, cap.contacts()) == null,
                "у сломанного преобразователя нет геометрии крышки");
        src.sendSuccess(
                () -> Component.literal("ПАРЫ BREAK PASS: преобразователь с крышкой сломан без исключения"),
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

    private static int count(Iterable<ItemStack> stacks, Item item) {
        int count = 0;
        for (var stack : stacks) if (stack.is(item)) count += stack.getCount();
        return count;
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new IllegalStateException("ПАРЫ FAIL: " + message);
    }

    private CapPairsTest() {}
}
