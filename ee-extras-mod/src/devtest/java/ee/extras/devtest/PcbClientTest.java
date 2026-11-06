package ee.extras.devtest;

import ee.extras.pcb.PcbCircuitBlock;
import ee.extras.pcb.PcbDesignItem;
import ee.extras.pcb.PcbEditorBlockEntity;
import ee.extras.pcb.PcbEditorMenu;
import ee.extras.pcb.PcbEditorScreen;
import ee.extras.pcb.PcbItems;
import ee.extras.pcb.PcbTopology;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Настоящие клики по экрану редактора: ошибка, чертёж, сохранение, повторное открытие и L2/via. */
final class PcbClientTest {
    private static final BlockPos TABLE = new BlockPos(11, -60, 81);
    private static final BlockPos BOARD = TABLE.east();
    private static int fails;

    private PcbClientTest() {}

    private static void check(String name, boolean ok) {
        if (!ok) fails++;
        ClientTest.check("PCB " + name, ok);
    }

    private static PcbEditorScreen screen(Minecraft mc) {
        return mc.screen instanceof PcbEditorScreen screen ? screen : null;
    }

    private static void click(PcbEditorScreen screen, double x, double y) {
        screen.mouseClicked(screen.getGuiLeft() + x, screen.getGuiTop() + y, 0);
    }

    private static void cell(PcbEditorScreen screen, int x, int y) {
        click(screen, 12 + x * 7 + 2, 26 + y * 7 + 2);
    }

