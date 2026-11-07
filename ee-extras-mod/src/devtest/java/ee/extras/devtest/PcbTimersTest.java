package ee.extras.devtest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.mojang.brigadier.CommandDispatcher;
import ee.extras.pcb.*;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

final class PcbTimersTest {
    private static final BlockPos BOARD = new BlockPos(24, -60, 16), BATTERY = BOARD.east(4), WIND = BOARD.east(6);
    private static ServerLevel watched;
    private static int samples, high, low, edges;
    private static boolean previous;
    private static boolean listening;

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        if (!listening) {
            listening = true;
            NeoForge.EVENT_BUS.addListener(PcbTimersTest::tick);
        }
        dispatcher.register(Commands.literal("pcbtimers").requires(s -> s.hasPermission(2))
                .then(Commands.literal("mechanical").executes(c -> build(c.getSource(), false)))
                .then(Commands.literal("clock").executes(c -> build(c.getSource(), true)))
                .then(Commands.literal("click").executes(c -> click(c.getSource(), false)))
                .then(Commands.literal("reset").executes(c -> click(c.getSource(), true)))
                .then(Commands.literal("reload").executes(c -> {
                    var tag = new CompoundTag(); device(c.getSource().getLevel()).write(tag); device(c.getSource().getLevel()).read(tag); return 1;
                }))
                .then(Commands.literal("watch").executes(c -> { watched = c.getSource().getLevel(); samples = high = low = edges = 0; previous = false; return 1; }))
                .then(Commands.literal("report").executes(c -> {
                    if (samples < 100 || high < 20 || low < 20 || edges < 3 || edges > 7) throw new IllegalStateException("Клок: " + samples + " тиков, " + high + "/" + low + ", фронтов " + edges);
                    c.getSource().sendSuccess(() -> Component.literal("Клок: " + samples + " тиков, высокий/низкий " + high + "/" + low + ", переходов " + edges), false);
                    return 1;
                }))
                .then(Commands.literal("expect").then(Commands.argument("volts", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0,24))
                        .executes(c -> expect(c.getSource(), com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(c,"volts")))))
                .then(Commands.literal("wind").then(Commands.argument("volts", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0,24))
                        .executes(c -> { power(c.getSource().getLevel(), WIND, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"volts")); return 1; })))
                .then(Commands.literal("power").then(Commands.argument("volts", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0,24))
                        .executes(c -> { power(c.getSource().getLevel(), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"volts")); return 1; }))));
    }

    private static PcbCircuitDevice device(ServerLevel level) { return DevicesSavedData.load(level).getDevice(BOARD, PcbCircuitDevice.class); }
    private static double volts(ServerLevel level) { var tag = new CompoundTag(); device(level).write(tag); return tag.getDouble("Meter"); }

    private static void tick(ServerTickEvent.Post event) {
        if (watched == null || samples >= 100) return;
        boolean state = volts(watched) > 12;
        if (state) high++; else low++;
        if (samples > 0 && previous != state) edges++;
        previous = state; samples++;
    }

    private static int build(CommandSourceStack source, boolean clock) {
        if (!clock) return mechanical(source);
        var level = source.getLevel();
        DevTest.place(level, BOARD, Blocks.AIR.defaultBlockState()); DevTest.place(level, BATTERY, Blocks.AIR.defaultBlockState());
        DevTest.place(level, BOARD, PcbItems.CIRCUIT_BLOCK.get().defaultBlockState().setValue(PcbCircuitBlock.CUSTOM,true));
        DevTest.place(level, BATTERY, CEEBlocks.CREATIVE_BATTERY.getDefaultState());
        DevTest.place(level, WIND, Blocks.AIR.defaultBlockState());
        DevTest.place(level, WIND, CEEBlocks.CREATIVE_BATTERY.getDefaultState());
        var topology = PcbTopology.create(clock ? 2 : 1);
        PcbTopology.set(topology,0,0,PcbTopology.TERMINAL); PcbTopology.set(topology,0,15,PcbTopology.TERMINAL);
        PcbTopology.set(topology,0,85,PcbTopology.COMPONENT);
        PcbModulesTest.call(PcbTopology.class,null,"setPart",topology,0,85,PcbModulesTest.definition(clock ? 17 : 16,0));
        var stack = clock ? PcbItems.L2_BOARD.toStack() : PcbItems.L1_BOARD.toStack();
        PcbTopology.attach(stack,topology);
        PcbModulesTest.call(PcbCircuitBlockEntity.class,level.getBlockEntity(BOARD),"setBoard",stack);
        var parts = List.of(new PcbCircuitDevice.Part(clock ? PcbCircuitDevice.Kind.CLOCK : PcbCircuitDevice.Kind.MECHANICAL_TIMER,0,2,1000,clock ? 0 : 3,clock ? 1 : 0,clock ? 40 : 60,false,85,clock ? 17 : 16),
                new PcbCircuitDevice.Part(PcbCircuitDevice.Kind.RESISTOR,2,1,1000,0,0,24),
                new PcbCircuitDevice.Part(PcbCircuitDevice.Kind.VOLTMETER,2,1,1e9,0,0,24));
        try {
            var setter = PcbCircuitDevice.class.getDeclaredMethod("setCircuit",List.class,PcbCircuitDevice.Rating.class,int.class);
            setter.setAccessible(true); setter.invoke(device(level),parts,clock ? PcbCircuitDevice.Rating.L2 : PcbCircuitDevice.Rating.L1,clock ? 2 : 4);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        DevTest.wire(level,BATTERY,0,BOARD,0); DevTest.wire(level,BATTERY,1,BOARD,1);
        power(level,WIND,0); power(level,24); return 1;
    }

    private static int mechanical(CommandSourceStack source) {
        var level = source.getLevel();
        for (var pos : new BlockPos[]{BOARD, BATTERY, WIND}) DevTest.place(level,pos,Blocks.AIR.defaultBlockState());
        var topology = PcbTopology.create(1);
        PcbTopology.set(topology,0,85,PcbTopology.COMPONENT);
        PcbModulesTest.call(PcbTopology.class,null,"setPart",topology,0,85,PcbModulesTest.definition(16,0));
        var parameter = new CompoundTag(); parameter.putDouble("Nominal",3);
        var parameters = new CompoundTag(); parameters.put("85",parameter); topology.put("ModuleParameters",parameters);
        for (int x=0;x<=4;x++) PcbTopology.set(topology,0,6*16+x,x==0?PcbTopology.TERMINAL:PcbTopology.TRACE);
        for (int x=8;x<16;x++) PcbTopology.set(topology,0,6*16+x,x==15?PcbTopology.TERMINAL:PcbTopology.TRACE);
        for (int y=0;y<=4;y++) PcbTopology.set(topology,0,y*16+6,y==0?PcbTopology.TERMINAL:PcbTopology.TRACE);
        PcbTopology.set(topology,0,255,PcbTopology.TERMINAL);
        java.util.List<PcbCircuitDevice.Part> compiled;
        try { compiled=PcbModulesTest.call(Class.forName("ee.extras.pcb.PcbWiring"),null,"compile",topology); }
        catch (ClassNotFoundException e) { throw new IllegalStateException(e); }
        if (compiled==null || compiled.size()!=1) throw new IllegalStateException("Таймер не компилируется с тремя выводами");
        var timer=compiled.get(0);
        if (timer.a()!=1 || timer.b()!=2 || timer.coilA()!=0 || timer.coilB()!=1)
            throw new IllegalStateException("Неверные контакты взвода/A/B");
        var parts=new java.util.ArrayList<>(compiled);
        parts.add(new PcbCircuitDevice.Part(PcbCircuitDevice.Kind.RESISTOR,2,3,1000,0,0,24));
        parts.add(new PcbCircuitDevice.Part(PcbCircuitDevice.Kind.VOLTMETER,2,3,1e9,0,0,24));
        DevTest.place(level,BOARD,PcbItems.CIRCUIT_BLOCK.get().defaultBlockState().setValue(PcbCircuitBlock.CUSTOM,true));
        var stack=PcbItems.L1_BOARD.toStack(); PcbTopology.attach(stack,topology);
        PcbModulesTest.call(PcbCircuitBlockEntity.class,level.getBlockEntity(BOARD),"setBoard",stack);
        PcbModulesTest.call(PcbCircuitDevice.class,device(level),"setCircuit",List.copyOf(parts),PcbCircuitDevice.Rating.L1,4);
        for (var pos:new BlockPos[]{BATTERY,WIND}) DevTest.place(level,pos,CEEBlocks.CREATIVE_BATTERY.getDefaultState());
        DevTest.wire(level,BATTERY,0,BOARD,1); DevTest.wire(level,BATTERY,1,BOARD,3);
        DevTest.wire(level,WIND,0,BOARD,1); DevTest.wire(level,WIND,1,BOARD,0);
        power(level,WIND,0); power(level,24);
        source.sendSuccess(()->Component.literal("Механический таймер 3×3: скомпилированы взвод, A и B"),false);
        return 1;
    }

    private static void power(ServerLevel level,int volts) { power(level,BATTERY,volts); }
    private static void power(ServerLevel level,BlockPos pos,int volts) {
        var be = (com.simibubi.create.foundation.blockEntity.SmartBlockEntity) level.getBlockEntity(pos);
        be.getBehaviour(com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour.TYPE).setValue(volts*1000);
        var battery = DevicesSavedData.load(level).getDevice(pos,CreativeBatteryDevice.class); battery.voltage=volts; battery.acFrequency=0;
    }

    private static int click(CommandSourceStack source,boolean reset) {
        var level = source.getLevel(); var player = FakePlayerFactory.getMinecraft(level); player.setShiftKeyDown(reset);
        Vec3 mounted = PcbModulesTest.call(PcbCircuitBlock.class,null,"mountedPoint",new Vec3(6.5/16,2.0/16,6.5/16),Direction.UP);
        var hit = new BlockHitResult(mounted.add(Vec3.atLowerCornerOf(BOARD)),Direction.UP,BOARD,false);
        boolean accepted=level.getBlockState(BOARD).useWithoutItem(level,player,hit).consumesAction(); player.setShiftKeyDown(false);
        if (!accepted) throw new IllegalStateException("Таймер не принял клик"); return 1;
    }

    private static int expect(CommandSourceStack source,double expected) {
        double value=volts(source.getLevel());
        if (Math.abs(value-expected)>.12) throw new IllegalStateException("Таймер: " + value + " В вместо " + expected);
        source.sendSuccess(() -> Component.literal(String.format(java.util.Locale.ROOT,"Таймер: %.3f В (ожидалось %.3f В)",value,expected)),false); return 1;
    }
}
