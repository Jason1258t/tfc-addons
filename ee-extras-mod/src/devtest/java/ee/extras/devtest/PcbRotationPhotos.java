package ee.extras.devtest;
import ee.extras.pcb.*;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
/** Демонстрация, реверс и настоящий оконный ввод редактора. */
final class PcbRotationPhotos {
 static int phase;static BlockPos board,table;
 static void tick(Minecraft mc,MinecraftServer server){if(phase==0&&mc.screen!=null)return;int t=++phase;
 if(t%20==0)server.execute(()->p(mc,server).removeAllEffects());
 if(t%10==0)mc.gui.getChat().clearMessages(false);
 if(t==20){mc.options.guiScale().set(2);mc.resizeDisplay();}
 if(t==40)server.execute(()->{var p=p(mc,server);board=p.blockPosition().atY(300).east(32);table=board.west(3);PcbBridgeTest.BOARD=board;cmd(p,"pcbrotation demo");p.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,ItemStack.EMPTY);p.teleportTo(p.serverLevel(),board.getX()+.5,board.getY()-.7,board.getZ()+.8,180,70);});
 if(t==260)server.execute(()->{cmd(p(mc,server),"pcbrotation check 128");cmd(p(mc,server),"pcbsensor lit");});
 if(t==280){mc.options.hideGui=true;photo(mc,"positive");}
 if(t==300)server.execute(()->cmd(p(mc,server),"pcbrotation speed -128"));
 if(t==360)server.execute(()->{cmd(p(mc,server),"pcbrotation check -128");cmd(p(mc,server),"pcbsensor unlit");});
 if(t==380)photo(mc,"reverse");
 if(t==400)server.execute(()->{var p=p(mc,server);cmd(p,"pcbrotation speed 0");p.teleportTo(p.serverLevel(),board.getX()+2,board.getY()-1.8,board.getZ()+2,135,0);});
 if(t==460)server.execute(()->cmd(p(mc,server),"pcbrotation check 0"));
 if(t==480)photo(mc,"shaft-motor");
 if(t==500)server.execute(()->{var p=p(mc,server);cmd(p,"pcbrotation speed 128");DevTest.place(p.serverLevel(),table,PcbItems.EDITOR_BLOCK.get().defaultBlockState());var e=(PcbEditorBlockEntity)p.serverLevel().getBlockEntity(table);var stack=PcbItems.PCB_DESIGN.toStack();PcbTopology.attach(stack,PcbSensorTest.topology(false));e.setItem(0,stack);e.loadDesign();p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);p.getAbilities().flying=true;p.onUpdateAbilities();p.teleportTo(p.serverLevel(),table.getX()+.5,table.getY()+1,table.getZ()+2.5,180,20);});
 if(t==540)server.execute(()->((PcbEditorBlockEntity)p(mc,server).serverLevel().getBlockEntity(table)).open(p(mc,server)));
 if(t==580){mc.options.hideGui=false;ClientTest.check("редактор демонстрации открыт",mc.screen instanceof PcbEditorScreen);photo(mc,"demo-editor");}
 if(t==600){mc.setScreen(null);server.execute(()->{var e=(PcbEditorBlockEntity)p(mc,server).serverLevel().getBlockEntity(table);var stack=PcbItems.PCB_DESIGN.toStack();PcbTopology.attach(stack,PcbTopology.create(2));e.setItem(0,stack);e.loadDesign();});}
 if(t==640)server.execute(()->((PcbEditorBlockEntity)p(mc,server).serverLevel().getBlockEntity(table)).open(p(mc,server)));
 if(t==680)key(java.awt.event.KeyEvent.VK_K,false);
 if(t==700&&mc.screen instanceof PcbEditorScreen s){ClientTest.check("K открыла каталог",(boolean)field(s,"catalogOpen"));((net.minecraft.client.gui.components.EditBox)field(s,"catalogSearch")).setValue("вращения");java.util.List<Integer> list=PcbModulesTest.call(PcbEditorScreen.class,s,"filteredParts");ClientTest.check("в палитре датчик вращения",list.equals(java.util.List.of(33)));}
 if(t==720)photo(mc,"palette");
 if(t==740)click(mc,145,70);
 if(t==770&&mc.screen instanceof PcbEditorScreen s){int part=PcbModulesTest.call(PcbEditorMenu.class,s.getMenu(),"part");ClientTest.check("датчик выбран мышью",part==33);click(mc,s.cellScreenX(5),s.cellScreenY(5));}
 if(t==810)server.execute(()->cell(p(mc,server),33));
 if(t==830)photo(mc,"placed");
 if(t==850)key(java.awt.event.KeyEvent.VK_Z,true);
 if(t==880)server.execute(()->cell(p(mc,server),-1));
 if(t==900&&mc.screen instanceof PcbEditorScreen s)click(mc,s.width-169,18);
 if(t==940)server.execute(()->cell(p(mc,server),33));
 if(t==960)photo(mc,"restored");
 if(t==1000)server.execute(()->{cmd(p(mc,server),"pcbrotation check 128");PcbBridgeTest.export(p(mc,server).createCommandSourceStack());System.out.println("EECLIENT PCB ROTATION DONE");});
 }
 static void cell(ServerPlayer p,int wanted){var e=(PcbEditorBlockEntity)p.serverLevel().getBlockEntity(table);int[] parts=e.topology().getIntArray("ElectricalParts");int def=parts.length>85?parts[85]:0;int actual=def==0?-1:PcbModulesTest.call(PcbBridgeTest.wiring(),null,"type",def);ClientTest.check("монтаж/история датчика: "+wanted,actual==wanted);}
 static void cmd(ServerPlayer p,String text){try{ClientTest.check(text,p.server.getCommands().getDispatcher().execute(text,p.createCommandSourceStack())>0);}catch(Exception e){ClientTest.check(text+e,false);throw new IllegalStateException(e);}}
 static ServerPlayer p(Minecraft mc,MinecraftServer s){return s.getPlayerList().getPlayer(mc.player.getUUID());}
 static Object field(Object o,String n){try{var f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}catch(Exception e){throw new IllegalStateException(e);}}
 static void click(Minecraft mc,int x,int y){PcbModulesTest.call(PcbModulePhotos.class,null,"nativeClick",mc,x,y);}
 static void key(int k,boolean ctrl){try{var r=new java.awt.Robot();if(ctrl)r.keyPress(java.awt.event.KeyEvent.VK_CONTROL);r.keyPress(k);r.keyRelease(k);if(ctrl)r.keyRelease(java.awt.event.KeyEvent.VK_CONTROL);}catch(Exception e){throw new IllegalStateException(e);}}
 static void photo(Minecraft mc,String n){Screenshot.grab(mc.gameDirectory,"pcb-rotation-"+n+".png",mc.getMainRenderTarget(),m->{});}
}
