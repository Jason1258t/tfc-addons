package ee.extras.devtest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltBlock;
import com.simibubi.create.content.kinetics.belt.BeltPart;
import ee.extras.motor.BeltMotorHost;
import ee.extras.motor.BeltMotorDevice;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import ee.extras.motor.IntegratedMotor;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

final class BeltMotorTest {
    private static final BlockPos PULLEY = new BlockPos(12, -59, 5);
    private static final BlockPos BATTERY = PULLEY.south(3);
    private static final IntegratedMotor MOTOR = new IntegratedMotor(256);

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("beltmotortest")
                .then(Commands.argument("rpm", IntegerArgumentType.integer(-32, 32))
                        .executes(ctx -> drive(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "rpm"))))
                .then(Commands.literal("build").executes(ctx -> build(ctx.getSource())))
                .then(Commands.literal("wire").executes(ctx -> wire(ctx.getSource())))
                .then(Commands.literal("voltage").then(Commands.argument("volts", IntegerArgumentType.integer(0, 500))
                        .executes(ctx -> voltage(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "volts")))))
                .then(Commands.literal("report").executes(ctx -> report(ctx.getSource())))
                .then(Commands.literal("load").executes(ctx -> load(ctx.getSource()))));
    }

    private static int build(CommandSourceStack source) {
        var belt = AllBlocks.BELT.getDefaultState().setValue(BeltBlock.HORIZONTAL_FACING, Direction.EAST);
        source.getLevel().setBlockAndUpdate(PULLEY.west(), belt.setValue(BeltBlock.PART, BeltPart.START));
        source.getLevel().setBlockAndUpdate(PULLEY, belt.setValue(BeltBlock.PART, BeltPart.PULLEY));
        source.getLevel().setBlockAndUpdate(PULLEY.east(), belt.setValue(BeltBlock.PART, BeltPart.END));
        return 1;
    }

    private static int wire(CommandSourceStack source) {
        var level = source.getLevel();
        if (!BeltMotorDevice.supports(level.getBlockState(PULLEY))) return 0;
        DevTest.place(level, BATTERY, CEEBlocks.CREATIVE_BATTERY.getDefaultState());
        DevicesSavedData.load(level).addDevice(BeltMotorDevice.TYPE, PULLEY, new CompoundTag());
        InfrastructureSavedData.load(level).registerOrUpdateNodes(PULLEY, List.of(0, 1));
        DevTest.wire(level, BATTERY, 0, PULLEY, 0);
        DevTest.wire(level, BATTERY, 1, PULLEY, 1);
        return voltage(source, 300);
    }

    private static int voltage(CommandSourceStack source, int volts) {
        var battery = DevicesSavedData.load(source.getLevel()).getDevice(BATTERY, CreativeBatteryDevice.class);
        if (battery == null) return 0;
        battery.voltage = volts;
        return 1;
    }

    private static int drive(CommandSourceStack source, int rpm) {
        if (!(source.getLevel().getBlockEntity(PULLEY) instanceof BeltMotorHost host)) {
            source.sendFailure(Component.literal("Нет шкива для проверки"));
            return 0;
        }
        host.eeExtras$drive(rpm, MOTOR.capacityPerRpm(300, rpm));
        return report(source);
    }

    private static int load(CommandSourceStack source) {
        // Пресс подключён к крайнему шкиву, встроенный мотор — к среднему.
        var press = AllBlocks.MECHANICAL_PRESS.getDefaultState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        source.getLevel().setBlockAndUpdate(PULLEY.east().north(), press);
        return 1;
    }

    private static int report(CommandSourceStack source) {
        if (!(source.getLevel().getBlockEntity(PULLEY) instanceof KineticBlockEntity middle)
                || !(middle instanceof BeltMotorHost host)
                || !(source.getLevel().getBlockEntity(PULLEY.east()) instanceof KineticBlockEntity end)) {
            source.sendFailure(Component.literal("Лента не собрана"));
            return 0;
        }
        float current = host.eeExtras$networkStress();
        float available = host.eeExtras$networkCapacity();
        source.sendSuccess(() -> Component.literal("Шкив " + middle.getSpeed() + ", конец " + end.getSpeed()
                + ", нагрузка " + current + "/" + available + " SU, сопротивление "
                + MOTOR.resistance(current, available) + " Ом"), false);
        return 1;
    }
}
