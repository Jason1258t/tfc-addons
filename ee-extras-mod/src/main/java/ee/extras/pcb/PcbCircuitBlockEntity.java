package ee.extras.pcb;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class PcbCircuitBlockEntity extends BlockEntity {
    private ItemStack board = ItemStack.EMPTY;
    private VoxelShape shape;
    private Direction shapeFace;
    private long litIndicators;
    private long controlStates;
    private int[] controlPositions = new int[0];
    private java.util.BitSet caps = new java.util.BitSet(PcbTopology.CELLS);

    CompoundTag topology() {
        return board.get(PcbItems.PCB_TOPOLOGY.get());
    }

    VoxelShape shape() {
        Direction face = getBlockState().getValue(PcbCircuitBlock.FACING);
        if (shape == null || shapeFace != face) {
            shapeFace = face;
            shape = Shapes.empty();
            CompoundTag topology = topology();
            if (topology == null) return shape;
            int[] definitions = topology.getIntArray(PcbWiring.KEY);
            for (int cell = 0; cell < PcbTopology.CELLS; cell++) {
                if (PcbTopology.cut(topology, cell)) continue;
                int x = cell % PcbTopology.SIZE;
                int z = cell / PcbTopology.SIZE;
                shape = Shapes.or(shape,
                        Block.box(x, 0, z, x + 1, 1, z + 1));
                if (PcbTopology.get(topology, 0, cell) == PcbTopology.TERMINAL)
                    shape = Shapes.or(shape, Block.box(x, 1, z, x + 1, 2, z + 1));
                for (int layer = 0; layer < PcbTopology.level(topology); layer++) {
                    int owner = PcbFootprint.owner(definitions, layer, cell);
                    if (owner >= 0) {
                        int height = (PcbModules.get(definitions[owner]).adjustable() || PcbModules.get(definitions[owner]).timed()) ? 3 : hasCap(cell) ? 4 : 2;
                        shape = Shapes.or(shape, Block.box(x, 1, z, x + 1, height, z + 1));
                        break;
                    }
                }
            }
            shape = PcbCircuitBlock.mountedShape(shape, face);
        }
        return shape;
    }

    public PcbCircuitBlockEntity(BlockPos pos, BlockState state) {
        super(PcbItems.CIRCUIT_BE.get(), pos, state);
    }

    void setBoard(ItemStack stack) {
        board = stack.copyWithCount(1);
        shape = null;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    int[] positionedContacts() {
        var topology = board.get(PcbItems.PCB_TOPOLOGY.get());
        return PcbTopology.positionedContacts(topology) ? PcbTopology.contacts(topology) : null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = saveWithoutMetadata(registries);
        tag.putLong("LitIndicators", litIndicators);
        tag.putLong("ControlStates", controlStates);
        tag.putIntArray("ControlPositions", controlPositions);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    void setBlownFuses(long mask) {
        CompoundTag topology = board.get(PcbItems.PCB_TOPOLOGY.get());
        if (topology == null) return;
        CompoundTag copy = topology.copy();
        copy.putLong("BlownFuses", mask);
        board.set(PcbItems.PCB_TOPOLOGY.get(), copy);
        setChanged();
    }

    int controlAt(int cell) {
        var topology = topology();
        return topology == null ? -1 : controlAt(cell, topology.getIntArray(PcbWiring.KEY));
    }

    private static int controlAt(int cell, int[] definitions) {
        if (cell < 0 || cell >= PcbTopology.CELLS) return -1;
        for (int layer = PcbTopology.MAX_LAYERS - 1; layer >= 0; layer--) {
            int index = PcbFootprint.owner(definitions, layer, cell);
            if (index < 0) continue;
            int type = PcbWiring.type(definitions[index]);
            return PcbModules.ALL[type].input() ? index : -1;
        }
        return -1;
    }

    boolean adjustable(int index) { return PcbModules.get(topology().getIntArray(PcbWiring.KEY)[index]).adjustable(); }

    boolean acceptsCap(int index) {
        var kind = PcbModules.get(topology().getIntArray(PcbWiring.KEY)[index]).kind();
        return kind == PcbCircuitDevice.Kind.BUTTON || kind == PcbCircuitDevice.Kind.SWITCH;
    }

    int controlPosition(int ordinal) { return ordinal < controlPositions.length ? controlPositions[ordinal] : 50; }

    boolean isSwitch(int index) {
        return PcbWiring.type(topology().getIntArray(PcbWiring.KEY)[index]) == 3;
    }

    int componentOrdinal(int index) {
        return PcbWiring.componentOrdinal(topology().getIntArray(PcbWiring.KEY), index);
    }

    boolean hasCap(int cell) {
        int index = controlAt(cell);
        return index >= 0 && caps.get(index % PcbTopology.CELLS);
    }

    void addCap(int cell) {
        int index = controlAt(cell);
        if (index < 0) return;
        caps.set(index % PcbTopology.CELLS);
        shape = null;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    void addCapDrops(java.util.List<ItemStack> drops) {
        for (int cell = caps.nextSetBit(0); cell >= 0; cell = caps.nextSetBit(cell + 1)) {
            int index = controlAt(cell);
            if (index >= 0) drops.add(isSwitch(index) ? PcbItems.SWITCH_CAP.toStack() : PcbItems.BUTTON_CAP.toStack());
        }
    }

    void setVisualStates(long indicators, long controls, int[] positions) {
        if (litIndicators == indicators && controlStates == controls && java.util.Arrays.equals(controlPositions, positions)) return;
        litIndicators = indicators;
        controlStates = controls;
        controlPositions = positions.clone();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    boolean controlActive(int ordinal) {
        return (controlStates & (1L << ordinal)) != 0;
    }

    boolean indicatorLit(int ordinal) {
        return (litIndicators & (1L << ordinal)) != 0;
    }

    ItemStack board() {
        return board.copy();
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        shape = null;
        litIndicators = tag.getLong("LitIndicators");
        controlStates = tag.getLong("ControlStates");
        controlPositions = tag.getIntArray("ControlPositions");
        caps = java.util.BitSet.valueOf(tag.getLongArray("Caps"));
        board = ItemStack.parseOptional(registries, tag.getCompound("Board"));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!board.isEmpty()) tag.put("Board", board.save(registries));
        if (!caps.isEmpty()) tag.putLongArray("Caps", caps.toLongArray());
    }
}
