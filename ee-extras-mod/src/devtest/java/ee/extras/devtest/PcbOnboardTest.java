package ee.extras.devtest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryBlock;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import ee.extras.cable.*;
import ee.extras.pcb.*;
import java.util.ArrayList;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;

/** Бортовой БП с настоящим двухжильным кабелем и скомпилированными площадками вторички. */
final class PcbOnboardTest {
    static BlockPos BOARD = new BlockPos(12,-60,12), INPUT = BOARD.east(5), BATTERY = BOARD.east(8);
    static int buildAt(CommandSourceStack source,BlockPos pos,boolean connect) {
        BOARD=pos;INPUT=pos.east(5);BATTERY=pos.east(8);
        return build(source,true,1000,connect);
    }
    static void sourcePower(CommandSourceStack source,int volts,boolean ac) { power(source,volts,ac); }
    private static PcbCircuitDevice device(CommandSourceStack source) { return DevicesSavedData.load(source.getLevel()).getDevice(BOARD,PcbCircuitDevice.class); }
    private static void check(boolean value,String message) { if (!value) throw new IllegalStateException("Бортовой БП: "+message); }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("pcbonboard").requires(s -> s.hasPermission(2))
                .then(Commands.literal("ac").executes(c -> build(c.getSource(),true,1000)))
                .then(Commands.literal("dc").executes(c -> build(c.getSource(),false,1000)))
                .then(Commands.literal("short").executes(c -> build(c.getSource(),true,.01)))
                .then(Commands.literal("volts").then(Commands.argument("value",IntegerArgumentType.integer(0,400)).executes(c -> {
                    power(c.getSource(),IntegerArgumentType.getInteger(c,"value"),true);return 1;
                })))
                .then(Commands.literal("expect").then(Commands.argument("value",DoubleArgumentType.doubleArg(0,48)).executes(c -> expect(c.getSource(),DoubleArgumentType.getDouble(c,"value")))))
                .then(Commands.literal("report").executes(c -> report(c.getSource())))
                .then(Commands.literal("status").executes(c -> {
                    String text=PcbModulesTest.call(PcbCircuitDevice.class,device(c.getSource()),"interactComponent",0,c.getSource().getLevel().getGameTime(),false);
                    check(text!=null && text.contains("Выход"),"клик корпуса показывает состояние и напряжения");
                    c.getSource().sendSuccess(() -> Component.literal(text),false);return 1;
                }))
                .then(Commands.literal("reload").executes(c -> { var tag=new CompoundTag();device(c.getSource()).write(tag);device(c.getSource()).read(tag);return 1; })));
    }

    private static int build(CommandSourceStack source,boolean ac,double load) { return build(source,ac,load,true); }
    private static int build(CommandSourceStack source,boolean ac,double load,boolean connect) {
        var level = source.getLevel();
        check(source.getServer().getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.parse("ee_extras:pcb_onboard_transformer")).isPresent(),"рецепт механической сборки загружен");
        for (var pos : new BlockPos[]{BOARD,INPUT,BATTERY}) DevTest.place(level,pos,Blocks.AIR.defaultBlockState());
        var topology = PcbTopology.create(1);
        int definition = PcbModulesTest.definition(27,0);
        PcbTopology.set(topology,0,85,PcbTopology.COMPONENT);
        PcbModulesTest.call(PcbTopology.class,null,"setPart",topology,0,85,definition);
        var parameters = new CompoundTag();parameters.putDouble("Threshold",12);
        try { PcbModulesTest.call(Class.forName("ee.extras.pcb.PcbParameters"),null,"put",topology,85,parameters); }
        catch (ClassNotFoundException e) { throw new IllegalStateException(e); }
        PcbTopology.set(topology,0,100,PcbTopology.TRACE);
        PcbTopology.set(topology,0,104,PcbTopology.TRACE);
        check(PcbTopology.validate(topology)==null,"проект без лишних краевых клемм компилируется");
        java.util.List<PcbCircuitDevice.Part> compiled;
        try { compiled = PcbModulesTest.call(Class.forName("ee.extras.pcb.PcbWiring"),null,"compile",topology); }
        catch (ClassNotFoundException e) { throw new IllegalStateException(e); }
        check(compiled.size()==1 && compiled.get(0).kind()==PcbCircuitDevice.Kind.ONBOARD_TRANSFORMER,"один трансформатор с двумя выводами");
        var part=compiled.get(0);
        var parts=new ArrayList<>(compiled);
        parts.add(new PcbCircuitDevice.Part(PcbCircuitDevice.Kind.RESISTOR,part.a(),part.b(),load,0,0,1));
        parts.add(new PcbCircuitDevice.Part(PcbCircuitDevice.Kind.VOLTMETER,part.a(),part.b(),1e9,0,0,48));
        DevTest.place(level,BOARD,PcbItems.CIRCUIT_BLOCK.get().defaultBlockState().setValue(PcbCircuitBlock.CUSTOM,true));
        var stack=PcbItems.L1_BOARD.toStack();PcbTopology.attach(stack,topology);
        PcbModulesTest.call(PcbCircuitBlockEntity.class,level.getBlockEntity(BOARD),"setBoard",stack);
        geometry(source, topology, stack);
        PcbModulesTest.call(PcbCircuitDevice.class,device(source),"setCircuit",java.util.List.copyOf(parts),PcbCircuitDevice.Rating.L1,0);
        DevTest.place(level,INPUT,Extras.eeTermination());
        DevTest.place(level,BATTERY,CEEBlocks.CREATIVE_BATTERY.getDefaultState().setValue(CreativeBatteryBlock.AC,ac));
        var inlet=CableEnd.of(level,BOARD,level.getBlockState(BOARD));
        check(inlet!=null && inlet.base()==PcbPowerInlet.BASE,"плата распознана концом кабеля");
        check(!CableCap.put(level,BOARD,null,ee.extras.EeExtras.CABLE_CAP.toStack()).consumesAction(),"лишняя крышка на вводе БП отклоняется");
        check(!((PcbCircuitBlock)level.getBlockState(BOARD).getBlock()).isNodeAccessible(level,BOARD,level.getBlockState(BOARD),PcbPowerInlet.INPUT_A),"первичка скрыта от обычного провода");
        var end=Extras.end(level,INPUT);
        DevTest.wire(level,BATTERY,0,INPUT,end.conductor(1).id());
        DevTest.wire(level,BATTERY,1,INPUT,end.conductor(2).id());
        if (connect) check(CableLaying.connect(level,CableKind.DUPLEX,end,inlet),"реальный двухжильный кабель подключён прямо к модулю");
        power(source,300,ac);
        source.sendSuccess(() -> Component.literal("Бортовой БП:300В "+(ac?"ACEE,8отсчётов":"DC")+", выход12В, нагрузка"+load+"Ом"),false);
        return 1;
    }

    private static void geometry(CommandSourceStack source,CompoundTag topology,net.minecraft.world.item.ItemStack original) {
        var level=source.getLevel();
        try {
            Class<?> wiring=Class.forName("ee.extras.pcb.PcbWiring"), footprint=Class.forName("ee.extras.pcb.PcbFootprint"), region=Class.forName("ee.extras.pcb.PcbRegion");
            check(!(boolean)PcbModulesTest.call(footprint,null,"canPlace",topology,0,170,PcbModulesTest.definition(27,0),-1),"нельзя установить второй БП");
            check(PcbModulesTest.call(region,null,"apply",topology,topology,68,136,153,false)==null,"копирование не создаёт второй БП");
            for (int rotation=0;rotation<4;rotation++) for (int mirror=0;mirror<2;mirror++) {
                var variant=topology.copy();
                int definition=PcbModulesTest.definition(27,rotation);
                if (mirror!=0) definition=PcbModulesTest.call(wiring,null,"mirror",definition);
                PcbModulesTest.call(PcbTopology.class,null,"setPart",variant,0,85,definition);
                var item=PcbItems.L1_BOARD.toStack();PcbTopology.attach(item,variant);
                PcbModulesTest.call(PcbCircuitBlockEntity.class,level.getBlockEntity(BOARD),"setBoard",item);
                for (var face:net.minecraft.core.Direction.values()) {
                    var state=level.getBlockState(BOARD).setValue(PcbCircuitBlock.FACING,face);level.setBlockAndUpdate(BOARD,state);
                    var block=(PcbCircuitBlock)state.getBlock();var positions=block.getNodePositions(level,BOARD,state);
                    check(positions.size()==3,"первичка не занимает пользовательские клеммы");
                    var first=positions.get(PcbPowerInlet.INPUT_A);var second=positions.get(PcbPowerInlet.INPUT_B);var center=positions.get(PcbPowerInlet.BASE);
                    check(first!=null && second!=null && center!=null && first.add(second).scale(.5).distanceTo(center)<1e-8,"ввод центрирован на корпусе");
                    check(PcbPowerInlet.hit(level,BOARD,state,center.add(net.minecraft.world.phys.Vec3.atLowerCornerOf(BOARD))),"клик корпуса выбирает ввод");
                    var delta=first.subtract(center);
                    var localDirection=new net.minecraft.core.Direction[]{net.minecraft.core.Direction.WEST,net.minecraft.core.Direction.NORTH,net.minecraft.core.Direction.EAST,net.minecraft.core.Direction.SOUTH}[rotation];
                    if (mirror!=0) localDirection=localDirection.getOpposite();
                    net.minecraft.core.Direction expectedDirection=PcbModulesTest.call(PcbCircuitBlock.class,null,"mountedDirection",localDirection,face);
                    check(net.minecraft.core.Direction.getNearest(delta.x,delta.y,delta.z)==expectedDirection,"метка жилы A следует повороту/отражению");
                    check(Math.abs(first.distanceTo(second)-.6/16*.75)<1e-8,"расстояние жил на "+face+"/"+rotation+"/"+mirror);
                }
            }
            level.setBlockAndUpdate(BOARD,level.getBlockState(BOARD).setValue(PcbCircuitBlock.FACING,net.minecraft.core.Direction.UP));
            PcbModulesTest.call(PcbCircuitBlockEntity.class,level.getBlockEntity(BOARD),"setBoard",original);
            source.sendSuccess(() -> Component.literal("Бортовой ввод:48положений,6граней,повороты/отражения; запрет второго БП,включая вставку — пройдены"),false);
        } catch(ClassNotFoundException e) { throw new IllegalStateException(e); }
    }

    private static void power(CommandSourceStack source,int volts,boolean ac) {
        var level=source.getLevel();
        var entity=(com.simibubi.create.foundation.blockEntity.SmartBlockEntity)level.getBlockEntity(BATTERY);
        entity.getBehaviour(com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour.TYPE).setValue((int)Math.round(volts*(ac?Math.sqrt(2):1)*1000));
        var battery=DevicesSavedData.load(level).getDevice(BATTERY,CreativeBatteryDevice.class);
        battery.voltage=volts*(ac?Math.sqrt(2):1);battery.acFrequency=ac?1:0;
    }

    static int expect(CommandSourceStack source,double volts) {
        var tag=new CompoundTag();device(source).write(tag);
        double measured=tag.getDouble("Meter");
        check(!tag.getBoolean("Burnt") && Math.abs(measured-volts)<.05,"выход"+measured+"В вместо"+volts+", данные"+tag.getCompound("OnboardPower"));
        if (volts > 0) {
            var supply=tag.getCompound("OnboardPower");
            var parts=tag.getList("Circuit",net.minecraft.nbt.Tag.TAG_COMPOUND);
            double load=0;
            for (int i=0;i<parts.size();i++) if (parts.getCompound(i).getString("Kind").equals("RESISTOR")) load=parts.getCompound(i).getDouble("Ohms");
            check(load>0,"на стенде есть независимая нагрузка");
            double consumed=measured*measured/load + measured*measured/1e9 + supply.getDouble("LossWatts");
            check(Math.abs(supply.getDouble("InputWatts")-consumed)<1e-5,"баланс входа, нагрузки и потерь");
        }
        source.sendSuccess(() -> Component.literal("Бортовой БП:выход"+measured+"В, ожидалось"+volts+"В, плата цела"),false);
        return 1;
    }

    private static int report(CommandSourceStack source) {
        var tag=new CompoundTag();device(source).write(tag);
        source.sendSuccess(() -> Component.literal("Бортовой БП:"+tag.getCompound("OnboardPower")+", плата="+tag.getString("Rating")+", мощность="+tag.getDouble("Watts")+", сгорела="+tag.getBoolean("Burnt")),false);
        return 1;
    }
}
