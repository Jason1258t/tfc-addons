package ee.extras.devtest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import ee.extras.EeExtras;
import ee.extras.pcb.PcbCircuitBlock;
import ee.extras.pcb.PcbEditorMenu;
import ee.extras.pcb.PcbEditorScreen;
import ee.extras.pcb.PcbItems;
import ee.extras.pcb.PcbTopology;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

final class PcbUpgradePhotos {
    private enum BoardMode { LED_FORWARD, LED_REVERSE, INDICATOR }

    private static BlockPos base;
    private static volatile BlockPos activeBoard;
    private static BlockPos activeBattery;
    private static boolean indicatorCaptured;
    private static volatile Component measurement;
    private static final boolean INDICATOR_ONLY = java.nio.file.Files.exists(java.nio.file.Path.of("pcb-indicator-photo"));

    private PcbUpgradePhotos() {}

    static void tick(Minecraft mc, MinecraftServer server, int tick) {
        if (tick % 10 == 0) mc.gui.getChat().clearMessages(false);
        if (INDICATOR_ONLY) {
            indicatorOnly(mc, server, tick);
            return;
        }
        if (tick >= 845) PcbMountPhotos.tick(mc, server, tick - 745);
        if (tick == 100) server.execute(() -> {
            var player = player(mc, server);
            base = player.blockPosition().atY(300);
            var level = player.serverLevel();
            level.setDayTime(6000);
            level.setWeatherParameters(24000, 0, false, false);
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.NIGHT_VISION, 4000, 0, false, false, false));
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            PcbSupplyTest.build(player.createCommandSourceStack(), base, false, true);
            camera(player, base.east(4), new Vec3(0, 4, 6));
        });
        if (tick == 175) supplyPhoto(mc, server, false);
        if (tick == 179 && measurement != null) mc.gui.setOverlayMessage(measurement, false);
        if (tick == 185) photo(mc, "02-ac");
        if (tick == 190) server.execute(() -> {
            var player = player(mc, server);
            PcbSupplyTest.build(player.createCommandSourceStack(), base, true, true);
        });
        if (tick == 260) supplyPhoto(mc, server, true);
        if (tick == 264 && measurement != null) mc.gui.setOverlayMessage(measurement, false);
        if (tick == 270) photo(mc, "02-dc");
        if (tick == 275) server.execute(() -> {
            var player = player(mc, server);
            camera(player, base.south(2), new Vec3(0, 1.5, 1.8));
        });
        if (tick == 310) {
            var table = base.south(2);
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(table).add(0, 0.5, 0), Direction.UP, table, false));
        }
        if (tick == 345) {
            boolean opened = mc.screen instanceof PcbEditorScreen;
            ClientTest.check("Открыт редактор модуля", opened);
            if (opened) {
                mc.gameMode.handleInventoryButtonClick(mc.player.containerMenu.containerId, PcbEditorMenu.VOLTS);
            }
        }
        if (tick == 370 && mc.screen instanceof PcbEditorScreen screen) {
            var data = screen.getMenu().getSlot(0).getItem().get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            ClientTest.check("После клика в редакторе модуль настроен на 36 В",
                    data != null && data.copyTag().getInt("PcbSupplySetting") == 4);
            photo(mc, "03-supply-editor");
        }
        if (tick == 380) server.execute(() -> {
            var player = player(mc, server);
            if (player.serverLevel().getBlockEntity(base.south(2)) instanceof ee.extras.pcb.PcbEditorBlockEntity editor) {
                editor.setItem(0, ItemStack.EMPTY);
            }
        });
        if (tick == 410 && mc.screen instanceof PcbEditorScreen) {
            for (int i = 0; i < 5; i++) {
                mc.gameMode.handleInventoryButtonClick(mc.player.containerMenu.containerId, PcbEditorMenu.OHMS);
            }
        }
        if (tick == 435 && mc.screen instanceof PcbEditorScreen) photo(mc, "03-resistor-editor");
        if (tick == 445) {
            mc.player.closeContainer();
            server.execute(() -> setupBoard(player(mc, server)));
        }
        if (tick == 510) {
            ClientTest.check("Декоративные провода от 300 В не подали напряжение на плату L1",
                    !mc.level.getBlockState(activeBoard).getValue(PcbCircuitBlock.BURNT));
            photo(mc, "01-wires");
        }
        if (tick == 525) server.execute(() -> {
            var player = player(mc, server);
            camera(player, activeBoard, new Vec3(0, 1.7, 1.2));
            click(player, 7);
        });
        if (tick == 570) photo(mc, "04-caps");
        if (tick == 585) server.execute(() -> click(player(mc, server), 3));
        if (tick == 592) {
            mc.gui.setOverlayMessage(Component.literal("25 В · прямая полярность"), false);
            var state = mc.level.getBlockState(activeBoard);
            ClientTest.check("Плата под 25 В включилась", state.getValue(PcbCircuitBlock.LIT)
                    && !state.getValue(PcbCircuitBlock.BURNT));
            photo(mc, "06-led-forward");
        }
        if (tick == 620) ClientTest.check("Кнопка отпустилась",
                !mc.level.getBlockState(activeBoard).getValue(PcbCircuitBlock.LIT));
        if (tick == 625) server.execute(() -> {
            var player = player(mc, server);
            setupBoard(player, BoardMode.LED_REVERSE);
            camera(player, activeBoard, new Vec3(0, 1.7, 1.2));
        });
        if (tick == 665) server.execute(() -> {
            var player = player(mc, server);
            click(player, 7);
            click(player, 3);
        });
        if (tick == 672) {
            mc.gui.setOverlayMessage(Component.literal("25 В · обратная полярность"), false);
            var state = mc.level.getBlockState(activeBoard);
            ClientTest.check("Светодиод при обратной полярности выключен", !state.getValue(PcbCircuitBlock.LIT)
                    && !state.getValue(PcbCircuitBlock.BURNT));
            photo(mc, "06-led-reverse");
        }
        if (tick == 700) server.execute(() -> {
            var player = player(mc, server);
            setupBoard(player, BoardMode.INDICATOR);
            camera(player, activeBoard, new Vec3(0, 1.7, 1.2));
        });
        if (tick == 740) server.execute(() -> click(player(mc, server), 3));
        if (tick > 740 && tick < 775 && !indicatorCaptured && mc.level.getBlockState(activeBoard)
                .getOptionalValue(PcbCircuitBlock.LIT).orElse(false)) {
            indicatorCaptured = true;
            ClientTest.check("Кнопка и индикатор на 25 В со старым тонким проводом",
                    !mc.level.getBlockState(activeBoard).getValue(PcbCircuitBlock.BURNT));
            photo(mc, "05-indicator-25v");
        }
        if (tick == 775 && !indicatorCaptured) {
            ClientTest.check("Кнопка и индикатор на 25 В со старым тонким проводом", false);
            server.execute(() -> {
                var level = player(mc, server).serverLevel();
                var data = new CompoundTag();
                DevicesSavedData.load(level).getDevice(activeBoard).write(data);
                System.out.println("EECLIENT PCB 25 В " + data);
            });
        }
        if (tick == 780) server.execute(() -> setBattery(player(mc, server).serverLevel(), activeBattery, 60));
        if (tick == 815) {
            ClientTest.check("Защита платы сработала при 60 В", mc.level.getBlockState(activeBoard).getValue(PcbCircuitBlock.BURNT));
            photo(mc, "05-protection-60v");
        }
        if (tick == 1230) mc.stop();
    }

    private static void indicatorOnly(Minecraft mc, MinecraftServer server, int tick) {
        if (tick == 100) server.execute(() -> {
            var player = player(mc, server);
            base = player.blockPosition().atY(300);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            player.serverLevel().setDayTime(6000);
            player.serverLevel().setWeatherParameters(24000, 0, false, false);
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.NIGHT_VISION, 4000, 0, false, false, false));
            setupBoard(player, BoardMode.INDICATOR);
            camera(player, activeBoard, new Vec3(0.2, 1.3, 0.95));
        });
        if (tick == 185) mc.gui.setOverlayMessage(Component.literal("Кнопка и индикатор · батарея 25 В"), false);
        if (tick == 190) server.execute(() -> click(player(mc, server), 3));
        if (tick > 190 && tick < 220 && !indicatorCaptured && mc.level.getBlockState(activeBoard)
                .getOptionalValue(PcbCircuitBlock.LIT).orElse(false)) {
            indicatorCaptured = true;
            ClientTest.check("Индикатор при 25 В", !mc.level.getBlockState(activeBoard).getValue(PcbCircuitBlock.BURNT));
            photo(mc, "05-indicator-25v");
        }
        if (tick == 220 && !indicatorCaptured) ClientTest.check("Индикатор при 25 В", false);
        if (tick == 230) {
            mc.gui.setOverlayMessage(Component.literal("Защита платы L1 · батарея 60 В"), false);
            server.execute(() -> setBattery(player(mc, server).serverLevel(), activeBattery, 60));
        }
        if (tick == 260) {
            ClientTest.check("Защита платы при 60 В", mc.level.getBlockState(activeBoard).getValue(PcbCircuitBlock.BURNT));
            photo(mc, "05-protection-60v");
        }
        if (tick == 300) mc.stop();
    }

    private static void supplyPhoto(Minecraft mc, MinecraftServer server, boolean dc) {
        server.execute(() -> {
            var player = player(mc, server);
            var data = new CompoundTag();
            DevicesSavedData.load(player.serverLevel()).getDevice(base.east(5)).write(data);
            double min = data.getDouble("OutputMin"), max = data.getDouble("OutputMax");
            ClientTest.check("Модуль питания " + (dc ? "DC" : "AC") + ": " + data,
                    dc ? min > 22 && max < 25 : min < -22 && max > 22);
            measurement = Component.literal(dc
                    ? String.format(java.util.Locale.ROOT, "Двухжильный вход 300 В → %.2f В DC", max)
                    : String.format(java.util.Locale.ROOT, "Двухжильный вход 300 В → %.2f…%.2f В AC", min, max));
        });
    }

    private static void setupBoard(ServerPlayer player) {
        setupBoard(player, BoardMode.LED_FORWARD);
        camera(player, activeBoard.south(), new Vec3(2.5, 3, 3.5));
    }

    private static void setupBoard(ServerPlayer player, BoardMode mode) {
        var level = player.serverLevel();
        // Разные позиции исключают переиспользование удалённых контактов во время перестройки сети.
        BlockPos board = base.offset(mode.ordinal() * 6, 0, 8);
        BlockPos battery = board.south(3);
        activeBoard = board;
        activeBattery = battery;
        DevTest.place(level, battery.below(), Blocks.SMOOTH_STONE.defaultBlockState());
        DevTest.place(level, battery, CEEBlocks.CREATIVE_BATTERY.getDefaultState());
        setBattery(level, battery, 25);
        DevTest.place(level, board.below(), Blocks.SMOOTH_STONE.defaultBlockState());
        var topology = PcbTopology.create(1);
        int[] parts = new int[PcbTopology.CELLS];
        parts[115] = 3;
        if (mode == BoardMode.INDICATOR) {
            parts[123] = 5 + 3 * 512;
        } else {
            parts[119] = 4;
            parts[123] = 14 + 3 * 32 + (mode == BoardMode.LED_REVERSE ? 4096 : 0);
        }
        for (int cell = 112; cell < 128; cell++) {
            int kind = cell == 112 || cell == 127 ? PcbTopology.TERMINAL
                    : parts[cell] == 0 ? PcbTopology.TRACE : PcbTopology.COMPONENT;
            PcbTopology.set(topology, 0, cell, kind);
        }
        topology.putIntArray("ElectricalParts", parts);
        topology.putBoolean("Assembled", true);
        var stack = PcbItems.L1_BOARD.toStack();
        stack.set(PcbItems.PCB_TOPOLOGY.get(), topology);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atBottomCenterOf(board), Direction.UP, board.below(), false)));
        var state = level.getBlockState(board);
        for (int x : mode == BoardMode.INDICATOR ? new int[] {3} : new int[] {3, 7}) {
            var cap = new ItemStack(x == 3 ? Items.STONE_BUTTON : Items.LEVER);
            ClientTest.check("Накладка " + x, state.useItemOn(cap, level, player, InteractionHand.MAIN_HAND, controlHit(x))
                    .consumesAction());
        }
        if (mode == BoardMode.INDICATOR) {
            for (int i = 0; i < 2; i++) ClientTest.check("Существующий тонкий провод " + i,
                    DevTest.spoolWire(player, EeExtras.THIN_SPOOL.toStack(), board, i, battery, i));
        } else {
            for (int i = 0; i < 2; i++) ClientTest.check("Одножильный ввод " + i,
                    DevTest.spoolWire(player, EeExtras.THIN_SINGLE_SPOOL.toStack(), battery, i, board, i));
            BlockPos decoration = board.west(3);
            DevTest.place(level, decoration.below(), Blocks.SMOOTH_STONE.defaultBlockState());
            DevTest.place(level, decoration, CEEBlocks.CREATIVE_BATTERY.getDefaultState());
            // У декоративного источника 300 В. При ошибочной проводимости плата L1 сгорела бы.
            for (int i = 0; i < 2; i++) ClientTest.check("Декоративный одножильный провод " + i,
                    DevTest.spoolWire(player, EeExtras.DECORATIVE_SINGLE_SPOOL.toStack(), decoration, i, board, i));
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    private static void setBattery(ServerLevel level, BlockPos pos, int volts) {
        var entity = (SmartBlockEntity) level.getBlockEntity(pos);
        entity.getBehaviour(ScrollValueBehaviour.TYPE).setValue(volts * 1000);
        DevicesSavedData.load(level).getDevice(pos, CreativeBatteryDevice.class).voltage = volts;
    }

    private static void click(ServerPlayer player, int x) {
        BlockPos board = activeBoard;
        var level = player.serverLevel();
        level.getBlockState(board).useWithoutItem(level, player, controlHit(x));
    }

    private static BlockHitResult controlHit(int x) {
        BlockPos board = activeBoard;
        return new BlockHitResult(Vec3.atLowerCornerOf(board).add((2 + 0.75 * (x + 0.5)) / 16,
                0.22, (2 + 0.75 * 7.5) / 16), Direction.UP, board, false);
    }

    private static void camera(ServerPlayer player, BlockPos pos, Vec3 offset) {
        Vec3 target = Vec3.atCenterOf(pos).add(0, -0.32, 0);
        Vec3 eye = target.add(offset);
        Vec3 aim = target.subtract(eye);
        float yaw = (float) Math.toDegrees(Math.atan2(-aim.x, aim.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(aim.y, aim.horizontalDistance()));
        player.teleportTo(player.serverLevel(), eye.x, eye.y - player.getEyeHeight(), eye.z, yaw, pitch);
    }

    private static ServerPlayer player(Minecraft mc, MinecraftServer server) {
        return server.getPlayerList().getPlayer(mc.player.getUUID());
    }

    private static void photo(Minecraft mc, String name) {
        mc.gui.getChat().clearMessages(false);
        Screenshot.grab(mc.gameDirectory, "pcb-" + name + ".png", mc.getMainRenderTarget(),
                message -> System.out.println(message.getString()));
    }
}
