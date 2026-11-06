package ee.extras.pcb;

import com.george_vi.electroenergetics.content.bundled_wire.BundledWireTerminationDevice;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import ee.extras.cable.CableEndBlock;
import ee.extras.cable.CableKind;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;

final class PcbSupplyBlock extends CableEndBlock {
    static final int[] VOLTS = {3, 5, 12, 24, 36};
    static final int DEFAULT_SETTING = 2;
    static final IntegerProperty SETTING = IntegerProperty.create("setting", 0, VOLTS.length - 1);
    static final int OUTPUT_A = 3;
    static final int OUTPUT_B = 4;
    private static final float[][] POINTS = {{8, 2, 13}, {8, 2, 13}, {8, 2, 13}, {5, 7, 4}, {11, 7, 4}};
    private static final net.createmod.catnip.math.VoxelShaper SHAPE =
            net.createmod.catnip.math.VoxelShaper.forDirectional(box(1, 0, 1, 15, 7, 15), net.minecraft.core.Direction.UP);
    final boolean rectified;

    PcbSupplyBlock(Properties properties, boolean rectified) {
        super(properties, CableKind.DUPLEX, POINTS);
        this.rectified = rectified;
        registerDefaultState(defaultBlockState().setValue(SETTING, DEFAULT_SETTING));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SETTING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return super.getStateForPlacement(context).setValue(SETTING, PcbSupplyItem.setting(context.getItemInHand()));
    }

    @Override
    protected net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state,
            net.minecraft.world.level.BlockGetter level, BlockPos pos,
            net.minecraft.world.phys.shapes.CollisionContext context) {
        return SHAPE.get(state.getValue(FACING));
    }

    @Override
    public boolean isNodeAccessible(Level level, BlockPos pos, BlockState state, int id) {
        return id == OUTPUT_A || id == OUTPUT_B;
    }

    @Override
    public MutableComponent getNodeLabel(Level level, BlockPos pos, BlockState state, int node) {
        if (node < OUTPUT_A) return Component.translatable("ee_extras.nodes.cable");
        return Component.translatable("ee_extras.pcb.supply.terminal", node - OUTPUT_A + 1);
    }

    @Override
    public SimulatedDeviceType<BundledWireTerminationDevice> getDevice() {
        return PcbItems.SUPPLY_DEVICE.get();
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder loot) {
        var item = rectified ? PcbItems.SUPPLY_DC_ITEM : PcbItems.SUPPLY_ITEM;
        ItemStack result = item.toStack();
        PcbSupplyItem.configure(result, state.getValue(SETTING));
        return List.of(result);
    }
}
