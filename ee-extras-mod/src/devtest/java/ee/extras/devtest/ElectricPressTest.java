package ee.extras.devtest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.simibubi.create.AllItems;
import ee.extras.EeExtras;
import ee.extras.machine.ElectricPressBlockEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

final class ElectricPressTest {
    private static final BlockPos PRESS = new BlockPos(9, -58, 9);
    private static final BlockPos BATTERY = PRESS.south(3);

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("electricpresstest")
                .then(Commands.literal("build").executes(ctx -> build(ctx.getSource())))
                .then(Commands.literal("wire").executes(ctx -> wire(ctx.getSource())))
                .then(Commands.literal("feed").executes(ctx -> feed(ctx.getSource())))
                .then(Commands.literal("voltage").then(Commands.argument("volts", IntegerArgumentType.integer(0, 500))
                        .executes(ctx -> voltage(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "volts")))))
                .then(Commands.literal("speed").then(Commands.argument("rpm", IntegerArgumentType.integer(0, 32))
                        .executes(ctx -> speed(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "rpm")))))
                .then(Commands.literal("sheet").executes(ctx -> sheet(ctx.getSource()))));
    }

    private static int build(CommandSourceStack source) {
        var level = source.getLevel();
        DevTest.place(level, PRESS.below(2), Blocks.STONE.defaultBlockState());
        DevTest.place(level, PRESS, EeExtras.ELECTRIC_PRESS.get().defaultBlockState());
        DevTest.place(level, BATTERY, CEEBlocks.CREATIVE_BATTERY.getDefaultState());
        return 1;
    }

    private static int wire(CommandSourceStack source) {
        DevTest.wire(source.getLevel(), BATTERY, 0, PRESS, 0);
        DevTest.wire(source.getLevel(), BATTERY, 1, PRESS, 1);
        return voltage(source, 330);
    }

    private static int voltage(CommandSourceStack source, int volts) {
        var battery = DevicesSavedData.load(source.getLevel()).getDevice(BATTERY, CreativeBatteryDevice.class);
        if (battery == null) throw new IllegalStateException("Батарея стенда отсутствует");
        battery.voltage = volts;
        return 1;
    }

    private static int feed(CommandSourceStack source) {
        var level = source.getLevel();
        var ingot = new ItemEntity(level, PRESS.getX() + 0.5, PRESS.getY() - 0.9, PRESS.getZ() + 0.5,
                new ItemStack(Items.IRON_INGOT), 0, 0, 0);
        return level.addFreshEntity(ingot) ? 1 : 0;
    }

    private static int speed(CommandSourceStack source, int expected) {
        if (!(source.getLevel().getBlockEntity(PRESS) instanceof ElectricPressBlockEntity press)) {
            source.sendFailure(Component.literal("FAIL: пресс отсутствует"));
            return 0;
        }
        if (press.getSpeed() != expected || press.hasSource() || press.hasNetwork()) {
            source.sendFailure(Component.literal("FAIL: ожидалось " + expected + " об/мин без кинетической сети, получено "
                    + press.getSpeed() + " об/мин, источник " + press.hasSource() + ", сеть " + press.hasNetwork()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("PASS: " + expected + " об/мин без кинетической сети"), false);
        return 1;
    }

    private static int sheet(CommandSourceStack source) {
        var sheets = source.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(PRESS.below()).inflate(0.1),
                item -> AllItems.IRON_SHEET.isIn(item.getItem()));
        if (sheets.size() != 1) {
            source.sendFailure(Component.literal("FAIL: пресс не изготовил железный лист"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("PASS: железный лист изготовлен по рецепту Create"), false);
        return 1;
    }
}
