package ee.extras.devtest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.CEEItems;
import com.george_vi.electroenergetics.client.WireRenderer;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.content.cut_off_switch.CutOffSwitchBlock;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import ee.extras.EeExtras;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import tfc.stand.scene.Scene;

final class WallDisconnectScene {
    static final boolean ON = Scene.requested("wall-disconnect");

    static void start() {
        Scene scene = new Scene("wall-disconnect");
        scene.server("установить рубильник на стену", context -> {
            var player = context.player();
            var level = player.serverLevel();
            var control = context.at(0, 2, 0);
            var support = control.north();
            context.set(support, Blocks.STONE_BRICKS.defaultBlockState());
            var stack = EeExtras.WALL_DISCONNECT_ITEM.toStack();
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            player.gameMode.useItemOn(player, level, stack, InteractionHand.MAIN_HAND, hit(support, Direction.SOUTH));
            var state = level.getBlockState(control);
            scene.check("установлен предметом на стену в разомкнутом положении", state.is(EeExtras.WALL_DISCONNECT.get())
                    && state.getValue(CutOffSwitchBlock.FACING) == Direction.SOUTH
                    && !state.getValue(CutOffSwitchBlock.CLOSED));
            context.set(context.at(-3, 1, 0), CEEBlocks.CREATIVE_BATTERY.getDefaultState());
            context.set(context.at(3, 1, 0), EeExtras.ELECTRIC_PRESS.get().defaultBlockState());
        });
        scene.ticks(20).server("подключить пресс через рубильник", context -> {
            var player = context.player();
            var source = context.at(-3, 1, 0);
            var control = context.at(0, 2, 0);
            var press = context.at(3, 1, 0);
            var battery = DevicesSavedData.load(player.serverLevel()).getDevice(source, CreativeBatteryDevice.class);
            scene.check("источник EE зарегистрирован", battery != null);
            if (battery == null) return;
            battery.voltage = 330;
            scene.check("входная клемма подключена катушкой", wire(player, source, 0, control, 0));
            scene.check("выходная клемма подключена катушкой", wire(player, control, 1, press, 0));
            scene.check("обратный провод подключён катушкой", wire(player, source, 1, press, 1));
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        });
        scene.ticks(30).server("включить питание вручную", context -> {
            var player = context.player();
            scene.check("пресс стоит при разомкнутой цепи", speed(player.serverLevel(), context.at(3, 1, 0)) == 0);
            click(player, context.at(0, 2, 0));
        });
        scene.ticks(40).server("проверить работу цепи", context -> {
            scene.check("пресс работает при замкнутой цепи",
                    speed(context.player().serverLevel(), context.at(3, 1, 0)) == 32);
        });
        scene.until("три провода видны клиенту", 400, () -> WireRenderer.getAllConnections().size() >= 3);
        scene.gui(false);
        scene.camera(0, 2.45, 0, 5, 196, 16);
        scene.shot("disconnect-closed");
        scene.server("подать сигнал аварийной остановки", context -> {
            context.set(context.at(0, 1, 0), Blocks.REDSTONE_BLOCK.defaultBlockState());
        });
        scene.ticks(30).server("проверить остановку и блокировку включения", context -> {
            var player = context.player();
            var level = player.serverLevel();
            var control = context.at(0, 2, 0);
            scene.check("красный камень разомкнул цепь и остановил пресс",
                    !level.getBlockState(control).getValue(CutOffSwitchBlock.CLOSED)
                            && speed(level, context.at(3, 1, 0)) == 0);
            click(player, control);
            scene.check("при активном сигнале ручное включение заблокировано",
                    !level.getBlockState(control).getValue(CutOffSwitchBlock.CLOSED));
        });
        scene.shot("disconnect-open");
        scene.server("снять сигнал остановки", context -> {
            context.set(context.at(0, 1, 0), Blocks.AIR.defaultBlockState());
        });
        scene.ticks(20).server("проверить ручной сброс", context -> {
            var level = context.player().serverLevel();
            var control = context.at(0, 2, 0);
            scene.check("после снятия сигнала пресс остаётся выключен",
                    speed(level, context.at(3, 1, 0)) == 0);
            click(context.player(), control);
        });
        scene.ticks(40).server("проверить повторный пуск и демонтаж", context -> {
            var level = context.player().serverLevel();
            scene.check("после ручного включения пресс снова работает", speed(level, context.at(3, 1, 0)) == 32);
            context.set(context.at(0, 2, -1), Blocks.AIR.defaultBlockState());
            scene.check("рубильник снимается при удалении опоры", level.isEmptyBlock(context.at(0, 2, 0)));
        });
        scene.ticks(20).server("проверить отключение после демонтажа", context -> {
            scene.check("пресс обесточен после демонтажа",
                    speed(context.player().serverLevel(), context.at(3, 1, 0)) == 0);
        });
        scene.start();
    }

    private static BlockHitResult hit(BlockPos pos, Direction face) {
        return new BlockHitResult(Vec3.atCenterOf(pos).relative(face, 0.5), face, pos, false);
    }

    private static void click(Player player, BlockPos pos) {
        var level = player.level();
        level.getBlockState(pos).useItemOn(ItemStack.EMPTY, level, player, InteractionHand.MAIN_HAND,
                hit(pos, Direction.SOUTH));
    }

    private static boolean wire(net.minecraft.server.level.ServerPlayer player, BlockPos from, int fromNode,
                                BlockPos to, int toNode) {
        return DevTest.spoolWire(player, new ItemStack(CEEItems.COPPER_WIRE_SPOOL.get(), 16), from, fromNode, to, toNode);
    }

    private static float speed(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof KineticBlockEntity be ? be.getSpeed() : Float.NaN;
    }

    private WallDisconnectScene() {}
}
