package ee.extras.devtest;

import ee.extras.pcb.*;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.minecraft.commands.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import com.george_vi.electroenergetics.CEEBlocks;
import net.minecraft.world.level.block.Blocks;
import java.util.*;

/** Независимая нагрузка и реальные отсчёты EE: стабилизация, просадка, КЗ и тепловая защита. */
@net.neoforged.fml.common.EventBusSubscriber(modid="ee_extras_devtest")
final class PcbRegulatorTest {
    private static boolean watching;
    private static int samples;
    private static double lowest,highest,balanceError;
    @net.neoforged.bus.api.SubscribeEvent
    public static void observe(com.george_vi.electroenergetics.events.FinishElectricSimulationEvent event){
        var device=com.george_vi.electroenergetics.devices.device.DevicesSavedData.load(event.level).getDevice(PcbBridgeTest.BOARD,PcbCircuitDevice.class);
        if(device==null)return;var t=new CompoundTag();device.write(t);var all=t.getCompound("RegulatorPower");
        for(String k:all.getAllKeys()){var m=all.getCompound(k);if(!watching)continue;lowest=Math.min(lowest,m.getDouble("MinOutput"));highest=Math.max(highest,m.getDouble("MaxOutput"));balanceError=Math.max(balanceError,Math.abs(m.getDouble("InputWatts")-m.getDouble("OutputWatts")-m.getDouble("LossWatts")));samples++;}
    }

