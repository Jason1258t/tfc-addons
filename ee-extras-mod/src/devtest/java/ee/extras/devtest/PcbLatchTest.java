package ee.extras.devtest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
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
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/** Флаг записывается электрически, снимается питание, затем проверяется сохранённый контакт. */
final class PcbLatchTest {
    private static BlockPos BOARD = new BlockPos(8,-60,8), LOAD = BOARD.east(4), SET = BOARD.east(6), RESET = BOARD.east(8);
    private static net.minecraft.server.level.ServerPlayer photoPlayer;
    static int buildAt(CommandSourceStack source, BlockPos board) {
        BOARD=board; LOAD=board.east(4); SET=board.east(6); RESET=board.east(8);
        photoPlayer=source.getPlayer();
        try { return build(source); } finally { photoPlayer=null; }
    }
    static void drive(ServerLevel level,int set,int reset,int load) {
        power(level,SET,set); power(level,RESET,reset); power(level,LOAD,load);
    }
    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("pcblatch").requires(s -> s.hasPermission(2))
                .then(Commands.literal("build").executes(c -> build(c.getSource())))
                .then(Commands.literal("set").then(Commands.argument("volts", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0,24))
                        .executes(c -> { power(c.getSource().getLevel(),SET,com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"volts")); return 1; })))
                .then(Commands.literal("reset").then(Commands.argument("volts", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0,24))
                        .executes(c -> { power(c.getSource().getLevel(),RESET,com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"volts")); return 1; })))
                .then(Commands.literal("power").then(Commands.argument("volts", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0,24))
                        .executes(c -> { power(c.getSource().getLevel(),LOAD,com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"volts")); return 1; })))
                .then(Commands.literal("reload").executes(c -> { var saved=new CompoundTag(); device(c.getSource().getLevel()).write(saved); device(c.getSource().getLevel()).read(saved); return 1; }))
                .then(Commands.literal("expect").then(Commands.argument("flag",com.mojang.brigadier.arguments.BoolArgumentType.bool())
                        .then(Commands.argument("volts",com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0,24))
                                .executes(c -> expect(c.getSource(),com.mojang.brigadier.arguments.BoolArgumentType.getBool(c,"flag"),com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(c,"volts")))))));
    }
    private static void check(boolean ok,String text) { if(!ok) throw new IllegalStateException("Релейный флаг: "+text); }
    private static void trace(CompoundTag tag,int x,int y,boolean edge) { PcbTopology.set(tag,0,y*16+x,edge?PcbTopology.TERMINAL:PcbTopology.TRACE); }
    private static PcbCircuitDevice device(ServerLevel level) { return DevicesSavedData.load(level).getDevice(BOARD,PcbCircuitDevice.class); }
    @SuppressWarnings("unchecked")
    private static int build(CommandSourceStack source) {
        var level=source.getLevel(); var player=FakePlayerFactory.getMinecraft(level);
        for(var pos:new BlockPos[]{BOARD,LOAD,SET,RESET}) DevTest.place(level,pos,Blocks.AIR.defaultBlockState());
        var topology=PcbTopology.create(1); int definition=PcbModulesTest.definition(20,0);
        PcbTopology.set(topology,0,102,PcbTopology.COMPONENT); PcbModulesTest.call(PcbTopology.class,null,"setPart",topology,0,102,definition);
        for(int x=0;x<=5;x++) trace(topology,x,7,x==0);
        for(int y=0;y<=5;y++) trace(topology,6,y,y==0);
        for(int y=9;y<16;y++) trace(topology,6,y,y==15);
        for(int x=8;x<16;x++) {
            trace(topology,x,7,x==15); trace(topology,x,8,x==15);
            PcbModulesTest.call(PcbTopology.class,null,"setEdgeCut",topology,0,7*16+x,3,true);
        }
        check(PcbTopology.validate(topology)==null && PcbModulesTest.modulePins(definition)==5,"пять независимых контактов L1");
        List<PcbCircuitDevice.Part> parts;
        try { parts=new ArrayList<>((List<PcbCircuitDevice.Part>)PcbModulesTest.call(Class.forName("ee.extras.pcb.PcbWiring"),null,"compile",topology)); }
        catch(ClassNotFoundException e) { throw new IllegalStateException(e); }
        check(parts.size()==1,"одна физическая деталь");
        var part=parts.get(0);
        check(part.a()==1 && part.b()==2 && part.coilA()==0 && part.coilB()==3 && part.alternate()==4,"назначения SET/RESET и общего минуса");
        parts.add(new PcbCircuitDevice.Part(PcbCircuitDevice.Kind.RESISTOR,2,4,1000,0,0,3));
        parts.add(new PcbCircuitDevice.Part(PcbCircuitDevice.Kind.VOLTMETER,2,4,1e9,0,0,24));
        DevTest.place(level,BOARD,PcbItems.CIRCUIT_BLOCK.get().defaultBlockState().setValue(PcbCircuitBlock.CUSTOM,true));
        var stack=PcbItems.L1_BOARD.toStack(); PcbTopology.attach(stack,topology);
        ((PcbCircuitBlock)level.getBlockState(BOARD).getBlock()).setPlacedBy(level,BOARD,level.getBlockState(BOARD),player,stack);
        PcbModulesTest.call(PcbCircuitDevice.class,device(level),"setCircuit",List.copyOf(parts),PcbCircuitDevice.Rating.L1,5);
        for(var pos:new BlockPos[]{LOAD,SET,RESET}) DevTest.place(level,pos,CEEBlocks.CREATIVE_BATTERY.getDefaultState());
        for(int i=0;i<3;i++) {
            BlockPos battery=new BlockPos[]{LOAD,SET,RESET}[i]; int contact=new int[]{1,0,3}[i];
            wire(level,battery,1,BOARD,contact); wire(level,battery,0,BOARD,4);
            power(level,battery,i==0?24:0);
        }
        source.sendSuccess(()->Component.literal("Реле SET/RESET L1: две обмотки и нагрузка подключены, начальное состояние 0"),false);return 1;
    }
    private static void wire(ServerLevel level,BlockPos a,int na,BlockPos b,int nb) {
        if (photoPlayer==null) DevTest.wire(level,a,na,b,nb);
        else check(DevTest.spoolWire(photoPlayer,ee.extras.EeExtras.THIN_SINGLE_SPOOL.toStack(),a,na,b,nb),"тонкий провод фотостенда");
    }
    private static void power(ServerLevel level,BlockPos pos,int volts) {
        var entity=(com.simibubi.create.foundation.blockEntity.SmartBlockEntity)level.getBlockEntity(pos);
        entity.getBehaviour(com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour.TYPE).setValue(volts*1000);
        var battery=DevicesSavedData.load(level).getDevice(pos,CreativeBatteryDevice.class); battery.voltage=volts; battery.acFrequency=0;
    }
    static int expect(CommandSourceStack source,boolean flag,double expected) {
        var saved=new CompoundTag(); device(source.getLevel()).write(saved);
        double volts=saved.getDouble("Meter");
        check(!saved.getBoolean("Burnt") && ((saved.getLong("Latched")&1)!=0)==flag && Math.abs(volts-expected)<.1,"флаг="+saved.getLong("Latched")+", выход="+volts+", ожидалось="+flag+"/"+expected);
        source.sendSuccess(()->Component.literal(String.format(java.util.Locale.ROOT,"Релейный флаг %s, выход %.4f В — верно",flag?"1":"0",volts)),false);return 1;
    }
}
