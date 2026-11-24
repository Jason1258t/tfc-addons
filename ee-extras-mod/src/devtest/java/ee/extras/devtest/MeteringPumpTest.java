package ee.extras.devtest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import ee.extras.EeExtras;
import ee.extras.machine.MeteringPumpBlock;
import ee.extras.machine.MeteringPumpBlockEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

final class MeteringPumpTest {
    static final BlockPos PUMP = new BlockPos(10, -58, 20);
    static final BlockPos SOURCE = PUMP.west(4);
    static final BlockPos TARGET = PUMP.east(4);
    static final BlockPos BATTERY = PUMP.south(3);
    static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("meteringtest")
            .then(Commands.literal("build").executes(c -> build(c.getSource())))
            .then(Commands.literal("ports").executes(c -> ports(c.getSource())))
            .then(Commands.literal("wire").executes(c -> wire(c.getSource())))
            .then(Commands.literal("start").executes(c -> check(c.getSource(), pump(c.getSource()).startDose(), "ручной пуск")))
            .then(Commands.literal("high").executes(c -> signal(c.getSource(), true)))
            .then(Commands.literal("low").executes(c -> signal(c.getSource(), false)))
            .then(Commands.literal("dose").then(Commands.argument("amount", IntegerArgumentType.integer(1,1000))
                .executes(c -> { var setting = BlockEntityBehaviour.get(c.getSource().getLevel(), PUMP, ScrollValueBehaviour.TYPE);
                    setting.setValue(IntegerArgumentType.getInteger(c,"amount")); return 1; })))
            .then(Commands.literal("volume").then(Commands.argument("amount", IntegerArgumentType.integer(0,16000))
                .executes(c -> volume(c.getSource(), IntegerArgumentType.getInteger(c,"amount")))))
            .then(Commands.literal("off").executes(c -> { DevicesSavedData.load(c.getSource().getLevel()).getDevice(BATTERY, CreativeBatteryDevice.class).voltage = 0; return 1; })));
    }
    private static int ports(CommandSourceStack source) {
        var level = source.getLevel();
        BlockPos pos = new BlockPos(22, -55, 24);
        boolean ok = true;
        for (Direction facing : Direction.values()) for (boolean roll : new boolean[] {false, true}) {
            DevTest.place(level, pos, EeExtras.METERING_PUMP.get().defaultBlockState()
                    .setValue(MeteringPumpBlock.FACING, facing).setValue(MeteringPumpBlock.ROLL, roll));
            var pump = (MeteringPumpBlockEntity) level.getBlockEntity(pos);
            var setting = BlockEntityBehaviour.get(level, pos, ScrollValueBehaviour.TYPE);
            var filter = BlockEntityBehaviour.get(level, pos, com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour.TYPE);
            var water = new FluidStack(Fluids.WATER, 1000);
            var input = pump.port(facing.getOpposite());
            var output = pump.port(facing);
            ok &= !pump.startDose() && input.fill(water, FluidAction.EXECUTE) == 0;
            pump.setVoltage(330);
            setting.setValue(310);
            ok &= pump.startDose() && !pump.startDose();
            setting.setValue(1000);
            filter.setFilter(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.LAVA_BUCKET));
            ok &= input.fill(water, FluidAction.EXECUTE) == 0;
            filter.setFilter(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WATER_BUCKET));
            ok &= input.fill(water, FluidAction.SIMULATE) == 310 && input.getFluidInTank(0).isEmpty();
            ok &= input.fill(water.copyWithAmount(100), FluidAction.EXECUTE) == 100;
            ok &= output.drain(1000, FluidAction.EXECUTE).isEmpty();
            ok &= input.fill(water, FluidAction.EXECUTE) == 210;
            pump.setVoltage(0);
            ok &= output.drain(1000, FluidAction.EXECUTE).isEmpty();
            pump.setVoltage(330);
            ok &= output.drain(1000, FluidAction.SIMULATE).getAmount() == 310;
            ok &= output.drain(100, FluidAction.EXECUTE).getAmount() == 100;
            ok &= output.drain(1000, FluidAction.EXECUTE).getAmount() == 210;
            ok &= output.drain(1000, FluidAction.EXECUTE).isEmpty() && pump.getSpeed() == 0;
            for (Direction side : Direction.values()) ok &= (pump.port(side) != null) == (side.getAxis() == facing.getAxis());
            level.removeBlock(pos, false);
        }
        return check(source, ok, "12 положений: порция 310, фиксация настройки, фильтр, SIMULATE, запрет неполной порции и выдачи без питания");
    }
    private static MeteringPumpBlockEntity pump(CommandSourceStack s) { return (MeteringPumpBlockEntity)s.getLevel().getBlockEntity(PUMP); }
    private static IFluidHandler tank(CommandSourceStack s, BlockPos pos) { return s.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,pos,Direction.UP); }
    private static int build(CommandSourceStack s) {
        var l=s.getLevel();
        DevTest.place(l,PUMP,EeExtras.METERING_PUMP.get().defaultBlockState().setValue(MeteringPumpBlock.FACING,Direction.EAST));
        for(int n=1;n<4;n++) {
            DevTest.place(l,PUMP.west(n),AllBlocks.FLUID_PIPE.getDefaultState());
            DevTest.place(l,PUMP.east(n),AllBlocks.FLUID_PIPE.getDefaultState());
        }
        DevTest.place(l,SOURCE,AllBlocks.FLUID_TANK.getDefaultState());
        DevTest.place(l,TARGET,AllBlocks.FLUID_TANK.getDefaultState());
        DevTest.place(l,BATTERY,CEEBlocks.CREATIVE_BATTERY.getDefaultState());
        return check(s,tank(s,SOURCE).fill(new FluidStack(Fluids.WATER,4000),FluidAction.EXECUTE)==4000,"вода 4000 мБ; помпа между двумя линиями труб");
    }
    private static int wire(CommandSourceStack s) {
        for(int i=0;i<2;i++) DevTest.wire(s.getLevel(),BATTERY,i,PUMP,i);
        var battery=DevicesSavedData.load(s.getLevel()).getDevice(BATTERY,CreativeBatteryDevice.class);
        battery.voltage=330;
        return check(s,true,"питание 330 В");
    }
    private static int signal(CommandSourceStack s,boolean high) {
        s.getLevel().setBlockAndUpdate(PUMP.north(),(high?Blocks.REDSTONE_BLOCK:Blocks.AIR).defaultBlockState()); return 1;
    }
    private static int volume(CommandSourceStack s,int expected) {
        int actual=tank(s,TARGET).getFluidInTank(0).getAmount();
        int input=tank(s,SOURCE).getFluidInTank(0).getAmount();
        var port=pump(s).port(Direction.EAST);
        int chamber=port.getFluidInTank(0).getAmount();
        return check(s,actual==expected && actual+input+chamber==4000,"выход="+actual+", вход="+input+", камера="+chamber+", ожидалось="+expected);
    }
    private static int check(CommandSourceStack s,boolean ok,String text) {
        if(ok) s.sendSuccess(()->Component.literal("PASS: "+text),false); else s.sendFailure(Component.literal("FAIL: "+text)); return ok?1:0;
    }
}
