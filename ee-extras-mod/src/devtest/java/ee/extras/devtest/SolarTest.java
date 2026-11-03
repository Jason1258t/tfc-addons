package ee.extras.devtest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.gauge.GaugeDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.mojang.brigadier.CommandDispatcher;
import ee.extras.EeExtras;
import ee.extras.generator.GeneratorDevice;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/** Солнечная панель в настоящей симуляции EE: напряжение холостого хода и отдача в нагреватель. */
final class SolarTest {
    private static final BlockPos PANEL = new BlockPos(80, -60, 10);
    private static final BlockPos METER = new BlockPos(82, -60, 10);
    private static final BlockPos HEATER = new BlockPos(84, -60, 10);
    private static final BlockPos ROOF = PANEL.above(3);

    private SolarTest() {}

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("rectest").then(Commands.literal("solarbuild").executes(c -> build(c.getSource())))
                .then(Commands.literal("solarroof").executes(c -> roof(c.getSource())))
                .then(Commands.literal("solarreport").then(Commands.argument("mode", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .executes(c -> report(c.getSource(), com.mojang.brigadier.arguments.StringArgumentType.getString(c, "mode"))))));
    }

    private static int build(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        DevTest.place(level, ROOF, Blocks.AIR.defaultBlockState());
        DevTest.place(level, PANEL, EeExtras.SOLAR_PANEL.get().defaultBlockState());
        DevTest.place(level, METER, CEEBlocks.VOLTMETER.getDefaultState());
        DevTest.place(level, HEATER, CEEBlocks.RESISTIVE_HEATER.getDefaultState());
        DevTest.wire(level, PANEL, 0, METER, 0);
        DevTest.wire(level, PANEL, 1, METER, 1);
        DevTest.wire(level, PANEL, 0, HEATER, 0);
        DevTest.wire(level, PANEL, 1, HEATER, 1);
        level.setDayTime(1000);
        src.sendSuccess(() -> Component.literal("SOLAR: панель, вольтметр и нагреватель собраны"), false);
        return 1;
    }

    private static int roof(CommandSourceStack src) {
        src.getLevel().setDayTime(1000);
        DevTest.place(src.getLevel(), ROOF, Blocks.STONE.defaultBlockState());
        src.sendSuccess(() -> Component.literal("SOLAR: над панелью установлена крыша"), false);
        return 1;
    }

    private static int report(CommandSourceStack src, String mode) {
        ServerLevel level = src.getLevel();
        DevicesSavedData devices = DevicesSavedData.load(level);
        GeneratorDevice panel = devices.getDevice(PANEL, GeneratorDevice.class);
        GaugeDevice meter = devices.getDevice(METER, GaugeDevice.class);
        if (panel == null || meter == null) throw new IllegalStateException("SOLAR FAIL: устройства EE не созданы");
        CompoundTag data = new CompoundTag();
        panel.write(data);
        double voltage = meter.rmsVoltages.getSigned();
        double output = data.getDouble("OutputPower");
        boolean on = mode.equals("day");
        if (on && (Math.abs(voltage) < 40 || Math.abs(voltage) > 50 || output <= 0))
            throw new IllegalStateException(String.format("SOLAR FAIL: днём %.2f В, %.2f Вт; %s; блок-сущность %s; день %s, небо %s, свет %d",
                    voltage, output, data, level.getBlockEntity(PANEL), level.isDay(), level.canSeeSky(PANEL.above()),
                    level.getBrightness(net.minecraft.world.level.LightLayer.SKY, PANEL.above())));
        if (!on && (Math.abs(voltage) > 0.1 || output > 0.1 || data.getDouble("MaxPower") != 0))
            throw new IllegalStateException(String.format("SOLAR FAIL: %s %.2f В, %.2f Вт", mode, voltage, output));
        String message = String.format("SOLAR PASS %s: %.2f В, %.2f Вт, запас %.2f", mode, voltage, output, data.getDouble("Stored"));
        src.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }
}
