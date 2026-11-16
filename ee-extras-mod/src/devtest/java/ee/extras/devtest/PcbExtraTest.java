package ee.extras.devtest;

import ee.extras.pcb.*;
import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.*;
import net.minecraft.commands.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import java.util.*;

/** Настоящие модули каталога, реальные выводы, источники и нагрузка без подмены результатов. */
final class PcbExtraTest {
    static BlockPos BOARD=new BlockPos(64,-60,64);
    private static BlockPos source(int i){return BOARD.offset(4+i*2,-2,4);}
    private static Class<?> wiring(){try{return Class.forName("ee.extras.pcb.PcbWiring");}catch(Exception e){throw new IllegalStateException(e);}}
    static PcbCircuitDevice device(ServerLevel l){return DevicesSavedData.load(l).getDevice(BOARD,PcbCircuitDevice.class);}
    static CompoundTag snapshot(ServerLevel l){var t=new CompoundTag();device(l).write(t);return t;}
    static void check(boolean ok,String msg){if(!ok){System.out.println("PCB_EXTRA_FAIL "+msg);throw new IllegalStateException("Модули каталога: "+msg);}}
    static void register(CommandDispatcher<CommandSourceStack> d){var r=Commands.literal("pcbextra").requires(s->s.hasPermission(2));
        for(int type=36;type<=38;type++){int t=type;r.then(Commands.literal(new String[]{"knob","buzzer","display"}[type-36]).executes(c->build(c.getSource(),t)));}
        r.then(Commands.literal("mask").then(Commands.argument("mask",IntegerArgumentType.integer(0,127)).executes(c->{mask(c.getSource().getLevel(),IntegerArgumentType.getInteger(c,"mask"));return 1;})));
        r.then(Commands.literal("volts").then(Commands.argument("volts",IntegerArgumentType.integer(-7,7)).executes(c->{power(c.getSource().getLevel(),0,IntegerArgumentType.getInteger(c,"volts"));return 1;})));
        r.then(Commands.literal("position").then(Commands.argument("value",IntegerArgumentType.integer(0,100)).executes(c->{check((boolean)PcbModulesTest.call(PcbCircuitDevice.class,device(c.getSource().getLevel()),"adjustComponent",0,IntegerArgumentType.getInteger(c,"value")),"поворот ручки");return 1;})));
        r.then(Commands.literal("meter").then(Commands.argument("volts",DoubleArgumentType.doubleArg(0,5)).executes(c->{var t=snapshot(c.getSource().getLevel());double v=t.getDouble("Meter");check(!t.getBoolean("Burnt")&&Math.abs(v-DoubleArgumentType.getDouble(c,"volts"))<.015,"напряжение движка "+v);c.getSource().sendSuccess(()->Component.literal("Ручка "+v+"В, "+t.getDouble("Watts")+"Вт"),false);return 1;})));
        r.then(Commands.literal("lit").then(Commands.argument("mask",IntegerArgumentType.integer(0,127)).executes(c->{var l=c.getSource().getLevel();var be=(PcbCircuitBlockEntity)l.getBlockEntity(BOARD);long actual=be.getUpdateTag(l.registryAccess()).getLong("LitIndicators");var t=snapshot(l);check(!t.getBoolean("Burnt")&&actual==IntegerArgumentType.getInteger(c,"mask"),"сегменты "+actual);c.getSource().sendSuccess(()->Component.literal("Сегменты "+actual+", реальная мощность "+t.getDouble("Watts")),false);return 1;})));
        r.then(Commands.literal("sound").then(Commands.argument("enabled",IntegerArgumentType.integer(0,1)).executes(c->{var l=c.getSource().getLevel();var be=(PcbCircuitBlockEntity)l.getBlockEntity(BOARD);long actual=be.getUpdateTag(l.registryAccess()).getLong("ControlStates");var t=snapshot(l);check(!t.getBoolean("Burnt")&&actual==IntegerArgumentType.getInteger(c,"enabled"),"зуммер "+actual);c.getSource().sendSuccess(()->Component.literal("Зуммер "+actual+", мощность "+t.getDouble("Watts")),false);return 1;})));
        for(boolean reverse:new boolean[]{false,true})r.then(Commands.literal(reverse?"reverse":"normal").executes(c->{var l=c.getSource().getLevel();var network=com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData.load(l);for(int a=0;a<2;a++)for(int b=0;b<2;b++)network.removeConnection(new com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection(new com.george_vi.electroenergetics.foundation.nodes.InWorldNode(a,source(0)),new com.george_vi.electroenergetics.foundation.nodes.InWorldNode(b,BOARD)));var p=net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(l);for(int a=0;a<2;a++)check(DevTest.spoolWire(p,ee.extras.EeExtras.THIN_SINGLE_SPOOL.toStack(),source(0),a,BOARD,reverse?a:1-a),"настоящая смена полярности");return 1;}));
        r.then(Commands.literal("geometry").executes(c->geometry(c.getSource())));
        r.then(Commands.literal("at").then(Commands.argument("pos",net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos()).executes(c->{BOARD=net.minecraft.commands.arguments.coordinates.BlockPosArgument.getLoadedBlockPos(c,"pos");check(device(c.getSource().getLevel())!=null,"сохранённая плата загружена");return 1;})));
        r.then(Commands.literal("load").then(Commands.argument("ohms",DoubleArgumentType.doubleArg(.01,1e9)).executes(c->{var l=c.getSource().getLevel();var t=snapshot(l);var parts=t.getList("Circuit",Tag.TAG_COMPOUND);for(int i=0;i<parts.size();i++)if(parts.getCompound(i).getString("Kind").equals("RESISTOR"))parts.getCompound(i).putDouble("Ohms",DoubleArgumentType.getDouble(c,"ohms"));device(l).read(t);return 1;})));
        r.then(Commands.literal("name").executes(c->name(c.getSource())));
        r.then(Commands.literal("export").executes(c->PcbBridgeTest.export(c.getSource())));
        r.then(Commands.literal("reload").executes(c->{var l=c.getSource().getLevel();device(l).read(snapshot(l));return 1;}));
        r.then(Commands.literal("prepare-restart").executes(c -> prepareRestart(c.getSource())));
        r.then(Commands.literal("check-saved-name").executes(c -> checkSavedName(c.getSource())));
        d.register(r);
    }
    private static final BlockPos RESTART_KNOB = new BlockPos(64, -60, 64);
    private static final String SAVED_NAME = "Контроллер 5 В";

