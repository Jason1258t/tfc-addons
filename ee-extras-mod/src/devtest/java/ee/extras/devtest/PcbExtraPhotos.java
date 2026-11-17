package ee.extras.devtest;

import ee.extras.pcb.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Полный клиент: работающие модули и оконный ввод каталога, монтаж, отмена и повтор. */
@net.neoforged.fml.common.EventBusSubscriber(modid="ee_extras_devtest",value=net.neoforged.api.distmarker.Dist.CLIENT)
final class PcbExtraPhotos {
    private static int sounds, soundPackets;
    @net.neoforged.bus.api.SubscribeEvent public static void packet(net.neoforged.neoforge.event.PlayLevelSoundEvent.AtPosition e){if(e.getLevel().isClientSide&&e.getSound()!=null&&e.getSound().value().getLocation().toString().equals("minecraft:block.note_block.bell")){soundPackets++;System.out.println("PCB_EXTRA_CLIENT_SOUND "+soundPackets);}}
    @net.neoforged.bus.api.SubscribeEvent public static void sound(net.neoforged.neoforge.client.event.sound.PlaySoundEvent e){if(e.getSound()!=null&&e.getSound().getLocation().toString().equals("minecraft:block.note_block.bell"))sounds++;}
    private static final boolean SAVED = java.nio.file.Files.exists(java.nio.file.Path.of("pcb-saved-name"));
    private static final BlockPos SAVED_TABLE = new BlockPos(61, -60, 64);
    private static final String ORIGINAL_NAME = "Контроллер 5 В";
    private static final String NEW_NAME = "pcb 5v";
    private static java.awt.Robot robot;
    private static int phase;
    private static BlockPos base,table;
    private static final String[] NAMES={"Поворотная ручка","Зуммер","Индикатор 7","Логическая сборка"};
    private static final int[] CELLS={51,57,147,153};
    static void tick(Minecraft mc,MinecraftServer server){
        if(phase==0&&mc.screen!=null)return;int t=++phase;
        if (SAVED) {
            savedName(mc, server, t);
            return;
        }
        if(t%20==0)server.execute(()->player(mc,server).removeAllEffects());
        if(t%10==0)mc.gui.getChat().clearMessages(false);
        if(t==20){mc.options.guiScale().set(2);mc.options.fov().set(35);mc.resizeDisplay();}
        if(t==40)server.execute(()->{var p=player(mc,server);base=p.blockPosition();table=base.west(4);System.out.println("PCB_EXTRA_BASE "+base.toShortString());p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);p.getAbilities().flying=true;p.onUpdateAbilities();p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,ItemStack.EMPTY);
            for(int i=0;i<3;i++){PcbExtraTest.BOARD=base.south(12*i);PcbExtraTest.build(p.createCommandSourceStack(),36+i);}
            PcbSequentialTest.BOARD=base.south(36);PcbSequentialTest.SUPPLY=PcbSequentialTest.BOARD.east(4);ClientTest.check("физическая плата NAND собрана",PcbSequentialTest.build(p.createCommandSourceStack(),39)==1);
            DevTest.place(p.serverLevel(),table,PcbItems.EDITOR_BLOCK.get().defaultBlockState());var editor=(PcbEditorBlockEntity)p.serverLevel().getBlockEntity(table);var stack=PcbItems.PCB_DESIGN.toStack();PcbTopology.attach(stack,PcbTopology.create(2));editor.setItem(0,stack);editor.loadDesign();
        });
        for(int i=0;i<4;i++){int n=i;if(t==110+i*100){mc.options.hideGui=true;server.execute(()->camera(player(mc,server),base.south(12*n)));}if(t==150+i*100)photo(mc,"world-"+i);}
        if (t == 510) {
            mc.options.hideGui = false;
            server.execute(() -> {
                var player = player(mc, server);
                var standing = table.south(2);
                DevTest.place(player.serverLevel(), standing.below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                player.teleportTo(player.serverLevel(), standing.getX() + .5, standing.getY(), standing.getZ() + .5, 180, 20);
            });
        }
        if (t == 535) server.execute(() -> {
            var player = player(mc, server);
            ((PcbEditorBlockEntity) player.serverLevel().getBlockEntity(table)).open(player);
        });
        for(int i=0;i<4;i++){int n=i;int start=560+i*130;
            if(t==start){ClientTest.check("редактор каталога",mc.screen instanceof PcbEditorScreen);key(java.awt.event.KeyEvent.VK_K,false);}
            if(t==start+20&&mc.screen instanceof PcbEditorScreen s){ClientTest.check("нативнаяK открыла каталог",(boolean)field(s,"catalogOpen"));((net.minecraft.client.gui.components.EditBox)field(s,"catalogSearch")).setValue(NAMES[n]);java.util.List<Integer> parts=PcbModulesTest.call(PcbEditorScreen.class,s,"filteredParts");ClientTest.check("поиск "+NAMES[n],parts.equals(java.util.List.of(36+n)));}
            if(t==start+40)photo(mc,"palette-"+i);
            if(t==start+45)click(mc,145,70);
            if(t==start+65&&mc.screen instanceof PcbEditorScreen s){ClientTest.check("нативный выбор "+NAMES[n],(int)PcbModulesTest.call(PcbEditorMenu.class,s.getMenu(),"part")==36+n);click(mc,s.cellScreenX(CELLS[n]%16),s.cellScreenY(CELLS[n]/16));}
            if(t==start+95)server.execute(()->checkCell(player(mc,server),CELLS[n],36+n));
        }
        if(t==1100)photo(mc,"editor-four");
        if(t==1110)key(java.awt.event.KeyEvent.VK_Z,true);
        if(t==1135)server.execute(()->checkCell(player(mc,server),153,-1));
        if(t==1140&&mc.screen instanceof PcbEditorScreen s)click(mc,s.width-169,18);
        if(t==1165)server.execute(()->checkCell(player(mc,server),153,39));
        if(t==1175)photo(mc,"editor-redo");
        if(t==1190&&mc.screen instanceof PcbEditorScreen screen){var name=(net.minecraft.client.gui.components.EditBox)field(screen,"projectName");click(mc,name.getX()+3,name.getY()+3);}
        if(t==1198&&mc.screen instanceof PcbEditorScreen screen){var name=(net.minecraft.client.gui.components.EditBox)field(screen,"projectName");ClientTest.check("имя получило фокус нативным кликом",name.isFocused());mc.keyboardHandler.setClipboard("Плата контроля 5 В");key(java.awt.event.KeyEvent.VK_V,true);}
        if(t==1205)key(java.awt.event.KeyEvent.VK_ENTER,false);
        if(t==1230)server.execute(()->{var e=(PcbEditorBlockEntity)player(mc,server).serverLevel().getBlockEntity(table);ClientTest.check("имя принято реальным клиентским пакетом",e.topology().getString("ProjectName").equals("Плата контроля 5 В"));});
        if(t==1240)photo(mc,"project-name");
        if(t==1250)ClientTest.check("клиент получил событие звука зуммера (OpenAL не требуется)",soundPackets>0);
        if(t==1260)server.execute(()->{PcbBridgeTest.export(player(mc,server).createCommandSourceStack());System.out.println("EECLIENT PCB EXTRA DONE");});
    }
    private static void savedName(Minecraft mc, MinecraftServer server, int t) {
        if (t == 20) {
            mc.options.guiScale().set(2);
            mc.resizeDisplay();
            server.execute(() -> {
                var player = player(mc, server);
                player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                player.teleportTo(player.serverLevel(), 61.5, -60, 66.5, 180, 20);
            });
        }
        if (t == 140) {
            ClientTest.check("сохранённый стол загружен на клиенте",
                    mc.level.getBlockEntity(SAVED_TABLE) instanceof PcbEditorBlockEntity);
            server.execute(() -> {
                var player = player(mc, server);
                ((PcbEditorBlockEntity) player.serverLevel().getBlockEntity(SAVED_TABLE)).open(player);
            });
        }
        if (t == 180) {
            checkVisibleName(mc, ORIGINAL_NAME, "saved-name");
            if (mc.screen instanceof PcbEditorScreen screen) {
                var name = (net.minecraft.client.gui.components.EditBox) field(screen, "projectName");
                click(mc, name.getX() + 3, name.getY() + 3);
            }
        }
        if (t == 190) {
            key(java.awt.event.KeyEvent.VK_A, true);
        }
        if (t == 195) {
            for (int i = 0; i < NEW_NAME.length(); i++) {
                char character = NEW_NAME.charAt(i);
                key(java.awt.event.KeyEvent.getExtendedKeyCodeForChar(Character.toLowerCase(character)), false);
            }
        }
        if (t == 200) {
            ClientTest.check("нативный ввод имени до отправки", mc.screen instanceof PcbEditorScreen screen
                    && field(screen, "projectName") instanceof net.minecraft.client.gui.components.EditBox name
                    && name.isFocused() && NEW_NAME.equals(name.getValue()));
            key(java.awt.event.KeyEvent.VK_ENTER, false);
        }
        if (t == 230) server.execute(() -> checkServerName(mc, server, NEW_NAME));
        if (t == 330) server.execute(() -> checkServerName(mc, server, ORIGINAL_NAME));
        if (t == 430) server.execute(() -> checkServerName(mc, server, NEW_NAME));
        if (t == 300) {
            checkVisibleName(mc, NEW_NAME, "renamed");
            key(java.awt.event.KeyEvent.VK_Z, true);
        }
        if (t == 400) {
            checkVisibleName(mc, ORIGINAL_NAME, "undo-name");
            if (mc.screen instanceof PcbEditorScreen screen) click(mc, screen.width - 169, 18);
        }
        if (t == 500) checkVisibleName(mc, NEW_NAME, "redo-name");
        if (t == 520) System.out.println("EECLIENT PCB SAVED NAME DONE");
    }

    private static void checkServerName(Minecraft mc, MinecraftServer server, String expected) {
        var entity = (PcbEditorBlockEntity) player(mc, server).serverLevel().getBlockEntity(SAVED_TABLE);
        ClientTest.check("имя стола на сервере: " + expected,
                entity != null && expected.equals(entity.topology().getString("ProjectName")));
    }

    private static void checkVisibleName(Minecraft mc, String expected, String shot) {
        if (mc.screen instanceof PcbEditorScreen screen) {
            var field = (net.minecraft.client.gui.components.EditBox) field(screen, "projectName");
            net.minecraft.nbt.CompoundTag topology = PcbModulesTest.call(PcbEditorMenu.class, screen.getMenu(), "topology");
            System.out.println("PCB_NAME_STATE " + shot + " field=" + field.getValue() + " focus=" + field.isFocused()
                    + " menu=" + topology.getString("ProjectName"));
        }
        boolean visible = mc.screen instanceof PcbEditorScreen screen
                && ((net.minecraft.client.gui.components.EditBox) field(screen, "projectName")).getValue().equals(expected);
        ClientTest.check("имя в редакторе: " + shot, visible);
        photo(mc, shot);
    }

    private static void camera(ServerPlayer p,BlockPos board){Vec3 target=Vec3.atLowerCornerOf(board).add(.41,.1,.43),eye=target.add(.1,1.9,.7),d=target.subtract(eye);p.teleportTo(p.serverLevel(),eye.x,eye.y-p.getEyeHeight(),eye.z,(float)Math.toDegrees(Math.atan2(-d.x,d.z)),(float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z))));}
    private static ServerPlayer player(Minecraft mc,MinecraftServer s){return s.getPlayerList().getPlayer(mc.player.getUUID());}
    private static Object field(Object o,String n){try{var f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}catch(Exception e){throw new IllegalStateException(e);}}
    private static void checkCell(ServerPlayer p,int cell,int expected){var e=(PcbEditorBlockEntity)p.serverLevel().getBlockEntity(table);int[] parts=e.topology().getIntArray("ElectricalParts");int def=parts.length>cell?parts[cell]:0;int actual=def==0?-1:PcbModulesTest.call(PcbBridgeTest.wiring(),null,"type",def);ClientTest.check("модуль в клетке"+cell+"="+expected,actual==expected);}
    private static void click(Minecraft mc,int x,int y){PcbModulesTest.call(PcbModulePhotos.class,null,"nativeClick",mc,x,y);}
    private static void key(int code,boolean ctrl){try{if(robot==null)robot=new java.awt.Robot();var r=robot;if(ctrl)r.keyPress(java.awt.event.KeyEvent.VK_CONTROL);r.keyPress(code);r.keyRelease(code);if(ctrl)r.keyRelease(java.awt.event.KeyEvent.VK_CONTROL);}catch(Exception e){throw new IllegalStateException(e);}}
    private static void photo(Minecraft mc,String n){System.out.println("PCB_EXTRA_CAMERA "+n+" eye="+mc.player.getEyePosition()+" yaw="+mc.player.getYRot()+" pitch="+mc.player.getXRot());Screenshot.grab(mc.gameDirectory,"pcb-extra-"+n+".png",mc.getMainRenderTarget(),m->{});}
}
