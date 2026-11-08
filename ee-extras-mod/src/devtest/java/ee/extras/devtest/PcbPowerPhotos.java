package ee.extras.devtest;

import com.george_vi.electroenergetics.CEEDataComponents;
import ee.extras.EeExtras;
import ee.extras.cable.CableEnd;
import ee.extras.pcb.PcbCircuitBlock;
import ee.extras.pcb.PcbPowerInlet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Настоящие клиентские пакеты двух кликов катушкой, измерения сервера и кадры ввода на плате. */
final class PcbPowerPhotos {
    private static int phase;
    private PcbPowerPhotos() {}

    static void tick(Minecraft mc,MinecraftServer server,int elapsed) {
        if (phase == 0 && mc.screen != null) return;
        int tick = ++phase;
        if (tick % 20 == 0) server.execute(() -> player(mc,server).removeAllEffects());
        if (tick % 10 == 0) mc.gui.getChat().clearMessages(false);
        if (tick == 40) server.execute(() -> {
            var player=player(mc,server);
            var pos=player.blockPosition().atY(300);
            var calendar=net.dries007.tfc.util.calendar.Calendars.SERVER;
            calendar.setCalendarTickRate(0);
            calendar.skipForwardBy(Math.floorMod(12000-Math.floorMod(calendar.getCalendarTicks(),24000),24000));
            player.setGameMode(GameType.CREATIVE);player.getAbilities().flying=true;player.onUpdateAbilities();
            PcbOnboardTest.buildAt(player.createCommandSourceStack(),pos,false);
            player.setItemInHand(InteractionHand.MAIN_HAND,EeExtras.THIN_SPOOL.toStack());
            camera(player,inlet(player),1.1,false);
        });
        if (tick == 80) {
            ClientTest.check("катушка клиента видит бортовой ввод", CableEnd.of(mc.level,PcbOnboardTest.BOARD,mc.level.getBlockState(PcbOnboardTest.BOARD))!=null);
            var block=(PcbCircuitBlock)mc.level.getBlockState(PcbOnboardTest.BOARD).getBlock();
            Vec3 point=block.getNodePosition(mc.level,PcbOnboardTest.BOARD,mc.level.getBlockState(PcbOnboardTest.BOARD),PcbPowerInlet.BASE)
                    .add(Vec3.atLowerCornerOf(PcbOnboardTest.BOARD));
            mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(point,Direction.UP,PcbOnboardTest.BOARD,false));
        }
        if (tick == 100) server.execute(() -> {
            var item=player(mc,server).getMainHandItem();
            var selected=item.get(CEEDataComponents.SELECTED_WIRE_TERMINATION);
            ClientTest.check("клик корпуса выбрал кабельный ввод, не одиночную жилу", selected!=null && selected.pos().equals(PcbOnboardTest.BOARD) && !item.has(CEEDataComponents.SELECTED_NODE));
        });
        if (tick == 105) photo(mc,"01-selected-inlet");
        if (tick == 110) server.execute(() -> {
            var player=player(mc,server);var end=CableEnd.of(player.serverLevel(),PcbOnboardTest.INPUT,player.serverLevel().getBlockState(PcbOnboardTest.INPUT));
            camera(player,end.cable().getPosition(player.serverLevel()),1.1,false);
        });
        if (tick == 140) mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(PcbOnboardTest.INPUT),Direction.UP,PcbOnboardTest.INPUT,false));
        if (tick == 170) server.execute(() -> {
            var player=player(mc,server);
            expect(player,11.718,"второй клиентский клик соединил и запитал плату");
            player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
            camera(player,inlet(player),.65,true);
        });
        if (tick == 210) { mc.options.hideGui=true;photo(mc,"02-powered-board"); }
        if (tick == 230) server.execute(() -> PcbOnboardTest.sourcePower(player(mc,server).createCommandSourceStack(),300,false));
        if (tick == 260) server.execute(() -> expect(player(mc,server),0,"DC не питает вторичку бортового трансформатора"));
        if (tick == 280) photo(mc,"03-dc-blocked");
        if (tick == 300) System.out.println("EECLIENT PCB POWER PHOTOS DONE");
    }

    private static void expect(ServerPlayer player,double volts,String label) {
        try { PcbOnboardTest.expect(player.createCommandSourceStack(),volts);ClientTest.check(label,true); }
        catch (RuntimeException error) { ClientTest.check(label+": "+error,false);error.printStackTrace(); }
    }

    private static ServerPlayer player(Minecraft mc,MinecraftServer server) { return server.getPlayerList().getPlayer(mc.player.getUUID()); }
    private static Vec3 inlet(ServerPlayer player) {
        var level=player.serverLevel();var pos=PcbOnboardTest.BOARD;var block=(PcbCircuitBlock)level.getBlockState(pos).getBlock();
        return block.getNodePosition(level,pos,level.getBlockState(pos),PcbPowerInlet.BASE).add(Vec3.atLowerCornerOf(pos));
    }
    private static void camera(ServerPlayer player,Vec3 target,double scale,boolean spectator) {
        player.setGameMode(spectator?GameType.SPECTATOR:GameType.CREATIVE);
        player.getAbilities().flying=true;player.onUpdateAbilities();
        Vec3 eye=target.add(.15*scale,.9*scale,1.1*scale),delta=target.subtract(eye);
        float yaw=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z));
        float pitch=(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z)));
        player.teleportTo(player.serverLevel(),eye.x,eye.y-player.getEyeHeight(),eye.z,yaw,pitch);
    }
    private static void photo(Minecraft mc,String name) {
        Screenshot.grab(mc.gameDirectory,"pcb-power-"+name+".png",mc.getMainRenderTarget(),message -> {});
    }
}
