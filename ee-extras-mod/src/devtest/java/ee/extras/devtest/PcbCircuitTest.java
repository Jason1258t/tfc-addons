package ee.extras.devtest;

import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.mojang.brigadier.CommandDispatcher;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import ee.extras.pcb.PcbCircuitBlock;
import ee.extras.pcb.PcbCircuitDevice;
import ee.extras.pcb.PcbCircuitDevice.Kind;
import ee.extras.pcb.PcbCircuitDevice.Part;
import ee.extras.pcb.PcbItems;
import ee.extras.pcb.PcbEditorMenu;
import ee.extras.pcb.PcbTopology;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

final class PcbCircuitTest {
    private static final BlockPos BOARD = new BlockPos(8, -60, 8);
    private static final BlockPos BATTERY = BOARD.offset(0, 0, 4);
    private static final BlockPos RESERVE = BOARD.offset(0, 0, -4);

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("pcbcircuit").requires(source -> source.hasPermission(2))
                .then(Commands.literal("build").executes(context -> build(context.getSource().getLevel())))
                .then(Commands.literal("advanced").then(Commands.argument("circuit", StringArgumentType.word())
                        .executes(context -> advanced(context.getSource(), StringArgumentType.getString(context, "circuit")))))
                .then(Commands.literal("build_lead").executes(context -> build(context.getSource().getLevel(), true)))
                .then(Commands.literal("controls").executes(context -> controls(context.getSource().getLevel())))
                .then(Commands.literal("click_button").executes(context -> clickControl(context.getSource().getLevel(), 3)))
                .then(Commands.literal("click_switch").executes(context -> clickControl(context.getSource().getLevel(), 7)))
                .then(Commands.literal("meter").then(Commands.argument("value", DoubleArgumentType.doubleArg(0))
                        .executes(context -> meter(context.getSource(), DoubleArgumentType.getDouble(context, "value")))))
                .then(Commands.literal("display").executes(context -> display(context.getSource())))
                .then(Commands.literal("stress").executes(context -> stress(context.getSource(), false)))
                .then(Commands.literal("stress_activate").executes(context -> stress(context.getSource(), true)))
                .then(Commands.literal("stress_check").executes(context -> stressCheck(context.getSource())))
                .then(Commands.literal("salvage").executes(context -> salvage(context.getSource())))
                .then(Commands.literal("editor").executes(context -> editor(context.getSource())))
                .then(Commands.literal("nominal").executes(context -> nominal(context.getSource().getLevel())))
                .then(Commands.literal("indicator").then(Commands.argument("ohms", DoubleArgumentType.doubleArg(10))
                        .executes(context -> indicator(context.getSource().getLevel(),
                                DoubleArgumentType.getDouble(context, "ohms")))))
                .then(Commands.literal("overload").executes(context -> overload(context.getSource().getLevel(), false)))
                .then(Commands.literal("overcurrent").executes(context -> overload(context.getSource().getLevel(), true)))
                .then(Commands.literal("reserve_power").executes(context -> {
                    var battery = DevicesSavedData.load(context.getSource().getLevel()).getDevice(RESERVE, CreativeBatteryDevice.class);
                    battery.voltage = 24;
                    battery.acFrequency = 0;
                    return 1;
                }))
                .then(Commands.literal("power").then(Commands.argument("volts", DoubleArgumentType.doubleArg(0))
                        .executes(context -> {
                            power(context.getSource().getLevel(), DoubleArgumentType.getDouble(context, "volts"));
                            return 1;
                        })))
                .then(Commands.literal("expect_burnt").then(Commands.argument("burnt", BoolArgumentType.bool())
                        .executes(context -> expectBurnt(context.getSource(), BoolArgumentType.getBool(context, "burnt")))))
                .then(Commands.literal("press").executes(context -> {
                    ServerLevel level = context.getSource().getLevel();
                    DevicesSavedData.load(level).getDevice(BOARD, PcbCircuitDevice.class)
                            .interact(false, level.getGameTime());
                    return 1;
                }))
                .then(Commands.literal("logic").executes(context -> logic(context.getSource().getLevel())))
                .then(Commands.literal("toggle").executes(context -> {
                    ServerLevel level = context.getSource().getLevel();
                    DevicesSavedData.load(level).getDevice(BOARD, PcbCircuitDevice.class)
                            .interact(true, level.getGameTime());
                    return 1;
                }))
                .then(Commands.literal("expect_output").then(Commands.argument("powered", BoolArgumentType.bool())
                        .executes(context -> expectOutput(context.getSource(), BoolArgumentType.getBool(context, "powered")))))
                .then(Commands.literal("expect").then(Commands.argument("lit", BoolArgumentType.bool())
                        .executes(context -> expect(context.getSource(), BoolArgumentType.getBool(context, "lit"))))));
    }

    private static int build(ServerLevel level) {
        return build(level, false);
    }

    private static int build(ServerLevel level, boolean thinLead) {
        DevTest.place(level, BOARD, Blocks.AIR.defaultBlockState());
        DevTest.place(level, BATTERY, Blocks.AIR.defaultBlockState());
        DevTest.place(level, BOARD, PcbItems.CIRCUIT_BLOCK.get().defaultBlockState());
        DevTest.place(level, BATTERY, CEEBlocks.CREATIVE_BATTERY.getDefaultState());
        DevTest.place(level, RESERVE, Blocks.AIR.defaultBlockState());
        DevTest.place(level, RESERVE, CEEBlocks.CREATIVE_BATTERY.getDefaultState());
        if (thinLead) {
            var player = FakePlayerFactory.getMinecraft(level);
            for (int contact = 0; contact < 2; contact++) {
                if (!DevTest.spoolWire(player, ee.extras.EeExtras.THIN_SPOOL.toStack(), BOARD, contact, BATTERY, contact))
                    throw new IllegalStateException("Не подключился прежний тонкий провод " + contact);
            }
        } else {
            DevTest.wire(level, BATTERY, 0, BOARD, 0);
            DevTest.wire(level, BATTERY, 1, BOARD, 1);
        }
        return 1;
    }

    private static int controls(ServerLevel level) {
        build(level);
        power(level, 25);
        DevTest.place(level, BOARD, Blocks.AIR.defaultBlockState());
        DevTest.place(level, BOARD.south(), Blocks.STONE.defaultBlockState());
        var player = FakePlayerFactory.getMinecraft(level);
        var topology = PcbTopology.create(1);
        int[] parts = new int[PcbTopology.CELLS];
        parts[115] = 3;
        parts[119] = 4;
        parts[123] = 5 + 3 * 32 + 3 * 512;
        for (int cell = 112; cell < 128; cell++) {
            int kind = cell == 112 || cell == 127 ? PcbTopology.TERMINAL
                    : parts[cell] == 0 ? PcbTopology.TRACE : PcbTopology.COMPONENT;
            PcbTopology.set(topology, 0, cell, kind);
        }
        topology.putIntArray("ElectricalParts", parts);
        topology.putBoolean("Assembled", true);
        var stack = PcbItems.L1_BOARD.toStack();
        stack.set(PcbItems.PCB_TOPOLOGY.get(), topology);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(BOARD.south()).add(0, 0, -0.5), Direction.NORTH, BOARD.south(), false)));
        DevTest.wire(level, BATTERY, 0, BOARD, 0);
        DevTest.wire(level, BATTERY, 1, BOARD, 1);
        var state = level.getBlockState(BOARD);
        for (int x : new int[] {3, 7}) {
            var hit = controlHit(x);
            var cap = x == 3 ? PcbItems.BUTTON_CAP.toStack() : PcbItems.SWITCH_CAP.toStack();
            if (!state.useItemOn(cap, level, player, InteractionHand.MAIN_HAND, hit).consumesAction())
                throw new IllegalStateException("Накладка не установилась на компонент " + x);
            Vec3 raised = hit.getLocation().subtract(Vec3.atLowerCornerOf(BOARD)).add(0, 0, -0.12);
            if (state.getShape(level, BOARD).toAabbs().stream().noneMatch(box -> box.contains(raised)))
                throw new IllegalStateException("Хитбокс накладки не увеличился для компонента " + x);
        }
        return 1;
    }

    private static BlockHitResult controlHit(int x) {
        return new BlockHitResult(Vec3.atLowerCornerOf(BOARD).add((2 + 0.75 * (x + 0.5)) / 16,
                (2 + 0.75 * 7.5) / 16, 0.875), Direction.NORTH, BOARD, false);
    }

    private static int clickControl(ServerLevel level, int x) {
        var player = FakePlayerFactory.getMinecraft(level);
        if (!level.getBlockState(BOARD).useWithoutItem(level, player, controlHit(x)).consumesAction())
            throw new IllegalStateException("Клик не попал в компонент " + x);
        return 1;
    }

    private static int editor(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        BlockPos table = BOARD.offset(0, 0, 2);
        DevTest.place(level, table, PcbItems.EDITOR_BLOCK.get().defaultBlockState());
        var player = FakePlayerFactory.getMinecraft(level);
        ItemStack[] boards = {PcbItems.L1_BOARD.toStack(), PcbItems.L2_BOARD.toStack(), PcbItems.L3_BOARD.toStack()};
        for (int tier = 0; tier < boards.length; tier++) {
            var editor = (ee.extras.pcb.PcbEditorBlockEntity) level.getBlockEntity(table);
            editor.clear(tier + 1);
            editor.setItem(0, tier == 0 ? ItemStack.EMPTY : boards[tier].copyWithCount(3));
            player.getInventory().clearContent();
            if (tier == 0) player.getInventory().setItem(2, boards[tier].copyWithCount(3));
            var menu = new PcbEditorMenu(1, player.getInventory(), ContainerLevelAccess.create(level, table), editor);
            // Два вывода по краям строки и резистор между дорожками.
            for (int cell = 1; cell < 15; cell++) if (cell != 7) menu.clickMenuButton(player, cell);
            for (int row = 1; row < 15; row++) menu.clickMenuButton(player, row * 16);
            menu.clickMenuButton(player, PcbEditorMenu.SELECT_TOOL + 6);
            menu.clickMenuButton(player, 8 * 16 + 8);
            menu.clickMenuButton(player, PcbEditorMenu.SELECT_TOOL + 3);
            menu.clickMenuButton(player, 0);
            menu.clickMenuButton(player, 15);
            menu.clickMenuButton(player, 1);
            menu.clickMenuButton(player, 16);
            menu.clickMenuButton(player, 31);
            menu.clickMenuButton(player, 240);
            menu.clickMenuButton(player, PcbEditorMenu.SELECT_TOOL + 5);
            for (int i = 0; i < 2 * tier + 1; i++) menu.clickMenuButton(player, PcbEditorMenu.OHMS);
            menu.clickMenuButton(player, 7);
            menu.clickMenuButton(player, PcbEditorMenu.SAVE);
            if (menu.status() == 1) throw new IllegalStateException("Сборка прошла без резистора");
            player.getInventory().setItem(1, PcbItems.RESISTOR.toStack());
            menu.clickMenuButton(player, PcbEditorMenu.SAVE);
            if (!player.getInventory().getItem(1).isEmpty()) throw new IllegalStateException("Резистор не списан");
            int remaining = player.getInventory().items.stream()
                    .filter(stack -> stack.is(boards[PcbTopology.level(editor.topology()) - 1].getItem()))
                    .mapToInt(ItemStack::getCount).sum();
            if (remaining != 2 || editor.getItem(0).getCount() != 1)
                throw new IllegalStateException("Сборка должна расходовать ровно одну плату");
            ItemStack active = editor.getItem(0).copy();
            CompoundTag topology = active.get(PcbItems.PCB_TOPOLOGY.get());
            if (menu.status() != 1 || topology == null || PcbTopology.level(topology) != tier + 1)
                throw new IllegalStateException("Редактор не сохранил плату L" + (tier + 1));
            BlockPos target = BOARD.east(tier + 1);
            DevTest.place(level, target, Blocks.AIR.defaultBlockState());
            DevTest.place(level, target.below(), Blocks.STONE.defaultBlockState());
            var hit = new BlockHitResult(Vec3.atBottomCenterOf(target), Direction.UP, target.below(), false);
            player.setItemInHand(InteractionHand.MAIN_HAND, boards[tier].copy());
            if (player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)).consumesAction())
                throw new IllegalStateException("Пассивная плата поставилась как блок");
            player.setItemInHand(InteractionHand.MAIN_HAND, active.copy());
            if (!player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)).consumesAction()
                    || !level.getBlockState(target).is(PcbItems.CIRCUIT_BLOCK.get()))
                throw new IllegalStateException("Готовая плата не поставилась из предмета");
            var device = DevicesSavedData.load(level).getDevice(target, PcbCircuitDevice.class);
            CompoundTag snapshot = new CompoundTag();
            device.write(snapshot);
            if (!snapshot.getString("Rating").equals("L" + (tier + 1)) || snapshot.getList("Circuit", 10).size() != 5 || snapshot.getInt("Terminals") != 6)
                throw new IllegalStateException("При установке потеряны схема или уровень платы");
            double expectedOhms = switch (tier) {
                case 0 -> 47;
                case 1 -> 220;
                default -> 1000;
            };
            boolean resistanceSaved = snapshot.getList("Circuit", 10).stream()
                    .map(CompoundTag.class::cast)
                    .anyMatch(part -> part.getString("Kind").equals("RESISTOR") && part.getDouble("Ohms") == expectedOhms);
            if (!resistanceSaved) throw new IllegalStateException("Плата потеряла настройку " + expectedOhms + " Ом");
            if (tier == 0) {
                for (Direction face : Direction.values()) {
                    BlockPos mounted = target.offset(0, 4 + face.ordinal() * 3, 0);
                    BlockPos support = mounted.relative(face.getOpposite());
                    DevTest.place(level, mounted, Blocks.AIR.defaultBlockState());
                    DevTest.place(level, support, Blocks.STONE.defaultBlockState());
                    Vec3 hitPoint = Vec3.atCenterOf(support).add(Vec3.atLowerCornerOf(face.getNormal()).scale(0.5));
                    player.setItemInHand(InteractionHand.MAIN_HAND, active.copy());
                    player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                            new BlockHitResult(hitPoint, face, support, false)));
                    var mountedState = level.getBlockState(mounted);
                    if (!(mountedState.getBlock() instanceof PcbCircuitBlock mountedBoard)
                            || mountedState.getValue(PcbCircuitBlock.FACING) != face)
                        throw new IllegalStateException("Плата не поставилась на грань " + face);
                    Vec3 contact = mountedBoard.getNodePositions(level, mounted, mountedState).get(0);
                    double depth = contact.get(face.getAxis());
                    double expectedDepth = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 0.125 : 0.875;
                    if (depth != expectedDepth)
                        throw new IllegalStateException("Контакт на неверной высоте для грани " + face);
                    var box = mountedState.getShape(level, mounted).bounds();
                    for (Direction.Axis axis : Direction.Axis.values()) {
                        if (axis != face.getAxis() && (box.min(axis) != 0.125 || box.max(axis) != 0.875))
                            throw new IllegalStateException("Неверная рамка платы на грани " + face);
                    }
                }
            }
            var circuitBlock = (PcbCircuitBlock) level.getBlockState(target).getBlock();
            var contacts = circuitBlock.getNodePositions(level, target, level.getBlockState(target));
            if (contacts.size() != 6 || !contacts.get(5).equals(new Vec3(2.375 / 16, 2.0 / 16, 13.625 / 16)))
                throw new IllegalStateException("Узлы не соответствуют контактам редактора");
            if (level.getBlockState(target).getShape(level, target).toAabbs().stream()
                    .anyMatch(box -> box.contains(8.375 / 16, 0.5 / 16, 8.375 / 16)))
                throw new IllegalStateException("Вырезанная ячейка осталась в контуре платы");
            player.setItemInHand(InteractionHand.MAIN_HAND, ee.extras.EeExtras.THIN_SPOOL.toStack());
            for (int contact : new int[] {4, 5}) {
                var wireHit = new BlockHitResult(Vec3.atLowerCornerOf(target).add(contacts.get(contact)), Direction.UP, target, false);
                if (!player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, wireHit)).consumesAction())
                    throw new IllegalStateException("Катушка не приняла контакт " + contact);
            }
            if (!InfrastructureSavedData.load(level).isConnected(new InWorldNode(4, target), new InWorldNode(5, target)))
                throw new IllegalStateException("Тонкий провод не подключился к выбранным контактам");
            var drops = net.minecraft.world.level.block.Block.getDrops(level.getBlockState(target), level, target,
                    level.getBlockEntity(target));
            if (drops.size() != 1 || !ItemStack.isSameItemSameComponents(drops.getFirst(), active))
                throw new IllegalStateException("Снятая плата потеряла схему");
        }
        source.sendSuccess(() -> Component.literal("Редактор, установка и снятие L1–L3 подтверждены"), false);
        return 1;
    }

    private static int salvage(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        BlockPos pos = BOARD.east();
        var state = level.getBlockState(pos);
        if (!state.is(PcbItems.CIRCUIT_BLOCK.get()) || !state.getValue(PcbCircuitBlock.BURNT))
            throw new IllegalStateException("Установленная плата не сгорела");
        ItemStack burnt = net.minecraft.world.level.block.Block.getDrops(state, level, pos, level.getBlockEntity(pos)).getFirst();
        if (!burnt.is(PcbItems.BURNT_BOARD.get())) throw new IllegalStateException("Не выпала сгоревшая плата");
        var player = FakePlayerFactory.getMinecraft(level);
        var table = BOARD.offset(0, 0, 2);
        var editor = (ee.extras.pcb.PcbEditorBlockEntity) level.getBlockEntity(table);
        var menu = new PcbEditorMenu(1, player.getInventory(), ContainerLevelAccess.create(level, table), editor);
        player.getInventory().clearContent();
        player.getRandom().setSeed(272);
        for (int i = 0; i < 32; i++) {
            editor.setItem(0, burnt.copy());
            menu.clickMenuButton(player, PcbEditorMenu.SAVE);
            if (!editor.getItem(0).isEmpty()) throw new IllegalStateException("Сгоревшая плата не списана");
        }
        menu.clickMenuButton(player, PcbEditorMenu.SAVE);
        int recovered = player.getInventory().items.stream()
                .filter(stack -> stack.is(PcbItems.RESISTOR.get())).mapToInt(ItemStack::getCount).sum();
        if (recovered == 0 || recovered >= 32) throw new IllegalStateException("Неверный возврат деталей: " + recovered);
        source.sendSuccess(() -> Component.literal("Утилизация: возвращено " + recovered + " из 32 резисторов"), false);
        return 1;
    }

    private static int advanced(CommandSourceStack source, String mode) {
        ServerLevel level = source.getLevel();
        power(level, 48);
        List<Part> parts = switch (mode) {
            case "timer" -> List.of(part(Kind.RESISTOR, 0, 4, 220, 1),
                    part(Kind.CAPACITOR, 4, 1, 10, 1), part(Kind.VOLTMETER, 4, 1, 1, 48),
                    part(Kind.LAMP, 4, 1, 10000, 24));
            case "led", "led_reverse" -> {
                power(level, 12);
                boolean forward = mode.equals("led");
                yield List.of(part(Kind.LED, forward ? 1 : 0, forward ? 0 : 1, 220, 3));
            }
            case "diode" -> List.of(part(Kind.DIODE, 1, 4, 1, 1),
                    part(Kind.RESISTOR, 4, 0, 1000, 1), part(Kind.VOLTMETER, 4, 0, 1, 48));
            case "reverse" -> List.of(part(Kind.DIODE, 4, 1, 1, 1),
                    part(Kind.RESISTOR, 4, 0, 1000, 1), part(Kind.VOLTMETER, 4, 0, 1, 48));
            case "fuse", "ammeter" -> List.of(part(Kind.FUSE, 0, 4, 1, mode.equals("fuse") ? 24 : 48),
                    part(Kind.RESISTOR, 4, 5, 150, 1), part(Kind.AMMETER, 5, 1, 1, 48));
            case "transistor" -> List.of(part(Kind.BUTTON, 0, 4, 1, 1),
                    part(Kind.RESISTOR, 1, 4, 1000, 1),
                    new Part(Kind.TRANSISTOR, 0, 5, 1, 4, 0, 12),
                    part(Kind.RESISTOR, 5, 1, 1000, 1), part(Kind.VOLTMETER, 5, 1, 1, 48));
            case "cutoff" -> {
                power(level, 16);
                yield List.of(new Part(Kind.RELAY, 0, 4, 1000, 0, 1, 24, true),
                        part(Kind.RESISTOR, 4, 1, 1000, 1), part(Kind.VOLTMETER, 4, 1, 1, 48));
            }
            case "reserve" -> {
                DevTest.wire(level, RESERVE, 0, BOARD, 2);
                DevTest.wire(level, RESERVE, 1, BOARD, 1);
                yield List.of(new Part(Kind.RELAY, 0, 3, 10000, 0, 1, 24),
                        new Part(Kind.RELAY, 2, 3, 10000, 0, 1, 24, true),
                        part(Kind.RESISTOR, 3, 1, 1000, 1), part(Kind.VOLTMETER, 3, 1, 1, 48));
            }
            case "salvage" -> {
                DevTest.wire(level, BATTERY, 0, BOARD.east(), 0);
                DevTest.wire(level, BATTERY, 1, BOARD.east(), 1);
                power(level, 60);
                yield List.of();
            }
            default -> throw new IllegalArgumentException("Неизвестный стенд " + mode);
        };
        DevicesSavedData.load(level).getDevice(BOARD, PcbCircuitDevice.class).setCircuit(parts);
        return 1;
    }

    private static int display(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        BlockPos linkPos = BOARD.south();
        var linkBlock = AllBlocks.DISPLAY_LINK.get();
        for (Direction direction : Direction.values()) {
            DevTest.place(level, linkPos, linkBlock.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.DirectionalBlock.FACING, direction));
            var link = (DisplayLinkBlockEntity) level.getBlockEntity(linkPos);
            var context = new DisplayLinkContext(level, link);
            if (!context.getSourcePos().equals(BOARD)) continue;
            var display = DisplaySource.getAll(level, BOARD).stream()
                    .filter(entry -> entry.getName().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents translation
                            && translation.getKey().equals("ee_extras.pcb.meter")).findFirst().orElseThrow();
            String line = display.provideText(context, new DisplayTargetStats(1, 32, null)).getFirst().getString();
            if (line.isBlank() || !line.matches(".*[0-9].*")) throw new IllegalStateException("Табло: " + line);
            source.sendSuccess(() -> Component.literal("Табло Create получает «" + line + "»"), false);
            return 1;
        }
        throw new IllegalStateException("Табло не нашло плату");
    }

    private static int stress(CommandSourceStack source, boolean activate) {
        ServerLevel level = source.getLevel();
        List<Part> parts = new ArrayList<>();
        int node = 0;
        for (int next = 4; next < 32; next++) {
            parts.add(part(Kind.RESISTOR, node, next, 100, 1));
            node = next;
        }
        parts.add(part(Kind.RESISTOR, node, 1, 100, 1));
        while (parts.size() < 64) parts.add(new Part(Kind.RELAY, 0, 1, 10000, 0, 1, 96));
        BlockPos supply = new BlockPos(24, -57, 24);
        if (activate) {
            var battery = DevicesSavedData.load(level).getDevice(supply, CreativeBatteryDevice.class);
            battery.voltage = 48;
            battery.acFrequency = 0;
        } else {
            DevTest.place(level, supply, CEEBlocks.CREATIVE_BATTERY.getDefaultState());
        }
        for (int z = 16; z < 32; z += 4) {
            for (int x = 16; x < 32; x += 4) {
                BlockPos pos = new BlockPos(x, -60, z);
                if (!activate) {
                    DevTest.place(level, pos, PcbItems.CIRCUIT_BLOCK.get().defaultBlockState());
                    continue;
                }
                DevTest.wire(level, supply, 0, pos, 0);
                DevTest.wire(level, supply, 1, pos, 1);
                DevicesSavedData.load(level).getDevice(pos, PcbCircuitDevice.class)
                        .setCircuit(parts, PcbCircuitDevice.Rating.L3);
            }
        }
        source.sendSuccess(() -> Component.literal(activate
                ? "16 плат на общей шине: 32 узла и 64 компонента на каждой" : "16 плат и общий источник установлены"), false);
        return 1;
    }

    private static int stressCheck(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        for (int z = 16; z < 32; z += 4) {
            for (int x = 16; x < 32; x += 4) {
                var pos = new BlockPos(x, -60, z);
                var device = DevicesSavedData.load(level).getDevice(pos, PcbCircuitDevice.class);
                var snapshot = new CompoundTag();
                device.write(snapshot);
                if (snapshot.getBoolean("Burnt") || snapshot.getDouble("Watts") < 7) {
                    throw new IllegalStateException("Плата стенда не работает: " + pos);
                }
            }
        }
        source.sendSuccess(() -> Component.literal("Все 16 плат под нагрузкой"), false);
        return 1;
    }

    private static int meter(CommandSourceStack source, double expected) {
        var device = DevicesSavedData.load(source.getLevel()).getDevice(BOARD, PcbCircuitDevice.class);
        CompoundTag snapshot = new CompoundTag();
        device.write(snapshot);
        double actual = snapshot.getDouble("Meter");
        if (!Double.isFinite(actual) || Math.abs(actual - expected) > (expected < 1 ? 0.03 : 1)) {
            source.sendFailure(Component.literal("Измеритель: ожидалось " + expected + ", получено " + actual));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Измеритель: " + actual), false);
        return 1;
    }

    private static void power(ServerLevel level, double volts) {
        var battery = DevicesSavedData.load(level).getDevice(BATTERY, CreativeBatteryDevice.class);
        var entity = (com.simibubi.create.foundation.blockEntity.SmartBlockEntity) level.getBlockEntity(BATTERY);
        entity.getBehaviour(com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour.TYPE)
                .setValue((int) Math.round(volts * 1000));
        battery.voltage = volts;
        battery.acFrequency = 0;
    }

    private static int nominal(ServerLevel level) {
        power(level, 48);
        DevicesSavedData data = DevicesSavedData.load(level);
        // Кнопка питает катушку, реле включает делитель. На лампе ожидается 24 В.
        data.getDevice(BOARD, PcbCircuitDevice.class).setCircuit(List.of(
                part(Kind.BUTTON, 0, 4, 1, 1),
                new Part(Kind.RELAY, 0, 5, 1000, 4, 1, 30),
                part(Kind.RESISTOR, 5, 6, 1000, 1),
                part(Kind.LAMP, 6, 1, 1000, 22)));
        return 1;
    }

    private static int indicator(ServerLevel level, double ohms) {
        DevicesSavedData.load(level).getDevice(BOARD, PcbCircuitDevice.class).setCircuit(List.of(
                part(Kind.BUTTON, 0, 4, 1, 1),
                part(Kind.LAMP, 4, 1, ohms, 24)));
        return 1;
    }

    private static int overload(ServerLevel level, boolean current) {
        power(level, current ? 2 : 48);
        DevicesSavedData.load(level).getDevice(BOARD, PcbCircuitDevice.class)
                .setCircuit(List.of(part(Kind.RESISTOR, 0, 1, current ? 2.5 : 120, 1)));
        return 1;
    }

    private static int expectBurnt(CommandSourceStack source, boolean expected) {
        boolean actual = source.getLevel().getBlockState(BOARD).getValue(PcbCircuitBlock.BURNT);
        if (actual != expected) {
            source.sendFailure(Component.literal("Плата: ожидалось перегорание " + expected + ", получено " + actual));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Плата: перегорание " + actual + " подтверждено"), false);
        return 1;
    }

    private static int logic(ServerLevel level) {
        DevicesSavedData.load(level).getDevice(BOARD, PcbCircuitDevice.class).setCircuit(List.of(
                part(Kind.TRACE, 0, 4, 1, 1),
                part(Kind.REDSTONE_IN, 4, 5, 1, 1),
                part(Kind.SWITCH, 5, 6, 1, 1),
                part(Kind.REDSTONE_OUT, 6, 1, 1000, 22)));
        return 1;
    }

    private static int expectOutput(CommandSourceStack source, boolean expected) {
        int actual = source.getLevel().getSignal(BOARD, Direction.EAST);
        if ((actual == 15) != expected) {
            source.sendFailure(Component.literal("Плата: ожидался выход " + expected + ", сигнал " + actual));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Плата: сигнал западного выхода " + actual), false);
        return 1;
    }

    private static Part part(Kind kind, int a, int b, double ohms, double threshold) {
        return new Part(kind, a, b, ohms, 0, 0, threshold);
    }

    private static int expect(CommandSourceStack source, boolean expected) {
        boolean actual = source.getLevel().getBlockState(BOARD).getValue(PcbCircuitBlock.LIT);
        CompoundTag diagnostics = new CompoundTag();
        DevicesSavedData.load(source.getLevel()).getDevice(BOARD, PcbCircuitDevice.class).write(diagnostics);
        double volts = diagnostics.getDouble("IndicatorVoltage");
        double target = expected ? 24 : 0;
        if (!Double.isFinite(volts) || Math.abs(volts - target) > 1) {
            source.sendFailure(Component.literal("Плата: ожидалось " + target + " В, измерено " + volts + " В"));
            return 0;
        }
        if (expected != actual) {
            source.sendFailure(Component.literal("Плата: ожидалось свечение " + expected + ", получено " + actual));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Плата: свечение " + actual + " подтверждено, " + volts + " В"), false);
        return 1;
    }
}
