package ee.extras.devtest;

import ee.extras.pcb.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** Полный клиент: рабочая демонстрация, реальные грани/позы и проект в редакторе. */
final class PcbRegulatorPhotos {
    private static int phase;
    private static net.minecraft.core.BlockPos table;
    static void tick(Minecraft mc,MinecraftServer server,int elapsed) {
        if(phase==0&&mc.screen!=null)return;int tick=++phase;
        if(tick%20==0)server.execute(()->player(mc,server).removeAllEffects());
        if(tick%10==0)mc.gui.getChat().clearMessages(false);
        if(tick==40)server.execute(()->{
            var p=player(mc,server);var calendar=net.dries007.tfc.util.calendar.Calendars.SERVER;calendar.setCalendarTickRate(0);calendar.skipForwardBy(Math.floorMod(12000-Math.floorMod(calendar.getCalendarTicks(),24000),24000));
            p.setGameMode(GameType.SPECTATOR);p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
            PcbBridgeTest.BOARD=p.blockPosition().atY(300);PcbRegulatorDemo.build(p.createCommandSourceStack(),true);camera(p,PcbBridgeTest.BOARD,Direction.UP,.9);
        });
        if(tick==200)server.execute(()->PcbRegulatorDemo.write(player(mc,server).createCommandSourceStack()));
        if(tick==250)server.execute(()->verify(player(mc,server),true,"регистр записал5 от трансформатора через мост и конденсатор"));
        if(tick==260){mc.options.hideGui=true;photo(mc,"01-demo-powered");}
        for(int face=0;face<6;face++){
            var facing=Direction.values()[face];int start=280+face*45;
            if(tick==start)server.execute(()->{var p=player(mc,server);var l=p.serverLevel();var pos=PcbBridgeTest.BOARD;l.setBlockAndUpdate(pos,l.getBlockState(pos).setValue(PcbCircuitBlock.FACING,facing));camera(p,pos,facing,.9);});
            if(tick==start+25)server.execute(()->verify(player(mc,server),true,"питание и слово5 на грани "+facing));
            if(tick==start+30)photo(mc,"face-"+facing.getName());
        }
        for(int pose=0;pose<8;pose++){
            int variant=pose,start=570+pose*50;
            if(tick==start)server.execute(()->{var p=player(mc,server);PcbBridgeTest.BATTERY=PcbBridgeTest.BOARD.east(4);PcbRegulatorTest.build(p.createCommandSourceStack(),"nominal",variant/2,variant%2!=0);camera(p,PcbBridgeTest.BOARD,Direction.UP,.8);});
            if(tick==start+40)server.execute(()->verify(player(mc,server),false,"стабилизатор: поворот/отражение "+variant));
            if(pose==3&&tick==start+45)photo(mc,"02-mirrored-bridge");
        }
        if(tick==1000)server.execute(()->{var p=player(mc,server);PcbRegulatorDemo.build(p.createCommandSourceStack(),true);camera(p,PcbBridgeTest.BOARD,Direction.UP,.9);});
        if(tick==1160)server.execute(()->PcbRegulatorDemo.write(player(mc,server).createCommandSourceStack()));
        if(tick==1180)server.execute(()->{var p=player(mc,server);p.setGameMode(GameType.CREATIVE);p.getAbilities().flying=true;p.onUpdateAbilities();p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);var at=regulatorHit(p.serverLevel());p.teleportTo(p.serverLevel(),at.x,at.y+.6,at.z+.8,180,60);});
        if(tick==1190){setOverlay(mc,null);var hit=regulatorHit(mc.level);mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new net.minecraft.world.phys.BlockHitResult(hit,Direction.UP,PcbBridgeTest.BOARD,false));}
        if(tick==1196){
            var message=overlay(mc);var matcher=message==null?null:java.util.regex.Pattern.compile("^Стабилизатор 5 В: выход (\\d+\\.\\d+)–(\\d+\\.\\d+) В").matcher(message);
            ClientTest.check("ПКМ по корпусу стабилизатора показал состояние: "+message,matcher!=null&&matcher.find()&&Math.abs(Double.parseDouble(matcher.group(1))-5)<.05&&Math.abs(Double.parseDouble(matcher.group(2))-5)<.05);
            photo(mc,"05-rmb-status");
        }
        if(tick==1200)server.execute(()->{
            var p=player(mc,server);verify(p,true,"финальная демонстрация");table=PcbBridgeTest.BOARD.west(3);DevTest.place(p.serverLevel(),table,PcbItems.EDITOR_BLOCK.get().defaultBlockState());
            var editor=(PcbEditorBlockEntity)p.serverLevel().getBlockEntity(table);var stack=PcbItems.PCB_DESIGN.toStack();PcbTopology.attach(stack,PcbRegulatorDemo.topology(true));editor.setItem(0,stack);editor.loadDesign();
            ClientTest.check("серверный проект содержит10модулей",java.util.Arrays.stream(editor.topology().getIntArray("ElectricalParts")).filter(v->v!=0).count()==10);
            p.setGameMode(GameType.CREATIVE);p.getAbilities().flying=true;p.onUpdateAbilities();p.teleportTo(p.serverLevel(),table.getX()+.5,table.getY()+1,table.getZ()+2.5,180,20);
        });
        if(tick==1240){
            ClientTest.check("проект дошёл до клиентского стола",mc.level.getBlockEntity(table) instanceof PcbEditorBlockEntity editor&&java.util.Arrays.stream(editor.topology().getIntArray("ElectricalParts")).filter(v->v!=0).count()==10);
            server.execute(()->((PcbEditorBlockEntity)player(mc,server).serverLevel().getBlockEntity(table)).open(player(mc,server)));
        }
        if(tick==1300){mc.options.hideGui=false;ClientTest.check("проект демонстрации открыт в редакторе",mc.screen instanceof PcbEditorScreen);photo(mc,"03-editor");}
        if(tick==1320&&mc.screen instanceof PcbEditorScreen screen)screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_H,0,0);
        if(tick==1340&&mc.screen instanceof PcbEditorScreen screen){
            try{var field=PcbEditorScreen.class.getDeclaredField("catalogSearch");field.setAccessible(true);((net.minecraft.client.gui.components.EditBox)field.get(screen)).setValue("Стабилизатор");}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}

        }
        if(tick==1360)photo(mc,"04-editor-bridge-search");
        if(tick==1380)server.execute(()->{PcbBridgeTest.export(player(mc,server).createCommandSourceStack());System.out.println("EECLIENT PCB REGULATOR PHOTOS DONE");});
    }
    private static void verify(ServerPlayer p,boolean demo,String label){try{if(demo)PcbRegulatorDemo.expect(p.createCommandSourceStack(),5,true);else{var m=PcbRegulatorTest.measurement(p.createCommandSourceStack());PcbRegulatorTest.balance(m);PcbBridgeTest.check(!PcbBridgeTest.snapshot(p.createCommandSourceStack()).getBoolean("Burnt")&&m.getDouble("MinOutput")>=4.97&&m.getDouble("MaxOutput")<=5.03,m.toString());}ClientTest.check(label,true);}catch(RuntimeException e){ClientTest.check(label+": "+e,false);e.printStackTrace();}}
    /** Центр корпуса 3×3 стабилизатора демонстрации (якорь 89) в мировых координатах. */
    private static Vec3 regulatorHit(net.minecraft.world.level.Level level){var pos=PcbBridgeTest.BOARD;int center=89+PcbTopology.SIZE+1;Vec3 local=new Vec3((center%PcbTopology.SIZE+.5)/PcbTopology.SIZE,.1,(center/PcbTopology.SIZE+.5)/PcbTopology.SIZE);Vec3 point=PcbModulesTest.call(PcbCircuitBlock.class,null,"mountedPoint",local,level.getBlockState(pos).getValue(PcbCircuitBlock.FACING));return Vec3.atLowerCornerOf(pos).add(point);}
    private static String overlay(Minecraft mc){try{var field=net.minecraft.client.gui.Gui.class.getDeclaredField("overlayMessageString");field.setAccessible(true);var text=(net.minecraft.network.chat.Component)field.get(mc.gui);return text==null?null:text.getString();}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
    private static void setOverlay(Minecraft mc,net.minecraft.network.chat.Component text){try{var field=net.minecraft.client.gui.Gui.class.getDeclaredField("overlayMessageString");field.setAccessible(true);field.set(mc.gui,text);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
    private static ServerPlayer player(Minecraft mc,MinecraftServer server){return server.getPlayerList().getPlayer(mc.player.getUUID());}
    private static void camera(ServerPlayer p,net.minecraft.core.BlockPos pos,Direction face,double distance){Vec3 local=new Vec3(.5,.08,.5),point=PcbModulesTest.call(PcbCircuitBlock.class,null,"mountedPoint",local,face);Vec3 target=Vec3.atLowerCornerOf(pos).add(point);Vec3 eye=target.add(Vec3.atLowerCornerOf(face.getNormal()).scale(distance)).add(.15,.15,.15),delta=target.subtract(eye);float yaw=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),pitch=(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z)));p.teleportTo(p.serverLevel(),eye.x,eye.y-p.getEyeHeight(),eye.z,yaw,pitch);}
    private static void photo(Minecraft mc,String name){Screenshot.grab(mc.gameDirectory,"pcb-regulator-"+name+".png",mc.getMainRenderTarget(),m->{});}
}
