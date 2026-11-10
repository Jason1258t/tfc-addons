package ee.extras.devtest;
import ee.extras.pcb.*;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
/** Две платы, девять проводов и настоящий оконный ввод редактора. */
final class PcbConnectorPhotos {
 static int phase;static BlockPos board,table;static long editorWait;static boolean originalWireBackend;static volatile long finishedAt;static final boolean BASIC=java.nio.file.Files.exists(java.nio.file.Path.of("pcb-connector-basic-renderer"));
 static void tick(Minecraft mc,MinecraftServer server){if(finishedAt!=0&&System.nanoTime()-finishedAt>15_000_000_000L){mc.stop();return;}if(phase==0&&mc.screen!=null)return;
 if((phase==579||phase==679)&&!(mc.screen instanceof PcbEditorScreen)){if(editorWait==0)editorWait=System.nanoTime();if(System.nanoTime()-editorWait<90_000_000_000L)return;ClientTest.check("редактор открылся за90с",false);}else editorWait=0;
 int t=++phase;
 if(t%20==0)server.execute(()->p(mc,server).removeAllEffects());
 if(t%10==0){mc.gui.getChat().clearMessages(false);mc.gui.setTitle(net.minecraft.network.chat.Component.empty());mc.gui.setSubtitle(net.minecraft.network.chat.Component.empty());}
 if(t==20){mc.options.guiScale().set(2);mc.options.renderDistance().set(4);mc.options.simulationDistance().set(4);mc.options.broadcastOptions();if(BASIC){com.george_vi.electroenergetics.config.CEEConfigs.client().disableFlywheelWireRendering.set(true);com.george_vi.electroenergetics.client.WireRenderer.recreateVisuals();}mc.resizeDisplay();}
 if(t==40)server.execute(()->{var p=p(mc,server);board=PcbConnectorTest.TX;table=board.south(3);p.serverLevel().setChunkForced(board.getX()>>4,board.getZ()>>4,true);p.serverLevel().setChunkForced(PcbConnectorTest.RX.getX()>>4,board.getZ()>>4,true);p.serverLevel().setDayTime(6000);p.serverLevel().setWeatherParameters(0,20000,false,false);p.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,ItemStack.EMPTY);p.teleportTo(p.serverLevel(),board.getX()+2,board.getY()+2.5,board.getZ()+3,180,65);});
 if(t==150)server.execute(()->{var player=p(mc,server);player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);for(int pin=0;pin<9;pin++){PcbConnectorTest.link(player.createCommandSourceStack(),pin,false);ClientTest.check("катушка: контакт "+(pin+1),DevTest.spoolWire(player,new ItemStack(ee.extras.EeExtras.THIN_SPOOL.get()),board,PcbConnector.node(PcbConnectorTest.SOCKET,pin),PcbConnectorTest.RX,PcbConnector.node(PcbConnectorTest.SOCKET,pin)));}player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,ItemStack.EMPTY);player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);});
 if(t==220)server.execute(()->{cmd(p(mc,server),"pcbconnector audit");cmd(p(mc,server),"pcbconnector mask 255");});
 if(t==280)server.execute(()->{cmd(p(mc,server),"pcbconnector expect 255");cmd(p(mc,server),"pcbconnector measure");});
 if(t==290)wireReport(mc);
 if(t==300){mc.options.hideGui=true;photo(mc,"both-boards");}
 if(t==305&&!BASIC){originalWireBackend=com.george_vi.electroenergetics.config.CEEConfigs.client().disableFlywheelWireRendering.get();com.george_vi.electroenergetics.config.CEEConfigs.client().disableFlywheelWireRendering.set(true);com.george_vi.electroenergetics.client.WireRenderer.recreateVisuals();}
 if(t==315&&!BASIC)photo(mc,"both-boards-basic-renderer");
 if(t==316&&!BASIC){com.george_vi.electroenergetics.config.CEEConfigs.client().disableFlywheelWireRendering.set(originalWireBackend);com.george_vi.electroenergetics.client.WireRenderer.recreateVisuals();}
 if(t==320)server.execute(()->{var p=p(mc,server);p.teleportTo(p.serverLevel(),board.getX()+.5,board.getY()-.7,board.getZ()+.8,180,70);});
 if(t==360)photo(mc,"transmitter");
 if(t==380)server.execute(()->{var p=p(mc,server);p.teleportTo(p.serverLevel(),board.getX()+3.5,board.getY()-.7,board.getZ()+.8,180,70);cmd(p,"pcbconnector mask 85");});
 if(t==420)server.execute(()->cmd(p(mc,server),"pcbconnector expect 85"));
 if(t==440)photo(mc,"receiver-85");
 if(t==460)server.execute(()->{var p=p(mc,server);p.teleportTo(p.serverLevel(),board.getX()+2,board.getY()+6,board.getZ()+8,180,40);cmd(p,"pcbconnector mask 255");});
 if(t==480)photo(mc,"separate-supplies");
 if(t==500)server.execute(()->{var p=p(mc,server);DevTest.place(p.serverLevel(),table,PcbItems.EDITOR_BLOCK.get().defaultBlockState());var e=(PcbEditorBlockEntity)p.serverLevel().getBlockEntity(table);var stack=PcbItems.PCB_DESIGN.toStack();PcbTopology.attach(stack,PcbConnectorTest.design(true));e.setItem(0,stack);e.loadDesign();p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);p.getAbilities().flying=true;p.onUpdateAbilities();p.teleportTo(p.serverLevel(),table.getX()+.5,table.getY()+1,table.getZ()+2.5,180,20);});
 if(t==540)server.execute(()->((PcbEditorBlockEntity)p(mc,server).serverLevel().getBlockEntity(table)).open(p(mc,server)));
 if(t==580){mc.options.hideGui=false;ClientTest.check("редактор демонстрации открыт",mc.screen instanceof PcbEditorScreen);if(mc.screen instanceof PcbEditorScreen screen){PcbEditorBlockEntity editor=PcbModulesTest.call(PcbEditorMenu.class,screen.getMenu(),"editor");int[] definitions=editor.topology().getIntArray("ElectricalParts");ClientTest.check("в редакторе17модулей демонстрации",java.util.Arrays.stream(definitions).filter(v->v!=0).count()==17);}photo(mc,"demo-editor");}
 if(t==600){mc.setScreen(null);server.execute(()->{var e=(PcbEditorBlockEntity)p(mc,server).serverLevel().getBlockEntity(table);var stack=PcbItems.PCB_DESIGN.toStack();PcbTopology.attach(stack,PcbTopology.create(2));e.setItem(0,stack);e.loadDesign();});}
 if(t==640)server.execute(()->((PcbEditorBlockEntity)p(mc,server).serverLevel().getBlockEntity(table)).open(p(mc,server)));
 if(t==680)key(java.awt.event.KeyEvent.VK_K,false);
 if(t==700&&mc.screen instanceof PcbEditorScreen s){ClientTest.check("K открыла каталог",(boolean)field(s,"catalogOpen"));((net.minecraft.client.gui.components.EditBox)field(s,"catalogSearch")).setValue("разъём");java.util.List<Integer> list=PcbModulesTest.call(PcbEditorScreen.class,s,"filteredParts");ClientTest.check("в палитре разъём",list.equals(java.util.List.of(35)));}
 if(t==720)photo(mc,"palette");
 if(t==740)click(mc,145,70);
 if(t==770&&mc.screen instanceof PcbEditorScreen s){int part=PcbModulesTest.call(PcbEditorMenu.class,s.getMenu(),"part");ClientTest.check("разъём выбран мышью",part==35);click(mc,s.cellScreenX(5),s.cellScreenY(5));}
 if(t==810)server.execute(()->cell(p(mc,server),35));
 if(t==830)photo(mc,"placed");
 if(t==850)key(java.awt.event.KeyEvent.VK_Z,true);
 if(t==880)server.execute(()->cell(p(mc,server),-1));
 if(t==900&&mc.screen instanceof PcbEditorScreen s)click(mc,s.width-169,18);
 if(t==940)server.execute(()->cell(p(mc,server),35));
 if(t==960)photo(mc,"restored");
 if(t==980)mc.setScreen(null);
 if(t==1000)server.execute(()->{var p=p(mc,server);cmd(p,"pcbconnector audit");var e=(PcbEditorBlockEntity)p.serverLevel().getBlockEntity(table);var stack=PcbItems.PCB_DESIGN.toStack();PcbTopology.attach(stack,PcbConnectorTest.design(true));e.setItem(0,stack);e.loadDesign();p.teleportTo(p.serverLevel(),board.getX()+2,board.getY()+2.5,board.getZ()+3,180,65);PcbBridgeTest.export(p.createCommandSourceStack());finishedAt=System.nanoTime();System.out.println("EECLIENT PCB CONNECTOR DONE");});
 }
 static void wireReport(Minecraft mc){var connections=com.george_vi.electroenergetics.client.WireRenderer.getAllConnections();int found=0;for(var pair:connections){var c=pair.getFirst();boolean ours=c.node1().sourcePos().equals(board)&&c.node2().sourcePos().equals(PcbConnectorTest.RX)||c.node2().sourcePos().equals(board)&&c.node1().sourcePos().equals(PcbConnectorTest.RX);if(!ours)continue;found++;System.out.println("EECLIENT WIRE "+c+" positions="+c.node1().getPosition(mc.level)+" / "+c.node2().getPosition(mc.level)+" loaded="+mc.level.isLoaded(c.node1().sourcePos())+" / "+mc.level.isLoaded(c.node2().sourcePos())+" model="+pair.getSecond().wireType().getModel().modelLocation());}ClientTest.check("клиент получил9проводов EE",found==9);PcbModulesTest.call(ee.extras.WireDiag.class,null,"report");}
 static void cell(ServerPlayer p,int wanted){var e=(PcbEditorBlockEntity)p.serverLevel().getBlockEntity(table);int[] parts=e.topology().getIntArray("ElectricalParts");int def=parts.length>85?parts[85]:0;int actual=def==0?-1:PcbModulesTest.call(PcbBridgeTest.wiring(),null,"type",def);ClientTest.check("монтаж/история разъёма: "+wanted,actual==wanted);}
 static void cmd(ServerPlayer p,String text){try{ClientTest.check(text,p.server.getCommands().getDispatcher().execute(text,p.createCommandSourceStack().withPermission(4))>0);}catch(Exception e){ClientTest.check(text+e,false);throw new IllegalStateException(e);}}
 static ServerPlayer p(Minecraft mc,MinecraftServer s){return s.getPlayerList().getPlayer(mc.player.getUUID());}
 static Object field(Object o,String n){try{var f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}catch(Exception e){throw new IllegalStateException(e);}}
 static void click(Minecraft mc,int x,int y){PcbModulesTest.call(PcbModulePhotos.class,null,"nativeClick",mc,x,y);}
 static void key(int k,boolean ctrl){try{var r=new java.awt.Robot();if(ctrl)r.keyPress(java.awt.event.KeyEvent.VK_CONTROL);r.keyPress(k);r.keyRelease(k);if(ctrl)r.keyRelease(java.awt.event.KeyEvent.VK_CONTROL);}catch(Exception e){throw new IllegalStateException(e);}}
 static void photo(Minecraft mc,String n){Screenshot.grab(mc.gameDirectory,"pcb-connector-"+n+".png",mc.getMainRenderTarget(),m->{});}
}
