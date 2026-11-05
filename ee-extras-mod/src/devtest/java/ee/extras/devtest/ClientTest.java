package ee.extras.devtest;

import blusunrize.immersiveengineering.api.IEProperties;
import blusunrize.immersiveengineering.common.blocks.metal.FloodlightBlockEntity;
import com.george_vi.electroenergetics.CEEItems;
import ee.extras.lantern.EeFloodlightBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** Настоящий клиент (test-client.sh): прожектор IE превращается кликом катушкой, состояние и блок-сущность доходят до клиента, снимки вида */
@EventBusSubscriber(modid = "ee_extras_devtest", value = Dist.CLIENT)
public final class ClientTest {
    private static boolean started;
    private static int tick, fails;
    /** Перед игроком: его кликаем настоящим клиентом */
    private static final BlockPos F3 = Flood.BAT.offset(11, 0, -3);

    static void check(String name, boolean ok) {
        if (!ok) fails++;
        System.out.println("EECLIENT " + (ok ? "OK " : "FAIL ") + name);
    }

    @SubscribeEvent
    public static void screen(ScreenEvent.Init.Post e) {
        if (started || !(e.getScreen() instanceof TitleScreen)) return;
        started = true;
        Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        var settings = new LevelSettings("eetest", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel("eetest", settings, new WorldOptions(1, false, false),
                reg -> reg.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), e.getScreen());
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getSingleplayerServer() == null) return;
        tick++;
        var server = mc.getSingleplayerServer();
        try {
            if (System.getenv("WIRE") != null) {
                WireClientTest.tick(mc, server, tick);
                return;
            }
            if (tick == 40) server.execute(() -> {
                var p = server.getPlayerList().getPlayer(mc.player.getUUID());
                p.teleportTo(p.serverLevel(), 11.5, -58, 84, 0f, 20f);
                p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(CEEItems.DUPLEX_WIRE_SPOOL.get(), 16));
                p.getAbilities().flying = true;
                p.onUpdateAbilities();
                p.serverLevel().setDayTime(18000);
                server.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
                Flood.build(p.createCommandSourceStack());
                DevTest.place(p.serverLevel(), F3, Flood.ie("floodlight").setValue(IEProperties.FACING_ALL, Direction.UP));
            });
            if (tick == 100) server.execute(() -> Flood.power(server.getPlayerList().getPlayer(mc.player.getUUID()).createCommandSourceStack(), 230));
            if (tick == 110) {
                check("прожектор F3 у клиента ещё прожектор IE", !(mc.level.getBlockState(F3).getBlock() instanceof EeFloodlightBlock));
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(F3), Direction.UP, F3, false));
            }
            if (tick == 200) {
                var f1 = mc.level.getBlockState(Flood.F1);
                check("F1 у клиента — блок сети EE", f1.getBlock() instanceof EeFloodlightBlock);
                check("F1 у клиента светит", f1.getBlock() instanceof EeFloodlightBlock && f1.getValue(IEProperties.ACTIVE));
                check("F1 блок-сущность у клиента, поворот дошёл", mc.level.getBlockEntity(Flood.F1) instanceof FloodlightBlockEntity be && be.rotX == 22.5f && be.rotY == 45f);
                check("F2 на стене у клиента светит", mc.level.getBlockState(Flood.F2).getBlock() instanceof EeFloodlightBlock && mc.level.getBlockState(Flood.F2).getValue(IEProperties.ACTIVE));
                check("F3 превращён кликом настоящего клиента", mc.level.getBlockState(F3).getBlock() instanceof EeFloodlightBlock);
                check("F3 на сервере — блок сети EE", server.overworld().getBlockState(F3).getBlock() instanceof EeFloodlightBlock);
                check("катушка выбрала F3", mc.player.getMainHandItem().has(com.george_vi.electroenergetics.CEEDataComponents.SELECTED_WIRE_TERMINATION));
                net.minecraft.client.Screenshot.grab(mc.gameDirectory, "floodlight_on.png", mc.getMainRenderTarget(), c -> {});
                server.execute(() -> Flood.power(server.getPlayerList().getPlayer(mc.player.getUUID()).createCommandSourceStack(), 0));
            }
            if (tick == 340) {
                check("без напряжения F1 у клиента погас", !mc.level.getBlockState(Flood.F1).getValue(IEProperties.ACTIVE));
                net.minecraft.client.Screenshot.grab(mc.gameDirectory, "floodlight_off.png", mc.getMainRenderTarget(), c -> {});
            }
            PcbClientTest.tick(mc, server, tick);
        } catch (Throwable t) {
            check("исключение: " + t, false);
            t.printStackTrace();
            mc.stop();
        }
    }
}