    private static int prepareRestart(CommandSourceStack source) {
        for (int type = 36; type <= 38; type++) {
            BOARD = RESTART_KNOB.south((type - 36) * 32);
            build(source, type);
        }
        PcbSequentialTest.build(source, 39);
        BOARD = RESTART_KNOB;
        name(source);
        var level = source.getLevel();
        var editor = (PcbEditorBlockEntity) level.getBlockEntity(BOARD.west(3));
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);
        boolean instabuild = player.getAbilities().instabuild;
        try {
            player.getAbilities().instabuild = true;
            editor.setItem(0, PcbItems.L2_BOARD.toStack());
            String error = editor.saveDesign(player);
            check(error == null, "сборка именованной платы: " + error);
        } finally {
            player.getAbilities().instabuild = instabuild;
        }
        var position = BOARD.west(6);
        DevTest.place(level, position, PcbItems.CIRCUIT_BLOCK.get().defaultBlockState().setValue(PcbCircuitBlock.CUSTOM, true));
        PcbModulesTest.call(PcbCircuitBlockEntity.class, level.getBlockEntity(position), "setBoard", editor.getItem(0));
        return checkSavedName(source);
    }

    private static int checkSavedName(CommandSourceStack source) {
        var level = source.getLevel();
        var editor = (PcbEditorBlockEntity) level.getBlockEntity(RESTART_KNOB.west(3));
        check(editor != null && SAVED_NAME.equals(editor.topology().getString("ProjectName")), "имя в редакторе");
        check(SAVED_NAME.equals(editor.getItem(0).getHoverName().getString()), "имя на собранной плате в столе");
        var entity = (PcbCircuitBlockEntity) level.getBlockEntity(RESTART_KNOB.west(6));
        check(entity != null, "установленная плата");
        net.minecraft.world.item.ItemStack board = PcbModulesTest.call(PcbCircuitBlockEntity.class, entity, "board");
        var topology = board.get(PcbItems.PCB_TOPOLOGY.get());
        check(topology != null && SAVED_NAME.equals(topology.getString("ProjectName")), "имя в данных установленной платы");
        check(SAVED_NAME.equals(board.getHoverName().getString()), "имя предмета установленной платы");
        source.sendSuccess(() -> Component.literal("Имя редактора и установленной платы сохранено"), false);
        return 1;
    }

    static int geometry(CommandSourceStack s) {
        int checks=0;
        for(var face:net.minecraft.core.Direction.values())for(int rot=0;rot<4;rot++)for(boolean mirror:new boolean[]{false,true})for(int percent:new int[]{0,25,50,75,100}) {
            int def=PcbModulesTest.definition(36,rot);if(mirror)def=PcbModulesTest.call(wiring(),null,"mirror",def);
            double angle=Math.toRadians(-135+2.7*percent),dx=.35*Math.sin(angle),dz=-.35*Math.cos(angle);
            if(mirror)dx=-dx;for(int i=0;i<rot;i++){double next=-dz;dz=dx;dx=next;}
            var local=new net.minecraft.world.phys.Vec3((5+(dx+.5)*2)/16,.12,(5+(dz+.5)*2)/16);
            var mounted=PcbModulesTest.<net.minecraft.world.phys.Vec3>call(PcbCircuitBlock.class,null,"mountedPoint",local,face);
            var restored=PcbModulesTest.<net.minecraft.world.phys.Vec3>call(PcbCircuitBlock.class,null,"localPoint",mounted,face);
            int actual=PcbModulesTest.call(PcbCircuitBlock.class,null,"sliderPosition",restored,85,def);
            check(actual==percent,"угловое управление "+face+"/"+rot+"/"+mirror+": "+actual+" вместо"+percent);checks++;
        }
        int count=checks;s.sendSuccess(()->Component.literal("Ручка: "+count+" позиций на6гранях с поворотами/отражениями"),false);return 1;
    }
    static int name(CommandSourceStack s) {
        var l=s.getLevel();var at=BOARD.west(3);DevTest.place(l,at,PcbItems.EDITOR_BLOCK.get().defaultBlockState());
        var e=(PcbEditorBlockEntity)l.getBlockEntity(at);e.clear(2);check(e.setPart(0,85,PcbModulesTest.definition(0,0)),"деталь именуемого проекта");
        for(int x=0;x<16;x++)if(x!=5)e.setCell(0,80+x,x==0||x==15?PcbTopology.TERMINAL:PcbTopology.TRACE);
        var p=net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(l);
        var menu=new PcbEditorMenu(77,p.getInventory(),net.minecraft.world.inventory.ContainerLevelAccess.create(l,at),e);
        check((boolean)PcbModulesTest.call(PcbEditorMenu.class,menu,"renameProject",new PcbEditorPackets.Rename(77,"Контроллер 5 В","")),"новое имя");
        check(e.topology().getString("ProjectName").equals("Контроллер 5 В"),"имя на сервере");
        check(!(boolean)PcbModulesTest.call(PcbEditorMenu.class,menu,"renameProject",new PcbEditorPackets.Rename(78,"Чужое","Контроллер 5 В")),"чужое меню отклонено");
        check(!(boolean)PcbModulesTest.call(PcbEditorMenu.class,menu,"renameProject",new PcbEditorPackets.Rename(77,"Старое","")),"устаревший запрос отклонён");
        check(!(boolean)PcbModulesTest.call(PcbEditorMenu.class,menu,"renameProject",new PcbEditorPackets.Rename(77,"Я".repeat(41),"Контроллер 5 В")),"ограничение длины");
        menu.clickMenuButton(p,PcbEditorMenu.UNDO);check(e.topology().getString("ProjectName").isEmpty(),"отмена имени");
        menu.clickMenuButton(p,PcbEditorMenu.REDO);check(e.topology().getString("ProjectName").equals("Контроллер 5 В"),"повтор имени");
        e.setItem(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PAPER));String error=e.saveDesign();check(error==null,"сохранение чертежа: "+error);
        var stack=e.getItem(0);check(stack.getHoverName().getString().equals("Контроллер 5 В"),"имя предмета");
        var saved=stack.save(l.registryAccess());var restored=net.minecraft.world.item.ItemStack.parse(l.registryAccess(),saved).orElseThrow();e.setItem(0,restored);e.loadDesign();
        check(e.topology().getString("ProjectName").equals("Контроллер 5 В"),"имя после сериализации и загрузки");
        s.sendSuccess(()->Component.literal("Имя проекта: сервер/чужое меню/устаревший запрос/лимит/отмена/повтор/предмет/NBT/загрузка — успешно"),false);return 1;
    }
    static int build(CommandSourceStack s,int type){var l=s.getLevel();
        DevTest.place(l,BOARD,Blocks.AIR.defaultBlockState());for(int i=0;i<7;i++)DevTest.place(l,source(i),Blocks.AIR.defaultBlockState());
        int def=PcbModulesTest.definition(type,0),pins=PcbModulesTest.modulePins(def);
        var routing=new PcbDemoRouting(pins);int[] pinNets=new int[pins];for(int i=0;i<pins;i++)pinNets[i]=i;
        routing.part(85,type,pinNets,type==36?10000:type==38?1000:0,0);
        if(type==36){routing.part(161,0,new int[]{2,1},10000,0);routing.part(169,10,new int[]{2,1},0,5);}
        for(int pin=0;pin<pins;pin++)routing.terminal(PcbDemoRouting.terminalCell(pin),pin);
        var t=routing.build();
        List<PcbCircuitDevice.Part> compiled=PcbModulesTest.call(wiring(),null,"compile",t);
        check(compiled!=null&&compiled.size()==(type==38?7:type==36?3:1),"число видимых физических ветвей: "+compiled);
        if(type==38)for(int i=0;i<7;i++)check(compiled.get(i).a()==i&&compiled.get(i).b()==7,"независимые аноды/общий катод");
        if(type==36)check(compiled.get(0).a()==0&&compiled.get(0).b()==1&&compiled.get(0).coilA()==2,"видимые дорожки ручки");
        var parts=List.copyOf(compiled);
        DevTest.place(l,BOARD,PcbItems.CIRCUIT_BLOCK.get().defaultBlockState().setValue(PcbCircuitBlock.CUSTOM,true));var stack=PcbItems.L2_BOARD.toStack();PcbTopology.attach(stack,t);PcbModulesTest.call(PcbCircuitBlockEntity.class,l.getBlockEntity(BOARD),"setBoard",stack);
        PcbModulesTest.call(PcbCircuitDevice.class,device(l),"setCircuit",List.copyOf(parts),PcbCircuitDevice.Rating.L2,pins);
        for(int i=0;i<(type==38?7:1);i++){DevTest.place(l,source(i),CEEBlocks.CREATIVE_BATTERY.getDefaultState());var player=net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(l);check(DevTest.spoolWire(player,ee.extras.EeExtras.THIN_SINGLE_SPOOL.toStack(),source(i),1,BOARD,type==38?i:0),"тонкий провод+");check(DevTest.spoolWire(player,ee.extras.EeExtras.THIN_SINGLE_SPOOL.toStack(),source(i),0,BOARD,type==38?7:1),"тонкий провод−");power(l,i,5);}
        check(s.getServer().getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.parse("ee_extras:pcb_"+new String[]{"rotary_knob","buzzer","seven_segment"}[type-36])).isPresent(),"рецепт загружен");
        s.sendSuccess(()->Component.literal("Модуль "+type+", ветвей "+compiled.size()+", выводов "+pins+", плата "+BOARD.toShortString()),false);return 1;
    }
    static void mask(ServerLevel l,int mask){for(int i=0;i<7;i++)power(l,i,(mask&(1<<i))!=0?5:0);}
    static void power(ServerLevel l,int i,int v){var b=DevicesSavedData.load(l).getDevice(source(i),CreativeBatteryDevice.class);b.voltage=v;b.acFrequency=0;var be=(com.simibubi.create.foundation.blockEntity.SmartBlockEntity)l.getBlockEntity(source(i));be.getBehaviour(com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour.TYPE).setValue(v*1000);}
}
