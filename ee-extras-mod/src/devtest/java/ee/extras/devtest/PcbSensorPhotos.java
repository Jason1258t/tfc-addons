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

/** Два настоящих проекта от стабилизатора: показания, пороги и редактор в полном клиенте. */
final class PcbSensorPhotos {
    private static int phase;
    private static net.minecraft.core.BlockPos temperatureBoard,lightBoard,table;
    static void tick(Minecraft mc,MinecraftServer server,int elapsed){
        if(phase==0&&mc.screen!=null)return;int tick=++phase;
        if(tick%20==0)server.execute(()->player(mc,server).removeAllEffects());
        if(tick%10==0)mc.gui.getChat().clearMessages(false);
        if(tick==40)server.execute(()->{var p=player(mc,server);p.setGameMode(GameType.SPECTATOR);p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);temperatureBoard=p.blockPosition().atY(300);lightBoard=temperatureBoard.east(16);PcbBridgeTest.BOARD=temperatureBoard;PcbSensorTest.build(p.createCommandSourceStack(),true,true);camera(p,temperatureBoard,Direction.UP,1.2);});
        if(tick==220)server.execute(()->{command(player(mc,server),"pcbsensor threshold 50");verify(player(mc,server),"температура20°C/5В");});
        if(tick==260)server.execute(()->{command(player(mc,server),"pcbsensor unlit");command(player(mc,server),"pcbsensor heat 150");});
        if(tick==320)server.execute(()->{verify(player(mc,server),"температура выросла");command(player(mc,server),"pcbsensor lit");});
        if(tick==340){mc.options.hideGui=true;photo(mc,"temperature-hot");}
        if(tick==360)server.execute(()->{command(player(mc,server),"pcbsensor threshold 95");});
        if(tick==400)server.execute(()->{command(player(mc,server),"pcbsensor unlit");PcbBridgeTest.BOARD=lightBoard;PcbSensorTest.build(player(mc,server).createCommandSourceStack(),false,true);camera(player(mc,server),lightBoard,Direction.NORTH,1.4);});
        if(tick==600)server.execute(()->{command(player(mc,server),"pcbsensor threshold 50");verify(player(mc,server),"темнота/5В");command(player(mc,server),"pcbsensor unlit");command(player(mc,server),"pcbsensor bright");});
        if(tick==680)server.execute(()->{verify(player(mc,server),"освещение выросло");command(player(mc,server),"pcbsensor lit");});
        if(tick==690)server.execute(()->{var p=player(mc,server);for(int y=0;y<=2;y++)for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)if(y>0||x!=0||z!=0)DevTest.place(p.serverLevel(),lightBoard.offset(x,y,z),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());camera(p,lightBoard,Direction.UP,1.2);});
        if(tick==710)photo(mc,"light-bright");
        if(tick==720)server.execute(()->command(player(mc,server),"pcbsensor threshold 95"));
        if(tick==760)server.execute(()->{command(player(mc,server),"pcbsensor unlit");command(player(mc,server),"pcbsensor threshold 50");table=lightBoard.west(3);var p=player(mc,server);DevTest.place(p.serverLevel(),table,PcbItems.EDITOR_BLOCK.get().defaultBlockState());var editor=(PcbEditorBlockEntity)p.serverLevel().getBlockEntity(table);var stack=PcbItems.PCB_DESIGN.toStack();PcbTopology.attach(stack,PcbSensorTest.topology(false));editor.setItem(0,stack);editor.loadDesign();ClientTest.check("серверный проект:8модулей",java.util.Arrays.stream(editor.topology().getIntArray("ElectricalParts")).filter(v->v!=0).count()==8);p.setGameMode(GameType.CREATIVE);p.getAbilities().flying=true;p.onUpdateAbilities();p.teleportTo(p.serverLevel(),table.getX()+.5,table.getY()+1,table.getZ()+2.5,180,20);});
        if(tick==800){ClientTest.check("клиентский проект:8модулей",mc.level.getBlockEntity(table) instanceof PcbEditorBlockEntity editor&&java.util.Arrays.stream(editor.topology().getIntArray("ElectricalParts")).filter(v->v!=0).count()==8);server.execute(()->((PcbEditorBlockEntity)player(mc,server).serverLevel().getBlockEntity(table)).open(player(mc,server)));}
        if(tick==850){mc.options.hideGui=false;ClientTest.check("редактор открыт",mc.screen instanceof PcbEditorScreen);photo(mc,"editor");}
        if(tick==870&&mc.screen instanceof PcbEditorScreen screen){try{var f=PcbEditorScreen.class.getDeclaredField("catalogSearch");f.setAccessible(true);((net.minecraft.client.gui.components.EditBox)f.get(screen)).setValue("Датчик");}catch(Exception e){throw new IllegalStateException(e);}}
        if(tick==900)photo(mc,"sensors-palette");
        if(tick==920)server.execute(()->{PcbBridgeTest.export(player(mc,server).createCommandSourceStack());System.out.println("EECLIENT PCB SENSOR PHOTOS DONE");});
    }
    private static void command(ServerPlayer p,String cmd){try{int result=p.server.getCommands().getDispatcher().execute(cmd,p.createCommandSourceStack());ClientTest.check(cmd,result>0);}catch(Exception e){ClientTest.check(cmd+": "+e,false);throw new IllegalStateException(e);}}
    private static void verify(ServerPlayer p,String label){try{PcbSensorTest.report(p.createCommandSourceStack(),true);ClientTest.check(label,true);}catch(RuntimeException e){ClientTest.check(label+": "+e,false);e.printStackTrace();}}
    private static ServerPlayer player(Minecraft mc,MinecraftServer server){return server.getPlayerList().getPlayer(mc.player.getUUID());}
    private static void camera(ServerPlayer p,net.minecraft.core.BlockPos pos,Direction face,double distance){Vec3 local=new Vec3(.5,.08,.5),point=PcbModulesTest.call(PcbCircuitBlock.class,null,"mountedPoint",local,face);Vec3 target=Vec3.atLowerCornerOf(pos).add(point);Vec3 eye=target.add(Vec3.atLowerCornerOf(face.getNormal()).scale(distance)).add(.15,.15,.15),delta=target.subtract(eye);float yaw=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),pitch=(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z)));p.teleportTo(p.serverLevel(),eye.x,eye.y-p.getEyeHeight(),eye.z,yaw,pitch);}
    private static void photo(Minecraft mc,String name){Screenshot.grab(mc.gameDirectory,"pcb-sensor-"+name+".png",mc.getMainRenderTarget(),m->{});}
}
