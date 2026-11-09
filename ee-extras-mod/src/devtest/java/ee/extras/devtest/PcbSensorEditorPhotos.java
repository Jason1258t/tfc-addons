package ee.extras.devtest;

import ee.extras.pcb.*;
import java.nio.file.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Оконный ввод: открыть каталог, выбрать оба датчика, разместить, отменить и вернуть. */
final class PcbSensorEditorPhotos {
    private static int phase;
    private static BlockPos temperature,light,table;
    static void tick(Minecraft mc,MinecraftServer server){
        if(phase==0&&mc.screen!=null)return;int tick=++phase;
        if(tick%20==0)server.execute(()->player(mc,server).removeAllEffects());
        if(tick%10==0)mc.gui.getChat().clearMessages(false);
        if(tick==20){mc.options.guiScale().set(2);mc.resizeDisplay();}
        if(tick==40)server.execute(()->{try{String[] a=Files.readString(Path.of("pcb-sensor-editor-photos")).trim().split("\\s+");temperature=new BlockPos(Integer.parseInt(a[0]),Integer.parseInt(a[1]),Integer.parseInt(a[2]));light=temperature.east(16);table=light.west(6);
            var p=player(mc,server);DevTest.place(p.serverLevel(),table,PcbItems.EDITOR_BLOCK.get().defaultBlockState());var editor=(PcbEditorBlockEntity)p.serverLevel().getBlockEntity(table);var stack=PcbItems.PCB_DESIGN.toStack();PcbTopology.attach(stack,PcbTopology.create(2));editor.setItem(0,stack);editor.loadDesign();p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,ItemStack.EMPTY);p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);p.getAbilities().flying=true;p.onUpdateAbilities();p.teleportTo(p.serverLevel(),table.getX()+.5,table.getY()+1,table.getZ()+2.5,180,20);
        }catch(Exception e){throw new IllegalStateException(e);}});
        if(tick==80)server.execute(()->((PcbEditorBlockEntity)player(mc,server).serverLevel().getBlockEntity(table)).open(player(mc,server)));
        if(tick==110){ClientTest.check("редактор датчиков открыт с широкой палитрой",mc.screen instanceof PcbEditorScreen s&&(boolean)field(s,"sidePalette"));key(java.awt.event.KeyEvent.VK_K,false);}
        if(tick==130&&mc.screen instanceof PcbEditorScreen s){ClientTest.check("нативнаяK открыла каталог",(boolean)field(s,"catalogOpen"));search(s);}
        if(tick==150)photo(mc,"palette-open");
        if(tick==160)click(mc,145,70);
        if(tick==185&&mc.screen instanceof PcbEditorScreen s){ClientTest.check("нативным кликом выбран термодатчик",part(s)==31);}
        if(tick==190&&mc.screen instanceof PcbEditorScreen s)click(mc,s.cellScreenX(5),s.cellScreenY(5));
        if(tick==215)server.execute(()->checkCell(player(mc,server),85,31,"термодатчик размещён оконным вводом"));
        if(tick==220)key(java.awt.event.KeyEvent.VK_K,false);
        if(tick==240&&mc.screen instanceof PcbEditorScreen s)search(s);
        if(tick==245)click(mc,145,104);
        if(tick==270&&mc.screen instanceof PcbEditorScreen s){ClientTest.check("нативным кликом выбран датчик света",part(s)==32);click(mc,s.cellScreenX(10),s.cellScreenY(5));}
        if(tick==295)server.execute(()->checkCell(player(mc,server),90,32,"датчик света размещён оконным вводом"));
        if(tick==305)photo(mc,"two-sensors-native");
        if(tick==310)key(java.awt.event.KeyEvent.VK_Z,true);
        if(tick==335)server.execute(()->checkCell(player(mc,server),90,-1,"CtrlZ отменил датчик света"));
        if(tick==340&&mc.screen instanceof PcbEditorScreen s)click(mc,s.width-169,18);
        if(tick==365)server.execute(()->{checkCell(player(mc,server),85,31,"термодатчик сохранился");checkCell(player(mc,server),90,32,"кнопка Повтор вернула датчик света");});
        if(tick==375)photo(mc,"two-sensors-restored");
        if(tick==400)server.execute(()->{var p=player(mc,server);PcbBridgeTest.BOARD=temperature;PcbBridgeTest.BATTERY=temperature.east(8);p.server.getCommands().performPrefixedCommand(p.createCommandSourceStack(),"pcbsensor threshold 50");});
        if(tick==440)server.execute(()->{PcbBridgeTest.export(player(mc,server).createCommandSourceStack());System.out.println("EECLIENT PCB SENSOR EDITOR DONE");});
    }
    private static int part(PcbEditorScreen s){return PcbModulesTest.call(PcbEditorMenu.class,s.getMenu(),"part");}
    private static Object field(Object o,String name){try{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(Exception e){throw new IllegalStateException(e);}}
    private static void search(PcbEditorScreen s){((net.minecraft.client.gui.components.EditBox)field(s,"catalogSearch")).setValue("Датчик");java.util.List<Integer> parts=PcbModulesTest.call(PcbEditorScreen.class,s,"filteredParts");ClientTest.check("в каталоге оба датчикаL2",parts.equals(java.util.List.of(31,32)));}
    private static void checkCell(ServerPlayer p,int cell,int type,String label){var e=(PcbEditorBlockEntity)p.serverLevel().getBlockEntity(table);int def=e.topology().getIntArray("ElectricalParts")[cell];int actual=def==0?-1:PcbModulesTest.call(PcbBridgeTest.wiring(),null,"type",def);ClientTest.check(label,actual==type);}
    private static ServerPlayer player(Minecraft mc,MinecraftServer s){return s.getPlayerList().getPlayer(mc.player.getUUID());}
    private static void click(Minecraft mc,int x,int y){PcbModulesTest.call(PcbModulePhotos.class,null,"nativeClick",mc,x,y);}
    private static void key(int code,boolean ctrl){try{var r=new java.awt.Robot();if(ctrl)r.keyPress(java.awt.event.KeyEvent.VK_CONTROL);r.keyPress(code);r.keyRelease(code);if(ctrl)r.keyRelease(java.awt.event.KeyEvent.VK_CONTROL);}catch(Exception e){throw new IllegalStateException(e);}}
    private static void photo(Minecraft mc,String name){Screenshot.grab(mc.gameDirectory,"pcb-sensor-editor-"+name+".png",mc.getMainRenderTarget(),m->{});}
}
