package ee.extras.devtest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.CommandDispatcher;
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

/** Настоящая матрица EE: аналоговые входы, явное питание и нагруженный логический выход. */
final class PcbLogicTest {
    private static final BlockPos BOARD=new BlockPos(8,-60,24), SUPPLY=BOARD.east(4), INPUT=BOARD.east(6), REFERENCE=BOARD.east(8);
    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root=Commands.literal("pcblogic").requires(s->s.hasPermission(2))
                .then(Commands.literal("comparator").executes(c->build(c.getSource())))
                .then(Commands.literal("reload").executes(c->{ var tag=new CompoundTag(); device(c.getSource().getLevel()).write(tag); device(c.getSource().getLevel()).read(tag); return 1; }))
                .then(Commands.literal("expect").then(Commands.argument("volts",DoubleArgumentType.doubleArg(0,12))
                        .then(Commands.argument("watts",DoubleArgumentType.doubleArg(0,1)).executes(c->expect(c.getSource(),DoubleArgumentType.getDouble(c,"volts"),DoubleArgumentType.getDouble(c,"watts"))))));
        for (String name:new String[]{"supply","input","reference"}) root.then(Commands.literal(name)
                .then(Commands.argument("volts",DoubleArgumentType.doubleArg(0,12)).executes(c->{ power(c.getSource().getLevel(),name.equals("supply")?SUPPLY:name.equals("input")?INPUT:REFERENCE,DoubleArgumentType.getDouble(c,"volts")); return 1; })));
        dispatcher.register(root);
    }
    private static void check(boolean ok,String message) { if(!ok) throw new IllegalStateException("Логика: "+message); }
    private static PcbCircuitDevice device(ServerLevel level) { return DevicesSavedData.load(level).getDevice(BOARD,PcbCircuitDevice.class); }
    private static void trace(CompoundTag tag,int x,int y,boolean edge) { PcbTopology.set(tag,0,y*16+x,edge?PcbTopology.TERMINAL:PcbTopology.TRACE); }
    private static int build(CommandSourceStack source) {
        var level=source.getLevel();
        for(var pos:new BlockPos[]{BOARD,SUPPLY,INPUT,REFERENCE}) DevTest.place(level,pos,Blocks.AIR.defaultBlockState());
        var topology=PcbTopology.create(2);
        PcbTopology.set(topology,0,102,PcbTopology.COMPONENT);
        PcbModulesTest.call(PcbTopology.class,null,"setPart",topology,0,102,PcbModulesTest.definition(22,0));
        for(int x=0;x<=5;x++) trace(topology,x,6,x==0);
        for(int y=0;y<=5;y++) trace(topology,6,y,y==0);
        for(int y=8;y<16;y++) trace(topology,6,y,y==15);
        for(int x=8;x<16;x++) {
            trace(topology,x,6,x==15); trace(topology,x,7,x==15);
            PcbModulesTest.call(PcbTopology.class,null,"setEdgeCut",topology,0,6*16+x,3,true);
        }
        List<PcbCircuitDevice.Part> compiled;
        try { compiled=PcbModulesTest.call(Class.forName("ee.extras.pcb.PcbWiring"),null,"compile",topology); }
        catch(ClassNotFoundException e) { throw new IllegalStateException(e); }
        check(compiled!=null && compiled.size()==1,"компиляция корпуса2×2 с пятью выводами");
        var part=compiled.get(0);
        check(part.a()==4 && part.b()==3 && part.coilA()==1 && part.coilB()==2 && part.alternate()==0,"явные вход/порог/выход/П/З");
        var parts=new ArrayList<>(compiled);
        parts.add(new PcbCircuitDevice.Part(PcbCircuitDevice.Kind.RESISTOR,0,3,1000,0,0,5));
        parts.add(new PcbCircuitDevice.Part(PcbCircuitDevice.Kind.VOLTMETER,0,3,1e9,0,0,5));
        DevTest.place(level,BOARD,PcbItems.CIRCUIT_BLOCK.get().defaultBlockState().setValue(PcbCircuitBlock.CUSTOM,true));
        var stack=PcbItems.L2_BOARD.toStack(); PcbTopology.attach(stack,topology);
        PcbModulesTest.call(PcbCircuitBlockEntity.class,level.getBlockEntity(BOARD),"setBoard",stack);
        PcbModulesTest.call(PcbCircuitDevice.class,device(level),"setCircuit",List.copyOf(parts),PcbCircuitDevice.Rating.L2,5);
        int i=0;
        for(var pos:new BlockPos[]{SUPPLY,INPUT,REFERENCE}) {
            DevTest.place(level,pos,CEEBlocks.CREATIVE_BATTERY.getDefaultState());
            DevTest.wire(level,pos,1,BOARD,new int[]{4,1,2}[i]); DevTest.wire(level,pos,0,BOARD,3); i++;
        }
        power(level,SUPPLY,5); power(level,INPUT,1); power(level,REFERENCE,2);
        source.sendSuccess(()->Component.literal("Компаратор2×2: вход1В, порог2В, питание5В, выходная нагрузка1кОм"),false);return 1;
    }
    private static void power(ServerLevel level,BlockPos pos,double volts) {
        var entity=(com.simibubi.create.foundation.blockEntity.SmartBlockEntity)level.getBlockEntity(pos);
        entity.getBehaviour(com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour.TYPE).setValue((int)Math.round(volts*1000));
        var battery=DevicesSavedData.load(level).getDevice(pos,CreativeBatteryDevice.class); battery.voltage=volts; battery.acFrequency=0;
    }
    private static int expect(CommandSourceStack source,double expected,double watts) {
        var saved=new CompoundTag(); device(source.getLevel()).write(saved);
        double actual=saved.getDouble("Meter");
        double power=saved.getDouble("Watts");
        check(!saved.getBoolean("Burnt") && Math.abs(actual-expected)<.025 && Math.abs(power-watts)<.0001,"выход"+actual+"В, мощность"+power+"Вт; ожидалось"+expected+"/"+watts);
        source.sendSuccess(()->Component.literal(String.format(java.util.Locale.ROOT,"Компаратор: %.5fВ, %.7fВт — верно",actual,power)),false);return 1;
    }
}
