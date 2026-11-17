package ee.extras.devtest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.CEEItems;
import com.george_vi.electroenergetics.client.WireRenderer;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem;
import ee.extras.EeExtras;
import ee.extras.motor.BeltMotorHost;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import tfc.stand.scene.Scene;

final class IntegratedDrivesScene {
    static final boolean ON = Scene.requested("integrated-drives");

    static void start() {
        var scene = new Scene("integrated-drives");
        var beltMount = new AtomicReference<BlockPos>();
        var destination = new AtomicReference<BlockPos>();
        var delivery = new AtomicReference<>(CompletableFuture.completedFuture(false));
        Item cargo = BuiltInRegistries.ITEM.get(ResourceLocation.parse("tfc:metal/ingot/copper"));
        scene.server("собрать конвейер и желоб", context -> {
            var level = context.player().serverLevel();
            var shaft = AllBlocks.SHAFT.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.Z);
            var first = context.at(-1, 1, 0);
            var last = context.at(1, 1, 0);
            for (int x = -1; x <= 1; x++) context.set(context.at(x, 1, 0), shaft);
            BeltConnectorItem.createBelts(level, first, last);
            beltMount.set(context.at(0, 1, 0));
            context.set(context.at(1, 1, -1), AllBlocks.MECHANICAL_PRESS.getDefaultState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
            var auger = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("simulated:auger_shaft"))
                    .defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X);
            for (int x = -1; x <= 1; x++) context.set(context.at(x, 1, 4), auger);
            destination.set(context.at(1, 1, 4));
            for (int z : new int[] {2, 6}) {
                context.set(context.at(-3, 1, z), CEEBlocks.CREATIVE_BATTERY.getDefaultState());
            }
        });
        scene.gui(false);
        scene.camera(0, 1.25, 0, 4.4, 190, 23);
        inspectBelt(scene, beltMount, "до монтажа");
        scene.shot("belt-before");
        scene.ticks(20).server("смонтировать и подключить катушкой", context -> {
            var player = context.player();
            var level = player.serverLevel();
            for (int z : new int[] {0, 4}) {
                var battery = context.at(-3, 1, z + 2);
                setVoltage(level, battery, 330);
                var pos = context.at(0, 1, z);
                var state = level.getBlockState(pos);
                var host = level.getBlockEntity(pos);
                var electrical = (ElectricalDeviceBlock<?>) state.getBlock();
                scene.check("до монтажа нет клемм на " + z, electrical.getNodePositions(level, pos, state).isEmpty());
                scene.check("привод установлен на " + z, install(player, pos));
                scene.check("монтаж сохранил блок и блок-сущность на " + z + "",
                        state == level.getBlockState(pos) && host == level.getBlockEntity(pos));
                for (int pin = 0; pin < 2; pin++) {
                    scene.check("катушка подключила клемму " + pin + " на " + z,
                            DevTest.spoolWire(player, new ItemStack(CEEItems.COPPER_WIRE_SPOOL.get(), 16),
                                    battery, pin, pos, pin));
                }
            }
            var first = context.at(-1, 1, 4);
            var stack = new ItemStack(cargo, 16);
            var remainder = augerSlot(level, first, "insertSlot",
                    new Class<?>[] {ItemStack.class, int.class, boolean.class}, stack, 0, false);
            var loaded = augerSlot(level, first, "getItem", new Class<?>[] {int.class}, 0);
            scene.check("в исходной секции 16 медных слитков (слот " + loaded + ", остаток " + remainder + ")",
                    remainder.isEmpty() && loaded.is(cargo) && loaded.getCount() == 16);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        });
        scene.until("крайняя секция получила 16 медных слитков", 300, () -> {
            var result = delivery.get();
            if (!result.isDone()) return false;
            if (result.join()) return true;
            var server = Minecraft.getInstance().getSingleplayerServer();
            delivery.set(server.submit(() -> deliveredCargo(server.overworld(), destination.get(), cargo) == 16));
            return false;
        });
        scene.server("проверить вращение и транспортировку", context -> {
            var level = context.player().serverLevel();
            scene.check("нагруженный конвейер движется при 330 В", speed(level, context.at(1, 1, 0)) == 32);
            scene.check("шнек вращается прямо при 330 В", speed(level, context.at(1, 1, 4)) == 32);
            int first = deliveredCargo(level, context.at(-1, 1, 4), cargo);
            int middle = deliveredCargo(level, context.at(0, 1, 4), cargo);
            int last = deliveredCargo(level, destination.get(), cargo);
            scene.check("шнек доставил слитки до крайней секции (" + first + " → " + middle + " → " + last + ")",
                    first == 0 && middle == 0 && last == 16);
        });
        scene.until("клиент получил сторону установки привода конвейера", 400, () ->
                Minecraft.getInstance().level.getBlockEntity(beltMount.get()) instanceof BeltMotorHost motor
                        && motor.eeExtras$mountSide() == Direction.SOUTH);
        scene.until("четыре провода видны клиенту", 400, () -> WireRenderer.getAllConnections().size() >= 4);
        scene.gui(false);
        scene.camera(0, 1.25, 0, 4.4, 190, 23);
        inspectBelt(scene, beltMount, "после монтажа");
        scene.shot("belt-drive");
        scene.camera(0, 1.25, 4, 4.4, 190, 23);
        scene.shot("auger-drive");
        scene.server("снизить напряжение", context -> setVoltage(context.player().serverLevel(), context.at(-3, 1, 2), 270));
        scene.ticks(25).server("просадка и реверс", context -> {
            var player = context.player();
            var level = player.serverLevel();
            scene.check("нагруженный конвейер остановился при 270 В", speed(level, context.at(1, 1, 0)) == 0);
            context.set(context.at(0, 0, 4), Blocks.REDSTONE_BLOCK.defaultBlockState());
        });
        scene.ticks(20).server("аварийная остановка", context -> {
            var player = context.player();
            scene.check("красный камень остановил шнек", speed(player.serverLevel(), context.at(1, 1, 4)) == 0);
            scene.check("ключ обработан на желобе", wrench(player, context.at(0, 1, 4), false));
            context.set(context.at(0, 0, 4), Blocks.STONE.defaultBlockState());
        });
        scene.ticks(30).server("обратный ход и снятие", context -> {
            var player = context.player();
            var level = player.serverLevel();
            var pos = context.at(0, 1, 4);
            var host = level.getBlockEntity(pos);
            scene.check("реверс передан крайней секции", speed(level, context.at(-1, 1, 4)) == -32);
            scene.check("Shift + ключ снимает привод", wrench(player, pos, true));
            scene.check("клеммы и провода убраны, блок сохранён",
                    host == level.getBlockEntity(pos) && host instanceof BeltMotorHost motor
                            && motor.eeExtras$mountSide() == null
                            && InfrastructureSavedData.load(level).getNodesAt(pos).isEmpty());
            scene.check("повторный монтаж в том же тике", install(player, pos));
        });
        scene.ticks(20).server("после повторного монтажа", context -> {
            var level = context.player().serverLevel();
            scene.check("клеммы нового привода сохранились",
                    level.getBlockEntity(context.at(0, 1, 4)) instanceof BeltMotorHost motor
                            && motor.eeExtras$mountSide() == Direction.SOUTH);
            scene.check("без проводов желоб стоит", speed(level, context.at(1, 1, 4)) == 0);
        });
        scene.start();
    }

    private static void inspectBelt(Scene scene, AtomicReference<BlockPos> mount, String phase) {
        scene.client("состояние ленты " + phase, () -> {
            var level = Minecraft.getInstance().level;
            for (int x = -1; x <= 1; x++) {
                var pos = mount.get().offset(x, 0, 0);
                var entity = level.getBlockEntity(pos);
                scene.note(phase + ", секция " + x + " в " + pos.toShortString() + ": "
                        + level.getBlockState(pos) + " / "
                        + (entity == null ? "без блок-сущности" : entity.saveWithoutMetadata(level.registryAccess())));
            }
        });
    }

    private static int deliveredCargo(ServerLevel level, BlockPos pos, Item cargo) {
        var stack = augerSlot(level, pos, "getItem", new Class<?>[] {int.class}, 0);
        return stack.is(cargo) ? stack.getCount() : 0;
    }

    private static ItemStack augerSlot(ServerLevel level, BlockPos pos, String operation,
                                      Class<?>[] types, Object... args) {
        var entity = level.getBlockEntity(pos);
        if (entity == null) return ItemStack.EMPTY;
        try {
            // Simulated необязателен для EE Extras. Сцена полной сборки обращается к его обычному инвентарю.
            var inventory = entity.getClass().getMethod("getInventory").invoke(entity);
            return (ItemStack) inventory.getClass().getMethod(operation, types).invoke(inventory, args);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Инвентарь шнека на " + pos.toShortString(), error);
        }
    }

    private static boolean install(ServerPlayer player, BlockPos pos) {
        var kit = EeExtras.BELT_MOTOR_ITEM.get().getDefaultInstance();
        player.setItemInHand(InteractionHand.MAIN_HAND, kit);
        return kit.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit(pos, Direction.SOUTH)))
                == InteractionResult.CONSUME;
    }

    private static BlockHitResult hit(BlockPos pos, Direction face) {
        return new BlockHitResult(Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(face.getNormal()).scale(0.5)),
                face, pos, false);
    }

    private static void setVoltage(ServerLevel level, BlockPos battery, double voltage) {
        DevicesSavedData.load(level).getDevice(battery, CreativeBatteryDevice.class).voltage = voltage;
    }

    private static float speed(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof KineticBlockEntity kinetic ? kinetic.getSpeed() : Float.NaN;
    }

    private static boolean wrench(ServerPlayer player, BlockPos pos, boolean sneaking) {
        player.setItemInHand(InteractionHand.MAIN_HAND, AllItems.WRENCH.asStack());
        player.setShiftKeyDown(sneaking);
        try {
            return NeoForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickBlock(
                    player, InteractionHand.MAIN_HAND, pos, hit(pos, Direction.SOUTH))).isCanceled();
        } finally {
            player.setShiftKeyDown(false);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
    }
}
