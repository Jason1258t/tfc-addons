package ee.extras.pcb;

import net.minecraft.core.BlockPos;
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

    CompoundTag topology() {
        return board.get(PcbItems.PCB_TOPOLOGY.get());
    }

    VoxelShape shape() {
        if (shape == null) {
            shape = Shapes.empty();
            CompoundTag topology = topology();
            if (topology == null) return shape;
            for (int cell = 0; cell < PcbTopology.CELLS; cell++) {
                if (PcbTopology.cut(topology, cell)) continue;
                int x = cell % PcbTopology.SIZE;
                int z = cell / PcbTopology.SIZE;
                shape = Shapes.or(shape,
                        Block.box(Math.max(x, 0.25), 0, Math.max(z, 0.25),
                                Math.min(x + 1, 15.75), 1, Math.min(z + 1, 15.75)));
                if (PcbTopology.get(topology, 0, cell) == PcbTopology.TERMINAL)
                    shape = Shapes.or(shape, Block.box(x, 1, z, x + 1, 2, z + 1));
            }
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
        return saveWithoutMetadata(registries);
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

    ItemStack board() {
        return board.copy();
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        shape = null;
        board = ItemStack.parseOptional(registries, tag.getCompound("Board"));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!board.isEmpty()) tag.put("Board", board.save(registries));
    }
}
