package ee.extras.devtest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.CEEItems;
import com.george_vi.electroenergetics.client.WireRenderer;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import ee.extras.EeExtras;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import tfc.stand.scene.Scene;

final class ElectricMachinesScene {
    static final boolean ON = Scene.requested("electric-machines");

    static void start() {
        Scene scene = new Scene("electric-machines");
        scene.server("поставить приборы", context -> {
            context.set(context.at(-3, 2, 0), EeExtras.ELECTRIC_MIXER.get().defaultBlockState());
            context.set(context.at(-3, 0, 0), AllBlocks.BASIN.getDefaultState());
            context.set(context.at(0, 2, 0), EeExtras.ELECTRIC_DEPLOYER.get().defaultBlockState()
                    .setValue(DirectionalKineticBlock.FACING, Direction.SOUTH));
            context.set(context.at(3, 2, 0), EeExtras.FLUID_DISPENSER.get().defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
            context.set(context.at(3, 1, 0), AllBlocks.FLUID_TANK.getDefaultState());
            context.set(context.at(-5, 1, 1), CEEBlocks.CREATIVE_BATTERY.getDefaultState());
        });
        scene.ticks(20).server("подключить смеситель и активатор", context -> {
            var player = context.player();
            var level = player.serverLevel();
            var source = context.at(-5, 1, 1);
            var supply = DevicesSavedData.load(level).getDevice(source, CreativeBatteryDevice.class);
            scene.check("источник EE зарегистрирован", supply != null);
            if (supply == null) return;
            supply.voltage = 330;
            for (int x : new int[] {-3, 0}) {
                var machine = context.at(x, 2, 0);
                for (int contact = 0; contact < 2; contact++) {
                    boolean wired = DevTest.spoolWire(player, new ItemStack(CEEItems.COPPER_WIRE_SPOOL.get(), 16),
                            source, contact, machine, contact);
                    scene.check("контакт " + contact + " прибора " + x + " подключён катушкой", wired);
                }
            }
            var filter = BlockEntityBehaviour.get(level, context.at(3, 2, 0), FilteringBehaviour.TYPE);
            scene.check("установлен фильтр воды", filter != null && filter.setFilter(new ItemStack(Items.WATER_BUCKET)));
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        });
        scene.ticks(40).server("обороты при питании", context -> {
            for (int x : new int[] {-3, 0}) {
                var machine = context.player().serverLevel().getBlockEntity(context.at(x, 2, 0));
                scene.check("32 об/мин без сети Create у прибора " + x, machine instanceof KineticBlockEntity be
                        && be.getSpeed() == 32 && !be.hasNetwork());
            }
        });
        scene.until("четыре провода у клиента", 400, () -> WireRenderer.getAllConnections().size() >= 4);
        scene.gui(false);
        scene.camera(-0.75, 1.4, 0, 10, 193, 17);
        scene.shot("electric-machines");
        scene.camera(3, 1.7, 0, 4, 200, 14);
        scene.shot("fluid-dispenser");
        scene.start();
    }

    private ElectricMachinesScene() {}
}
