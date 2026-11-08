package stress.test;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Стресс-сценарии для стенда. Замер: длительность каждого тика сервера (от ServerTickEvent.Pre до Post), раз в 30 с
 * и в конце отрезка — строка «[STRESS]» в журнале (медиана, p95, p99, максимум, тиков дольше 50/100 мс, реальный TPS,
 * загруженные чанки, сущности, куча, сборки мусора, FPS клиента).
 * План — файл stress-plan.txt в папке игры (оверлей стенда), стартует при входе первого игрока
 * (или сразу после запуска сервера, если первая строка «start server»). Строки:
 *   wait <с>                — пауза по настенным часам
 *   mark <имя>              — закрыть отрезок замера и начать новый
 *   fly <чанков> <блоков/тик> [y] — лететь игроком на восток, пока не загрузится столько новых чанков
 *   cmd <команда>           — команда от имени игрока (уровень 4), без игрока — от сервера
 *   end                     — закрыть отрезок, «[STRESS] DONE»
 * Команды: /stress plant [копий], /stress report, /stress pigs <n>, /stress fly <чанков> <скорость>, /stress mark <имя>.
 */
@Mod("stress_test")
public class StressTest {
    static final Logger LOG = LoggerFactory.getLogger("STRESS");

    public StressTest() {
        NeoForge.EVENT_BUS.addListener(StressTest::commands);
        NeoForge.EVENT_BUS.addListener(StressTest::started);
        NeoForge.EVENT_BUS.addListener(StressTest::login);
        NeoForge.EVENT_BUS.addListener(StressTest::chunk);
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Pre e) -> Meter.pre());
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> {
            Meter.post(e.getServer());
            Fly.tick(e.getServer());
            if (ModList.get().isLoaded("electroenergetics")) Plant.tick(e.getServer());
            Plan.tick(e.getServer());
        });
    }

    // ---------- Замер ----------

    static final class Meter {
        static final int CAP = 20 * 60 * 60;
        static String segment = "загрузка";
        static float[] seg = new float[CAP], win = new float[CAP];
        static int segN, winN;
        static long tickStart, segStartMs = System.currentTimeMillis(), winStartMs = segStartMs;
        static long gcCount, gcMs, segGcCount, segGcMs;

        static void pre() {
            tickStart = System.nanoTime();
        }

        static void post(MinecraftServer server) {
            float ms = (System.nanoTime() - tickStart) / 1e6f;
            if (segN < CAP) seg[segN++] = ms;
            if (winN < CAP) win[winN++] = ms;
            long now = System.currentTimeMillis();
            if (now - winStartMs >= 30_000) {
                long[] gc = gcDelta(true);
                LOG.info("[STRESS] {} +{}с: {}; {}", segment, (now - segStartMs) / 1000, stats(win, winN, now - winStartMs),
                        world(server, gc));
                winN = 0;
                winStartMs = now;
            }
        }

        /** Сборки мусора с прошлого вызова: {число, мс}; window — окно 30 с, иначе отрезок */
        static long[] gcDelta(boolean window) {
            long c = 0, t = 0;
            for (GarbageCollectorMXBean b : ManagementFactory.getGarbageCollectorMXBeans()) {
                c += Math.max(0, b.getCollectionCount());
                t += Math.max(0, b.getCollectionTime());
            }
            long[] r;
            if (window) {
                r = new long[] {c - gcCount, t - gcMs};
                gcCount = c;
                gcMs = t;
            } else {
                r = new long[] {c - segGcCount, t - segGcMs};
                segGcCount = c;
                segGcMs = t;
            }
            return r;
        }

        static String stats(float[] a, int n, long wallMs) {
            if (n == 0) return "тиков нет";
            float[] s = Arrays.copyOf(a, n);
            Arrays.sort(s);
            double sum = 0;
            int over50 = 0, over100 = 0;
            for (int i = 0; i < n; i++) {
                sum += s[i];
                if (s[i] > 50) over50++;
                if (s[i] > 100) over100++;
            }
            return String.format("тиков %d, TPS %.1f, MSPT ср %.1f мед %.1f p95 %.1f p99 %.1f макс %.0f, >50мс %d, >100мс %d",
                    n, n * 1000.0 / Math.max(1, wallMs), sum / n, q(s, 0.5), q(s, 0.95), q(s, 0.99), s[n - 1], over50, over100);
        }

        static float q(float[] sorted, double p) {
            return sorted[Math.min(sorted.length - 1, (int) Math.floor(p * sorted.length))];
        }

        static String world(MinecraftServer server, long[] gc) {
            int chunks = 0, entities = 0;
            for (ServerLevel l : server.getAllLevels()) {
                chunks += l.getChunkSource().getLoadedChunksCount();
                for (Entity ignored : l.getAllEntities()) entities++;
            }
            Runtime rt = Runtime.getRuntime();
            long used = (rt.totalMemory() - rt.freeMemory()) >> 20;
            String fps = FMLEnvironment.dist.isClient() ? ", FPS " + StressClient.fps() : "";
            return String.format("чанков %d (верхний мир %d), сущностей %d, игроков %d, куча %d/%d МБ, GC %d за %d мс%s",
                    chunks, server.overworld().getChunkSource().getLoadedChunksCount(), entities, server.getPlayerCount(), used,
                    rt.maxMemory() >> 20, gc[0], gc[1], fps);
        }

        static void mark(MinecraftServer server, String next) {
            long now = System.currentTimeMillis();
            long[] gc = gcDelta(false);
            LOG.info("[STRESS] ИТОГ «{}» за {} с: {}; {}", segment, (now - segStartMs) / 1000, stats(seg, segN, now - segStartMs),
                    world(server, gc));
            segment = next;
            segN = winN = 0;
            segStartMs = winStartMs = now;
            gcDelta(true);
            LOG.info("[STRESS] отрезок «{}»", next);
        }
    }

    // ---------- Пролёт ----------

    static final class Fly {
        static boolean active;
        static double speed, x, y, z;
        static int target, fresh, loads;

        static void start(ServerPlayer p, int chunks, double blocksPerTick, double height) {
            active = true;
            speed = blocksPerTick;
            target = chunks;
            fresh = loads = 0;
            x = p.getX();
            y = height;
            z = p.getZ();
            p.getAbilities().flying = true;
            p.onUpdateAbilities();
            LOG.info("[STRESS] пролёт: {} новых чанков, {} блоков/тик, высота {}", chunks, blocksPerTick, height);
        }

        static void tick(MinecraftServer server) {
            if (!active) return;
            ServerPlayer p = player(server);
            if (p == null) return;
            x += speed;
            p.connection.teleport(x, y, z, -90f, 10f);
            if (fresh >= target) {
                active = false;
                LOG.info("[STRESS] пролёт окончен у x={}: новых чанков {}, загрузок всего {}", (int) x, fresh, loads);
            }
        }
    }

    static void chunk(ChunkEvent.Load e) {
        if (!Fly.active || !(e.getLevel() instanceof ServerLevel)) return;
        Fly.loads++;
        if (e.isNewChunk()) Fly.fresh++;
    }

    // ---------- План ----------

    static final class Plan {
        static final Deque<String> lines = new ArrayDeque<>();
        static boolean running, serverStart;
        static long resumeAt;
        static BooleanSupplier until;

        static void load() {
            Path f = FMLPaths.GAMEDIR.get().resolve("stress-plan.txt");
            if (!Files.exists(f)) return;
            try {
                for (String l : Files.readAllLines(f)) {
                    l = l.strip();
                    if (l.isEmpty() || l.startsWith("#")) continue;
                    if (l.equals("start server")) serverStart = true;
                    else lines.add(l);
                }
            } catch (IOException ex) {
                LOG.error("[STRESS] план не прочитан", ex);
            }
            LOG.info("[STRESS] план: {} шагов", lines.size());
        }

        static void tick(MinecraftServer server) {
            if (!running) return;
            if (System.currentTimeMillis() < resumeAt) return;
            if (until != null) {
                if (!until.getAsBoolean()) return;
                until = null;
            }
            while (!lines.isEmpty()) {
                String l = lines.poll();
                LOG.info("[STRESS] шаг: {}", l);
                String[] w = l.split("\\s+", 2);
                String arg = w.length > 1 ? w[1] : "";
                switch (w[0]) {
                    case "wait" -> {
                        resumeAt = System.currentTimeMillis() + (long) (Double.parseDouble(arg) * 1000);
                        return;
                    }
                    case "mark" -> Meter.mark(server, arg);
                    case "cmd" -> run(server, arg);
                    case "fly" -> {
                        String[] a = arg.split("\\s+");
                        ServerPlayer p = player(server);
                        if (p == null) {
                            LOG.warn("[STRESS] пролёт без игрока пропущен");
                            continue;
                        }
                        Fly.start(p, Integer.parseInt(a[0]), Double.parseDouble(a[1]), a.length > 2 ? Double.parseDouble(a[2]) : 220);
                        until = () -> !Fly.active;
                        return;
                    }
                    case "end" -> {
                        Meter.mark(server, "после плана");
                        LOG.info("[STRESS] DONE");
                    }
                    default -> LOG.warn("[STRESS] непонятный шаг: {}", l);
                }
            }
            running = false;
        }
    }

    static void started(ServerStartedEvent e) {
        Plan.load();
        Meter.mark(e.getServer(), "сервер запущен");
        if (Plan.serverStart && !Plan.lines.isEmpty()) Plan.running = true;
    }

    static void login(PlayerEvent.PlayerLoggedInEvent e) {
        if (Plan.running || Plan.lines.isEmpty() || Plan.serverStart) return;
        Meter.mark(e.getEntity().getServer(), "игрок вошёл");
        Plan.running = true;
    }

    static ServerPlayer player(MinecraftServer server) {
        List<ServerPlayer> ps = server.getPlayerList().getPlayers();
        return ps.isEmpty() ? null : ps.get(0);
    }

    static void run(MinecraftServer server, String command) {
        ServerPlayer p = player(server);
        CommandSourceStack src = (p != null ? p.createCommandSourceStack() : server.createCommandSourceStack()).withPermission(4);
        server.getCommands().performPrefixedCommand(src, command);
    }

    // ---------- Команды ----------

    static void commands(RegisterCommandsEvent e) {
        CommandDispatcher<CommandSourceStack> d = e.getDispatcher();
        d.register(Commands.literal("stress").requires(s -> s.hasPermission(2))
                .then(Commands.literal("plant")
                        .executes(c -> Plant.build(c.getSource(), 1))
                        .then(Commands.argument("copies", IntegerArgumentType.integer(1, 16))
                                .executes(c -> Plant.build(c.getSource(), IntegerArgumentType.getInteger(c, "copies")))))
                .then(Commands.literal("report").executes(c -> Plant.report(c.getSource())))
                .then(Commands.literal("pigs").then(Commands.argument("n", IntegerArgumentType.integer(1, 1000))
                        .executes(c -> pigs(c.getSource(), IntegerArgumentType.getInteger(c, "n")))))
                .then(Commands.literal("mark").then(Commands.argument("name", StringArgumentType.greedyString())
                        .executes(c -> {
                            Meter.mark(c.getSource().getServer(), StringArgumentType.getString(c, "name"));
                            return 1;
                        })))
                .then(Commands.literal("fly").then(Commands.argument("chunks", IntegerArgumentType.integer(1))
                        .then(Commands.argument("speed", DoubleArgumentType.doubleArg(0.1, 16))
                                .executes(c -> {
                                    Fly.start(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "chunks"),
                                            DoubleArgumentType.getDouble(c, "speed"), 220);
                                    return 1;
                                })))));
    }

    /** Загон 8×8 на помосте в 12 блоках к западу от источника, n животных tfc:pig (без TFC — minecraft:pig) */
    static int pigs(CommandSourceStack src, int n) {
        ServerLevel level = src.getLevel();
        BlockPos c = BlockPos.containing(src.getPosition()).offset(-12, 0, 0);
        BlockState floor = Blocks.GRASS_BLOCK.defaultBlockState(), fence = Blocks.OAK_FENCE.defaultBlockState();
        for (int dx = -5; dx <= 5; dx++)
            for (int dz = -5; dz <= 5; dz++) {
                level.setBlock(c.offset(dx, -1, dz), floor, 3);
                for (int dy = 0; dy < 3; dy++) level.setBlock(c.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 3);
                if (Math.abs(dx) == 5 || Math.abs(dz) == 5) level.setBlock(c.offset(dx, 0, dz), fence, 3);
            }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.parse("tfc:pig")).orElse(EntityType.PIG);
        int spawned = 0;
        for (int i = 0; i < n; i++) {
            BlockPos p = c.offset(level.random.nextInt(9) - 4, 0, level.random.nextInt(9) - 4);
            if (type.spawn(level, p, MobSpawnType.COMMAND) != null) spawned++;
        }
        int count = spawned;
        LOG.info("[STRESS] загон у {}: {} из {} ({})", c.toShortString(), count, n, BuiltInRegistries.ENTITY_TYPE.getKey(type));
        src.sendSuccess(() -> Component.literal("загон: " + count + " животных"), false);
        return count;
    }
}
