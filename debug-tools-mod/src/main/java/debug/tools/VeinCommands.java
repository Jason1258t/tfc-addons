package debug.tools;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.dries007.tfc.util.collections.IWeighted;
import net.dries007.tfc.world.feature.vein.IVein;
import net.dries007.tfc.world.feature.vein.IVeinConfig;
import net.dries007.tfc.world.feature.vein.VeinFeature;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * Положение жилы TFC считается из сида мира и номера чанка ({@link VeinFeature#getVeinsAtChunk}), поэтому центры можно
 * узнать, не генерируя чанки. Будет ли там руда, решают биом (жила должна быть в его списке) и порода вокруг центра.
 */
final class VeinCommands {
    static final int MAX_RADIUS = 1000;

    private VeinCommands() {}

    record Vein(ResourceLocation id, PlacedFeature placed, VeinFeature<?, ?> feature, IVeinConfig config) {
        Set<Block> ores() {
            Set<Block> out = new HashSet<>();
            for (IWeighted<BlockState> states : config.config().states().values()) {
                for (BlockState state : states.values()) out.add(state.getBlock());
            }
            return out;
        }
    }

    private record Center(BlockPos pos, long distanceSqr, boolean inBiome) {}

    private static Registry<PlacedFeature> registry(ServerLevel level) {
        return level.registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
    }

    static Vein vein(ServerLevel level, ResourceLocation id) {
        PlacedFeature placed = registry(level).get(id);
        return placed == null ? null : vein(id, placed);
    }

    private static Vein vein(ResourceLocation id, PlacedFeature placed) {
        ConfiguredFeature<?, ?> configured = placed.feature().value();
        if (configured.feature() instanceof VeinFeature<?, ?> feature && configured.config() instanceof IVeinConfig config) {
            return new Vein(id, placed, feature, config);
        }
        return null;
    }

    private static List<Vein> all(ServerLevel level) {
        List<Vein> out = new ArrayList<>();
        Registry<PlacedFeature> registry = registry(level);
        for (ResourceLocation id : registry.keySet()) {
            Vein vein = vein(id, registry.get(id));
            if (vein != null) out.add(vein);
        }
        out.sort(Comparator.comparing(v -> v.id().toString()));
        return out;
    }

    static List<ResourceLocation> ids(ServerLevel level) {
        return all(level).stream().map(Vein::id).toList();
    }

    static int list(CommandSourceStack source, String filter) {
        ServerLevel level = source.getLevel();
        BlockPos here = BlockPos.containing(source.getPosition());
        Holder<Biome> biome = level.getBiome(here);
        List<Vein> veins = all(level).stream().filter(v -> v.id().toString().contains(filter)).toList();
        for (Vein vein : veins) {
            boolean inBiome = biome.value().getGenerationSettings().hasFeature(vein.placed());
            var c = vein.config().config();
            String line = String.format("%s — редкость 1 на %d чанков, y %d…%d, плотность %.2f%s", vein.id(), c.rarity(), c.minY(), c.maxY(),
                    c.density(), c.projectToSurface() ? ", y от поверхности" : "");
            source.sendSuccess(() -> Component.literal(line).withStyle(inBiome ? ChatFormatting.GREEN : ChatFormatting.GRAY), false);
        }
        String biomeName = biome.unwrapKey().map(k -> k.location().toString()).orElse("?");
        long inBiome = veins.stream().filter(v -> biome.value().getGenerationSettings().hasFeature(v.placed())).count();
        source.sendSuccess(() -> Component.literal("Жил: " + veins.size() + "; в биоме " + biomeName + " из них " + inBiome + " (зелёные)"), false);
        return veins.size();
    }

    static int find(CommandSourceStack source, Vein vein, int radius, int limit) {
        List<Center> centers = centers(source, vein, radius);
        long allowed = centers.stream().filter(Center::inBiome).count();
        List<Center> shown = centers.stream().sorted(nearestAllowed()).limit(limit).toList();
        for (Center center : shown) source.sendSuccess(() -> line(center, center.inBiome() ? "" : " — биом без этой жилы"), false);
        String sum = String.format("%s: центров в радиусе %d чанков — %d, в подходящем биоме — %d. Руда будет только в подходящей породе; "
                + "проверить — /tfcdebug vein check %s", vein.id(), radius, centers.size(), allowed, vein.id());
        source.sendSuccess(() -> Component.literal(sum), false);
        return centers.size();
    }

    static int check(CommandSourceStack source, Vein vein, int radius, int limit) {
        List<Center> centers = centers(source, vein, radius).stream().filter(Center::inBiome).sorted(nearestAllowed()).limit(limit).toList();
        if (centers.isEmpty()) {
            source.sendFailure(Component.literal(vein.id() + ": в радиусе " + radius + " чанков нет центров в подходящем биоме"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(vein.id() + ": генерирую чанки у " + centers.size() + " центров…"), false);
        // По одному центру за тик: генерация в одном тике на дедике упрётся в сторожевой таймер сервера
        checkNext(source, vein, centers, 0, 0, 0);
        return centers.size();
    }

    private static void checkNext(CommandSourceStack source, Vein vein, List<Center> centers, int index, int withOre, int total) {
        MinecraftServer server = source.getServer();
        if (index >= centers.size()) {
            String sum = String.format("%s: проверено центров %d, с рудой %d, всего блоков руды %d", vein.id(), centers.size(), withOre, total);
            DebugTools.report(source, Component.literal(sum).withStyle(withOre > 0 ? ChatFormatting.GREEN : ChatFormatting.RED));
            return;
        }
        server.tell(new TickTask(server.getTickCount() + 1, () -> {
            Center center = centers.get(index);
            int found = countOre(source.getLevel(), vein, center.pos());
            BlockState rock = source.getLevel().getBlockState(center.pos());
            String rockName = BuiltInRegistries.BLOCK.getKey(rock.getBlock()).toString();
            DebugTools.report(source, line(center, " — руды " + found + ", в центре " + rockName));
            checkNext(source, vein, centers, index + 1, withOre + (found > 0 ? 1 : 0), total + found);
        }));
    }

    private static int countOre(ServerLevel level, Vein vein, BlockPos pos) {
        Set<Block> ores = vein.ores();
        ChunkPos chunk = new ChunkPos(pos);
        int radius = vein.config().chunkRadius();
        int found = 0;
        for (int x = chunk.x - radius; x <= chunk.x + radius; x++) {
            for (int z = chunk.z - radius; z <= chunk.z + radius; z++) {
                found += OreScan.count(level.getChunk(x, z), ores).count();
            }
        }
        return found;
    }

    /** Все центры в квадрате вокруг игрока: только чтение сида, чанки не грузятся. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<Center> centers(CommandSourceStack source, Vein vein, int radius) {
        ServerLevel level = source.getLevel();
        BlockPos here = BlockPos.containing(source.getPosition());
        ChunkPos chunk = new ChunkPos(here);
        WorldGenerationContext context = new WorldGenerationContext(level.getChunkSource().getGenerator(), level);
        VeinFeature feature = vein.feature();
        List<IVein> found = new ArrayList<>();
        for (int x = chunk.x - radius; x <= chunk.x + radius; x++) {
            for (int z = chunk.z - radius; z <= chunk.z + radius; z++) {
                feature.getVeinsAtChunk(level, context, x, z, (List) found, vein.config());
            }
        }
        List<Center> out = new ArrayList<>(found.size());
        for (IVein v : found) {
            BlockPos pos = v.pos();
            long dx = pos.getX() - here.getX(), dz = pos.getZ() - here.getZ();
            boolean inBiome = level.getBiome(pos).value().getGenerationSettings().hasFeature(vein.placed());
            out.add(new Center(pos, dx * dx + dz * dz, inBiome));
        }
        return out;
    }

    private static Comparator<Center> nearestAllowed() {
        return Comparator.comparing((Center c) -> !c.inBiome()).thenComparingLong(Center::distanceSqr);
    }

    private static MutableComponent line(Center center, String suffix) {
        BlockPos p = center.pos();
        String coords = p.getX() + " " + p.getY() + " " + p.getZ();
        long distance = Math.round(Math.sqrt(center.distanceSqr()));
        String tp = "/tp @s " + coords;
        return Component.literal(coords)
                .setStyle(Style.EMPTY.withColor(ChatFormatting.AQUA)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, tp))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(tp))))
                .append(Component.literal(" — " + distance + " м" + suffix).withStyle(ChatFormatting.RESET));
    }

}
