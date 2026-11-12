package tfc.destroy.extras.electrolyzer;

import blusunrize.immersiveengineering.api.IEProperties;
import blusunrize.immersiveengineering.api.multiblocks.blocks.MultiblockRegistration;
import blusunrize.immersiveengineering.api.multiblocks.blocks.logic.IMultiblockBE;
import blusunrize.immersiveengineering.api.multiblocks.blocks.registry.MultiblockPartBlock;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.simulation.infrastructure.InWorldNodeData;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.Vec3;
import tfc.destroy.bridge.electric.BridgeDevices;
import tfc.destroy.bridge.electric.ProcessPowerDevice;

/**
 * Блок собранного электролизёра. Кроме обычной части мультиблока IE это ещё и устройство EE: устройство EE заводится
 * на каждой клетке (так устроен миксин EE по классу блока), но контакты есть только у клетки клемм
 * {@link ElectrolyzerLogic#POWER}; у остальных клеток пустая нагрузка без контактов.
 */
public final class ElectrolyzerPartBlock extends MultiblockPartBlock<ElectrolyzerLogic.State>
    implements ElectricalDeviceBlock<ProcessPowerDevice> {
    /** Вершины изоляторов на крыше клеммной коробки — в клетке не повёрнутой машины (лицом на юг). */
    private static final Vec3 PLUS_LOCAL = new Vec3(0.3, 0.9, 0.5);
    private static final Vec3 MINUS_LOCAL = new Vec3(0.7, 0.9, 0.5);

    public ElectrolyzerPartBlock(MultiblockRegistration<ElectrolyzerLogic.State> multiblock) {
        super(Properties.of().mapColor(MapColor.METAL).sound(SoundType.METAL).strength(3.0F, 15.0F)
            .requiresCorrectToolForDrops().noOcclusion(), multiblock);
    }

    @Override
    public SimulatedDeviceType<ProcessPowerDevice> getDevice() {
        return BridgeDevices.PROCESS_POWER.get();
    }

    private static boolean isPowerCell(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof IMultiblockBE<?> be
            && ElectrolyzerLogic.POWER.equals(be.getHelper().getPositionInMB());
    }

    /** Точка в клетке не повёрнутой машины (лицом на север по IE) → в клетке повёрнутой: поворот вокруг центра блока. */
    private static Vec3 node(BlockState state, Vec3 local) {
        double x = local.x - 0.5;
        double z = local.z - 0.5;
        Direction front = state.getValue(IEProperties.FACING_HORIZONTAL);
        for (Direction d = Direction.NORTH; d != front; d = d.getClockWise()) {
            double turned = -z;
            z = x;
            x = turned;
        }
        return new Vec3(x + 0.5, local.y, z + 0.5);
    }

    @Override
    public Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) {
        if (!isPowerCell(level, pos)) return Map.of();
        return Map.of(ProcessPowerDevice.PLUS, node(state, PLUS_LOCAL), ProcessPowerDevice.MINUS, node(state, MINUS_LOCAL));
    }

    @Override
    public Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int id) {
        if (!isPowerCell(level, pos)) return null;
        return node(state, id == ProcessPowerDevice.PLUS ? PLUS_LOCAL : MINUS_LOCAL);
    }

    @Override
    public MutableComponent getNodeLabel(Level level, BlockPos pos, BlockState state, int id) {
        return Component.translatable(id == ProcessPowerDevice.PLUS
            ? "node.destroy_extras.electrolyzer.plus" : "node.destroy_extras.electrolyzer.minus");
    }

    // Как SimpleElectricalDeviceBlock EE: контакты регистрируются тиком после установки, когда у клетки уже есть
    // данные мультиблока (IE пишет позицию в шаблоне сразу после setBlock).
    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.getBlockTicks().hasScheduledTick(pos, this)) level.scheduleTick(pos, this, 1);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        List<Integer> nodes = new ArrayList<>(getNodePositions(level, pos, state).keySet());
        InfrastructureSavedData.load(level).registerOrUpdateNodes(pos, nodes);
        super.tick(state, level, pos, random);
    }

    /** Машину разобрали: провода с клемм снимаются и падают предметами. */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (level instanceof ServerLevel serverLevel && state.getBlock() != newState.getBlock()) {
            InfrastructureSavedData infrastructure = InfrastructureSavedData.load(serverLevel);
            for (InWorldNodeData node : new ArrayList<>(infrastructure.getNodesAt(pos))) {
                for (InWorldNodeConnection connection : new ArrayList<>(infrastructure.getConnections(node))) {
                    infrastructure.removeAndDropConnection(connection);
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
