package tfcstudio.betterfluids.test;

import com.simibubi.create.AllBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** Клиентская проверка: кадры баков сверху и снизу (screenshots/bf-*.png). */
@EventBusSubscriber(modid = "better_fluids_devtest", value = Dist.CLIENT)
public final class ClientTest {
    private static BlockPos base;
    private static boolean started;
    private static int tick;

    @SubscribeEvent
    static void screen(ScreenEvent.Init.Post event) {
        // В полной сборке стенд сам входит в мир; плоский мир — только для запуска из исходников.
        if (started || !(event.getScreen() instanceof TitleScreen) || ModList.get().isLoaded("tfc")) {
            return;
        }
        started = true;
        Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        var settings = new LevelSettings("bftest", GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
            new GameRules(), WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel("bftest", settings, new WorldOptions(1, false, false),
            reg -> reg.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value()
                .createWorldDimensions(), event.getScreen());
    }

    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getSingleplayerServer() == null || tick == 0 && mc.screen != null) {
            return;
        }
        tick++;
        // На стенде полной сборки (программный GL) мир догружается долго, окно закрывает сам стенд.
        boolean pack = ModList.get().isLoaded("tfc");
        int step = tick - (pack ? 400 : 0);
        try {
            if (step == 40) {
                base = mc.player.blockPosition().offset(3, 12, 3);
                mc.getSingleplayerServer().execute(() -> build(mc.getSingleplayerServer().overworld()));
            }
            if (step == 60) {
                mc.options.hideGui = true;
                mc.player.getAbilities().flying = true;
                look(mc, -3.5, 6, -3.5, -45, 40);
            }
            if (step == 160) {
                shot(mc, "bf-above.png");
                look(mc, -3.5, -6.5, -3.5, -45, -40);
            }
            if (step == 260) {
                shot(mc, "bf-below.png");
                look(mc, 1, 1, 10.5, 180, 10);
            }
            if (step == 360) {
                shot(mc, "bf-wall.png");
                look(mc, 12.5, 2, 4, 145, 30);
            }
            if (step == 460) {
                shot(mc, "bf-corner.png");
                System.out.println("BFCLIENT готово");
                if (!pack) {
                    mc.stop();
                }
            }
        } catch (Throwable t) {
            System.out.println("BFCLIENT FAIL " + t);
            t.printStackTrace();
            mc.stop();
        }
    }

    private static void build(ServerLevel level) {
        Block tank = block("iron_tank");
        // Бак 2×2×2, одиночная секция, фасонные секции и медный бак Create рядом для сравнения.
        for (BlockPos pos : BlockPos.betweenClosed(base, base.offset(1, 1, 1))) {
            level.setBlockAndUpdate(pos, tank.defaultBlockState());
        }
        level.setBlockAndUpdate(base.offset(3, 0, 0), tank.defaultBlockState());
        level.setBlockAndUpdate(base.offset(0, 0, 3), AllBlocks.FLUID_TANK.getDefaultState());
        level.setBlockAndUpdate(base.offset(3, 0, 3), block("iron_tank_plate").defaultBlockState());
        level.setBlockAndUpdate(base.offset(5, 0, 0), block("iron_tank_quarter").defaultBlockState());
        level.setBlockAndUpdate(base.offset(5, 0, 3), block("iron_tank_bevel").defaultBlockState());
        // Полосы шириной в один блок (#322): стенка 2×1×2 и ряды по трём секциям вдоль X и Z.
        for (BlockPos pos : BlockPos.betweenClosed(base.offset(0, 0, 6), base.offset(1, 1, 6))) {
            level.setBlockAndUpdate(pos, tank.defaultBlockState());
        }
        for (int i = 0; i < 3; i++) {
            level.setBlockAndUpdate(base.offset(3 + i, 0, 6), tank.defaultBlockState());
            level.setBlockAndUpdate(base.offset(7, 0, 3 + i), tank.defaultBlockState());
        }
        // Г-образный слой: внутренний угол стенки.
        for (BlockPos pos : new BlockPos[] {base.offset(9, 0, 0), base.offset(10, 0, 0), base.offset(9, 0, 1)}) {
            level.setBlockAndUpdate(pos, tank.defaultBlockState());
        }
    }

    private static Block block(String name) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("better_fluids", name));
    }

    private static void look(Minecraft mc, double x, double y, double z, float yaw, float pitch) {
        var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
        mc.getSingleplayerServer().execute(() -> player.teleportTo(player.serverLevel(),
            base.getX() + x, base.getY() + y, base.getZ() + z, yaw, pitch));
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), message -> {});
        System.out.println("BFCLIENT кадр " + name);
    }
}
