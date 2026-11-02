package ee.extras.lantern;

import blusunrize.immersiveengineering.api.IEProperties;
import blusunrize.immersiveengineering.common.blocks.metal.FloodlightBlock;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.foundation.nodes.NodeConfigurator;
import com.george_vi.electroenergetics.simulation.infrastructure.InWorldNodeData;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import ee.extras.cable.CableEndBlock;
import ee.extras.cable.CableJunction;
import ee.extras.cable.CableKind;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Прожектор IE, подключённый к двухпроводному кабелю EE (в руки — тот же прожектор IE: из него и делается,
 * LanternConversion). В отличие от фонаря — наследник блока IE, а не соединителя EE: блок-сущность IE читает свойство
 * IEProperties.FACING_ALL, а у блоков EE «facing» — другой объект того же имени (два в одном блоке быть не могут). Так
 * остаются поворот луча молотом, отвёртка, редстоун, компьютеры, модель и обманный свет; от EE — интерфейс устройства и
 * то, что делает SimpleElectricalDeviceBlock (узлы, снятие проводов при ломании в творческом режиме).
 *
 * Для кабеля — как изолятор (CableJunction): все кабели соединены жила к жиле. Узлы — посередине основания, в 1 пикселе
 * от него (FloodlightBlockEntity.getConnectionOffset, FACING=up — основание снизу), обычный провод к ним не подключить.
 */
public class EeFloodlightBlock extends FloodlightBlock implements ElectricalDeviceBlock<FloodlightDevice>, CableJunction {
    private static final NodeConfigurator NODES = CableEndBlock.configurator(LanternBlock.points(), 0);

    public EeFloodlightBlock() {
        super(FloodlightBlock.PROPERTIES.get());
    }

    @Override
    public CableKind cableKind() {
        return CableKind.DUPLEX;
    }

    @Override
    public SimulatedDeviceType<FloodlightDevice> getDevice() {
        return EeFloodlight.DEVICE.get();
    }

    @Override
    public Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) {
        return NODES.getNodes(state.getValue(IEProperties.FACING_ALL));
    }

    @Override
    public Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int id) {
        return NODES.getNodePos(state.getValue(IEProperties.FACING_ALL), id);
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
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.getBlockTicks().hasScheduledTick(pos, this)) level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        ensureNodesExist(level, pos, state);
        super.tick(state, level, pos, random);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level instanceof ServerLevel sl && player.isCreative()) {
            InfrastructureSavedData sd = InfrastructureSavedData.load(sl);
            for (InWorldNodeData node : sd.getNodesAt(pos))
                for (InWorldNodeConnection connection : sd.getConnections(node)) sd.removeConnection(connection);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** У блока нет предмета: выбор средней кнопкой — прожектор IE */
    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        return new ItemStack(BuiltInRegistries.ITEM.get(LanternConversion.IE_FLOODLIGHT));
    }
}
