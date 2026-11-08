package rw.hostfix.devtest;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.momosoftworks.coldsweat.api.registry.TempModifierRegistry;
import com.momosoftworks.coldsweat.api.temperature.modifier.TempModifier;
import com.momosoftworks.coldsweat.core.init.TempModifierInit;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PortalProcessor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Portal;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.yazloysasha.tfcrealworld.config.TFCRealWorldConfig;

/**
 * Только для проверочного сервера: /rwtest clear — стирание настроек карты при работающем сервере;
 * /rwtest mixin — применился ли миксин к TFCRealWorldConfig и какие размеры карты
 * видит сервер; /rwtest portal|command <x> <z> — переход «ад → обычный мир» тестового игрока (без клиента)
 * через портал или как командой: отменён ли он и где игрок оказался через несколько тиков; /rwtest coldsweat —
 * перестройка реестра модификаторов Cold Sweat не из потока сервера пропускается, гонки с тиком температуры нет.
 */
@Mod("rw_host_fix_devtest")
public final class RwHostFixDevtest {
    public RwHostFixDevtest() {
        NeoForge.EVENT_BUS.addListener(RwHostFixDevtest::register);
    }

    private static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("rwtest")
                .then(Commands.literal("mixin").executes(RwHostFixDevtest::mixin))
                .then(Commands.literal("clear").executes(RwHostFixDevtest::clear))
                .then(Commands.literal("coldsweat").executes(RwHostFixDevtest::coldSweat))
                .then(Commands.literal("portal").then(Commands.argument("x", IntegerArgumentType.integer())
                        .then(Commands.argument("z", IntegerArgumentType.integer()).executes(c -> travel(c, true)))))
                .then(Commands.literal("command").then(Commands.argument("x", IntegerArgumentType.integer())
                        .then(Commands.argument("z", IntegerArgumentType.integer()).executes(c -> travel(c, false))))));
    }

    private static int mixin(CommandContext<CommandSourceStack> c) {
        String injected = String.join(", ", Arrays.stream(TFCRealWorldConfig.class.getDeclaredMethods())
                .map(m -> m.getName()).filter(n -> n.contains("rwHostFix")).toList());
        say(c, "миксин: " + (injected.isEmpty() ? "НЕ ПРИМЕНЁН" : injected));
        say(c, "карта: horizontal_scale=" + TFCRealWorldConfig.HORIZONTAL_SCALE.get() + " vertical_scale="
                + TFCRealWorldConfig.VERTICAL_SCALE.get());
        return 1;
    }

    /** То же, что делает TFC Real World при выгрузке мира на клиенте хоста: при работающем сервере не должно стереть */
    /**
     * Cold Sweat: перестройка реестра модификаторов из потока сервера (как ServerAboutToStartEvent) должна пройти,
     * из другого потока (как клиентский вход хоста) — быть пропущена. Затем гонка: другой поток 300 раз перестраивает
     * реестр, а поток сервера всё это время обходит его, как тик температуры (TempModifierRegistry.getHolder).
     * С исправлением ошибок 0; с -Drw_host_fix.coldsweat.disabled=true — ConcurrentModificationException.
     */
    private static int coldSweat(CommandContext<CommandSourceStack> c) {
        boolean injected = Arrays.stream(TempModifierInit.class.getDeclaredMethods())
                .anyMatch(m -> m.getName().contains("rwHostFix"));
        say(c, "Cold Sweat миксин: " + (injected ? "применён" : "НЕ ПРИМЕНЁН"));
        var before = TempModifierRegistry.getEntries();
        TempModifierInit.buildModifierRegistries();
        var serverBuilt = TempModifierRegistry.getEntries();
        say(c, "модификаторов: " + before.size() + ", после перестройки в потоке сервера: " + serverBuilt.size()
                + (holdersSame(before, serverBuilt) ? " (НЕ перестроен)" : " (перестроен)"));
        Thread other = new Thread(TempModifierInit::buildModifierRegistries, "rwtest-client");
        other.start();
        join(other);
        var otherBuilt = TempModifierRegistry.getEntries();
        say(c, "после вызова из другого потока: " + otherBuilt.size()
                + (holdersSame(serverBuilt, otherBuilt) ? " (пропущено)" : " (ПЕРЕСТРОЕН)"));

        TempModifier probe = TempModifierRegistry.getValue(ResourceLocation.fromNamespaceAndPath("cold_sweat", "simple"))
                .orElseThrow();
        AtomicBoolean done = new AtomicBoolean();
        AtomicInteger otherErrors = new AtomicInteger();
        Thread racer = new Thread(() -> {
            for (int i = 0; i < 300; i++) {
                try {
                    TempModifierInit.buildModifierRegistries();
                } catch (RuntimeException e) {
                    otherErrors.incrementAndGet();
                }
            }
            done.set(true);
        }, "rwtest-client-race");
        int reads = 0, errors = 0, missing = 0;
        String first = null;
        racer.start();
        while (!done.get()) {
            try {
                if (TempModifierRegistry.getHolder(probe) == null) missing++;
            } catch (RuntimeException e) {
                errors++;
                if (first == null) first = e.getClass().getSimpleName();
            }
            reads++;
        }
        join(racer);
        say(c, "гонка: обходов " + reads + ", ошибок " + errors + (first == null ? "" : " (" + first + ")")
                + ", модификатор не найден " + missing + ", ошибок в другом потоке " + otherErrors.get()
                + ", модификаторов в конце: " + TempModifierRegistry.getEntries().size());
        return 1;
    }

    private static void join(Thread thread) {
        try {
            thread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static boolean holdersSame(Map<ResourceLocation, TempModifierRegistry.TempModifierHolder> a,
            Map<ResourceLocation, TempModifierRegistry.TempModifierHolder> b) {
        return a.size() == b.size() && a.entrySet().stream().allMatch(e -> b.get(e.getKey()) == e.getValue());
    }

    private static int clear(CommandContext<CommandSourceStack> c) {
        TFCRealWorldConfig.clearServerConfig();
        say(c, "после clearServerConfig: horizontal_scale=" + TFCRealWorldConfig.HORIZONTAL_SCALE.get()
                + " vertical_scale=" + TFCRealWorldConfig.VERTICAL_SCALE.get());
        return 1;
    }

    private static int travel(CommandContext<CommandSourceStack> c, boolean portal) {
        int x = IntegerArgumentType.getInteger(c, "x");
        int z = IntegerArgumentType.getInteger(c, "z");
        MinecraftServer server = c.getSource().getServer();
        ServerLevel nether = server.getLevel(Level.NETHER);
        FakePlayer player = FakePlayerFactory.get(nether, new GameProfile(UUID.randomUUID(), "rwtest"));
        player.moveTo(x + 0.5, 70, z + 0.5, 0, 0);
        player.portalProcess = portal ? new PortalProcessor((Portal) Blocks.NETHER_PORTAL, player.blockPosition()) : null;
        EntityTravelToDimensionEvent event = new EntityTravelToDimensionEvent(player, Level.OVERWORLD);
        NeoForge.EVENT_BUS.post(event);
        say(c, (portal ? "портал" : "команда") + " из ада " + x + " " + z + ": переход "
                + (event.isCanceled() ? "отменён" : "разрешён"));
        // ответ RCON к этому времени уже отправлен — пишем в лог сервера
        server.tell(new TickTask(server.getTickCount() + 5, () -> System.out.println("[rwtest] " + x + " " + z
                + " через 5 тиков: " + player.level().dimension().location() + " " + player.blockPosition().toShortString())));
        return 1;
    }

    private static void say(CommandContext<CommandSourceStack> c, String text) {
        c.getSource().sendSystemMessage(Component.literal("[rwtest] " + text));
    }
}
