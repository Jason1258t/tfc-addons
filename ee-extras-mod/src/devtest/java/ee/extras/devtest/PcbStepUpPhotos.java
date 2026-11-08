package ee.extras.devtest;

import ee.extras.pcb.PcbCircuitBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** Кадры повышающего модуля на шести гранях и проверка AC/DC в полном клиенте. */
final class PcbStepUpPhotos {
    private static int phase;
    static void tick(Minecraft mc,MinecraftServer server,int elapsed) {
        if (phase==0 && mc.screen!=null) return;
        int tick=++phase;
        if (tick%20==0) server.execute(()->player(mc,server).removeAllEffects());
        if (tick%10==0) mc.gui.getChat().clearMessages(false);
        if (tick==40) server.execute(()->{
            var p=player(mc,server);var calendar=net.dries007.tfc.util.calendar.Calendars.SERVER;
            calendar.setCalendarTickRate(0);calendar.skipForwardBy(Math.floorMod(12000-Math.floorMod(calendar.getCalendarTicks(),24000),24000));
            p.setGameMode(GameType.SPECTATOR);p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
            PcbStepUpTest.buildAt(p.createCommandSourceStack(),p.blockPosition().atY(300),"ac");camera(p,Direction.UP);
        });
        if (tick==100) server.execute(()->expect(player(mc,server),196.703,"ACпитает настоящую внешнюю нагрузку"));
        for (int i=0;i<Direction.values().length;i++) {
            var face=Direction.values()[i];int start=120+i*40;
            if (tick==start) server.execute(()->{
                var p=player(mc,server);var level=p.serverLevel();var pos=PcbStepUpTest.BOARD;
                level.setBlockAndUpdate(pos,level.getBlockState(pos).setValue(PcbCircuitBlock.FACING,face));camera(p,face);
            });
            if (tick==start+20) server.execute(()->expect(player(mc,server),196.703,"нагруженный выход на грани "+face.getName()));
            if (tick==start+25) {mc.options.hideGui=true;photo(mc,"face-"+face.getName());}
        }
        for (int pose=0;pose<8;pose++) {
            int variant=pose,start=380+pose*40;
            if (tick==start) server.execute(()->{
                var p=player(mc,server);PcbStepUpTest.build(p.createCommandSourceStack(),"ac",variant/2,variant%2!=0);camera(p,Direction.UP,variant/2,variant%2!=0);
            });
            if (tick==start+25) server.execute(()->expect(player(mc,server),196.703,"поворот/отражение ВВ-модуля "+variant));
            if (pose==3 && tick==start+30) photo(mc,"mirrored-rotation");
        }
        if (tick==740) server.execute(()->{
            var p=player(mc,server);PcbStepUpTest.buildAt(p.createCommandSourceStack(),PcbStepUpTest.BOARD,"dc");camera(p,Direction.UP);
        });
        if (tick==800) server.execute(()->expect(player(mc,server),0,"DCне повышается"));
        if (tick==810) photo(mc,"dc-blocked");
        if (tick==840) server.execute(()->{
            var p=player(mc,server);PcbStepUpTest.build(p.createCommandSourceStack(),"open");camera(p,Direction.UP);
        });
        if (tick==900) server.execute(()->expect(player(mc,server),235.292,"холостой ход с внешним вольтметром"));
        if (tick==920) System.out.println("EECLIENT PCB STEPUP PHOTOS DONE");
    }
    private static void expect(ServerPlayer p,double value,String name) {
        try {PcbStepUpTest.expect(p.createCommandSourceStack(),value);ClientTest.check(name,true);}
        catch(RuntimeException error){ClientTest.check(name+": "+error,false);error.printStackTrace();}
    }
    private static ServerPlayer player(Minecraft mc,MinecraftServer server){return server.getPlayerList().getPlayer(mc.player.getUUID());}
    private static void camera(ServerPlayer p,Direction face) { camera(p,face,0,false); }
    private static void camera(ServerPlayer p,Direction face,int rotation,boolean mirror) {
        // Отдельный непитаемый образец: силовые провода рабочей платы не закрывают корпус на кадре.
        var pos=PcbStepUpTest.BOARD.east(10);var stack=ee.extras.pcb.PcbItems.L1_BOARD.toStack();
        ee.extras.pcb.PcbTopology.attach(stack,PcbStepUpTest.topology(rotation,mirror));
        DevTest.place(p.serverLevel(),pos,ee.extras.pcb.PcbItems.CIRCUIT_BLOCK.get().defaultBlockState()
                .setValue(PcbCircuitBlock.CUSTOM,true).setValue(PcbCircuitBlock.FACING,face));
        PcbModulesTest.call(ee.extras.pcb.PcbCircuitBlockEntity.class,p.serverLevel().getBlockEntity(pos),"setBoard",stack);
        Vec3 point=PcbModulesTest.call(PcbCircuitBlock.class,null,"mountedPoint",new Vec3(6.5/16,.07,6.5/16),face);
        Vec3 target=Vec3.atLowerCornerOf(pos).add(point);
        Vec3 eye=target.add(Vec3.atLowerCornerOf(face.getNormal()).scale(.28)).add(.075,.075,.075),delta=target.subtract(eye);
        float yaw=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),pitch=(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z)));
        p.teleportTo(p.serverLevel(),eye.x,eye.y-p.getEyeHeight(),eye.z,yaw,pitch);
    }
    private static void photo(Minecraft mc,String name){Screenshot.grab(mc.gameDirectory,"pcb-stepup-"+name+".png",mc.getMainRenderTarget(),m->{});}
}
