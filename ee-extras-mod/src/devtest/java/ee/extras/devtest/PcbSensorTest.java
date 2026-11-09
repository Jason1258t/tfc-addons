package ee.extras.devtest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryBlock;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import ee.extras.pcb.*;
import ee.extras.cable.*;
import java.util.*;
import net.minecraft.commands.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;

/** Реальные показания мира и независимые электрические измерения датчиков. */
final class PcbSensorTest {
    static final int SENSOR=165,COMPARATOR=170,LAMP=218,SLIDER=129;
    static boolean thermal=true;
    static double load=1e9;
    static void register(CommandDispatcher<CommandSourceStack> d){var r=Commands.literal("pcbsensor").requires(s->s.hasPermission(2));
        r.then(Commands.literal("temperature").executes(c->build(c.getSource(),true,true)));
        r.then(Commands.literal("light").executes(c->build(c.getSource(),false,true)));
        r.then(Commands.literal("isolated-temperature").executes(c->build(c.getSource(),true,false)));
        r.then(Commands.literal("isolated-light").executes(c->build(c.getSource(),false,false)));
        r.then(Commands.literal("expect-supply").then(Commands.argument("value",DoubleArgumentType.doubleArg(0,200)).executes(c->{var m=measurement(c.getSource());PcbBridgeTest.check(Math.abs(m.getDouble("Supply")-DoubleArgumentType.getDouble(c,"value"))<.03,"подтверждено фактическое питание "+m);return 1;})));
        r.then(Commands.literal("expect-reading").then(Commands.argument("value",DoubleArgumentType.doubleArg(-100,1600)).executes(c->{var m=measurement(c.getSource());PcbBridgeTest.check(Math.abs(m.getDouble("Reading")-DoubleArgumentType.getDouble(c,"value"))<3,"проверка измеряемой среды "+m);return 1;})));
        r.then(Commands.literal("expect-output").then(Commands.argument("value",DoubleArgumentType.doubleArg(0,100)).executes(c->{var m=measurement(c.getSource());PcbBridgeTest.check(Math.abs(m.getDouble("Output")-DoubleArgumentType.getDouble(c,"value"))<.05,"контроль выхода "+m);return 1;})));
        r.then(Commands.literal("report").executes(c->report(c.getSource(),true)));
        r.then(Commands.literal("off-check").executes(c->{var m=measurement(c.getSource());PcbBridgeTest.check(!m.getBoolean("Powered")&&Math.abs(m.getDouble("Output"))<.01,"обесточенный выход "+m);return report(c.getSource(),false);}));
        r.then(Commands.literal("off").executes(c->{PcbBridgeTest.power(c.getSource(),0,false);return 1;}));
        r.then(Commands.literal("on").executes(c->{PcbBridgeTest.power(c.getSource(),demo(c.getSource())?300:5,demo(c.getSource()));return 1;}));
        r.then(Commands.literal("burnt").executes(c->{PcbBridgeTest.check(PcbBridgeTest.snapshot(c.getSource()).getBoolean("Burnt"),"защита платы от высокого AC");return 1;}));
        r.then(Commands.literal("ac-volts").then(Commands.argument("value",DoubleArgumentType.doubleArg(0,200)).executes(c->{var s=c.getSource();s.getLevel().setBlockAndUpdate(PcbBridgeTest.BATTERY,s.getLevel().getBlockState(PcbBridgeTest.BATTERY).setValue(CreativeBatteryBlock.AC,true));PcbBridgeTest.power(s,DoubleArgumentType.getDouble(c,"value"),true);return 1;})));
        r.then(Commands.literal("volts").then(Commands.argument("value",DoubleArgumentType.doubleArg(0,12)).executes(c->{PcbBridgeTest.power(c.getSource(),DoubleArgumentType.getDouble(c,"value"),false);return 1;})));
        r.then(Commands.literal("heat").then(Commands.argument("value",DoubleArgumentType.doubleArg(0,1600)).executes(c->{heat(c.getSource(),(float)DoubleArgumentType.getDouble(c,"value"));return 1;})));
        r.then(Commands.literal("dark").executes(c->{light(c.getSource(),false);return 1;}));
        r.then(Commands.literal("bright").executes(c->{light(c.getSource(),true);return 1;}));
        r.then(Commands.literal("air").executes(c->{DevTest.place(c.getSource().getLevel(),PcbBridgeTest.BOARD.below(),Blocks.STONE.defaultBlockState());return 1;}));
        r.then(Commands.literal("threshold").then(Commands.argument("value",DoubleArgumentType.doubleArg(0,100)).executes(c->{var s=c.getSource();var parts=PcbBridgeTest.snapshot(s).getList("Circuit",Tag.TAG_COMPOUND);int ordinal=-1;for(int i=0;i<parts.size();i++){var p=parts.getCompound(i);if(!p.getString("Kind").equals("TRACE"))ordinal++;if(p.getString("Kind").equals("POTENTIOMETER")){boolean ok=PcbModulesTest.call(PcbCircuitDevice.class,PcbBridgeTest.device(s),"adjustComponent",ordinal,100-(int)DoubleArgumentType.getDouble(c,"value"));PcbBridgeTest.check(ok,"порог изменён");return 1;}}throw new IllegalStateException("Нет ползунка");})));
        for(boolean lit:new boolean[]{false,true})r.then(Commands.literal(lit?"lit":"unlit").executes(c->{boolean actual=c.getSource().getLevel().getBlockState(PcbBridgeTest.BOARD).getValue(PcbCircuitBlock.LIT);PcbBridgeTest.check(actual==lit,"индикатор "+actual+", ожидался "+lit);return 1;}));
        r.then(Commands.literal("reload").executes(c->{var s=c.getSource();var t=PcbBridgeTest.snapshot(s);PcbBridgeTest.device(s).read(t);return 1;}));
        r.then(Commands.literal("export").executes(c->PcbBridgeTest.export(c.getSource())));
        r.then(Commands.literal("at").then(Commands.argument("pos",net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos()).executes(c->{PcbBridgeTest.BOARD=net.minecraft.commands.arguments.coordinates.BlockPosArgument.getBlockPos(c,"pos");PcbBridgeTest.BATTERY=PcbBridgeTest.BOARD.east(8);return 1;})));
        r.then(Commands.literal("select-light").executes(c->{PcbBridgeTest.BOARD=new BlockPos(64,-60,32);PcbBridgeTest.BATTERY=PcbBridgeTest.BOARD.east(8);return 1;}));
        r.then(Commands.literal("select-temperature").executes(c->{PcbBridgeTest.BOARD=new BlockPos(32,-60,32);PcbBridgeTest.BATTERY=PcbBridgeTest.BOARD.east(8);return 1;}));
        r.then(Commands.literal("resume").executes(c->{PcbBridgeTest.BATTERY=PcbBridgeTest.BOARD.east(8);return report(c.getSource(),true);}));
        r.then(Commands.literal("load").then(Commands.argument("ohms",DoubleArgumentType.doubleArg(.01,1e9)).executes(c->{var s=c.getSource();load=DoubleArgumentType.getDouble(c,"ohms");return isolatedLoad(s,load);} )));
        r.then(Commands.literal("geometry").executes(c->{for(int type:new int[]{31,32})for(int rot=0;rot<4;rot++)for(boolean mirror:new boolean[]{false,true}){var t=PcbTopology.create(2);int def=PcbModulesTest.definition(type,rot);if(mirror)def=PcbModulesTest.call(PcbBridgeTest.wiring(),null,"mirror",def);PcbTopology.set(t,0,85,PcbTopology.COMPONENT);PcbModulesTest.call(PcbTopology.class,null,"setPart",t,0,85,def);for(int pin=0;pin<3;pin++){int cell=PcbModulesTest.call(PcbBridgeTest.wiring(),null,"pinCell",85,def,pin);PcbTopology.set(t,0,cell,PcbTopology.TRACE);}
        List<PcbCircuitDevice.Part> parts=PcbModulesTest.call(PcbBridgeTest.wiring(),null,"compile",t);PcbBridgeTest.check(parts!=null&&parts.size()==1,"геометрия датчика");}return 1;}));
        d.register(r);
    }
    static boolean demo(CommandSourceStack s){return PcbBridgeTest.snapshot(s).getList("Circuit",Tag.TAG_COMPOUND).stream().anyMatch(t->((CompoundTag)t).getString("Kind").equals("ONBOARD_TRANSFORMER"));}
    static CompoundTag measurement(CommandSourceStack s){var all=PcbBridgeTest.snapshot(s).getCompound("Sensors");PcbBridgeTest.check(all.size()==1,"единственный датчик: "+all);return all.getCompound(all.getAllKeys().iterator().next());}
    static int report(CommandSourceStack s,boolean powered){var m=measurement(s);PcbBridgeTest.check(!PcbBridgeTest.snapshot(s).getBoolean("Burnt"),"плата цела");double v=m.getDouble("Supply"),f=m.getDouble("Fraction");PcbBridgeTest.check(Math.abs(m.getDouble("InputWatts")-m.getDouble("OutputWatts")-m.getDouble("Watts"))<1e-5,"энергобаланс "+m);
        if(powered){PcbBridgeTest.check(m.getBoolean("Powered")&&v>=3&&v<=6,"питание "+m);double actualLoad=demo(s)?1e9:load;double expected=v*f*actualLoad/(actualLoad+m.getDouble("OutputResistance"));PcbBridgeTest.check(Math.abs(m.getDouble("Output")-expected)<.025,"выход по независимой формуле "+expected+": "+m);}
        if(demo(s)&&powered)PcbRegulatorTest.expect(s,5);
        s.sendSuccess(()->Component.literal("Датчик: "+m+", индикатор="+s.getLevel().getBlockState(PcbBridgeTest.BOARD).getValue(PcbCircuitBlock.LIT)),false);return 1;
    }
    static CompoundTag topology(boolean temperature){var t=PcbTopology.create(2);int[] nets=new int[512];Arrays.fill(nets,-1);boolean[] blocked=new boolean[512];var endpoints=new ArrayList<List<Integer>>();for(int i=0;i<13;i++)endpoints.add(new ArrayList<>());
        PcbRegulatorDemo.add(t,nets,blocked,endpoints,17,27,new int[]{0,1},0,9);
        PcbRegulatorDemo.add(t,nets,blocked,endpoints,23,29,new int[]{0,1,2,3},0,0);
        PcbRegulatorDemo.add(t,nets,blocked,endpoints,84,7,new int[]{2,3},100,0);
        PcbRegulatorDemo.add(t,nets,blocked,endpoints,89,30,new int[]{2,3,9,3},0,0);
        PcbRegulatorDemo.add(t,nets,blocked,endpoints,SENSOR,temperature?31:32,new int[]{10,9,3},0,0);
        PcbRegulatorDemo.add(t,nets,blocked,endpoints,COMPARATOR,22,new int[]{10,11,12,9,3},0,0);
        PcbRegulatorDemo.add(t,nets,blocked,endpoints,SLIDER,15,new int[]{9,3,11},10000,0);
        PcbRegulatorDemo.add(t,nets,blocked,endpoints,LAMP,4,new int[]{12,3},1000,2);
        for(int net=0;net<13;net++){var points=endpoints.get(net);for(int i=1;i<points.size();i++)PcbRegulatorDemo.route(nets,blocked,points.get(0)+256,points.get(i)+256,net);}
        for(int i=0;i<512;i++)if(nets[i]>=0){int cell=i%256;PcbTopology.set(t,i/256,cell,nets[cell]>=0&&nets[cell]==nets[cell+256]?PcbTopology.VIA:PcbTopology.TRACE);}
        for(int layer=0;layer<2;layer++)for(int cell=0;cell<256;cell++)for(int direction:new int[]{1,3}){int next=direction==1?(cell%16==15?-1:cell+1):(cell>=240?-1:cell+16);if(next>=0&&nets[layer*256+cell]>=0&&nets[layer*256+next]>=0&&nets[layer*256+cell]!=nets[layer*256+next])PcbModulesTest.call(PcbTopology.class,null,"setEdgeCut",t,layer,cell,direction,true);}
        String error=PcbTopology.validate(t);PcbBridgeTest.check(error==null,"проект датчика "+error);return t;
    }
    static int build(CommandSourceStack s,boolean temperature,boolean demo){
        PcbBridgeTest.check(s.getServer().getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.parse("ee_extras:pcb_"+(temperature?"temperature_sensor":"light_sensor"))).isPresent(),"рецепт датчика загружен");thermal=temperature;load=1e9;var l=s.getLevel();var board=PcbBridgeTest.BOARD;PcbBridgeTest.BATTERY=board.east(demo?8:4);DevTest.place(l,board,Blocks.AIR.defaultBlockState());DevTest.place(l,PcbBridgeTest.BATTERY,Blocks.AIR.defaultBlockState());
        CompoundTag t;if(demo)t=topology(temperature);else{t=PcbTopology.create(2);PcbTopology.set(t,0,85,PcbTopology.COMPONENT);PcbModulesTest.call(PcbTopology.class,null,"setPart",t,0,85,PcbModulesTest.definition(temperature?31:32,0));expose(t,temperature?31:32);}
        DevTest.place(l,board,PcbItems.CIRCUIT_BLOCK.get().defaultBlockState().setValue(PcbCircuitBlock.CUSTOM,true));var stack=PcbItems.L2_BOARD.toStack();PcbTopology.attach(stack,t);PcbModulesTest.call(PcbCircuitBlockEntity.class,l.getBlockEntity(board),"setBoard",stack);List<PcbCircuitDevice.Part> parts=PcbModulesTest.call(PcbBridgeTest.wiring(),null,"compile",t);
        PcbBridgeTest.check(parts!=null&&parts.stream().filter(p->p.kind()!=PcbCircuitDevice.Kind.TRACE).count()==(demo?8:1),"только видимые модули");PcbModulesTest.call(PcbCircuitDevice.class,PcbBridgeTest.device(s),"setCircuit",parts,PcbCircuitDevice.Rating.L2,demo?0:4);
        DevTest.place(l,PcbBridgeTest.BATTERY,CEEBlocks.CREATIVE_BATTERY.getDefaultState().setValue(CreativeBatteryBlock.AC,demo));
        if(demo){var input=board.east(5);DevTest.place(l,input,Extras.eeTermination());var end=Extras.end(l,input);DevTest.wire(l,PcbBridgeTest.BATTERY,0,input,end.conductor(1).id());DevTest.wire(l,PcbBridgeTest.BATTERY,1,input,end.conductor(2).id());PcbBridgeTest.check(CableLaying.connect(l,CableKind.DUPLEX,end,CableEnd.of(l,board,l.getBlockState(board))),"настоящий ввод");}
        else{var p=parts.get(0);DevTest.wire(l,PcbBridgeTest.BATTERY,1,board,p.a());DevTest.wire(l,PcbBridgeTest.BATTERY,0,board,p.b());isolatedLoad(s,load);}
        if(demo){int owner=PcbModulesTest.call(PcbCircuitBlockEntity.class,l.getBlockEntity(board),"controlAt",SENSOR);PcbBridgeTest.check(owner==SENSOR,"корпус датчика доступен для чтения ПКМ");}
        PcbBridgeTest.power(s,demo?300:5,demo);if(temperature)heat(s,20);else light(s,false);return 1;
    }
    static int isolatedLoad(CommandSourceStack s,double ohms){PcbBridgeTest.check(!demo(s),"нагрузка только для изолированного измерения");var t=PcbTopology.create(2);PcbTopology.set(t,0,85,PcbTopology.COMPONENT);PcbModulesTest.call(PcbTopology.class,null,"setPart",t,0,85,PcbModulesTest.definition(thermal?31:32,0));expose(t,thermal?31:32);List<PcbCircuitDevice.Part> compiled=PcbModulesTest.call(PcbBridgeTest.wiring(),null,"compile",t);var parts=new ArrayList<>(compiled);var p=compiled.get(0);parts.add(new PcbCircuitDevice.Part(PcbCircuitDevice.Kind.RESISTOR,p.coilA(),p.b(),ohms,0,0,1));PcbModulesTest.call(PcbCircuitDevice.class,PcbBridgeTest.device(s),"setCircuit",parts,PcbCircuitDevice.Rating.L2,0);load=ohms;return 1;}
    static void expose(CompoundTag t,int type){int def=PcbModulesTest.definition(type,0);for(int pin=0;pin<3;pin++){
        int cell=PcbModulesTest.call(PcbBridgeTest.wiring(),null,"pinCell",85,def,pin),dx=cell%16<5?-1:cell%16>6?1:0,dy=cell/16<5?-1:cell/16>6?1:0;
        while(true){PcbTopology.set(t,0,cell,PcbTopology.TRACE);int x=cell%16,y=cell/16;if(x==0||x==15||y==0||y==15){PcbTopology.set(t,0,cell,PcbTopology.TERMINAL);break;}cell+=dx+16*dy;}
    }}
    static void heat(CommandSourceStack s,float value){var p=PcbBridgeTest.BOARD.below();DevTest.place(s.getLevel(),p,Blocks.AIR.defaultBlockState());DevTest.place(s.getLevel(),p,net.dries007.tfc.common.blocks.TFCBlocks.CRUCIBLE.get().defaultBlockState());var h=s.getLevel().getCapability(net.dries007.tfc.common.capabilities.BlockCapabilities.HEAT,p,Direction.UP);PcbBridgeTest.check(h!=null,"тепловой интерфейс TFC");h.setTemperature(value);}
    static void light(CommandSourceStack s,boolean bright){var l=s.getLevel();var center=PcbBridgeTest.BOARD.above();for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){DevTest.place(l,PcbBridgeTest.BOARD.offset(x,-1,z),Blocks.STONE.defaultBlockState());if(x!=0||z!=0)DevTest.place(l,PcbBridgeTest.BOARD.offset(x,0,z),Blocks.STONE.defaultBlockState());}
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){DevTest.place(l,center.offset(x,1,z),Blocks.STONE.defaultBlockState());if(x!=0||z!=0)DevTest.place(l,center.offset(x,0,z),Blocks.STONE.defaultBlockState());}DevTest.place(l,center,Blocks.AIR.defaultBlockState());DevTest.place(l,center.above(),bright?Blocks.GLOWSTONE.defaultBlockState():Blocks.STONE.defaultBlockState());}
}
