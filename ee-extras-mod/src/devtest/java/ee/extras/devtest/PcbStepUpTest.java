package ee.extras.devtest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryBlock;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.content.electronic_components.resistor.ResistorDevice;
import com.george_vi.electroenergetics.content.gauge.GaugeDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import ee.extras.pcb.*;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;

/** Подготовленный сценарий: выполнять только после освобождения стенда, MICROTICKS=8. */
final class PcbStepUpTest {
    static BlockPos BOARD=new BlockPos(24,-60,24), BATTERY=BOARD.east(4), LOAD=BOARD.south(4), METER=BOARD.west(4);
    private static final int TYPE=28;

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root=Commands.literal("pcbstepup").requires(s->s.hasPermission(2));
        for (String mode : new String[]{"ac","dc","short","open","ordinary","overload","overvoltage"})
            root.then(Commands.literal(mode).executes(c->build(c.getSource(),mode)));
        root.then(Commands.literal("geometry").executes(c->geometry(c.getSource())));
        root.then(Commands.literal("report").executes(c->{c.getSource().sendSuccess(()->Component.literal(snapshot(c.getSource()).toString()),false);return 1;}));
        root.then(Commands.literal("expect").then(Commands.argument("value",DoubleArgumentType.doubleArg(0,500)).executes(c->expect(c.getSource(),DoubleArgumentType.getDouble(c,"value")))));
        root.then(Commands.literal("burnt").executes(c->{check(snapshot(c.getSource()).getBoolean("Burnt"),"обычная защита платы должна сработать");return 1;}));
        root.then(Commands.literal("reload").executes(c->{device(c.getSource()).read(snapshot(c.getSource()));return 1;}));
        root.then(Commands.literal("cooldown").executes(c->{
            var tag=snapshot(c.getSource());var all=tag.getCompound("StepUpPower");
            for (String key:all.getAllKeys()) all.getCompound(key).putInt("Cooldown",100);
            device(c.getSource()).read(tag);var restored=snapshot(c.getSource()).getCompound("StepUpPower");
            for (String key:restored.getAllKeys()) check(restored.getCompound(key).getInt("Cooldown")==100 && !restored.getCompound(key).getBoolean("AcInput"),"выдержка защиты сохранена, разрешение AC проверяется заново");
            return 1;
        }));
        root.then(Commands.literal("volts").then(Commands.argument("value",DoubleArgumentType.doubleArg(0,60)).executes(c->{power(c.getSource(),DoubleArgumentType.getDouble(c,"value"),true);return 1;})));
        root.then(Commands.literal("off").executes(c->{power(c.getSource(),0,true);return 1;}));
        dispatcher.register(root);
    }

    private static Class<?> wiring() { try { return Class.forName("ee.extras.pcb.PcbWiring"); } catch (ClassNotFoundException e) { throw new IllegalStateException(e); } }
    private static void check(boolean condition,String text) { if (!condition) throw new IllegalStateException("ВВ-трансформатор: "+text); }
    private static PcbCircuitDevice device(CommandSourceStack source) { return DevicesSavedData.load(source.getLevel()).getDevice(BOARD,PcbCircuitDevice.class); }
    private static CompoundTag snapshot(CommandSourceStack source) { var tag=new CompoundTag();device(source).write(tag);return tag; }

    static CompoundTag topology(int rotation,boolean mirror) {
        var topology=PcbTopology.create(1);
        int definition=PcbModulesTest.definition(TYPE,rotation);
        if (mirror) definition=PcbModulesTest.call(wiring(),null,"mirror",definition);
        PcbTopology.set(topology,0,85,PcbTopology.COMPONENT);
        PcbModulesTest.call(PcbTopology.class,null,"setPart",topology,0,85,definition);
        for (int pin=0;pin<4;pin++) {
            int cell=PcbModulesTest.call(wiring(),null,"pinCell",85,definition,pin);
            int dx=cell%16<5?-1:cell%16>7?1:0,dy=cell/16<5?-1:cell/16>7?1:0;
            while (cell>=0 && cell<256) {
                PcbTopology.set(topology,0,cell,PcbTopology.TRACE);
                int x=cell%16,y=cell/16;
                if (x==0 || x==15 || y==0 || y==15) { PcbTopology.set(topology,0,cell,PcbTopology.TERMINAL); break; }
                cell+=dx+16*dy;
            }
        }
        check(PcbTopology.validate(topology)==null,"четыре независимых вывода компилируются");
        return topology;
    }

    private static List<PcbCircuitDevice.Part> compile(CompoundTag topology) { return PcbModulesTest.call(wiring(),null,"compile",topology); }

    private static int geometry(CommandSourceStack source) {
        for (int rotation=0;rotation<4;rotation++) for (boolean mirror:new boolean[]{false,true}) {
            var tag=topology(rotation,mirror); var parts=compile(tag);
            var part=parts.stream().filter(p->p.kind()==PcbCircuitDevice.Kind.STEP_UP_TRANSFORMER).findFirst().orElseThrow();
            check(java.util.stream.IntStream.of(part.a(),part.b(),part.coilA(),part.coilB()).distinct().count()==4,"не замкнуты обмотки");
            check(java.util.stream.IntStream.of(part.a(),part.b(),part.coilA(),part.coilB()).allMatch(n->n<60),"все выводы разведены на край");
            int[] groups=PcbModulesTest.call(circuitClass(),null,"terminalGroups",parts);
            check(groups[part.a()]==groups[part.b()],"первичка остаётся низковольтным контуром");
            check(groups[part.coilA()]!=groups[part.coilB()],"изоляция вторички не отключает защиту всей платы");
            int[] highVoltage=PcbModulesTest.call(circuitClass(),null,"highVoltageGroups",parts);
            check(highVoltage[part.coilA()]==highVoltage[part.coilB()] && highVoltage[part.a()]!=highVoltage[part.coilA()],"вторичка имеет отдельный граф предела480В");
            var withResistor=new ArrayList<>(parts);withResistor.add(new PcbCircuitDevice.Part(PcbCircuitDevice.Kind.RESISTOR,part.coilA(),part.coilB(),1e6,0,0,1));
            groups=PcbModulesTest.call(circuitClass(),null,"terminalGroups",withResistor);
            check(groups[part.coilA()]==groups[part.coilB()],"обычная деталь снова включает низковольтную проверку контура");
            var stack=PcbItems.L1_BOARD.toStack();PcbTopology.attach(stack,tag);
            DevTest.place(source.getLevel(),BOARD,PcbItems.CIRCUIT_BLOCK.get().defaultBlockState().setValue(PcbCircuitBlock.CUSTOM,true));
            PcbModulesTest.call(PcbCircuitBlockEntity.class,source.getLevel().getBlockEntity(BOARD),"setBoard",stack);
            for (var face:Direction.values()) {
                var state=source.getLevel().getBlockState(BOARD).setValue(PcbCircuitBlock.FACING,face);source.getLevel().setBlockAndUpdate(BOARD,state);
                var positions=((PcbCircuitBlock)state.getBlock()).getNodePositions(source.getLevel(),BOARD,state);
                check(positions.containsKey(part.a()) && positions.containsKey(part.b()) && positions.containsKey(part.coilA()) && positions.containsKey(part.coilB()),"четыре внешних контакта на "+face);
            }
        }
        source.sendSuccess(()->Component.literal("ВВ-трансформатор:48положений и независимые первичка/вторичка, защита обычного контура — OK"),false);return 1;
    }
    private static Class<?> circuitClass() { try {return Class.forName("ee.extras.pcb.PcbCircuitDevice$Circuit");}catch(ClassNotFoundException e){throw new IllegalStateException(e);} }

    static int buildAt(CommandSourceStack source,BlockPos pos,String mode) {
        BOARD=pos;BATTERY=pos.east(4);LOAD=pos.south(4);METER=pos.west(4);return build(source,mode);
    }

    static int build(CommandSourceStack source,String mode) { return build(source,mode,0,false); }

    static int build(CommandSourceStack source,String mode,int rotation,boolean mirror) {
        var level=source.getLevel();
        check(source.getServer().getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.parse("ee_extras:pcb_step_up_transformer")).isPresent(),"рецепт загружен");
        for (var pos:new BlockPos[]{BOARD,BATTERY,LOAD,METER}) DevTest.place(level,pos,Blocks.AIR.defaultBlockState());
        var topology=topology(rotation,mirror);var parts=new ArrayList<>(compile(topology));
        var part=parts.stream().filter(p->p.kind()==PcbCircuitDevice.Kind.STEP_UP_TRANSFORMER).findFirst().orElseThrow();
        if (mode.equals("ordinary")) parts.add(new PcbCircuitDevice.Part(PcbCircuitDevice.Kind.RESISTOR,part.coilA(),part.coilB(),1e6,0,0,1));
        if (mode.equals("overload")) parts.add(new PcbCircuitDevice.Part(PcbCircuitDevice.Kind.RESISTOR,part.a(),part.b(),20,0,0,1));
        DevTest.place(level,BOARD,PcbItems.CIRCUIT_BLOCK.get().defaultBlockState().setValue(PcbCircuitBlock.CUSTOM,true));
        var stack=PcbItems.L1_BOARD.toStack();PcbTopology.attach(stack,topology);
        PcbModulesTest.call(PcbCircuitBlockEntity.class,level.getBlockEntity(BOARD),"setBoard",stack);
        PcbModulesTest.call(PcbCircuitDevice.class,device(source),"setCircuit",List.copyOf(parts),PcbCircuitDevice.Rating.L1,4);
        DevTest.place(level,BATTERY,CEEBlocks.CREATIVE_BATTERY.getDefaultState().setValue(CreativeBatteryBlock.AC,!mode.equals("dc")));
        DevTest.wire(level,BATTERY,0,BOARD,part.a());DevTest.wire(level,BATTERY,1,BOARD,part.b());
        if (!mode.equals("open")) {
            DevTest.place(level,LOAD,CEEBlocks.RESISTOR.getDefaultState());
            ((com.george_vi.electroenergetics.content.electronic_components.resistor.ResistorBlockEntity)level.getBlockEntity(LOAD)).setResistance(mode.equals("short")?1:100000);
            var load=DevicesSavedData.load(level).getDevice(LOAD,ResistorDevice.class);load.properties=ElectricalProperties.resistor(mode.equals("short")?1:100000);
            DevTest.wire(level,BOARD,part.coilA(),LOAD,0);DevTest.wire(level,BOARD,part.coilB(),LOAD,1);
        }
        Power.meter(level,METER,BOARD,part.coilA(),BOARD,part.coilB());
        power(source,mode.equals("overvoltage")?60:24,!mode.equals("dc"));
        source.sendSuccess(()->Component.literal("ВВ-трансформатор:сценарий "+mode+", внешняя нагрузка, плата L1"),false);return 1;
    }

    private static void power(CommandSourceStack source,double rms,boolean ac) {
        var battery=DevicesSavedData.load(source.getLevel()).getDevice(BATTERY,CreativeBatteryDevice.class);
        var entity=(com.simibubi.create.foundation.blockEntity.SmartBlockEntity)source.getLevel().getBlockEntity(BATTERY);
        entity.getBehaviour(com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour.TYPE).setValue((int)Math.round(rms*(ac?Math.sqrt(2):1)*1000));
        battery.voltage=rms*(ac?Math.sqrt(2):1);battery.acFrequency=ac?1:0;
    }

    static int expect(CommandSourceStack source,double expected) {
        var tag=snapshot(source);var saved=tag.getCompound("StepUpPower");
        check(!saved.isEmpty(),"сохранено состояние ВВ-модуля");var power=saved.getCompound(saved.getAllKeys().iterator().next());
        var meter=DevicesSavedData.load(source.getLevel()).getDevice(METER,GaugeDevice.class);
        double measured=meter.rmsVoltages.get();
        check(!tag.getBoolean("Burnt") && Math.abs(measured-expected)<(expected>0 && expected<1?.002:.15),"внешний вольтметр="+measured+", ожидалось "+expected+", плата должна быть целой");
        check(Math.abs(power.getDouble("OutputRms")-measured)<.05,"реальный внешний вольтметр согласен с модулем");
        double current=power.getDouble("OutputAmps");
        check(current<=.02501 && power.getDouble("InputAmps")<=.50001,"ограничение токов");
        if (expected>0 && expected<1) check(current>.005 && power.getBoolean("AcInput"),"КЗограничено, но обмотка действительно работает");
        if (expected>0) {
            double primary=power.getDouble("InputAmps"),vin=power.getDouble("InputRms");
            double copper=.1*primary*primary+20000.1*current*current+vin*vin/1e8;
            double upper=copper+101*vin*vin/1e8;
            check(power.getDouble("LossWatts")>=copper-1e-5 && power.getDouble("LossWatts")<=upper+1e-5,"тепло в пределах потерь сопротивлений и конечной проводимости EE");
            // Баланс использует действительный ток EE, включая внешние провода и вольтметр.
            double useful=measured*current;
            check(Math.abs(power.getDouble("InputWatts")-power.getDouble("LossWatts")-useful)<1e-5,"баланс входа, выходной мощности и потерь: "+power+", полезная="+useful);
        }
        source.sendSuccess(()->Component.literal("ВВ-трансформатор:внешний выход="+measured+"В; баланс/токи/плата — OK"),false);return 1;
    }
}
