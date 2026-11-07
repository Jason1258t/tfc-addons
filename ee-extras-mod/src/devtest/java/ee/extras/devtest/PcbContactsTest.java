package ee.extras.devtest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.mojang.brigadier.CommandDispatcher;
import ee.extras.pcb.*;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/** Компиляция трёх физических выводов и проверка обоих положений в матрице EE. */
final class PcbContactsTest {
    private static final BlockPos BOARD = new BlockPos(24, -60, 24), SUPPLY = BOARD.east(4), BASE = BOARD.east(6);
    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("pcbcontacts").requires(s -> s.hasPermission(2))
                .then(Commands.literal("switch").executes(c -> build(c.getSource(), false)))
                .then(Commands.literal("transistor").executes(c -> build(c.getSource(), true)))
                .then(Commands.literal("click").executes(c -> {
                    var level = c.getSource().getLevel(); var player = FakePlayerFactory.getMinecraft(level);
                    Vec3 mounted = PcbModulesTest.call(PcbCircuitBlock.class, null, "mountedPoint", new Vec3(7.0/16, 1.5/16, 7.0/16), Direction.UP);
                    var hit = new BlockHitResult(mounted.add(Vec3.atLowerCornerOf(BOARD)), Direction.UP, BOARD, false);
                    check(level.getBlockState(BOARD).useWithoutItem(level, player, hit).consumesAction(), "переключатель принял клик"); return 1;
                }))
                .then(Commands.literal("base").then(Commands.argument("volts", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0, 5))
                        .executes(c -> { power(c.getSource().getLevel(), BASE, com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(c, "volts")); return 1; })))
                .then(Commands.literal("reload").executes(c -> {
                    var saved = new CompoundTag(); device(c.getSource().getLevel()).write(saved); device(c.getSource().getLevel()).read(saved); return 1;
                }))
                .then(Commands.literal("expect").then(Commands.argument("volts", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0, 24))
                        .executes(c -> expect(c.getSource(), com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(c, "volts"))))));
    }
    private static void check(boolean ok, String text) { if (!ok) throw new IllegalStateException("Контакты платы: " + text); }
    private static PcbCircuitDevice device(ServerLevel level) { return DevicesSavedData.load(level).getDevice(BOARD, PcbCircuitDevice.class); }
    private static void trace(CompoundTag tag, int x, int y, boolean terminal) { PcbTopology.set(tag, 0, y * 16 + x, terminal ? PcbTopology.TERMINAL : PcbTopology.TRACE); }
    @SuppressWarnings("unchecked")
    private static int build(CommandSourceStack source, boolean transistor) {
        var level = source.getLevel(); var player = FakePlayerFactory.getMinecraft(level);
        for (var pos : new BlockPos[]{BOARD, SUPPLY, BASE}) DevTest.place(level, pos, Blocks.AIR.defaultBlockState());
        var topology = PcbTopology.create(transistor ? 2 : 1);
        int definition = PcbModulesTest.definition(transistor ? 12 : 3, 0);
        PcbTopology.set(topology, 0, 102, PcbTopology.COMPONENT);
        PcbModulesTest.call(PcbTopology.class, null, "setPart", topology, 0, 102, definition);
        int row = transistor ? 6 : 7;
        for (int x = 0; x <= 5; x++) trace(topology, x, row, x == 0);
        for (int x = transistor ? 7 : 8; x < 16; x++) trace(topology, x, row, x == 15);
        for (int y = 0; y <= 5; y++) trace(topology, 6, y, y == 0);
        trace(topology, 6, 15, true);
        check(PcbTopology.validate(topology) == null && PcbModulesTest.modulePins(definition) == 3, "чертёж имеет три настоящих вывода");
        List<PcbCircuitDevice.Part> parts;
        try { parts = new ArrayList<>((List<PcbCircuitDevice.Part>) PcbModulesTest.call(Class.forName("ee.extras.pcb.PcbWiring"), null, "compile", topology)); }
        catch (ClassNotFoundException e) { throw new IllegalStateException(e); }
        check(parts.size() == 1, "модуль не размножается при компиляции");
        var first = parts.get(0);
        check(first.a() == 1 && first.b() == 2 && first.coilA() == 0 && (!transistor || first.coilB() == 2), "назначение общего/НО/НЗ либо коллектора/эмиттера/базы");
        check(!transistor || Math.abs(first.threshold() - .7) < 1e-9, "порог транзистора0,7В");
        if (transistor) {
            parts.add(new PcbCircuitDevice.Part(PcbCircuitDevice.Kind.RESISTOR, 3, 1, 1000, 0, 0, 3));
            parts.add(new PcbCircuitDevice.Part(PcbCircuitDevice.Kind.VOLTMETER, 1, 2, 1e9, 0, 0, 24));
        } else {
            parts.add(new PcbCircuitDevice.Part(PcbCircuitDevice.Kind.RESISTOR, 0, 3, 1000, 0, 0, 3));
            parts.add(new PcbCircuitDevice.Part(PcbCircuitDevice.Kind.RESISTOR, 2, 3, 1000, 0, 0, 3));
            parts.add(new PcbCircuitDevice.Part(PcbCircuitDevice.Kind.VOLTMETER, 2, 3, 1e9, 0, 0, 24));
        }
        DevTest.place(level, BOARD, PcbItems.CIRCUIT_BLOCK.get().defaultBlockState().setValue(PcbCircuitBlock.CUSTOM, true));
        var stack = transistor ? PcbItems.L2_BOARD.toStack() : PcbItems.L1_BOARD.toStack(); PcbTopology.attach(stack, topology);
        ((PcbCircuitBlock) level.getBlockState(BOARD).getBlock()).setPlacedBy(level, BOARD, level.getBlockState(BOARD), player, stack);
        PcbModulesTest.call(PcbCircuitDevice.class, device(level), "setCircuit", List.copyOf(parts),
                transistor ? PcbCircuitDevice.Rating.L2 : PcbCircuitDevice.Rating.L1, 4);
        DevTest.place(level, SUPPLY, CEEBlocks.CREATIVE_BATTERY.getDefaultState()); power(level, SUPPLY, 24);
        DevTest.wire(level, SUPPLY, transistor ? 1 : 0, BOARD, transistor ? 3 : 1);
        DevTest.wire(level, SUPPLY, transistor ? 0 : 1, BOARD, transistor ? 2 : 3);
        if (transistor) {
            DevTest.place(level, BASE, CEEBlocks.CREATIVE_BATTERY.getDefaultState()); power(level, BASE, .5);
            DevTest.wire(level, BASE, 1, BOARD, 0); DevTest.wire(level, BASE, 0, BOARD, 2);
        }
        source.sendSuccess(() -> Component.literal(transistor ? "Транзистор: коллектор, эмиттер и база с общим эмиттером" : "Переключатель: общий, НО и НЗ, две независимые нагрузки"), false);
        return 1;
    }
    private static void power(ServerLevel level, BlockPos pos, double volts) {
        var entity = (com.simibubi.create.foundation.blockEntity.SmartBlockEntity) level.getBlockEntity(pos);
        entity.getBehaviour(com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour.TYPE).setValue((int) Math.round(volts * 1000));
        var battery = DevicesSavedData.load(level).getDevice(pos, CreativeBatteryDevice.class); battery.voltage = volts; battery.acFrequency = 0;
    }
    private static int expect(CommandSourceStack source, double expected) {
        var saved = new CompoundTag(); device(source.getLevel()).write(saved);
        double value = saved.getDouble("Meter"), watts = saved.getDouble("Watts");
        double control = PcbModulesTest.call(PcbCircuitDevice.class, device(source.getLevel()), "controlVoltage", 0);
        var baseDevice = DevicesSavedData.load(source.getLevel()).getDevice(BASE, CreativeBatteryDevice.class);
        double base = baseDevice == null ? 0 : baseDevice.voltage;
        check(!saved.getBoolean("Burnt") && Math.abs(value - expected) < .1, "напряжение " + value + " вместо " + expected + "; управление=" + control + ", батарея базы=" + base);
        if (saved.getList("Circuit", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).getString("Kind").equals("SWITCH"))
            check(Math.abs(watts - .576) < .005, "ровно одна нагрузка получает питание, мощность " + watts);
        source.sendSuccess(() -> Component.literal(String.format(java.util.Locale.ROOT, "Контакты: %.4fВ, ожидалось%.1fВ; мощность%.6fВт", value, expected, watts)), false);
        return 1;
    }
}
