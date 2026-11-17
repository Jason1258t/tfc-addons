package ee.extras.devtest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.CEEItems;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import ee.extras.EeExtras;
import ee.extras.machine.ElectricPressBlockEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import tfc.stand.scene.Scene;

final class ElectricPressScene {
    static final boolean ON = Scene.requested("electric-press");

    static void start() {
        Scene scene = new Scene("electric-press");
        scene.server("пресс и источник", context -> {
            context.set(context.at(0, 0, 0), Blocks.STONE.defaultBlockState());
            context.set(context.at(0, 2, 0), EeExtras.ELECTRIC_PRESS.get().defaultBlockState());
            context.set(context.at(-3, 1, 0), CEEBlocks.CREATIVE_BATTERY.getDefaultState());
        });
        scene.ticks(20).server("подключение катушкой", context -> {
            var player = context.player();
            var battery = context.at(-3, 1, 0);
            var press = context.at(0, 2, 0);
            var supply = DevicesSavedData.load(player.serverLevel()).getDevice(battery, CreativeBatteryDevice.class);
            scene.check("источник EE зарегистрирован", supply != null);
            if (supply == null) return;
            supply.voltage = 330;
            for (int contact = 0; contact < 2; contact++) {
                boolean wired = DevTest.spoolWire(player, new ItemStack(CEEItems.COPPER_WIRE_SPOOL.get(), 16),
                        battery, contact, press, contact);
                scene.check("провод подведён к клемме " + contact, wired);
            }
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        });
        scene.ticks(40).server("пресс под напряжением", context -> {
            var blockEntity = context.player().serverLevel().getBlockEntity(context.at(0, 2, 0));
            scene.check("32 об/мин без внешнего вала", blockEntity instanceof ElectricPressBlockEntity press
                    && press.getSpeed() == 32 && !press.hasNetwork());
        });
        scene.gui(false);
        scene.camera(-1, 1.5, 0, 7, 145, 28);
        scene.shot("electric-press");
        scene.start();
    }

    private ElectricPressScene() {}
}
