package tfc.stand.scene;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Заготовка клиентской сцены для стенда (dev #342): сама создаёт мир, ждёт загрузку, ставит площадку у точки появления
 * и выполняет шаги по очереди. Проверка пишется как «поставь блоки, дождись их у клиента, наведи камеру, сними кадр».
 *
 * <p>Мир — {@code tfc:overworld} с постоянным сидом: в мире minecraft:flat TFC Eratosthenes роняет тик игрока, и NeoForge
 * сборки (removeErroringEntities) молча удаляет серверного игрока — клиенту перестают приходить блоки и чанки.
 *
 * <p>Лог — строки {@code SCENE OK|FAIL|NOTE|SHOT|END}, стенд собирает их в сводку; кадры — {@code
 * screenshots/scene-<имя>-<кадр>.png}. Конец — {@code SCENE END PASS|FAIL}, затем игра закрывается.
 */
public final class Scene {
    /** Сид мира сцен: та же точка появления от прогона к прогону */
    public static final long SEED = 20261113L;
    private static final ResourceKey<WorldPreset> TFC = ResourceKey.create(Registries.WORLD_PRESET,
            ResourceLocation.fromNamespaceAndPath("tfc", "overworld"));

    private final String name;
    private final Deque<Step> steps = new ArrayDeque<>();
    // Проверки идут и из потока сервера
    private final List<String> fails = Collections.synchronizedList(new ArrayList<>());
    private int radius = 12, height = 10;
    private BlockPos origin;
    private boolean created, finished;
    private int wait;
    private Step current;

    private interface Tick {
        /** true — шаг закончен */
        boolean run(int ticks);
    }

    private record Step(String name, Tick body) {}

    /** Что видит серверный шаг: мир, игрок, середина площадки */
    public record Ctx(MinecraftServer server, ServerLevel level, ServerPlayer player, BlockPos origin) {
        /** Точка относительно середины площадки (y = 0 — первый слой над полом) */
        public BlockPos at(int x, int y, int z) {
            return origin.offset(x, y, z);
        }

        public void set(BlockPos pos, BlockState state) {
            level.setBlock(pos, state, Block.UPDATE_ALL);
        }
    }

    /**
     * Имя сцены, которую просит стенд: файл {@code scene} в папке игры (stand_submit scene=&lt;имя&gt;) или свойство
     * {@code -Dstand.scene}; null — не просит.
     */
    public static String requested() {
        String prop = System.getProperty("stand.scene");
        if (prop != null && !prop.isBlank()) return prop.trim();
        try {
            Path file = Path.of("scene");
            return Files.isRegularFile(file) ? Files.readString(file).trim() : null;
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean requested(String name) {
        return name.equals(requested());
    }

    public Scene(String name) {
        this.name = name;
    }

    /** Площадка: пол на точке появления ±radius, над ним height слоёв воздуха (по умолчанию 12 и 10) */
    public Scene area(int radius, int height) {
        this.radius = radius;
        this.height = height;
        return this;
    }

    /** Середина площадки, первый слой над полом; есть после подготовки мира */
    public BlockPos origin() {
        return origin;
    }

    /** Точка от середины площадки (низ блока, центр по x и z); только во время шагов */
    public Vec3 at(double x, double y, double z) {
        return Vec3.atBottomCenterOf(origin).add(x, y, z);
    }

    // ------------------------------------------------------------------ шаги

    /** Действие на встроенном сервере; следующий шаг — после его выполнения */
    public Scene server(String step, Consumer<Ctx> action) {
        AtomicReference<Object> done = new AtomicReference<>();
        return add(step, t -> {
            if (t == 0) {
                MinecraftServer server = mc().getSingleplayerServer();
                server.execute(() -> {
                    try {
                        ServerPlayer player = server.getPlayerList().getPlayer(mc().player.getUUID());
                        action.accept(new Ctx(server, player.serverLevel(), player, origin));
                        done.set(Boolean.TRUE);
                    } catch (Throwable e) {
                        done.set(e);
                    }
                });
                return false;
            }
            Object result = done.get();
            if (result instanceof Throwable e) {
                check(step + ": " + e, false);
                e.printStackTrace();
            }
            return result != null;
        });
    }

    /** Действие на клиенте (поток отрисовки) */
    public Scene client(String step, Runnable action) {
        return add(step, t -> {
            action.run();
            return true;
        });
    }

    public Scene ticks(int ticks) {
        return add("пауза " + ticks, t -> t >= ticks);
    }

    /** Ждать условия на клиенте (проверка раз в тик); не дождались за timeout тиков — FAIL, сцена идёт дальше */
    public Scene until(String step, int timeout, BooleanSupplier condition) {
        return add(step, t -> {
            if (condition.getAsBoolean()) {
                note(step + ": через " + t + " тиков");
                return true;
            }
            if (t < timeout) return false;
            check(step + ": не дождались за " + timeout + " тиков", false);
            return true;
        });
    }

    /** Проверка на клиенте */
    public Scene check(String step, BooleanSupplier condition) {
        return client(step, () -> check(step, condition.getAsBoolean()));
    }

    /**
     * Ждать, пока клиент увидит те же блоки, что сервер, в коробке from..to (до 30 с): иначе кадр снимет пустоту.
     * Сравниваются состояния блоков; сервер снимает их в своём потоке.
     */
    public Scene synced(String step, BlockPos from, BlockPos to) {
        return synced(step, () -> new BlockPos[] {from, to});
    }

    private Scene synced(String step, Supplier<BlockPos[]> box) {
        AtomicReference<List<Object[]>> snapshot = new AtomicReference<>();
        return add(step, t -> {
            if (t % 10 == 0 && snapshot.get() == null) {
                BlockPos[] corners = box.get();
                MinecraftServer server = mc().getSingleplayerServer();
                server.execute(() -> {
                    List<Object[]> states = new ArrayList<>();
                    for (BlockPos pos : BlockPos.betweenClosed(corners[0], corners[1])) {
                        states.add(new Object[] {pos.immutable(), server.overworld().getBlockState(pos)});
                    }
                    snapshot.set(states);
                });
            }
            List<Object[]> states = snapshot.get();
            if (states == null) return false;
            int differ = 0;
            BlockPos first = null;
            for (Object[] s : states) {
                if (mc().level.getBlockState((BlockPos) s[0]) != s[1]) {
                    if (first == null) first = (BlockPos) s[0];
                    differ++;
                }
            }
            if (differ == 0) {
                note(step + ": клиент видит " + states.size() + " блоков сервера через " + t + " тиков");
                return true;
            }
            if (t >= 600) {
                check(step + ": у клиента отличаются " + differ + " из " + states.size() + " блоков, первый "
                        + first.toShortString() + " — " + mc().level.getBlockState(first), false);
                return true;
            }
            // Сервер мог поменять блоки за это время: снимок обновляется раз в 10 тиков
            if (t % 10 == 9) snapshot.set(null);
            return false;
        });
    }

    /**
     * Камера на точку (x, y, z) от середины площадки: игрок висит в воздухе в distance блоков от неё и смотрит под углами
     * yaw (0 — на юг, 90 — на запад) и pitch (вниз — плюс). Шаг ждёт загрузку чанков у клиента и отрисовку ландшафта.
     */
    public Scene camera(double x, double y, double z, double distance, float yaw, float pitch) {
        return server("камера на " + x + " " + y + " " + z, c -> {
            Vec3 target = at(x, y, z);
            Vec3 eye = target.subtract(Vec3.directionFromRotation(pitch, yaw).scale(distance));
            // Выпавшее с краёв площадки (растения, камешки TFC) — не в кадр
            c.level().getEntitiesOfClass(ItemEntity.class, new AABB(origin).inflate(radius + 2, height, radius + 2))
                    .forEach(Entity::discard);
            c.player().getAbilities().flying = true;
            c.player().onUpdateAbilities();
            c.player().teleportTo(c.level(), eye.x, eye.y - c.player().getEyeHeight(), eye.z, yaw, pitch);
        }).add("камера: загрузка вида", t -> {
            var player = mc().player;
            player.setYRot(yaw);
            player.setXRot(pitch);
            player.yRotO = yaw;
            player.xRotO = pitch;
            boolean chunks = mc().level.hasChunkAt(BlockPos.containing(at(x, y, z))) && mc().level.hasChunkAt(player.blockPosition());
            if (t >= 20 && chunks && terrainDone()) return true;
            if (t >= 600) {
                note("камера: за 30 с " + (chunks ? "ландшафт не дорисован" : "чанки у клиента не загружены") + ", снимаю как есть");
                return true;
            }
            return false;
        });
    }

    /** Скрыть интерфейс (руку, прицел, чат) на кадрах */
    public Scene gui(boolean shown) {
        return client(shown ? "интерфейс виден" : "интерфейс скрыт", () -> mc().options.hideGui = !shown);
    }

    /** Кадр screenshots/scene-&lt;сцена&gt;-&lt;shot&gt;.png; стенд забирает его сам */
    public Scene shot(String shot) {
        return add("кадр " + shot, t -> {
            if (t == 0) {
                Screenshot.grab(mc().gameDirectory, "scene-" + name + "-" + shot + ".png", mc().getMainRenderTarget(),
                        message -> System.out.println("SCENE SHOT " + shot + ": " + message.getString()));
            }
            return t >= 5;
        });
    }

    public void check(String what, boolean ok) {
        if (!ok) fails.add(what);
        System.out.println("SCENE " + (ok ? "OK " : "FAIL ") + what);
    }

    public void note(String what) {
        System.out.println("SCENE NOTE " + what);
    }

    /** Запустить: мир создаётся на главном меню, затем подготовка и шаги; в конце игра закрывается */
    public void start() {
        System.out.println("SCENE START " + name);
        Deque<Step> user = new ArrayDeque<>(steps);
        steps.clear();
        prologue();
        steps.addAll(user);
        NeoForge.EVENT_BUS.addListener(this::screen);
        NeoForge.EVENT_BUS.addListener(this::tick);
        if (mc().screen instanceof TitleScreen title) createWorld(title);
    }

    // ------------------------------------------------------------------ подготовка

    private void prologue() {
        until("мир открыт, экранов загрузки нет", 6000, () -> mc().screen == null && mc().level != null
                && mc().level.hasChunkAt(mc().player.blockPosition()));
        // На стенде сервер после входа отстаёт на десятки тиков: постройка и телепорт в это время теряются
        int[] calm = {0};
        until("встроенный сервер догнал клиент", 3000, () -> {
            float ms = mc().getSingleplayerServer().getCurrentSmoothedTickTime();
            calm[0] = ms < 50 ? calm[0] + 1 : 0;
            return calm[0] >= 40;
        });
        server("площадка у точки появления", c -> {
            var rules = c.server().getGameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, c.server());
            rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, c.server());
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, c.server());
            c.level().setDayTime(6000);
            c.level().setWeatherParameters(6000, 0, false, false);
            BlockPos spawn = c.level().getSharedSpawnPos();
            origin = c.level().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn);
            // Без соседских обновлений: обвалы и течения TFC площадку не трогают
            int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
            for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-radius, -1, -radius), origin.offset(radius, height, radius))) {
                c.level().setBlock(pos, pos.getY() < origin.getY() ? Blocks.SMOOTH_STONE.defaultBlockState()
                        : Blocks.AIR.defaultBlockState(), flags);
            }
            c.player().getAbilities().flying = true;
            c.player().onUpdateAbilities();
            c.player().teleportTo(c.level(), origin.getX() + 0.5, origin.getY(), origin.getZ() - radius + 2.5, 0, 20);
            note("площадка: середина " + origin.toShortString() + ", радиус " + radius + ", высота " + height
                    + ", генератор " + c.level().getChunkSource().getGenerator().getClass().getSimpleName());
        });
        // Коробка площадки известна только после серверного шага
        synced("площадка у клиента", () -> new BlockPos[] {origin.offset(-radius, -1, -radius),
                origin.offset(radius, height, radius)});
    }

    private void screen(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof TitleScreen title) createWorld(title);
    }

    private void createWorld(Screen parent) {
        if (created) return;
        created = true;
        Minecraft mc = mc();
        mc.options.pauseOnLostFocus = false;
        GameRules rules = new GameRules();
        LevelSettings settings = new LevelSettings("scene-" + name, GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                rules, WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel("scene-" + name, settings, new WorldOptions(SEED, false, false), reg -> {
            var presets = reg.registryOrThrow(Registries.WORLD_PRESET);
            // Без TFC (dev-запуск аддона без него) — плоский мир: Eratosthenes тогда тоже нет
            var preset = presets.getOptional(TFC).orElseGet(() -> presets.getOrThrow(WorldPresets.FLAT));
            System.out.println("SCENE NOTE мир " + (presets.containsKey(TFC) ? TFC.location() : WorldPresets.FLAT.location())
                    + ", сид " + SEED);
            return preset.createWorldDimensions();
        }, parent);
    }

    // ------------------------------------------------------------------ ход сцены

    private Scene add(String step, Tick body) {
        steps.add(new Step(step, body));
        return this;
    }

    private void tick(ClientTickEvent.Post event) {
        if (finished || mc().player == null || mc().getSingleplayerServer() == null) return;
        MinecraftServer server = mc().getSingleplayerServer();
        ServerPlayer player = server.getPlayerList().getPlayer(mc().player.getUUID());
        if (player != null && player.isRemoved()) {
            check("серверный игрок удалён из мира (ошибка в тике игрока, см. crash-reports) — клиенту больше не приходят"
                    + " блоки и чанки", false);
            finish();
            return;
        }
        if (current == null) {
            current = steps.poll();
            wait = 0;
            if (current == null) {
                finish();
                return;
            }
            System.out.println("SCENE STEP " + current.name());
        }
        Step step = current;
        boolean done;
        try {
            done = step.body().run(wait++);
        } catch (Throwable e) {
            check(step.name() + ": " + e, false);
            e.printStackTrace();
            done = true;
        }
        if (done && current == step) current = null;
    }

    private void finish() {
        finished = true;
        System.out.println("SCENE END " + (fails.isEmpty() ? "PASS" : "FAIL " + fails.size()) + " " + name);
        mc().stop();
    }

    /** Sodium (в сборке) отрисовал ландшафт; без него — ванильная очередь секций пуста */
    private static boolean terrainDone() {
        try {
            Class<?> sodium = Class.forName("net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer");
            Object renderer = sodium.getMethod("instanceNullable").invoke(null);
            if (renderer != null) return (boolean) sodium.getMethod("isTerrainRenderComplete").invoke(renderer);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Sodium нет или API другое — ванильная проверка
        }
        return mc().levelRenderer.hasRenderedAllSections();
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }
}
