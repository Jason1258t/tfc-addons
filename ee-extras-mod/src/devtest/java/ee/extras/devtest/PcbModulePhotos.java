package ee.extras.devtest;

import ee.extras.pcb.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.BlockHitResult;

/** Снимки реального редактора и монтажного макета в полном клиенте test1a. */
final class PcbModulePhotos {
    private static BlockPos table, board, stripBoard, lampBoard, lampBattery;
    private PcbModulePhotos() {}

    static void tick(Minecraft mc, MinecraftServer server, int tick) {
        if (tick % 10 == 0) mc.gui.getChat().clearMessages(false);
        if (tick % 20 == 0) server.execute(() -> { var player=server.getPlayerList().getPlayer(mc.player.getUUID()); if(player!=null) player.removeAllEffects(); });
        if (tick == 140 || tick >= 510 && tick < 1160) mc.options.hideGui = true;
        if (tick == 60) server.execute(() -> {
            var player = server.getPlayerList().getPlayer(mc.player.getUUID());
            var level = player.serverLevel();
            table = player.blockPosition().atY(300);
            board = table.east(2);
            calendarHour(server, 12);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            DevTest.place(level, table, PcbItems.EDITOR_BLOCK.get().defaultBlockState());
            var editor = (PcbEditorBlockEntity) level.getBlockEntity(table);
            editor.clear(3);
            int[][] modules = {{0,2,2},{7,8,2},{13,13,2},{2,2,6},{3,7,6},{15,12,6},{14,2,10},{12,7,12},{10,11,12}};
            for (int[] entry : modules) {
                if (!editor.setPart(0, entry[2] * 16 + entry[1], PcbModulesTest.definition(entry[0], 0)))
                    throw new IllegalStateException("Не установлен модуль макета " + entry[0]);
            }
            var stack = PcbItems.L3_BOARD.toStack();
            PcbTopology.attach(stack, editor.topology());
            DevTest.place(level, board, PcbItems.CIRCUIT_BLOCK.get().defaultBlockState()
                    .setValue(PcbCircuitBlock.CUSTOM, true).setValue(PcbCircuitBlock.FACING, Direction.NORTH));
            PcbModulesTest.call(PcbCircuitBlockEntity.class, level.getBlockEntity(board), "setBoard", stack);
            for (int[] entry : new int[][]{{2,6},{7,6}}) {
                Vec3 local = new Vec3((entry[0] + 1.0) / 16, 1.2 / 16, (entry[1] + 1.0) / 16);
                Vec3 mounted = PcbModulesTest.call(PcbCircuitBlock.class, null, "mountedPoint", local, Direction.NORTH);
                var hit = new BlockHitResult(mounted.add(Vec3.atLowerCornerOf(board)), Direction.NORTH, board, false);
                var cap = entry[0] == 2 ? PcbItems.BUTTON_CAP.toStack() : PcbItems.SWITCH_CAP.toStack();
                ClientTest.check("накладка макета " + entry[0], level.getBlockState(board)
                        .useItemOn(cap, level, player, InteractionHand.MAIN_HAND, hit).consumesAction());
            }
            Vec3 target = Vec3.atLowerCornerOf(board).add(.5, .5, .91);
            Vec3 eye = target.add(.1, .08, -1.25);
            Vec3 delta = target.subtract(eye);
            float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
            float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z)));
            player.teleportTo(level, eye.x, eye.y - player.getEyeHeight(), eye.z, yaw, pitch);
        });
        if (tick == 160) {
            mc.options.hideGui = true;
            Screenshot.grab(mc.gameDirectory, "pcb-modules-01-world.png", mc.getMainRenderTarget(), c -> {});
        }
        if (tick == 180) {
            mc.options.hideGui = false;
            server.execute(() -> ((PcbEditorBlockEntity) server.overworld().getBlockEntity(table))
                    .open(server.getPlayerList().getPlayer(mc.player.getUUID())));
        }
        if (tick == 200 && mc.screen instanceof PcbEditorScreen screen)
            screen.mouseClicked(screen.cellScreenX(2), screen.cellScreenY(2), 1);
        if (tick == 210 && mc.screen instanceof PcbEditorScreen screen) {
            try {
                var field = PcbEditorScreen.class.getDeclaredField("nominalInput"); field.setAccessible(true);
                var input = (net.minecraft.client.gui.components.EditBox) field.get(screen);
                screen.mouseClicked(input.getX() + 3, input.getY() + 4, 0);
                input.setValue("733,25");
                screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0, 0);
            } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        }
        if (tick == 225) server.execute(() -> {
            var editor = (PcbEditorBlockEntity) server.overworld().getBlockEntity(table);
            ClientTest.check("числовой ввод дошёл до сервера", editor.topology().getCompound("ModuleParameters").getCompound("34").getDouble("Nominal") == 733.25);
        });
        if (tick == 230) {
            ClientTest.check("новый редактор открыт", mc.screen instanceof PcbEditorScreen);
            Screenshot.grab(mc.gameDirectory, "pcb-modules-02-editor.png", mc.getMainRenderTarget(), c -> {});
        }
        if (tick == 250 && mc.screen instanceof PcbEditorScreen screen) {
            screen.mouseClicked(screen.cellScreenX(8), screen.cellScreenY(7), 1);
        }
        if (tick == 275 && mc.screen instanceof PcbEditorScreen screen) {
            screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_R, 0, 0);
        }
        if (tick == 310) Screenshot.grab(mc.gameDirectory, "pcb-modules-03-settings.png", mc.getMainRenderTarget(), c -> {});
        if (tick == 315 && mc.screen instanceof PcbEditorScreen screen)
            screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_K, 0, 0);
        if (tick == 325) Screenshot.grab(mc.gameDirectory, "pcb-modules-03a-catalog.png", mc.getMainRenderTarget(), c -> {});
        if (tick == 330 && mc.screen instanceof PcbEditorScreen screen) {
            screen.charTyped('2', 0); screen.charTyped('x', 0); screen.charTyped('2', 0);
        }
        if (tick == 335) Screenshot.grab(mc.gameDirectory, "pcb-modules-03b-search.png", mc.getMainRenderTarget(), c -> {});
        if (tick == 338 && mc.screen instanceof PcbEditorScreen screen)
            screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE, 0, 0);
        if (tick == 340 && mc.screen instanceof PcbEditorScreen screen) {
            screen.mouseScrolled(screen.cellScreenX(8), screen.cellScreenY(8), 0, 1);
        }
        if (tick == 345) Screenshot.grab(mc.gameDirectory, "pcb-modules-03c-zoom.png", mc.getMainRenderTarget(), c -> {});
        if (tick == 347 && mc.screen instanceof PcbEditorScreen screen)
            screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_M, 0, 0);
        if (tick == 350) Screenshot.grab(mc.gameDirectory, "pcb-modules-03d-bill.png", mc.getMainRenderTarget(), c -> {});
        if (tick == 370 && mc.screen instanceof PcbEditorScreen screen)
            screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE, 0, 0);
        if (tick == 400 && mc.screen instanceof PcbEditorScreen screen)
            screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_F7, 0, 0);
        if (tick == 430) Screenshot.grab(mc.gameDirectory, "pcb-modules-03e-errors.png", mc.getMainRenderTarget(), c -> {});
        if (tick == 450 && mc.screen instanceof PcbEditorScreen screen) {
            screen.mouseClicked(20, 60, 0);
            try {
                var field = PcbEditorScreen.class.getDeclaredField("diagnosticsOpen"); field.setAccessible(true);
                ClientTest.check("переход закрывает список ошибок", !field.getBoolean(screen));
                ClientTest.check("ошибка в центре холста", screen.cellScreenX(1) == screen.canvasCenterX() && screen.cellScreenY(2) == screen.canvasCenterY());
            } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        }
        if (tick == 480) Screenshot.grab(mc.gameDirectory, "pcb-modules-03f-jump.png", mc.getMainRenderTarget(), c -> {});
        if (tick == 500) mc.player.closeContainer();
        if (tick == 520) server.execute(() -> {
            var player = server.getPlayerList().getPlayer(mc.player.getUUID());
            stripBoard = table.east(5);
            PcbLightsTest.buildAt(player.createCommandSourceStack(), stripBoard);
            PcbLightsTest.power(player.serverLevel(), 21);
            camera(player, stripBoard);
        });
        if (tick == 620) photo(mc, "04-strip-day");
        if (tick == 640) server.execute(() -> {
            var player = server.getPlayerList().getPlayer(mc.player.getUUID());
            lampBoard = table.east(8);
            lampFixture(player);
            camera(player, lampBoard);
        });
        if (tick == 740) photo(mc, "05-lights-day");
        if (tick == 760) server.execute(() -> { calendarHour(server, 0); darkRoom(server.overworld()); });
        if (tick == 840) photo(mc, "06-lights-night");
        if (tick == 860) server.execute(() -> lampPower(server.overworld(), 0));
        if (tick == 940) photo(mc, "07-lights-off");
        if (tick == 960) server.execute(() -> camera(server.getPlayerList().getPlayer(mc.player.getUUID()), stripBoard));
        if (tick == 1040) photo(mc, "08-strip-night");
        if (tick == 1060) server.execute(() -> PcbLightsTest.power(server.overworld(), 0));
        if (tick == 1140) photo(mc, "09-strip-off");
        if (tick == 1160) {
            mc.options.hideGui = false;
            System.out.println("EECLIENT PCB MODULE PHOTOS DONE");
        }

    }
    private static void calendarHour(MinecraftServer server,int hour) {
        var calendar=net.dries007.tfc.util.calendar.Calendars.SERVER;
        calendar.setCalendarTickRate(0);
        long ticks=calendar.getCalendarTicks();
        calendar.skipForwardBy(Math.floorMod(hour*1000L-Math.floorMod(ticks,24000),24000));
        System.out.println("EECLIENT освещение: час TFC="+calendar.getHourOfDay()+", время мира="+server.overworld().getDayTime());
    }

    private static void darkRoom(net.minecraft.server.level.ServerLevel level) {
        for(int x=1;x<=13;x++) for(int z=-4;z<=11;z++) {
            DevTest.place(level,table.offset(x,-1,z),Blocks.STONE.defaultBlockState());
            DevTest.place(level,table.offset(x,5,z),Blocks.STONE.defaultBlockState());
            if(x==1 || x==13 || z==-4 || z==11)
                for(int y=0;y<5;y++) DevTest.place(level,table.offset(x,y,z),Blocks.STONE.defaultBlockState());
        }
        DevTest.place(level,table.offset(3,0,2),Blocks.SOUL_TORCH.defaultBlockState());
    }

    private static void photo(Minecraft mc, String name) {
        mc.options.hideGui = true;
        Screenshot.grab(mc.gameDirectory, "pcb-modules-" + name + ".png", mc.getMainRenderTarget(), c -> {});
    }

    private static void camera(net.minecraft.server.level.ServerPlayer player, BlockPos pos) {
        Vec3 target = Vec3.atLowerCornerOf(pos).add(.5,.08,.5);
        Vec3 eye = target.add(.15,1.2,1.0), delta = target.subtract(eye);
        float yaw = (float)Math.toDegrees(Math.atan2(-delta.x,delta.z));
        float pitch = (float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z)));
        player.teleportTo(player.serverLevel(),eye.x,eye.y-player.getEyeHeight(),eye.z,yaw,pitch);
    }

    private static void lampFixture(net.minecraft.server.level.ServerPlayer player) {
        var level=player.serverLevel();
        var editor=(PcbEditorBlockEntity)level.getBlockEntity(table); editor.clear(2);
        for(int[] module : new int[][]{{13,3},{4,7},{18,11}})
            if(!editor.setPart(0,7*16+module[1],PcbModulesTest.definition(module[0],0))) throw new IllegalStateException("Макет света");
        for(int x=0;x<16;x++) {
            if(x==3 || x==7 || x==8 || x==11 || x==12) continue;
            editor.setCell(0,7*16+x,x==0 || x==15?PcbTopology.TERMINAL:PcbTopology.TRACE);
        }
        var tag=editor.topology().copy(); tag.putBoolean("Assembled",true);
        var stack=PcbItems.L2_BOARD.toStack(); stack.set(PcbItems.PCB_TOPOLOGY.get(),tag);
        DevTest.place(level,lampBoard,Blocks.AIR.defaultBlockState()); DevTest.place(level,lampBoard.below(),Blocks.STONE.defaultBlockState());
        player.setItemInHand(InteractionHand.MAIN_HAND,stack);
        var hit=new BlockHitResult(Vec3.atCenterOf(lampBoard.below()).add(0,.5,0),Direction.UP,lampBoard.below(),false);
        ClientTest.check("три световых модуля установлены",stack.useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,hit)).consumesAction());
        player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        lampBattery=lampBoard.offset(2,-2,5);
        DevTest.place(level,lampBattery,Blocks.AIR.defaultBlockState()); DevTest.place(level,lampBattery,com.george_vi.electroenergetics.CEEBlocks.CREATIVE_BATTERY.getDefaultState());
        ClientTest.check("тонкие провода светового стенда",DevTest.spoolWire(player,ee.extras.EeExtras.THIN_SINGLE_SPOOL.toStack(),lampBattery,1,lampBoard,0)
                && DevTest.spoolWire(player,ee.extras.EeExtras.THIN_SINGLE_SPOOL.toStack(),lampBattery,0,lampBoard,1));
        player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        lampPower(level,24);
    }

    private static void lampPower(net.minecraft.server.level.ServerLevel level,int volts) {
        var be=(com.simibubi.create.foundation.blockEntity.SmartBlockEntity)level.getBlockEntity(lampBattery);
        be.getBehaviour(com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour.TYPE).setValue(volts*1000);
        var battery=com.george_vi.electroenergetics.devices.device.DevicesSavedData.load(level)
                .getDevice(lampBattery,com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice.class);
        battery.voltage=volts; battery.acFrequency=0;
    }

}
