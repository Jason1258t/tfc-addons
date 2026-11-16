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
import net.minecraft.world.item.Items;
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
                var kit = EeExtras.BELT_MOTOR_ITEM.get().getDefaultInstance();
                player.setItemInHand(InteractionHand.MAIN_HAND, kit);
                scene.check("привод установлен на " + z,
                        kit.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit(pos, Direction.SOUTH)))
                                == InteractionResult.CONSUME);
                scene.check("монтаж сохранил блок и блок-сущность на " + z + "",
                        state == level.getBlockState(pos) && host == level.getBlockEntity(pos));
                for (int pin = 0; pin < 2; pin++) {
                    scene.check("катушка подключила клемму " + pin + " на " + z,
                            DevTest.spoolWire(player, new ItemStack(CEEItems.COPPER_WIRE_SPOOL.get(), 16),
                                    battery, pin, pos, pin));
                }
            }
            var first = context.at(-1, 1, 4);
            var input = level.getBlockEntity(first);
            if (input != null) {
                // Загрузка через штатный формат секции проверяет путь по желобу отдельно от выдачи смежных блоков.
                var tag = input.saveWithoutMetadata(level.registryAccess());
                var inventory = tag.getCompound("Inventory");
                inventory.put("item", new ItemStack(Items.EMERALD, 16).save(level.registryAccess()));
                tag.put("Inventory", inventory);
                input.loadWithComponents(tag, level.registryAccess());
            }
            scene.check("в исходной секции 16 изумрудов", deliveredEmeralds(level, first) == 16);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        });
        scene.until("крайняя секция получила 16 изумрудов", 800, () -> {
            var result = delivery.get();
            if (!result.isDone()) return false;
            if (result.join()) return true;
            var server = Minecraft.getInstance().getSingleplayerServer();
            delivery.set(server.submit(() -> deliveredEmeralds(server.overworld(), destination.get()) == 16));
            return false;
        });
        scene.server("проверить вращение и транспортировку", context -> {
            var level = context.player().serverLevel();
            scene.check("нагруженный конвейер движется при 330 В", speed(level, context.at(1, 1, 0)) == 32);
            scene.check("шнек вращается прямо при 330 В", speed(level, context.at(1, 1, 4)) == 32);
            int first = deliveredEmeralds(level, context.at(-1, 1, 4));
            int middle = deliveredEmeralds(level, context.at(0, 1, 4));
            int last = deliveredEmeralds(level, destination.get());
            scene.check("шнек доставил изумруды до крайней секции (" + first + " → " + middle + " → " + last + ")",
                    first == 0 && middle == 0 && last == 16);
        });
        scene.until("клиент получил сторону установки привода конвейера", 400, () ->
                Minecraft.getInstance().level.getBlockEntity(beltMount.get()) instanceof BeltMotorHost motor
                        && motor.eeExtras$mountSide() == Direction.SOUTH);
        scene.until("четыре провода видны клиенту", 400, () -> WireRenderer.getAllConnections().size() >= 4);
        scene.gui(false);
        scene.camera(0, 1.25, 0, 4.4, 190, 23);
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
        });
        scene.ticks(20).server("после демонтажа", context ->
                scene.check("желоб стоит", speed(context.player().serverLevel(), context.at(1, 1, 4)) == 0));
        scene.start();
    }

    private static int deliveredEmeralds(ServerLevel level, BlockPos pos) {
        var output = level.getBlockEntity(pos);
        if (output == null) return 0;
        var item = output.saveWithoutMetadata(level.registryAccess()).getCompound("Inventory").getCompound("item");
        return "minecraft:emerald".equals(item.getString("id")) ? item.getInt("count") : 0;
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