    static CompoundTag measurement(CommandSourceStack s){var t=PcbBridgeTest.snapshot(s).getCompound("RegulatorPower");PcbBridgeTest.check(!t.isEmpty(),"есть измерения стабилизатора");return t.getCompound(t.getAllKeys().iterator().next());}
    static int expect(CommandSourceStack s,double volts){var m=measurement(s);s.sendSuccess(()->Component.literal("Проверка "+volts+"В: "+m),false);balance(m);PcbBridgeTest.check(!PcbBridgeTest.snapshot(s).getBoolean("Burnt")&&Math.abs(m.getDouble("MinOutput")-volts)<.03&&Math.abs(m.getDouble("MaxOutput")-volts)<.03,"напряжение: "+m);s.sendSuccess(()->Component.literal("Стабилизатор: "+m),false);return 1;}
    static void balance(CompoundTag m){PcbBridgeTest.check(m.getBoolean("Converged")&&Math.abs(m.getDouble("InputWatts")-m.getDouble("OutputWatts")-m.getDouble("LossWatts"))<1e-5&&m.getDouble("LossWatts")>=0,"пассивный энергобаланс: "+m);}
    static void register(CommandDispatcher<CommandSourceStack> d){var r=Commands.literal("pcbregulator").requires(s->s.hasPermission(2));
        for(String mode:new String[]{"nominal","low","high","dropout","short","hardshort","overload","open","thermal","hot"})r.then(Commands.literal(mode).executes(c->build(c.getSource(),mode,0,false)));
        r.then(Commands.literal("watch").executes(c->{watching=true;samples=0;lowest=Double.POSITIVE_INFINITY;highest=Double.NEGATIVE_INFINITY;balanceError=0;return 1;}));
        r.then(Commands.literal("stable").executes(c->{watching=false;PcbBridgeTest.check(samples>=40&&lowest>=4.97&&highest<=5.03&&balanceError<1e-5,"длительная стабилизация: отсчётов="+samples+", диапазон="+lowest+".."+highest+", баланс="+balanceError);c.getSource().sendSuccess(()->Component.literal("Стабильно: отсчётов="+samples+", диапазон="+lowest+".."+highest+"В, баланс="+balanceError+"Вт"),false);return 1;}));
        r.then(Commands.literal("expect").then(Commands.argument("volts",DoubleArgumentType.doubleArg(0,100)).executes(c->expect(c.getSource(),DoubleArgumentType.getDouble(c,"volts")))));
        r.then(Commands.literal("geometry").executes(c->{for(int rot=0;rot<4;rot++)for(boolean mirror:new boolean[]{false,true}){var t=topology(rot,mirror);List<PcbCircuitDevice.Part> parts=PcbModulesTest.call(PcbBridgeTest.wiring(),null,"compile",t);PcbBridgeTest.check(parts!=null&&parts.size()==1,"геометрия стабилизатора");}return 1;}));
        r.then(Commands.literal("short-check").executes(c->{expect(c.getSource(),0);var m=measurement(c.getSource());PcbBridgeTest.check(m.getDouble("MaxInput")>9.9&&m.getDouble("InputAmps")>.05&&m.getDouble("InputAmps")<.08,"подтверждены вход10В и ограниченный ток настоящегоКЗ: "+m);return 1;}));
        r.then(Commands.literal("load-check").executes(c->{var t=PcbBridgeTest.snapshot(c.getSource());var parts=t.getList("Circuit",net.minecraft.nbt.Tag.TAG_COMPOUND);CompoundTag reg=null;for(int i=0;i<parts.size();i++)if(parts.getCompound(i).getString("Kind").equals("VOLTAGE_REGULATOR"))reg=parts.getCompound(i);PcbBridgeTest.check(reg!=null,"есть стабилизатор");double load=0;for(int i=0;i<parts.size();i++){var q=parts.getCompound(i);if(q.getString("Kind").equals("RESISTOR"))load+=1/q.getDouble("Ohms");}var m=measurement(c.getSource());double v=m.getDouble("MinOutput");PcbBridgeTest.check(Math.abs(m.getDouble("OutputWatts")-v*v*load)<1e-5,"независимая мощность нагрузки: "+m+", проводимость="+load);return 1;}));
        r.then(Commands.literal("report").executes(c->{var m=measurement(c.getSource());c.getSource().sendSuccess(()->Component.literal(m+", плата="+PcbBridgeTest.summary(c.getSource())),false);balance(m);return 1;}));
        r.then(Commands.literal("tripped").executes(c->{var m=measurement(c.getSource());balance(m);PcbBridgeTest.check(m.getBoolean("Tripped")&&m.getDouble("Temperature")>=60&&m.getDouble("MaxOutput")<.1,"тепловое отключение: "+m);return 1;}));
        r.then(Commands.literal("off").executes(c->{PcbBridgeTest.power(c.getSource(),0,false);return 1;}));
        r.then(Commands.literal("on").executes(c->{PcbBridgeTest.power(c.getSource(),10,false);return 1;}));
        r.then(Commands.literal("reload").executes(c->{var t=PcbBridgeTest.snapshot(c.getSource());PcbBridgeTest.device(c.getSource()).read(t);var after=PcbBridgeTest.snapshot(c.getSource());PcbBridgeTest.check(t.getCompound("RegulatorPower").getAllKeys().stream().allMatch(k->{var a=t.getCompound("RegulatorPower").getCompound(k);var b=after.getCompound("RegulatorPower").getCompound(k);return a.getDouble("Temperature")==b.getDouble("Temperature")&&a.getBoolean("Tripped")==b.getBoolean("Tripped");}),"сохранение температуры/защиты");return 1;}));
        r.then(Commands.literal("demo").executes(c->PcbRegulatorDemo.build(c.getSource(),true)));
        r.then(Commands.literal("write").executes(c->PcbRegulatorDemo.write(c.getSource())));
        r.then(Commands.literal("hold").executes(c->PcbRegulatorDemo.expect(c.getSource(),5,true)));
        r.then(Commands.literal("demo-on").executes(c->{PcbBridgeTest.power(c.getSource(),300,true);return 1;}));
        r.then(Commands.literal("powered-reset").executes(c->PcbRegulatorDemo.expect(c.getSource(),0,true)));
        r.then(Commands.literal("reset").executes(c->PcbRegulatorDemo.expect(c.getSource(),0,false)));
        r.then(Commands.literal("export").executes(c->PcbBridgeTest.export(c.getSource())));
        r.then(Commands.literal("resume").executes(c->{PcbBridgeTest.BATTERY=PcbBridgeTest.BOARD.east(8);return PcbRegulatorDemo.expect(c.getSource(),5,true);}));d.register(r);
    }
    static CompoundTag topology(int rotation,boolean mirror){var t=PcbBridgeTest.topology(rotation,mirror);int[] definitions=t.getIntArray("ElectricalParts");int def=PcbModulesTest.definition(30,rotation);if(mirror)def=PcbModulesTest.call(PcbBridgeTest.wiring(),null,"mirror",def);PcbModulesTest.call(PcbTopology.class,null,"setPart",t,0,85,def);return t;}
    static int build(CommandSourceStack s,String mode,int rotation,boolean mirror){var l=s.getLevel();PcbBridgeTest.BATTERY=PcbBridgeTest.BOARD.east(4);var board=PcbBridgeTest.BOARD;DevTest.place(l,board,Blocks.AIR.defaultBlockState());DevTest.place(l,PcbBridgeTest.BATTERY,Blocks.AIR.defaultBlockState());
        PcbBridgeTest.check(s.getServer().getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.parse("ee_extras:pcb_voltage_regulator")).isPresent(),"рецепт стабилизатора загружен");
        var t=topology(rotation,mirror);
        if(mode.equals("hardshort")){int def=PcbModulesTest.definition(30,rotation);if(mirror)def=PcbModulesTest.call(PcbBridgeTest.wiring(),null,"mirror",def);int first=PcbModulesTest.call(PcbBridgeTest.wiring(),null,"pinCell",85,def,2),last=PcbModulesTest.call(PcbBridgeTest.wiring(),null,"pinCell",85,def,3);PcbTopology.set(t,0,first,PcbTopology.VIA);PcbTopology.set(t,0,last,PcbTopology.VIA);for(int cell=first;cell<=last;cell+=16)PcbTopology.set(t,1,cell,PcbTopology.TRACE);}
        List<PcbCircuitDevice.Part> compiled=PcbModulesTest.call(PcbBridgeTest.wiring(),null,"compile",t);var reg=compiled.stream().filter(part->part.kind()==PcbCircuitDevice.Kind.VOLTAGE_REGULATOR).findFirst().orElseThrow();var parts=new ArrayList<>(compiled);
        double load=mode.equals("short")?.01:mode.equals("overload")?100:1000.0/3;
        if(!mode.equals("open")&&!mode.equals("hardshort"))parts.add(new PcbCircuitDevice.Part(PcbCircuitDevice.Kind.RESISTOR,reg.coilA(),reg.coilB(),load,0,0,1));
        DevTest.place(l,board,PcbItems.CIRCUIT_BLOCK.get().defaultBlockState().setValue(PcbCircuitBlock.CUSTOM,true));var stack=PcbItems.L2_BOARD.toStack();PcbTopology.attach(stack,t);PcbModulesTest.call(PcbCircuitBlockEntity.class,l.getBlockEntity(board),"setBoard",stack);PcbModulesTest.call(PcbCircuitDevice.class,PcbBridgeTest.device(s),"setCircuit",List.copyOf(parts),PcbCircuitDevice.Rating.L2,4);
        DevTest.place(l,PcbBridgeTest.BATTERY,CEEBlocks.CREATIVE_BATTERY.getDefaultState());DevTest.wire(l,PcbBridgeTest.BATTERY,1,board,reg.a());DevTest.wire(l,PcbBridgeTest.BATTERY,0,board,reg.b());PcbBridgeTest.power(s,mode.equals("low")?8:mode.equals("high")?12:mode.equals("dropout")?4:(mode.equals("thermal")||mode.equals("hot"))?24:10,false);
        if(mode.equals("thermal")){var tag=PcbBridgeTest.snapshot(s);var all=tag.getCompound("RegulatorPower");for(String k:all.getAllKeys())all.getCompound(k).putDouble("Temperature",89.99);PcbBridgeTest.device(s).read(tag);}
        s.sendSuccess(()->Component.literal("Стабилизатор: "+mode+", нагрузка"+load+"Ом"),false);return 1;
    }
}
