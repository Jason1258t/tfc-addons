package ee.extras.converter;

import com.george_vi.electroenergetics.content.bundled_wire.BundledWireTerminationDevice;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import ee.extras.EeExtras;
import ee.extras.cable.CableEndBlock;
import ee.extras.cable.CableKind;
import ee.extras.cable.CableOrientation;
import net.createmod.catnip.math.VoxelShaper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Кабельный преобразователь: как потребляющая половина двустороннего, но вместо двух проводов — один двухжильный кабель.
 * Ставится задней гранью на машину IE (или другой блок, принимающий FE). Для кабеля — как изолятор: все кабели соединены
 * жила к жиле, поэтому от него кабель идёт дальше к следующему станку. Плита и ввод — как у отвода фазы (art/cables.py).
 */
public class CableConverterBlock extends CableEndBlock {
    /** Ввод кабеля — как у отвода фазы; все узлы в нём */
    private static final float[][] POINTS = {{8, 2, 13}, {8, 2, 13}, {8, 2, 13}};
    private static final VoxelShaper[] SHAPES = {
            VoxelShaper.forDirectional(box(0, 0, 1, 16, 6, 13), Direction.UP),
            VoxelShaper.forDirectional(box(3, 0, 0, 15, 6, 16), Direction.UP),
            VoxelShaper.forDirectional(box(0, 0, 3, 16, 6, 15), Direction.UP),
            VoxelShaper.forDirectional(box(1, 0, 0, 13, 6, 16), Direction.UP)};

    public CableConverterBlock(Properties properties) {
        super(properties, CableKind.DUPLEX, POINTS);
    }

    /** Устройство — наследник устройства соединителя EE (тип getDevice у BundledWireTerminationBlock закреплён) */
    @Override
    @SuppressWarnings("unchecked")
    public SimulatedDeviceType<BundledWireTerminationDevice> getDevice() {
        return (SimulatedDeviceType<BundledWireTerminationDevice>) (SimulatedDeviceType<?>) EeExtras.CABLE_CONVERTER_DEVICE.get();
    }

    @Override
    public boolean isNodeAccessible(Level level, BlockPos pos, BlockState state, int id) {
        return false;
    }

    @Override
    public MutableComponent getNodeLabel(Level level, BlockPos pos, BlockState state, int node) {
        return Component.translatable("ee_extras.nodes.cable");
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[CableOrientation.of(state)].get(state.getValue(FACING));
    }
}
