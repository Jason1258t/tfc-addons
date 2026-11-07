package ee.extras.devtest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import ee.extras.pcb.*;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/** Компилирует реальные площадки корпуса; стендовые перемычки выводят их к источникам и четырём пробникам. */
final class PcbSequentialTest {
    private static final BlockPos BOARD=new BlockPos(16,-60,8), SUPPLY=BOARD.east(4);
    private static int inputs,outputs,pins;
    private static PcbCircuitDevice.Kind kind;
    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root=Commands.literal("pcbseq").requires(s->s.hasPermission(2));
        for(int type=23;type<=26;type++) {
            int selected=type;
            root.then(Commands.literal(new String[]{"flip","counter","register","decoder"}[type-23]).executes(c->build(c.getSource(),selected)));
        }
        root.then(Commands.literal("resume").executes(c->{
            var saved=new CompoundTag();device(c.getSource().getLevel(),BOARD).write(saved);
            var part=saved.getList("Circuit",net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0);
            kind=PcbCircuitDevice.Kind.valueOf(part.getString("Kind"));pins=part.getIntArray("Pins").length;
            inputs=kind==PcbCircuitDevice.Kind.D_FLIP_FLOP?3:kind==PcbCircuitDevice.Kind.REGISTER?5:2;
            outputs=kind==PcbCircuitDevice.Kind.D_FLIP_FLOP?2:4;return 1;
        }));
        root.then(Commands.literal("inputs").then(Commands.argument("mask",IntegerArgumentType.integer(0,31)).executes(c->{
            int mask=IntegerArgumentType.getInteger(c,"mask");
            for(int i=0;i<inputs;i++) power(c.getSource().getLevel(),input(i),(mask&(1<<i))!=0?5:0); return 1;
        })));
        root.then(Commands.literal("power").then(Commands.argument("volts",IntegerArgumentType.integer(0,8)).executes(c->{ power(c.getSource().getLevel(),SUPPLY,IntegerArgumentType.getInteger(c,"volts"));return 1; })));
        root.then(Commands.literal("expect").then(Commands.argument("word",IntegerArgumentType.integer(0,15)).executes(c->expect(c.getSource(),IntegerArgumentType.getInteger(c,"word"),true))));
        root.then(Commands.literal("off").executes(c->expect(c.getSource(),0,false)));
        root.then(Commands.literal("reload").executes(c->{ var saved=new CompoundTag(); device(c.getSource().getLevel(),BOARD).write(saved); device(c.getSource().getLevel(),BOARD).read(saved); return 1; }));
        dispatcher.register(root);
    }
    private static BlockPos input(int i) { return BOARD.offset(6,0,i*2); }
    private static BlockPos probe(int i) { return BOARD.offset(-4,0,i*2); }
    private static PcbCircuitDevice device(ServerLevel level,BlockPos pos) { return DevicesSavedData.load(level).getDevice(pos,PcbCircuitDevice.class); }
    private static void check(boolean ok,String message) { if(!ok) throw new IllegalStateException("Память L2: "+message); }
    private static int build(CommandSourceStack source,int type) {
        var level=source.getLevel();
        for(int i=0;i<5;i++) { DevTest.place(level,input(i),Blocks.AIR.defaultBlockState()); if(i<4) DevTest.place(level,probe(i),Blocks.AIR.defaultBlockState()); }
        DevTest.place(level,BOARD,Blocks.AIR.defaultBlockState()); DevTest.place(level,SUPPLY,Blocks.AIR.defaultBlockState());
        int definition=PcbModulesTest.definition(type,0);
        pins=PcbModulesTest.modulePins(definition);
        inputs=type==23?3:type==25?5:2; outputs=type==23?2:4;
        var topology=PcbTopology.create(2);
        for(int i=0;i<pins;i++) {
            PcbTopology.set(topology,0,i,PcbTopology.TERMINAL);
            PcbModulesTest.call(PcbTopology.class,null,"setEdgeCut",topology,0,i,1,true);
        }
        PcbTopology.set(topology,0,85,PcbTopology.COMPONENT);
        PcbModulesTest.call(PcbTopology.class,null,"setPart",topology,0,85,definition);
        try {
            var wiring=Class.forName("ee.extras.pcb.PcbWiring");
            for(int pin=0;pin<pins;pin++) {
                int cell=PcbModulesTest.call(wiring,null,"pinCell",85,definition,pin);
                PcbTopology.set(topology,0,cell,PcbTopology.TRACE);
            }
            List<PcbCircuitDevice.Part> compiled=PcbModulesTest.call(wiring,null,"compile",topology);
            check(compiled!=null && compiled.size()==1,"компиляция многоконтактной детали");
            var part=compiled.get(0);kind=part.kind();
            check(part.pins().length==pins,"все сигналы и П/З сохранены");
            var isolated=part.pins();isolated[0]=-1;check(part.pins()[0]>=0,"массив выводов защищён от изменения снаружи");
            var parts=new ArrayList<>(compiled);
            for(int pin=0;pin<pins;pin++) parts.add(new PcbCircuitDevice.Part(PcbCircuitDevice.Kind.TRACE,pin,part.pins()[pin],.01,0,0,1));
            for(int output=0;output<outputs;output++) parts.add(new PcbCircuitDevice.Part(PcbCircuitDevice.Kind.RESISTOR,inputs+output,pins-1,1000,0,0,5));
            DevTest.place(level,BOARD,PcbItems.CIRCUIT_BLOCK.get().defaultBlockState().setValue(PcbCircuitBlock.CUSTOM,true));
            var stack=PcbItems.L2_BOARD.toStack(); PcbTopology.attach(stack,topology);
            PcbModulesTest.call(PcbCircuitBlockEntity.class,level.getBlockEntity(BOARD),"setBoard",stack);
            PcbModulesTest.call(PcbCircuitDevice.class,device(level,BOARD),"setCircuit",List.copyOf(parts),PcbCircuitDevice.Rating.L2,pins);
        } catch(ClassNotFoundException e) { throw new IllegalStateException(e); }
        DevTest.place(level,SUPPLY,CEEBlocks.CREATIVE_BATTERY.getDefaultState());
        DevTest.wire(level,SUPPLY,1,BOARD,pins-2);DevTest.wire(level,SUPPLY,0,BOARD,pins-1);power(level,SUPPLY,5);
        for(int i=0;i<inputs;i++) {
            DevTest.place(level,input(i),CEEBlocks.CREATIVE_BATTERY.getDefaultState());
            DevTest.wire(level,input(i),1,BOARD,i);DevTest.wire(level,input(i),0,BOARD,pins-1);power(level,input(i),0);
        }
        for(int i=0;i<outputs;i++) {
            DevTest.place(level,probe(i),PcbItems.CIRCUIT_BLOCK.get().defaultBlockState());
            device(level,probe(i)).setCircuit(List.of(new PcbCircuitDevice.Part(PcbCircuitDevice.Kind.VOLTMETER,0,1,1e9,0,0,5)),PcbCircuitDevice.Rating.L2);
            DevTest.wire(level,probe(i),0,BOARD,inputs+i);DevTest.wire(level,probe(i),1,BOARD,pins-1);
        }
        source.sendSuccess(()->Component.literal("Стенд L2: "+kind+", выводов "+pins+", нагрузка каждого выхода1кОм"),false);return 1;
    }
    private static void power(ServerLevel level,BlockPos pos,int volts) {
        var entity=(com.simibubi.create.foundation.blockEntity.SmartBlockEntity)level.getBlockEntity(pos);
        entity.getBehaviour(com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour.TYPE).setValue(volts*1000);
        var battery=DevicesSavedData.load(level).getDevice(pos,CreativeBatteryDevice.class);battery.voltage=volts;battery.acFrequency=0;
    }
    private static int expect(CommandSourceStack source,int word,boolean powered) {
        var level=source.getLevel();var saved=new CompoundTag();device(level,BOARD).write(saved);
        check(!saved.getBoolean("Burnt") && saved.getIntArray("LogicWords")[0]==word,"состояние "+java.util.Arrays.toString(saved.getIntArray("LogicWords"))+" вместо "+word);
        for(int i=0;i<outputs;i++) {
            boolean high=kind==PcbCircuitDevice.Kind.D_FLIP_FLOP ? i==0 ? word==1 : word==0 : (word&(1<<i))!=0;
            var measured=new CompoundTag();device(level,probe(i)).write(measured);
            double voltage=measured.getDouble("Meter"),expected=powered&&high?4.54545:0;
            check(Math.abs(voltage-expected)<.03,"выход "+i+" = "+voltage+" вместо "+expected);
        }
        source.sendSuccess(()->Component.literal(kind+": слово "+word+", все "+outputs+" электрических выхода верны"),false);return 1;
    }
}
