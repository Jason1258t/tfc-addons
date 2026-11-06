package perf.fix.devtest;

import com.jmsharedmaps.server.RegionTileStore;
import com.jmsharedmaps.server.ServerTileCache;
import com.jmsharedmaps.server.StoredTile;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.io.IOException;
import net.dries007.tfc.common.blocks.TFCBlocks;
import net.dries007.tfc.common.entities.aquatic.Octopoteuthis;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import perf.fix.TileWriter;

/**
 * /pftest bench <чанков> <проходов> — записать тайлы общей карты через сам мод (ServerTileCache.putIfNewer, как при
 * приходе тайла от игрока) прямо в главном потоке и вывести, сколько это заняло.
 * /pftest verify — прочитать файлы регионов кодом мода (RegionTileStore.loadRegion) и сравнить с памятью мода.
 * /pftest glow far|near — октопотеутис TFC с блоком света далеко (незагруженный чанк) или рядом; убрать существо и
 * показать, сколько это заняло, загрузился ли дальний чанк и снят ли ближний блок света.
 */
@Mod("perf_fix_devtest")
public final class DevTest {
    private static final ResourceLocation DIM = ResourceLocation.withDefaultNamespace("overworld");
    private static long stamp = System.currentTimeMillis();

    public DevTest() {
        NeoForge.EVENT_BUS.addListener(DevTest::register);
    }

    private static void register(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("pftest")
                .then(Commands.literal("bench")
                        .then(Commands.argument("chunks", IntegerArgumentType.integer(1))
                                .then(Commands.argument("rounds", IntegerArgumentType.integer(1))
                                        .executes(DevTest::bench))))
                .then(Commands.literal("verify").executes(DevTest::verify))
                .then(Commands.literal("glow")
                        .then(Commands.literal("far").executes(c -> glow(c, true)))
                        .then(Commands.literal("near").executes(c -> glow(c, false)))));
    }

    private static int bench(CommandContext<CommandSourceStack> ctx) {
        int chunks = IntegerArgumentType.getInteger(ctx, "chunks");
        int rounds = IntegerArgumentType.getInteger(ctx, "rounds");
        int side = (int) Math.ceil(Math.sqrt(chunks));
        Random random = new Random(42);
        long start = System.nanoTime();
        int accepted = 0;
        for (int r = 0; r < rounds; r++) {
            for (int i = 0; i < chunks; i++) {
                // Снимок 4–8 КБ, как сжатый тайл JourneyMap; каждый проход — новая версия того же чанка
                byte[] payload = new byte[4096 + random.nextInt(4096)];
                random.nextBytes(payload);
                StoredTile tile = new StoredTile(DIM, i % side - side / 2, i / side - side / 2, ++stamp, payload);
                if (ServerTileCache.get().putIfNewer("global", tile)) {
                    accepted++;
                }
            }
        }
        double ms = (System.nanoTime() - start) / 1e6;
        String msg = String.format("bench: тайлов %d, главный поток %.1f мс (%.3f мс на тайл), в очереди %d, записано %d",
                accepted, ms, ms / Math.max(1, accepted), TileWriter.pending(), TileWriter.written());
        ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
        return accepted;
    }

    private static int verify(CommandContext<CommandSourceStack> ctx) {
        Path dir = ctx.getSource().getServer().getWorldPath(LevelResource.ROOT)
                .resolve("jmsharedmaps/global/" + RegionTileStore.dimensionDirName(DIM));
        Map<StoredTile.ChunkKey, StoredTile> disk = new HashMap<>();
        long bytes = 0;
        int files = 0;
        try (Stream<Path> paths = Files.list(dir)) {
            for (Path p : paths.toList()) {
                if (p.getFileName().toString().endsWith(".jmregion")) {
                    files++;
                    bytes += Files.size(p);
                    RegionTileStore.loadRegion(p, DIM, t -> disk.put(t.key(), t));
                }
            }
        } catch (IOException e) {
            ctx.getSource().sendFailure(Component.literal("verify: " + e));
            return 0;
        }
        List<StoredTile> memory = ServerTileCache.get().snapshot("global");
        int same = 0, stale = 0, missing = 0;
        for (StoredTile t : memory) {
            StoredTile d = disk.get(t.key());
            if (d == null) {
                missing++;
            } else if (d.timestamp() == t.timestamp() && Arrays.equals(d.compressedSnapshot(), t.compressedSnapshot())) {
                same++;
            } else {
                stale++;
            }
        }
        String msg = String.format("verify: в памяти %d, на диске %d (файлов %d, %.1f МБ); совпало %d, устарело %d, нет %d; в очереди %d",
                memory.size(), disk.size(), files, bytes / 1048576.0, same, stale, missing, TileWriter.pending());
        ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
        return same;
    }

    private static int glow(CommandContext<CommandSourceStack> ctx, boolean far) {
        ServerLevel level = ctx.getSource().getLevel();
        Entity entity = EntityType.byString("tfc:octopoteuthis").orElseThrow().create(level);
        if (!(entity instanceof Octopoteuthis squid)) {
            ctx.getSource().sendFailure(Component.literal("glow: нет tfc:octopoteuthis"));
            return 0;
        }
        squid.moveTo(0.5, -59, 0.5);
        level.addFreshEntity(squid);
        BlockPos light = far ? new BlockPos(160_000, -50, 160_000) : new BlockPos(3, -59, 0);
        if (!far) {
            level.setBlockAndUpdate(light, TFCBlocks.LIGHT.get().defaultBlockState());
        }
        squid.setLightPos(light);
        boolean before = level.hasChunk(light.getX() >> 4, light.getZ() >> 4);
        long start = System.nanoTime();
        squid.discard();
        double ms = (System.nanoTime() - start) / 1e6;
        boolean after = level.hasChunk(light.getX() >> 4, light.getZ() >> 4);
        String block = far ? "-" : BuiltInRegistries.BLOCK.getKey(level.getBlockState(light).getBlock()).toString();
        String msg = String.format("glow %s: исчезновение %.1f мс, чанк света загружен до %s, после %s, блок света теперь %s",
                far ? "far" : "near", ms, before, after, block);
        ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
        return after ? 1 : 0;
    }
}
