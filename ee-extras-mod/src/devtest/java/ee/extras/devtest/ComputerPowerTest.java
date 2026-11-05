package ee.extras.devtest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import dan200.computercraft.shared.ModRegistry;
import dan200.computercraft.shared.computer.blocks.ComputerBlockEntity;
import dan200.computercraft.shared.lectern.CustomLecternBlockEntity;
import ee.extras.EeExtras;
import ee.extras.cable.CableKind;
import ee.extras.cable.CableLaying;
import ee.extras.computer.ComputerPowerBlock;
import ee.extras.computer.ComputerPowerDevice;
import ee.extras.computer.PocketCharge;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.util.Set;

final class ComputerPowerTest {
    private static final BlockPos SOURCE = new BlockPos(5, -59, 6);
    private static final BlockPos TERMINATION = SOURCE.east(2);
    private static final BlockPos INLET = SOURCE.east(5);
    private static final BlockPos DESKTOP = INLET.east();
    private static final BlockPos CHARGER = TERMINATION.south(3);
    private static final BlockPos LECTERN = DESKTOP.south(2);

    static int setup(CommandSourceStack source, boolean advanced) {
        var level = source.getLevel();
        DevTest.place(level, SOURCE, CEEBlocks.CREATIVE_BATTERY.getDefaultState());
        DevTest.place(level, TERMINATION, Extras.eeTermination());
        var computer = advanced ? ModRegistry.Blocks.COMPUTER_ADVANCED : ModRegistry.Blocks.COMPUTER_NORMAL;
        DevTest.place(level, DESKTOP, computer.get().defaultBlockState());
        DevTest.place(level, DESKTOP.east(), ModRegistry.Blocks.DISK_DRIVE.get().defaultBlockState());
        DevTest.place(level, INLET, EeExtras.COMPUTER_INLET.get().defaultBlockState()
                .setValue(ComputerPowerBlock.FACING, Direction.WEST));
        DevTest.place(level, CHARGER, Extras.up(EeExtras.POCKET_CHARGER.get().defaultBlockState()));
        var termination = Extras.end(level, TERMINATION);
        DevTest.wire(level, SOURCE, 0, TERMINATION, termination.conductor(1).id());
        DevTest.wire(level, SOURCE, 1, TERMINATION, termination.conductor(2).id());
        if (!CableLaying.connect(level, CableKind.DUPLEX, termination, Extras.end(level, INLET))
                || !CableLaying.connect(level, CableKind.DUPLEX, termination, Extras.end(level, CHARGER))) {
            throw new IllegalStateException("Не удалось проложить двухжильный кабель");
        }
        var charger = DevicesSavedData.load(level).getDevice(CHARGER, ComputerPowerDevice.class);
        if (charger == null || !charger.insertPocket(new ItemStack(ModRegistry.Items.POCKET_COMPUTER_NORMAL.get()))) {
            throw new IllegalStateException("Станция не приняла карманный ПК");
        }
        return voltage(source, 120);
    }

    static int voltage(CommandSourceStack source, double volts) {
        var battery = DevicesSavedData.load(source.getLevel()).getDevice(SOURCE, CreativeBatteryDevice.class);
        if (battery == null) throw new IllegalStateException("Источник отсутствует");
        battery.voltage = volts;
        return 1;
    }

    static int start(CommandSourceStack source) {
        if (!(source.getLevel().getBlockEntity(DESKTOP) instanceof ComputerBlockEntity desktop)) return 0;
        desktop.createServerComputer().turnOn();
        return 1;
    }

    static int installScript(CommandSourceStack source) {
        if (!(source.getLevel().getBlockEntity(DESKTOP) instanceof ComputerBlockEntity desktop)) return 0;
        var mount = desktop.createServerComputer().createRootMount();
        String script = """
                assert(peripheral.find("drive"), "Дисковод недоступен")
                local file = assert(fs.open("power-test-ok", "w"))
                file.write("ok")
                file.close()
                """;
        try (var file = mount.openFile("startup.lua", Set.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING))) {
            file.write(ByteBuffer.wrap(script.getBytes(StandardCharsets.UTF_8)));
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось записать программу Lua", e);
        }
        return 1;
    }

    static int discharge(CommandSourceStack source) {
        var level = source.getLevel();
        var stack = new ItemStack(ModRegistry.Items.POCKET_COMPUTER_ADVANCED.get());
        stack.set(ModRegistry.DataComponents.ON.get(), true);
        PocketCharge.set(stack, 120);
        DevTest.place(level, LECTERN, ModRegistry.Blocks.LECTERN.get().defaultBlockState());
        if (!(level.getBlockEntity(LECTERN) instanceof CustomLecternBlockEntity lectern)) return 0;
        var tag = new CompoundTag();
        tag.put("Item", stack.save(level.registryAccess()));
        lectern.loadAdditional(tag, level.registryAccess());
        return 1;
    }

    static int batteryReport(CommandSourceStack source) {
        if (!(source.getLevel().getBlockEntity(LECTERN) instanceof CustomLecternBlockEntity lectern)) return 0;
        var stack = lectern.getItem();
        boolean on = stack.getOrDefault(ModRegistry.DataComponents.ON.get(), false);
        source.sendSuccess(() -> Component.literal("Карманный ПК: " + PocketCharge.get(stack) + " Вт·тик, включён " + on), false);
        return 1;
    }

    static int report(CommandSourceStack source) {
        var level = source.getLevel();
        var devices = DevicesSavedData.load(level);
        var inlet = new CompoundTag();
        var charger = new CompoundTag();
        devices.getDevice(INLET, ComputerPowerDevice.class).write(inlet);
        devices.getDevice(CHARGER, ComputerPowerDevice.class).write(charger);
        var pocket = ItemStack.parseOptional(level.registryAccess(), charger.getCompound("Pocket"));
        String desktopState = "разрушен";
        boolean ranLua = false;
        if (level.getBlockEntity(DESKTOP) instanceof ComputerBlockEntity desktop) {
            var computer = desktop.getServerComputer();
            desktopState = computer != null && computer.isOn() ? "включён" : "выключен";
            try {
                ranLua = computer != null && computer.createRootMount().exists("power-test-ok");
            } catch (IOException e) {
                throw new IllegalStateException("Не удалось прочитать данные ПК", e);
            }
        }
        String message = String.format("ПК %s, ввод %.1f В / %.1f Вт, сгорел %s, заряд %d Вт·тик, Lua и дисковод %s",
                desktopState, inlet.getDouble("InputVoltage"), inlet.getDouble("InputPower"),
                inlet.getBoolean("Failed"), PocketCharge.get(pocket), ranLua);
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }
}
