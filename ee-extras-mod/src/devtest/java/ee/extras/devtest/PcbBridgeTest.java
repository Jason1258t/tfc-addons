package ee.extras.devtest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryBlock;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import ee.extras.pcb.*;
import java.util.*;
import net.minecraft.commands.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;

/** Измерения настоящего моста EE; команда demo собирает отдельную воспроизводимую плату. */
@net.neoforged.fml.common.EventBusSubscriber(modid="ee_extras_devtest")
final class PcbBridgeTest {
    static final Map<net.minecraft.server.level.ServerLevel,com.george_vi.electroenergetics.simulation.SimulationResults> RESULTS=new WeakHashMap<>();
    @net.neoforged.bus.api.SubscribeEvent
    public static void results(com.george_vi.electroenergetics.events.FinishElectricSimulationEvent event){RESULTS.put(event.level,event.results);}
    static BlockPos BOARD=new BlockPos(32,-60,32), BATTERY=BOARD.east(4);
    static void check(boolean ok,String message) {if(!ok) {System.err.println("PCB ASSERT FAIL: "+message);throw new IllegalStateException("Мост L2: "+message);}}
    static Class<?> wiring() {try{return Class.forName("ee.extras.pcb.PcbWiring");}catch(Exception e){throw new IllegalStateException(e);}}
    static PcbCircuitDevice device(CommandSourceStack s) {return DevicesSavedData.load(s.getLevel()).getDevice(BOARD,PcbCircuitDevice.class);}
    static CompoundTag snapshot(CommandSourceStack s) {var t=new CompoundTag();device(s).write(t);return t;}
    static String summary(CommandSourceStack s) {var t=snapshot(s);return "мост="+t.getCompound("BridgeMeasurements")+", трансформатор="+t.getCompound("OnboardPower")+", заряд="+t.get("Charge")+", регистр="+java.util.Arrays.toString(java.util.Arrays.copyOf(t.getIntArray("LogicWords"),10))+", перегрузка="+t.getDouble("Overload")+", тепло="+t.getDouble("Watts")+", сгорела="+t.getBoolean("Burnt");}
    static CompoundTag measurement(CommandSourceStack s) {var all=snapshot(s).getCompound("BridgeMeasurements");return all.getCompound(all.getAllKeys().iterator().next());}
    static void register(CommandDispatcher<CommandSourceStack> d) {
        var r=Commands.literal("pcbbridge").requires(s->s.hasPermission(2));
        for(String mode:new String[]{"ac","cap","dc","reverse","short","hardshort","overload","overvoltage"}) r.then(Commands.literal(mode).executes(c->build(c.getSource(),mode,0,false)));
        r.then(Commands.literal("resume").executes(c->{BATTERY=BOARD.east(8);check(snapshot(c.getSource()).getList("Circuit",Tag.TAG_COMPOUND).size()==9,"восстановлена демонстрационная плата");return 1;}));
        r.then(Commands.literal("export").executes(c->export(c.getSource())));
        r.then(Commands.literal("report").executes(c->{c.getSource().sendSuccess(()->Component.literal(summary(c.getSource())),false);return 1;}));
        r.then(Commands.literal("expect").then(Commands.argument("volts",DoubleArgumentType.doubleArg(0,200)).executes(c->{var m=measurement(c.getSource());check(!snapshot(c.getSource()).getBoolean("Burnt")&&Math.abs(m.getDouble("OutputRms")-DoubleArgumentType.getDouble(c,"volts"))<.1,m.toString());balance(m);c.getSource().sendSuccess(()->Component.literal("Мост: "+m),false);return 1;})));
        r.then(Commands.literal("reverse-check").executes(c->{var m=measurement(c.getSource());balance(m);check(m.getDouble("MinInput")< -4.9&&m.getDouble("MaxInput")< -4.9&&m.getDouble("MinOutput")>3.4,m.toString());return 1;}));
        r.then(Commands.literal("dc-check").executes(c->{var m=measurement(c.getSource());balance(m);check(m.getDouble("MinInput")>4.9&&m.getDouble("MinOutput")>3.4,m.toString());return 1;}));
        r.then(Commands.literal("short-check").executes(c->{var m=measurement(c.getSource());balance(m);check(!snapshot(c.getSource()).getBoolean("Burnt")&&m.getDouble("OutputRms")<.1&&m.getDouble("InputAmps")>0&&m.getDouble("InputAmps")<=1,m.toString());return 1;}));
        r.then(Commands.literal("raw-check").executes(c->{var m=measurement(c.getSource());balance(m);check(!snapshot(c.getSource()).getBoolean("Burnt")&&m.getDouble("MinOutput")<.05&&m.getDouble("MaxOutput")>5&&m.getDouble("OutputRms")>3,m.toString());return 1;}));
        r.then(Commands.literal("cap-check").executes(c->{var m=measurement(c.getSource());balance(m);check(!snapshot(c.getSource()).getBoolean("Burnt")&&m.getDouble("MinOutput")>=3&&m.getDouble("MaxOutput")<=6,m.toString());return 1;}));
        r.then(Commands.literal("burnt").executes(c->{check(snapshot(c.getSource()).getBoolean("Burnt"),"защита платы не сработала");return 1;}));
        r.then(Commands.literal("off").executes(c->{power(c.getSource(),0,true);return 1;}));
        r.then(Commands.literal("reload").executes(c->{var t=snapshot(c.getSource());device(c.getSource()).read(t);return 1;}));
        r.then(Commands.literal("geometry").executes(c->{for(int rotation=0;rotation<4;rotation++)for(boolean mirror:new boolean[]{false,true}) {var t=topology(rotation,mirror);List<PcbCircuitDevice.Part> p=PcbModulesTest.call(wiring(),null,"compile",t);check(p!=null&&p.size()==1,"компиляция геометрии");var b=p.get(0);check(java.util.stream.IntStream.of(b.a(),b.b(),b.coilA(),b.coilB()).distinct().count()==4,"четыре независимых вывода");}return 1;}));
        r.then(Commands.literal("demo").executes(c->PcbBridgeDemo.build(c.getSource(),true)));
        r.then(Commands.literal("demo-raw").executes(c->PcbBridgeDemo.build(c.getSource(),false)));
        r.then(Commands.literal("write").executes(c->PcbBridgeDemo.write(c.getSource())));
        r.then(Commands.literal("demo-hold").executes(c->PcbBridgeDemo.expect(c.getSource(),5,true)));
        r.then(Commands.literal("demo-reset").executes(c->PcbBridgeDemo.expect(c.getSource(),0,false)));
        d.register(r);
    }
    static void balance(CompoundTag m) {check(m.getBoolean("Converged"),"несходимость диодов "+m);check(Math.abs(m.getDouble("InputWatts")-m.getDouble("OutputWatts")-m.getDouble("LossWatts"))<1e-5,"энергобаланс "+m);check(m.getDouble("MinOutput")>=-.01,"полярность "+m);}
    static CompoundTag topology(int rotation,boolean mirror) {
        var t=PcbTopology.create(2);int def=PcbModulesTest.definition(29,rotation);if(mirror)def=PcbModulesTest.call(wiring(),null,"mirror",def);
        PcbTopology.set(t,0,85,PcbTopology.COMPONENT);PcbModulesTest.call(PcbTopology.class,null,"setPart",t,0,85,def);
        for(int pin=0;pin<4;pin++) {int cell=PcbModulesTest.call(wiring(),null,"pinCell",85,def,pin);int dx=cell%16<5?-1:cell%16>7?1:0,dy=cell/16<5?-1:cell/16>7?1:0;
            while(true){PcbTopology.set(t,0,cell,PcbTopology.TRACE);int x=cell%16,y=cell/16;if(x==0||x==15||y==0||y==15){PcbTopology.set(t,0,cell,PcbTopology.TERMINAL);break;}cell+=dx+16*dy;}}
        check(PcbTopology.validate(t)==null,"компиляция проекта");return t;
    }
    static int build(CommandSourceStack s,String mode,int rotation,boolean mirror) {
        check(s.getServer().getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.parse("ee_extras:pcb_bridge_rectifier")).isPresent(),"рецепт моста загружен");
        var l=s.getLevel();DevTest.place(l,BOARD,Blocks.AIR.defaultBlockState());DevTest.place(l,BATTERY,Blocks.AIR.defaultBlockState());
        var t=topology(rotation,mirror);
        if(mode.equals("hardshort")) {
            int def=PcbModulesTest.definition(29,rotation);int first=PcbModulesTest.call(wiring(),null,"pinCell",85,def,2),last=PcbModulesTest.call(wiring(),null,"pinCell",85,def,3);
            PcbTopology.set(t,0,first,PcbTopology.VIA);PcbTopology.set(t,0,last,PcbTopology.VIA);
            for(int cell=first;cell<=last;cell+=16)PcbTopology.set(t,1,cell,PcbTopology.TRACE);
        }
        List<PcbCircuitDevice.Part> compiled=PcbModulesTest.call(wiring(),null,"compile",t);var b=compiled.stream().filter(p->p.kind()==PcbCircuitDevice.Kind.BRIDGE_RECTIFIER).findFirst().orElseThrow();var parts=new ArrayList<>(compiled);
        double load=mode.equals("short")?.01:mode.equals("overload")?1:1000;
        if(!mode.equals("hardshort"))parts.add(new PcbCircuitDevice.Part(PcbCircuitDevice.Kind.RESISTOR,b.coilA(),b.coilB(),load,0,0,1));
        if(mode.equals("cap"))parts.add(new PcbCircuitDevice.Part(PcbCircuitDevice.Kind.CAPACITOR,b.coilA(),b.coilB(),10,0,0,1));
        DevTest.place(l,BOARD,PcbItems.CIRCUIT_BLOCK.get().defaultBlockState().setValue(PcbCircuitBlock.CUSTOM,true));var stack=PcbItems.L2_BOARD.toStack();PcbTopology.attach(stack,t);
        PcbModulesTest.call(PcbCircuitBlockEntity.class,l.getBlockEntity(BOARD),"setBoard",stack);
        PcbModulesTest.call(PcbCircuitDevice.class,device(s),"setCircuit",List.copyOf(parts),PcbCircuitDevice.Rating.L2,4);
        boolean ac=!mode.equals("dc")&&!mode.equals("reverse");DevTest.place(l,BATTERY,CEEBlocks.CREATIVE_BATTERY.getDefaultState().setValue(CreativeBatteryBlock.AC,ac));
        DevTest.wire(l,BATTERY,1,BOARD,mode.equals("reverse")?b.b():b.a());DevTest.wire(l,BATTERY,0,BOARD,mode.equals("reverse")?b.a():b.b());
        power(s,mode.equals("overvoltage")?150:mode.equals("overload")?24:5,ac);
        s.sendSuccess(()->Component.literal("Мост L2: "+mode+", нагрузка "+load+" Ом"),false);return 1;
    }
    static int export(CommandSourceStack s) {
        s.getServer().saveEverything(true,true,true);
        var from=s.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toAbsolutePath().normalize();
        var to=java.nio.file.Path.of(System.getProperty("user.home"),"tfc-stand","pcb301-bridge-save-"+System.currentTimeMillis());
        try(var files=java.nio.file.Files.walk(from)) {
            for(var file:files.toList()) {
                var relative=from.relativize(file);if(relative.toString().equals("session.lock"))continue;
                var target=to.resolve(relative);if(java.nio.file.Files.isDirectory(file))java.nio.file.Files.createDirectories(target);else java.nio.file.Files.copy(file,target);
            }
        } catch(java.io.IOException e){throw new IllegalStateException(e);}
        System.out.println("PCB_BRIDGE_SAVED_WORLD "+to);
        s.sendSuccess(()->Component.literal("PCB_BRIDGE_SAVED_WORLD "+to),false);return 1;
    }
    static void power(CommandSourceStack s,double volts,boolean ac) {
        var be=(com.simibubi.create.foundation.blockEntity.SmartBlockEntity)s.getLevel().getBlockEntity(BATTERY);
        be.getBehaviour(com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour.TYPE).setValue((int)Math.round(Math.abs(volts)*(ac?Math.sqrt(2):1)*1000));
        var b=DevicesSavedData.load(s.getLevel()).getDevice(BATTERY,CreativeBatteryDevice.class);b.voltage=volts*(ac?Math.sqrt(2):1);b.acFrequency=ac?1:0;
    }
}
