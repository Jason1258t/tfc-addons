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
            if (System.getenv("BF_SHOWCASE") != null) {
                showcase(mc, step);
                return;
            }
            if (step == 40) {
                base = mc.player.blockPosition().offset(3, 12, 3);
                mc.getSingleplayerServer().execute(() -> build(mc.getSingleplayerServer().overworld()));
            }
            if (step == 60) {
                mc.options.hideGui = true;
                fly(mc);
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

    /** Витрина схем из run/*.nbt (их пишет betterfluidstest tankers/tankfarm): кадры bf-show-*.png */
    private static final Object[][] SHOW = {
        {"tanker_9x22.nbt", 0, 0, 0, new double[]{-7, 8, -9}, new double[]{11, 5, 4.5}},
        {"tanker_9x22.nbt", 0, 0, 0, new double[]{-5, 6, 2}, new double[]{0, 5, 4.5}},
        {"storage_tank_15x9.nbt", 34, 0, 0, new double[]{20, 15, -12}, new double[]{42.5, 5, 8.5}},
        {"storage_tank_21x12.nbt", 0, 0, 28, new double[]{-16, 19, 14}, new double[]{11.5, 6, 39.5}},
        {"storage_tank_11x7.nbt", 34, 0, 26, new double[]{30, 8, 22}, new double[]{40.5, 4, 32.5}},
        {"storage_tank_15x9.nbt", 34, 0, 0, new double[]{44, 9, 30}, new double[]{42.5, 5, 8.5}},
        {"storage_tank_15x9.nbt", 34, 0, 0, new double[]{43, 22, 12}, new double[]{42.5, 10, 8.5}},
    };

    private static void showcase(Minecraft mc, int step) throws Exception {
        if (step == 40) {
            base = mc.player.blockPosition().offset(3, 12, 3);
            var server = mc.getSingleplayerServer();
            server.execute(() -> {
                var level = server.overworld();
                var placed = new java.util.HashSet<String>();
                for (Object[] show : SHOW) {
                    // Один файл — одна установка: повторная поверх срывает копикаты
                    if (!placed.add((String) show[0])) {
                        continue;
                    }
                    try {
                        var tag = net.minecraft.nbt.NbtIo.readCompressed(mc.gameDirectory.toPath().resolve((String) show[0]),
                            net.minecraft.nbt.NbtAccounter.unlimitedHeap());
                        var at = base.offset((int) show[1], (int) show[2], (int) show[3]);
                        level.getStructureManager().readStructure(tag).placeInWorld(level, at, at,
                            new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(), level.random, 3);
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }
            });
            mc.options.hideGui = true;
            fly(mc);
        }
        if (step == 70) {
            var server = mc.getSingleplayerServer();
            server.execute(() -> {
                var items = server.overworld().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(base).inflate(80));
                var where = new java.util.TreeMap<String, Integer>();
                for (var item : items) {
                    var p = item.blockPosition().subtract(base);
                    where.merge((p.getX() / 4 * 4) + "," + (p.getZ() / 4 * 4) + " y" + p.getY(), 1, Integer::sum);
                }
                System.out.println("BFCLIENT выпало " + items.size() + " " + where);
            });
        }
        for (int i = 0; i < SHOW.length; i++) {
            int at = 80 + i * 100;
            if (step == at) {
                double[] eye = (double[]) SHOW[i][4];
                double[] target = (double[]) SHOW[i][5];
                double dx = target[0] - eye[0];
                double dy = target[1] - eye[1] - 1.62;
                double dz = target[2] - eye[2];
                float yaw = (float) Math.toDegrees(-Math.atan2(dx, dz));
                float pitch = (float) Math.toDegrees(-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
                look(mc, eye[0], eye[1], eye[2], yaw, pitch);
            }
            if (step == at + 90) {
                shot(mc, "bf-show-" + i + ".png");
            }
        }
        if (step == 80 + SHOW.length * 100) {
            System.out.println("BFCLIENT готово");
            mc.stop();
        }
    }

    private static Block block(String name) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("better_fluids", name));
    }

    /** Полёт на стороне сервера: иначе игрок падает и кадр уходит в землю */
    private static void fly(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        var player = server.getPlayerList().getPlayer(mc.player.getUUID());
        server.execute(() -> {
            player.getAbilities().mayfly = true;
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
        });
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
