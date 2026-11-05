package ee.extras.pcb;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class PcbCircuitBlockEntity extends BlockEntity {
    private ItemStack board = ItemStack.EMPTY;

    public PcbCircuitBlockEntity(BlockPos pos, BlockState state) {
        super(PcbItems.CIRCUIT_BE.get(), pos, state);
    }

    void setBoard(ItemStack stack) {
        board = stack.copyWithCount(1);
        setChanged();
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
        board = ItemStack.parseOptional(registries, tag.getCompound("Board"));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!board.isEmpty()) tag.put("Board", board.save(registries));
    }
}
