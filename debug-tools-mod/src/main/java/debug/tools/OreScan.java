package debug.tools;

import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;

/** Счёт блока вокруг игрока. Чанки генерируются, по одному за тик: команда работает и в ещё не посещённой части мира. */
final class OreScan {
    static final int MAX_RADIUS = 32;

    private OreScan() {}

    /** Сколько блоков в чанке, нижняя и верхняя высота, первый найденный блок. */
    record Result(int count, int minY, int maxY, BlockPos first) {
        static final Result EMPTY = new Result(0, Integer.MAX_VALUE, Integer.MIN_VALUE, null);

        Result plus(Result o) {
            if (o.count == 0) return this;
            return new Result(count + o.count, Math.min(minY, o.minY), Math.max(maxY, o.maxY), first != null ? first : o.first);
        }
    }

    static Result count(ChunkAccess chunk, Set<Block> blocks) {
        Result total = Result.EMPTY;
        LevelChunkSection[] sections = chunk.getSections();
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section.hasOnlyAir() || !section.maybeHas(s -> blocks.contains(s.getBlock()))) continue;
            int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(i));
            ChunkPos pos = chunk.getPos();
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        if (blocks.contains(section.getBlockState(x, y, z).getBlock())) {
                            int worldY = baseY + y;
                            total = total.plus(new Result(1, worldY, worldY, new BlockPos(pos.getMinBlockX() + x, worldY, pos.getMinBlockZ() + z)));
                        }
                    }
                }
            }
        }
        return total;
    }

    static void start(CommandSourceStack source, Block block, int radius) {
        ChunkPos center = new ChunkPos(BlockPos.containing(source.getPosition()));
        String name = BuiltInRegistries.BLOCK.getKey(block).toString();
        int chunks = (2 * radius + 1) * (2 * radius + 1);
        source.sendSuccess(() -> Component.literal(name + ": считаю в " + chunks + " чанках…"), false);
        next(source, Set.of(block), name, center, radius, 0, Result.EMPTY);
    }

    private static void next(CommandSourceStack source, Set<Block> blocks, String name, ChunkPos center, int radius, int index, Result total) {
        MinecraftServer server = source.getServer();
        int side = 2 * radius + 1;
        if (index >= side * side) {
            finish(source, name, side * side, total);
            return;
        }
        server.tell(new TickTask(server.getTickCount() + 1, () -> {
            ServerLevel level = source.getLevel();
            ChunkAccess chunk = level.getChunk(center.x - radius + index % side, center.z - radius + index / side);
            next(source, blocks, name, center, radius, index + 1, total.plus(count(chunk, blocks)));
        }));
    }

    private static void finish(CommandSourceStack source, String name, int chunks, Result total) {
        if (total.count() == 0) {
            DebugTools.report(source, Component.literal(name + ": нет в " + chunks + " чанках").withStyle(ChatFormatting.YELLOW));
            return;
        }
        BlockPos first = total.first();
        String line = String.format("%s: %d блоков в %d чанках, y %d…%d, например %d %d %d", name, total.count(), chunks, total.minY(), total.maxY(),
                first.getX(), first.getY(), first.getZ());
        DebugTools.report(source, Component.literal(line).withStyle(ChatFormatting.GREEN));
    }
}
