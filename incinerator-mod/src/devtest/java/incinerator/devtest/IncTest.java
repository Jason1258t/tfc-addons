package incinerator.devtest;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import incinerator.Incinerator;
import incinerator.IncineratorBlockEntity;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Проверка мусоросжигателя на сервере разработки:
 *   /inctest setup — стенды: A) воронка с 64 булыжника → желоб Create → сжигатель, справа андезитовый шлюз на выход,
 *       слева андезитовый шлюз на вход (на него бросаются 32 гравия); B) 64 земли бросаются на сжигатель сверху,
 *       снизу воронка забирает гарь
 *   /inctest report — что осталось в воронке и желобе, что лежит перед шлюзами и на сжигателе, сколько гари вышло
 *   /inctest stats <n> — n предметов через ёмкость (как вставляет шлюз) в отдельный сжигатель: доля гари и её виды
 *   /inctest powered <n> — то же с блоком редстоуна рядом: гари быть не должно
 */
@Mod("incinerator_devtest")
public class IncTest {
    static final BlockPos A = new BlockPos(0, -60, 0), B = new BlockPos(8, -60, 0), C = new BlockPos(16, -60, 0), D = new BlockPos(24, -60, 0);

    public IncTest() {
        NeoForge.EVENT_BUS.addListener(IncTest::commands);
    }

    static void commands(RegisterCommandsEvent e) {
        CommandDispatcher<CommandSourceStack> d = e.getDispatcher();
        d.register(Commands.literal("inctest")
                .then(Commands.literal("setup").executes(c -> setup(c.getSource())))
                .then(Commands.literal("report").executes(c -> report(c.getSource())))
                .then(Commands.literal("stats").then(Commands.argument("n", IntegerArgumentType.integer(1, 100000))
                        .executes(c -> stats(c.getSource(), C, false, IntegerArgumentType.getInteger(c, "n")))))
                .then(Commands.literal("powered").then(Commands.argument("n", IntegerArgumentType.integer(1, 100000))
                        .executes(c -> stats(c.getSource(), D, true, IntegerArgumentType.getInteger(c, "n"))))));
    }

    static void set(ServerLevel level, BlockPos pos, String state) throws CommandSyntaxException {
        level.setBlock(pos, BlockStateParser.parseForBlock(level.holderLookup(Registries.BLOCK), state, false).blockState(), 3);
    }

    static int setup(CommandSourceStack src) throws CommandSyntaxException {
        ServerLevel level = src.getLevel();
        boolean create = BuiltInRegistries.BLOCK.containsKey(net.minecraft.resources.ResourceLocation.parse("create:chute"));
        // A: воронка → желоб → сжигатель, шлюзы по бокам
        level.setBlock(A, Incinerator.INCINERATOR.get().defaultBlockState(), 3);
        if (create) {
            set(level, A.above(), "create:chute");
            set(level, A.east(), "create:andesite_funnel[facing=east,extracting=true]");
            set(level, A.west(), "create:andesite_funnel[facing=west,extracting=false]");
        }
        set(level, A.above(create ? 2 : 1), "minecraft:hopper[facing=down]");
        ((Container) level.getBlockEntity(A.above(create ? 2 : 1))).setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        if (create) drop(level, A.west(), new ItemStack(Items.GRAVEL, 32));
        // B: бросок сверху, воронка снизу
        level.setBlock(B, Incinerator.INCINERATOR.get().defaultBlockState(), 3);
        set(level, B.below(), "minecraft:hopper[facing=down]");
        drop(level, B.above(), new ItemStack(Items.DIRT, 64));
        src.sendSuccess(() -> Component.literal("стенды готовы, Create: " + create), false);
        return 1;
    }

    static void drop(ServerLevel level, BlockPos pos, ItemStack stack) {
        ItemEntity item = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, stack);
        item.setDeltaMovement(0, 0, 0);
        level.addFreshEntity(item);
    }

    static String items(ServerLevel level, AABB box) {
        Map<String, Integer> n = new TreeMap<>();
        for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, box))
            n.merge(BuiltInRegistries.ITEM.getKey(e.getItem().getItem()).toString(), e.getItem().getCount(), Integer::sum);
        return n.isEmpty() ? "пусто" : n.toString();
    }

    static String contents(IItemHandler h) {
        if (h == null) return "нет ёмкости";
        Map<String, Integer> n = new TreeMap<>();
        for (int i = 0; i < h.getSlots(); i++) {
            ItemStack s = h.getStackInSlot(i);
            if (!s.isEmpty()) n.merge(BuiltInRegistries.ITEM.getKey(s.getItem()).toString(), s.getCount(), Integer::sum);
        }
        return n.isEmpty() ? "пусто" : n.toString();
    }

    static IItemHandler cap(ServerLevel level, BlockPos pos) {
        return level.getCapability(Capabilities.ItemHandler.BLOCK, pos, Direction.UP);
    }

    static int report(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        StringBuilder r = new StringBuilder();
        r.append("A воронка над желобом: ").append(contents(cap(level, A.above(2))));
        r.append("\nA желоб: ").append(contents(cap(level, A.above())));
        r.append("\nA перед шлюзом на выход: ").append(items(level, new AABB(A.east()).inflate(2, 1, 2).move(1, 0, 0)));
        r.append("\nA на шлюзе на вход: ").append(items(level, new AABB(A.west()).inflate(0.2)));
        r.append("\nA гарь внутри: ").append(contents(cap(level, A)));
        r.append("\nB на сжигателе: ").append(items(level, new AABB(B.above()).inflate(0.5)));
        r.append("\nB воронка снизу: ").append(contents(cap(level, B.below())));
        r.append("\nB гарь внутри: ").append(contents(cap(level, B)));
        r.append("\nрецепт incinerator:incinerator загружен: ").append(level.getServer().getRecipeManager()
                .byKey(Incinerator.rl("incinerator")).isPresent());
        src.sendSuccess(() -> Component.literal(r.toString()), false);
        return 1;
    }

    static int stats(CommandSourceStack src, BlockPos pos, boolean powered, int n) {
        ServerLevel level = src.getLevel();
        level.setBlock(pos, Incinerator.INCINERATOR.get().defaultBlockState(), 3);
        level.setBlock(pos.north(), powered ? Blocks.REDSTONE_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
        IItemHandler h = cap(level, pos);
        for (int left = n; left > 0; left -= 64) {
            ItemStack rest = h.insertItem(left % 5, new ItemStack(Items.ROTTEN_FLESH, Math.min(64, left)), false);
            if (!rest.isEmpty()) src.sendFailure(Component.literal("не принял " + rest));
        }
        int got = ((IncineratorBlockEntity) level.getBlockEntity(pos)).residueCount();
        String msg = String.format("%s: сожжено %d, гарь %d (%.1f%%, ожидание %.0f%%): %s", powered ? "под сигналом" : "без сигнала",
                n, got, 100.0 * got / n, powered ? 0.0 : IncineratorBlockEntity.RESIDUE_CHANCE * 100, contents(h));
        src.sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }
}
