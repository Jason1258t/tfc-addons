package ee.extras.devtest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.gauge.GaugeDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.foundation.base.DirectionalRolledDeviceBlock;
import com.mojang.brigadier.CommandDispatcher;
import ee.extras.thermometer.Thermometer;
import ee.extras.thermometer.ThermometerDevice;
import net.dries007.tfc.common.blocks.TFCBlocks;
import net.dries007.tfc.common.capabilities.BlockCapabilities;
import net.dries007.tfc.common.component.heat.IHeatConsumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

/** Термометр EE на настоящей heat-capability тигля TFC. Запускать только в стенде TFC=1. */
final class ThermometerTest {
    private static final BlockPos CRUCIBLE = new BlockPos(100, -60, 10);
    private static final BlockPos THERMOMETER = CRUCIBLE.south();
    private static final BlockPos METER = CRUCIBLE.south(3);

    private ThermometerTest() {}

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("rectest").then(Commands.literal("thermbuild").executes(c -> build(c.getSource())))
                .then(Commands.literal("thermreport").executes(c -> report(c.getSource()))));
    }

    private static int build(CommandSourceStack src) {
        var level = src.getLevel();
        DevTest.place(level, CRUCIBLE, TFCBlocks.CRUCIBLE.get().defaultBlockState());
        DevTest.place(level, THERMOMETER, Thermometer.BLOCK.get().defaultBlockState().setValue(DirectionalRolledDeviceBlock.FACING, Direction.SOUTH));
        DevTest.place(level, METER, CEEBlocks.VOLTMETER.getDefaultState());
        DevTest.wire(level, THERMOMETER, 0, METER, 0);
        DevTest.wire(level, THERMOMETER, 1, METER, 1);
        IHeatConsumer heat = level.getCapability(BlockCapabilities.HEAT, CRUCIBLE, Direction.SOUTH);
        if (heat == null) throw new IllegalStateException("THERM FAIL: у тигля нет heat-capability");
        heat.setTemperature(800);
        src.sendSuccess(() -> Component.literal("THERM: тигель нагрет до 800 °C"), false);
        return 1;
    }

    private static int report(CommandSourceStack src) {
        var level = src.getLevel();
        ThermometerDevice thermometer = DevicesSavedData.load(level).getDevice(THERMOMETER, ThermometerDevice.class);
        GaugeDevice meter = DevicesSavedData.load(level).getDevice(METER, GaugeDevice.class);
        if (thermometer == null || meter == null) throw new IllegalStateException("THERM FAIL: устройства EE не созданы");
        double measured = meter.rmsVoltages.getSigned();
        if (thermometer.temperature < 700 || thermometer.temperature > 900 || Math.abs(measured - 7.5) > 0.75)
            throw new IllegalStateException(String.format("THERM FAIL: %.1f °C, %.3f В", thermometer.temperature, measured));
        String message = String.format("THERM PASS: %.1f °C → %.3f В (ЭДС %.3f В)", thermometer.temperature, measured, thermometer.voltage);
        src.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }
}
