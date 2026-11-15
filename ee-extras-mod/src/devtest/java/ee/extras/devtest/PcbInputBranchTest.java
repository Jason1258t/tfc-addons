package ee.extras.devtest;

import ee.extras.pcb.*;
import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import java.util.*;

/** Внешний ползунок и вход микросхемы имеют общую землю: ветви не должны затираться. */
final class PcbInputBranchTest {
    private static final PcbCircuitDevice.Kind[] KINDS = {
            PcbCircuitDevice.Kind.D_FLIP_FLOP, PcbCircuitDevice.Kind.COUNTER,
            PcbCircuitDevice.Kind.REGISTER, PcbCircuitDevice.Kind.DECODER
    };
    private static BlockPos BOARD = new BlockPos(40,-60,40), SOURCE=BOARD.east(4);
    static void register(CommandDispatcher<CommandSourceStack> d) {
        var r=Commands.literal("pcbinput").requires(s->s.hasPermission(2));
        for(var kind:KINDS) {
            r.then(Commands.literal(kind.name().toLowerCase(Locale.ROOT)).executes(c->build(c.getSource(),kind,false)));
            r.then(Commands.literal(kind.name().toLowerCase(Locale.ROOT)+"-reverse").executes(c->build(c.getSource(),kind,true)));
        }
        r.then(Commands.literal("matrix").executes(c->matrix(c.getSource(),true,false)));
        r.then(Commands.literal("matrix-check").executes(c->matrix(c.getSource(),false,false)));
        r.then(Commands.literal("matrix-reload").executes(c->matrix(c.getSource(),false,true)));
        r.then(Commands.literal("expect").executes(c->{
            var t=new CompoundTag();device(c.getSource()).write(t);
            double v=t.getDouble("Meter");
            c.getSource().sendSuccess(()->Component.literal("Внешний ползунок: "+v+" В; "+t.getDouble("Watts")+" Вт"),false);
            if(t.getBoolean("Burnt") || !Double.isFinite(v) || Math.abs(v-2.5)>.01)throw new IllegalStateException("Вход затёр внешнюю ветвь: "+v+" В вместо 2,5 В");
            return 1;
        }));
        r.then(Commands.literal("reload").executes(c->{var t=new CompoundTag();device(c.getSource()).write(t);device(c.getSource()).read(t);return 1;}));
        d.register(r);
    }
    private static int matrix(CommandSourceStack s,boolean create,boolean reload){
        for(int i=0;i<KINDS.length * 2;i++){
            BOARD=new BlockPos(200+8*(i%KINDS.length),-60,200+12*(i/KINDS.length));SOURCE=BOARD.east(4);
            if(create){build(s,KINDS[i%KINDS.length],i>=KINDS.length);continue;}
            var t=new CompoundTag();device(s).write(t);if(reload)device(s).read(t);
            double v=t.getDouble("Meter");
            if(t.getBoolean("Burnt")||!Double.isFinite(v) || Math.abs(v-2.5)>.01)throw new IllegalStateException("Матрица входов "+i+": "+v+"В");
            int index=i;s.sendSuccess(()->Component.literal("Матрица входов "+index+": "+v+"В; "+t.getDouble("Watts")+"Вт"),false);
        }
        return 1;
    }
    private static PcbCircuitDevice device(CommandSourceStack s){return DevicesSavedData.load(s.getLevel()).getDevice(BOARD,PcbCircuitDevice.class);}
    private static int build(CommandSourceStack s,PcbCircuitDevice.Kind kind,boolean reverse){
        var l=s.getLevel();DevTest.place(l,BOARD,Blocks.AIR.defaultBlockState());DevTest.place(l,SOURCE,Blocks.AIR.defaultBlockState());
        int inputs=kind==PcbCircuitDevice.Kind.REGISTER?5:kind==PcbCircuitDevice.Kind.D_FLIP_FLOP?3:2;
        int outputs=kind==PcbCircuitDevice.Kind.D_FLIP_FLOP?2:4;
        int[] pins=new int[inputs+outputs+2];
        for(int i=0;i<inputs+outputs;i++)pins[i]=2+i;
        pins[pins.length-2]=0;pins[pins.length-1]=1;
        var chip=new PcbCircuitDevice.Part(kind,0,1,10000,0,0,3,false,-1,-1,-1,pins);
        var parts=new ArrayList<PcbCircuitDevice.Part>();
        int[] parallelPins = pins.clone();
        for (int output = 0; output < outputs; output++) parallelPins[inputs + output] += outputs;
        var parallelChip = new PcbCircuitDevice.Part(kind,0,1,10000,0,0,3,false,-1,-1,-1,parallelPins);
        if(reverse) {
            parts.add(chip);
            parts.add(parallelChip);
        }
        for(int i=0;i<inputs;i++)parts.add(new PcbCircuitDevice.Part(PcbCircuitDevice.Kind.POTENTIOMETER,0,1,10000,pins[i],0,1));
        if(!reverse) {
            parts.add(chip);
            parts.add(parallelChip);
        }
        parts.add(new PcbCircuitDevice.Part(PcbCircuitDevice.Kind.VOLTMETER,2,1,1e9,0,0,5));
        for(int i=0;i<outputs;i++)parts.add(new PcbCircuitDevice.Part(PcbCircuitDevice.Kind.RESISTOR,pins[inputs+i],1,1000,0,0,1));
        DevTest.place(l,BOARD,PcbItems.CIRCUIT_BLOCK.get().defaultBlockState());
        PcbModulesTest.call(PcbCircuitDevice.class,device(s),"setCircuit",List.copyOf(parts),PcbCircuitDevice.Rating.L2,2);
        DevTest.place(l,SOURCE,CEEBlocks.CREATIVE_BATTERY.getDefaultState());
        DevTest.wire(l,SOURCE,1,BOARD,0);DevTest.wire(l,SOURCE,0,BOARD,1);
        var battery=DevicesSavedData.load(l).getDevice(SOURCE,CreativeBatteryDevice.class);battery.voltage=5;battery.acFrequency=0;
        var be=(com.simibubi.create.foundation.blockEntity.SmartBlockEntity)l.getBlockEntity(SOURCE);
        be.getBehaviour(com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour.TYPE).setValue(5000);
        s.sendSuccess(()->Component.literal("Внешние ползунки: "+kind+", обратный порядок="+reverse),false);return 1;
    }
}
