package ee.extras.devtest;

import ee.extras.pcb.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.BlockHitResult;

/** Снимки реального редактора и монтажного макета в полном клиенте test1a. */
final class PcbModulePhotos {
    private static BlockPos table, board;
    private PcbModulePhotos() {}

    static void tick(Minecraft mc, MinecraftServer server, int tick) {
        if (tick == 60) server.execute(() -> {
            var player = server.getPlayerList().getPlayer(mc.player.getUUID());
            var level = player.serverLevel();
            table = player.blockPosition().atY(300);
            board = table.east(2);
            level.setDayTime(6000);
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
        if (tick == 230) {
            ClientTest.check("новый редактор открыт", mc.screen instanceof PcbEditorScreen);
            Screenshot.grab(mc.gameDirectory, "pcb-modules-02-editor.png", mc.getMainRenderTarget(), c -> {});
        }
        if (tick == 250 && mc.screen instanceof PcbEditorScreen screen) {
            screen.mouseClicked(screen.getGuiLeft() + 12 + 8 * 9 + 2, screen.getGuiTop() + 26 + 7 * 9 + 2, 1);
        }
        if (tick == 275 && mc.screen instanceof PcbEditorScreen screen) {
            screen.mouseClicked(screen.getGuiLeft() + 224, screen.getGuiTop() + 186, 0);
        }
        if (tick == 310) Screenshot.grab(mc.gameDirectory, "pcb-modules-03-settings.png", mc.getMainRenderTarget(), c -> {});
        if (tick == 340) {
            System.out.println("EECLIENT PCB MODULE PHOTOS DONE");
            mc.player.closeContainer();
        }
    }
}