    private static void open(Minecraft mc) {
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(TABLE), Direction.UP, TABLE, false));
    }

    static void tick(Minecraft mc, MinecraftServer server, int tick) {
        if (tick < 360) return;
        if (tick == 360) server.execute(() -> {
            var level = server.overworld();
            DevTest.place(level, TABLE, PcbItems.EDITOR_BLOCK.get().defaultBlockState());
            if (level.getBlockEntity(TABLE) instanceof PcbEditorBlockEntity editor) {
                editor.setItem(0, new ItemStack(Items.PAPER));
            }
        });
        if (tick == 370) open(mc);
        if (tick == 390) check("стол открыт настоящим клиентом", screen(mc) != null);
        if (tick == 400 && screen(mc) != null) click(screen(mc), 145, 104);
        if (tick == 410 && screen(mc) != null) {
            PcbEditorMenu menu = screen(mc).getMenu();
            check("русская ошибка дошла до клиента", menu.status() == 7
                    && menu.statusText().equals("Нужно компонентов: не менее 2"));
            Screenshot.grab(mc.gameDirectory, "pcb_error.png", mc.getMainRenderTarget(), c -> {});
        }
        if (tick == 420 && screen(mc) != null) {
            for (int x = 0; x <= 5; x++) cell(screen(mc), x, 0);
        }
        if (tick == 425 && screen(mc) != null) click(screen(mc), 188, 33);
        if (tick == 430 && screen(mc) != null) {
            cell(screen(mc), 1, 0);
            cell(screen(mc), 2, 0);
        }
        if (tick == 435 && screen(mc) != null) {
            click(screen(mc), 145, 47);
        }
        if (tick == 440 && screen(mc) != null) {
            cell(screen(mc), 0, 0);
            cell(screen(mc), 5, 0);
        }
        if (tick == 445 && screen(mc) != null) click(screen(mc), 145, 117);
        if (tick == 460 && screen(mc) != null) {
            PcbEditorMenu menu = screen(mc).getMenu();
            ItemStack result = menu.editor().getItem(0);
            check("валидный чертёж сохранён", menu.status() == 1 && result.getItem() instanceof PcbDesignItem
                    && result.has(PcbItems.PCB_TOPOLOGY.get()));
            check("топология после кликов сошлась", menu.editor().topology().getInt("Level") == 1
                    && PcbTopology.get(menu.editor().topology(), 0, 1) == PcbTopology.COMPONENT
                    && PcbTopology.get(menu.editor().topology(), 0, 5) == PcbTopology.TERMINAL);
            Screenshot.grab(mc.gameDirectory, "pcb_saved.png", mc.getMainRenderTarget(), c -> {});
        }
        if (tick == 470) mc.player.closeContainer();
        if (tick == 480) open(mc);
        if (tick == 500 && screen(mc) != null) {
            PcbEditorMenu menu = screen(mc).getMenu();
            check("после повторного открытия чертёж и ячейки сохранились", menu.editor().getItem(0).getItem() instanceof PcbDesignItem
                    && PcbTopology.get(menu.editor().topology(), 0, 2) == PcbTopology.COMPONENT);
            click(screen(mc), 145, 90);
            click(screen(mc), 145, 77);
        }
        if (tick == 520 && screen(mc) != null) click(screen(mc), 230, 33);
        if (tick == 525 && screen(mc) != null) cell(screen(mc), 3, 0);
        if (tick == 545 && screen(mc) != null) {
            PcbEditorMenu menu = screen(mc).getMenu();
            check("уровень L2 и второй слой выбраны", menu.level() == 2 && menu.layer() == 1);
            check("via поставлен кликом на втором слое", PcbTopology.get(menu.editor().topology(), 1, 3) == PcbTopology.VIA);
            Screenshot.grab(mc.gameDirectory, "pcb_l2_via.png", mc.getMainRenderTarget(), c -> {});
        }
        if (tick == 560) mc.player.closeContainer();
        if (tick == 565) server.execute(() -> {
            var editor = (PcbEditorBlockEntity) server.overworld().getBlockEntity(TABLE);
            editor.clear(1);
            editor.setItem(0, PcbItems.L1_BOARD.toStack());
        });
        if (tick == 575) open(mc);
        if (tick == 590 && screen(mc) != null) {
            for (int x = 1; x < 15; x++) if (x != 7) cell(screen(mc), x, 0);
            click(screen(mc), 145, 47);
            cell(screen(mc), 0, 0);
            cell(screen(mc), 15, 0);
            click(screen(mc), 230, 47);
            for (int i = 0; i < 5; i++) click(screen(mc), 200, 195);
            cell(screen(mc), 7, 0);
            click(screen(mc), 145, 62);
            for (int z = 4; z < 16; z++) {
                for (int x = 8; x < 16; x++) cell(screen(mc), x, z);
            }
            Screenshot.grab(mc.gameDirectory, "pcb_electrical.png", mc.getMainRenderTarget(), c -> {});
        }
        if (tick == 605 && screen(mc) != null) click(screen(mc), 145, 117);
        if (tick == 620) {
            var current = screen(mc);
            check("электрическая схема собрана кликами", current != null && current.getMenu().status() == 1
                    && current.getMenu().getSlot(0).getItem().is(PcbItems.L1_BOARD.get())
                    && current.getMenu().getSlot(0).getItem().get(PcbItems.PCB_TOPOLOGY.get()) != null
                    && current.getMenu().getSlot(0).getItem().get(PcbItems.PCB_TOPOLOGY.get()).getBoolean("Assembled"));
            mc.player.closeContainer();
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(mc.player.getUUID());
                var level = player.serverLevel();
                var editor = (PcbEditorBlockEntity) level.getBlockEntity(TABLE);
                DevTest.place(level, BOARD.below(), Blocks.STONE.defaultBlockState());
                player.setItemInHand(InteractionHand.MAIN_HAND, editor.getItem(0).copy());
                var hit = new BlockHitResult(Vec3.atBottomCenterOf(BOARD), Direction.UP, BOARD.below(), false);
                player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
                level.setDayTime(6000);
                player.teleportTo(level, 12.7, -59, 84, 180f, 32f);
            });
        }
        if (tick == 670) {
            var state = mc.level.getBlockState(BOARD);
            check("контакты и вырез дошли до клиента", state.getBlock() instanceof PcbCircuitBlock board
                    && state.getValue(PcbCircuitBlock.CUSTOM)
                    && board.getNodePositions(mc.level, BOARD, state).size() == 2
                    && state.getShape(mc.level, BOARD).toAabbs().stream()
                            .noneMatch(box -> box.contains(12.5 / 16, 0.5 / 16, 12.5 / 16)));
            Screenshot.grab(mc.gameDirectory, "pcb_installed.png", mc.getMainRenderTarget(), c -> {});
            System.out.println("EECLIENT PCB ИТОГ: ошибок " + fails);
            mc.stop();
        }
    }
}
