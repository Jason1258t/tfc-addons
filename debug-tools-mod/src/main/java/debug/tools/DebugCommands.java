package debug.tools;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * /tfcdebug vein list [слово] — какие жилы зарегистрированы, редкость, высоты, есть ли жила в биоме под игроком.
 * /tfcdebug vein find <жила> [радиус чанков] [сколько] — ближайшие центры по сиду мира, чанки не генерируются.
 * /tfcdebug vein check <жила> [радиус чанков] [сколько] — сгенерировать чанки у центров и посчитать настоящую руду.
 * /tfcdebug ore <блок> [радиус чанков] — сколько блоков вокруг, с генерацией чанков.
 */
public final class DebugCommands {
    private static final DynamicCommandExceptionType UNKNOWN_VEIN =
            new DynamicCommandExceptionType(id -> Component.literal("Нет такой жилы: " + id + ". Список — /tfcdebug vein list"));

    private DebugCommands() {}

    public static void register(RegisterCommandsEvent event) {
        var context = event.getBuildContext();
        event.getDispatcher().register(Commands.literal("tfcdebug").requires(s -> s.hasPermission(2))
                .then(Commands.literal("vein")
                        .then(Commands.literal("list")
                                .executes(c -> VeinCommands.list(c.getSource(), ""))
                                .then(Commands.argument("filter", StringArgumentType.word())
                                        .executes(c -> VeinCommands.list(c.getSource(), StringArgumentType.getString(c, "filter")))))
                        .then(veinCommand("find", 256, 10, VeinCommands::find))
                        .then(veinCommand("check", 256, 5, VeinCommands::check)))
                .then(Commands.literal("ore")
                        .then(Commands.argument("block", ResourceArgument.resource(context, Registries.BLOCK))
                                .executes(c -> ore(c.getSource(), ResourceArgument.getResource(c, "block", Registries.BLOCK), 4))
                                .then(Commands.argument("radius", IntegerArgumentType.integer(0, OreScan.MAX_RADIUS))
                                        .executes(c -> ore(c.getSource(), ResourceArgument.getResource(c, "block", Registries.BLOCK),
                                                IntegerArgumentType.getInteger(c, "radius")))))));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> veinCommand(
            String name, int defaultRadius, int defaultLimit, VeinAction action) {
        return Commands.literal(name)
                .then(Commands.argument("vein", ResourceLocationArgument.id())
                        .suggests((c, b) -> SharedSuggestionProvider.suggestResource(VeinCommands.ids(c.getSource().getLevel()), b))
                        .executes(c -> action.run(c.getSource(), vein(c), defaultRadius, defaultLimit))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, VeinCommands.MAX_RADIUS))
                                .executes(c -> action.run(c.getSource(), vein(c), IntegerArgumentType.getInteger(c, "radius"), defaultLimit))
                                .then(Commands.argument("limit", IntegerArgumentType.integer(1, 100))
                                        .executes(c -> action.run(c.getSource(), vein(c), IntegerArgumentType.getInteger(c, "radius"),
                                                IntegerArgumentType.getInteger(c, "limit"))))));
    }

    private static VeinCommands.Vein vein(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ResourceLocation id = ResourceLocationArgument.getId(c, "vein");
        VeinCommands.Vein vein = VeinCommands.vein(c.getSource().getLevel(), id);
        if (vein == null) throw UNKNOWN_VEIN.create(id);
        return vein;
    }

    private static int ore(CommandSourceStack source, Holder.Reference<Block> block, int radius) {
        OreScan.start(source, block.value(), radius);
        return 1;
    }

    @FunctionalInterface
    private interface VeinAction {
        int run(CommandSourceStack source, VeinCommands.Vein vein, int radius, int limit) throws CommandSyntaxException;
    }
}
