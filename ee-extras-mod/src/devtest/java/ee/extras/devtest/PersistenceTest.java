package ee.extras.devtest;

import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.foundation.scroll_value.VoltageScrollValueBehaviour;
import com.mojang.brigadier.CommandDispatcher;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import ee.extras.EeExtras;
import ee.extras.ac.AcBlocks;
import ee.extras.ac.FrequencyConverterDevice;
import ee.extras.converter.BidiConverterDevice;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

final class PersistenceTest {
    private static final BlockPos BIDI = new BlockPos(8, -60, 10);
    private static final BlockPos FREQUENCY = new BlockPos(10, -60, 10);
    private static final int VOLTS = 10;
    private static final int DISCHARGE_STEPS = 23;
    private static final int HERTZ = 17;

    static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("eepersist")
                .then(Commands.literal("place").executes(c -> place(c.getSource())))
                .then(Commands.literal("configure").executes(c -> configure(c.getSource())))
                .then(Commands.literal("check").executes(c -> check(c.getSource()))));
    }

    private static int place(CommandSourceStack src) {
        DevTest.build(src);
        ServerLevel level = src.getLevel();
        DevTest.place(level, BIDI, EeExtras.CONVERTER.get().defaultBlockState());
        DevTest.place(level, FREQUENCY, AcBlocks.CONVERTERS.getFirst().block.get().defaultBlockState());
        return 1;
    }

    private static int configure(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        var discharge = setting(level, BIDI);
        var frequency = setting(level, FREQUENCY);
        if (discharge == null || frequency == null) {
            src.sendFailure(Component.literal("Настройки преобразователей недоступны"));
            return 0;
        }
        discharge.setValue(DISCHARGE_STEPS);
        frequency.setValue(HERTZ);
        return DevTest.power(src, VOLTS);
    }

    private static int check(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        DevicesSavedData devices = DevicesSavedData.load(level);
        StringBuilder report = new StringBuilder();
        boolean ok = checkBatteries(level, devices, report);
        var bidi = devices.getDevice(BIDI, BidiConverterDevice.class);
        var discharge = setting(level, BIDI);
        report.append("; двусторонний: ").append(discharge == null ? null : discharge.getValue() * 10)
                .append('/').append(bidi == null ? null : bidi.voltage).append(" В");
        ok &= discharge != null && discharge.getValue() == DISCHARGE_STEPS
                && bidi != null && bidi.voltage == DISCHARGE_STEPS * 10;
        var converter = devices.getDevice(FREQUENCY, FrequencyConverterDevice.class);
        var frequency = setting(level, FREQUENCY);
        report.append("; частотный: ").append(frequency == null ? null : frequency.getValue())
                .append('/').append(converter == null ? null : converter.setHz).append(" Гц");
        ok &= frequency != null && frequency.getValue() == HERTZ && converter != null && converter.setHz == HERTZ;
        if (!ok) {
            src.sendFailure(Component.literal("Настройки не совпали (блок/симуляция): " + report));
            return 0;
        }
        src.sendSuccess(() -> Component.literal("Настройки сохранены (блок/симуляция): " + report), false);
        return 1;
    }

    private static boolean checkBatteries(ServerLevel level, DevicesSavedData devices, StringBuilder report) {
        boolean ok = true;
        for (BlockPos base : new BlockPos[] {DevTest.THREE, DevTest.SINGLE}) {
            int phases = base.equals(DevTest.THREE) ? 3 : 1;
            for (int i = 0; i < phases; i++) {
                BlockPos pos = DevTest.battery(base, i);
                var battery = devices.getDevice(pos, CreativeBatteryDevice.class);
                var scroll = setting(level, pos);
                Double volts = scroll instanceof VoltageScrollValueBehaviour voltage ? voltage.getVoltage() : null;
                if (!report.isEmpty()) report.append("; ");
                report.append("батарея ").append(pos.toShortString()).append(": ").append(volts)
                        .append('/').append(battery == null ? null : battery.voltage).append(" В, ")
                        .append(battery == null ? null : battery.acFrequency).append(" Гц, ")
                        .append(battery == null ? null : battery.phaseOffset).append("°");
                ok &= volts != null && volts == VOLTS && battery != null && battery.voltage == VOLTS
                        && battery.acFrequency == 1 && battery.phaseOffset == 120f * i;
            }
        }
        return ok;
    }

    private static ScrollValueBehaviour setting(ServerLevel level, BlockPos pos) {
        return BlockEntityBehaviour.get(level, pos, ScrollValueBehaviour.TYPE);
    }
}
