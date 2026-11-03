package ee.extras.pcb;

import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.CEELang;
import com.george_vi.electroenergetics.foundation.base.SimpleElectricalDeviceBlock;
import com.george_vi.electroenergetics.foundation.nodes.NodeConfigurator;
import com.simibubi.create.foundation.block.IBE;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Автоматизируемая машина без GUI: два контакта EE и предметный порт. */
public final class PcbMachineBlock extends SimpleElectricalDeviceBlock<PcbMachineDevice> implements IBE<PcbMachineBlockEntity> {
    private static final NodeConfigurator NODES = new NodeConfigurator.Builder()
        .add(4, 16, 8).add(12, 16, 8).simple();

    private final PcbMachineKind kind;

    public PcbMachineBlock(Properties properties, PcbMachineKind kind) {
        super(properties);
        this.kind = kind;
    }

    public PcbMachineKind kind() {
        return kind;
    }

    @Override
    public SimulatedDeviceType<PcbMachineDevice> getDevice() {
        return PcbItems.PCB_MACHINE_DEVICE.get();
    }

    @Override
    public Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) {
        return NODES.getNodes(Direction.UP);
    }

    @Override
    public Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int node) {
        return NODES.getNodePos(Direction.UP, node);
    }

    @Override
    public MutableComponent getNodeLabel(Level level, BlockPos pos, BlockState state, int node) {
        return CEELang.nodeLabel(node == PcbMachineDevice.PLUS ? "load_positive" : "load_negative");
    }

    @Override
    public Class<PcbMachineBlockEntity> getBlockEntityClass() {
        return PcbMachineBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends PcbMachineBlockEntity> getBlockEntityType() {
        return PcbItems.PCB_MACHINE_BE.get();
    }
}
