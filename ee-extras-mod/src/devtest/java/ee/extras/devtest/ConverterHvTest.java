package ee.extras.devtest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.content.gauge.GaugeDevice;
import com.mojang.brigadier.CommandDispatcher;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import ee.extras.EeExtras;
import ee.extras.converter.BidiConverterBlock;
import ee.extras.converter.BidiConverterDevice;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import java.util.Map;

final class ConverterHvTest {
    private static final BlockPos CAP = new BlockPos(4, -60, 16);
    private static final BlockPos CONV = CAP.above();
    private static final BlockPos LOAD = CAP.east(4);
    private static final BlockPos METER = CAP.east(6);
    static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("hvtest")
                .then(Commands.literal("build").executes(c -> { try { return build(c.getSource()); } catch (Exception e) { throw new IllegalStateException(e); } }))
                .then(Commands.literal("report").executes(c -> report(c.getSource())))
                .then(Commands.literal("block").executes(c -> { try { return block(c.getSource()); } catch(Exception e) { throw new IllegalStateException(e); } }))
                .then(Commands.literal("blocked").executes(c -> blocked(c.getSource())))
                .then(Commands.literal("voltage").then(Commands.argument("steps", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0,432))
                        .executes(c -> setting(c.getSource(), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"steps"))))));
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    private static int build(CommandSourceStack src) throws Exception {
        var level = src.getLevel();
        DevTest.place(level, CAP, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("immersiveengineering:capacitor_hv")).defaultBlockState());
        IEnergyStorage cap = level.getCapability(Capabilities.EnergyStorage.BLOCK,CAP,Direction.UP);
        while (cap.receiveEnergy(cap.getMaxEnergyStored(), false) > 0) {}
        var be = level.getBlockEntity(CAP);
        Map side = (Map) be.getClass().getField("sideConfig").get(be);
        var type = (Class<? extends Enum>) side.get(Direction.UP).getClass();
        side.put(Direction.UP, Enum.valueOf(type,"OUTPUT"));
        be.setChanged();
        DevTest.place(level, CONV, EeExtras.CONVERTER.get().defaultBlockState().setValue(BidiConverterBlock.FACING,Direction.UP));
        DevTest.place(level, LOAD, CEEBlocks.RESISTIVE_HEATER.getDefaultState());
        DevTest.place(level, METER, CEEBlocks.VOLTMETER.getDefaultState());
        for (int i=0;i<2;i++) {
            DevTest.wire(level,CONV,2+i,LOAD,i);
            DevTest.wire(level,CONV,2+i,METER,i);
        }
        return 1;
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    private static int block(CommandSourceStack src) throws Exception {
        var be=src.getLevel().getBlockEntity(CAP);
        Map side=(Map)be.getClass().getField("sideConfig").get(be);
        var type=(Class<? extends Enum>)side.get(Direction.UP).getClass();
        for(var dir:Direction.values()) side.put(dir,Enum.valueOf(type,"INPUT"));
        DevicesSavedData.load(src.getLevel()).getDevice(CONV,BidiConverterDevice.class).discharge=0;
        be.setChanged();
        return 1;
    }
    private static int blocked(CommandSourceStack src) {
        var be=src.getLevel().getBlockEntity(CONV);
        boolean ok=be.saveWithFullMetadata(src.getLevel().registryAccess()).getBoolean("FeBlocked");
        var text=Component.literal(ok?"HV BLOCKED PASS: показана причина отсутствия выхода FE":"HV BLOCKED FAIL");
        if(ok)src.sendSuccess(()->text,false);else src.sendFailure(text);
        return ok?1:0;
    }
    private static int setting(CommandSourceStack src,int value) {
        ScrollValueBehaviour setting=BlockEntityBehaviour.get(src.getLevel(),CONV,ScrollValueBehaviour.TYPE);
        setting.setValue(value);
        return 1;
    }
    private static int report(CommandSourceStack src) {
        var level=src.getLevel();
        var devices=DevicesSavedData.load(level);
        var dev=devices.getDevice(CONV,BidiConverterDevice.class);
        var meter=devices.getDevice(METER,GaugeDevice.class);
        ScrollValueBehaviour setting=BlockEntityBehaviour.get(level,CONV,ScrollValueBehaviour.TYPE);
        var cap=level.getCapability(Capabilities.EnergyStorage.BLOCK,CAP,Direction.UP);
        double measured=meter==null?Double.NaN:Math.abs(meter.rmsVoltages.get());
        double expected=setting.getValue()*10;
        boolean ok=dev!=null && dev.voltage==expected && Math.abs(measured-expected)<Math.max(0.1,expected*0.01)
                && (expected==0 || dev.dischargePower>1);
        var text=Component.literal((ok?"HV PASS":"HV FAIL")+": настройка="+expected+", симуляция="+(dev==null?null:dev.voltage)
                +", нагрузка="+measured+" В; FE="+cap.getEnergyStored()+"; запас="+(dev==null?null:dev.discharge));
        if(ok)src.sendSuccess(()->text,false);else src.sendFailure(text);
        return ok?1:0;
    }
    private ConverterHvTest() {}
}
