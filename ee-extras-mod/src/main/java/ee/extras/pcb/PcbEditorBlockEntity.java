package ee.extras.pcb;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerListener;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Состояние чертёжного стола: один лист/чертёж и редактируемая топология. */
public class PcbEditorBlockEntity extends BlockEntity implements MenuProvider, Container, ContainerListener {
    private final SimpleContainer items = new SimpleContainer(1);
    private CompoundTag topology = PcbTopology.create(1);

    public PcbEditorBlockEntity(BlockPos pos, BlockState state) {
        super(PcbItems.EDITOR_BE.get(), pos, state);
        items.addListener(this);
    }

    public void open(ServerPlayer player) {
        player.openMenu(this, buf -> buf.writeBlockPos(worldPosition));
    }

    public CompoundTag topology() {
        return topology;
    }

    public void setCell(int layer, int cell, int value) {
        PcbTopology.set(topology, layer, cell, value);
        sync();
    }

    public void setLevel(int level) {
        PcbTopology.setLevel(topology, level);
        sync();
    }

    public void clear(int level) {
        topology = PcbTopology.create(level);
        sync();
    }

    public void loadDesign() {
        ItemStack stack = items.getItem(0);
        if (!(stack.getItem() instanceof PcbDesignItem design)) return;
        CompoundTag tag = stack.get(PcbItems.PCB_TOPOLOGY.get());
        topology = tag == null ? PcbTopology.create(design.fallbackLevel()) : PcbTopology.normalize(tag, design.fallbackLevel());
        sync();
    }

    public String saveDesign() {
        String error = PcbTopology.validate(topology);
        if (error != null) return error;
        ItemStack stack = items.getItem(0);
        int level = PcbTopology.level(topology);
        if (stack.isEmpty() || (!stack.is(net.minecraft.world.item.Items.PAPER)
                && !(stack.getItem() instanceof PcbDesignItem))) return "Положите бумагу или чертёж в ячейку";
        if (stack.getItem() instanceof PcbDesignItem design && design.fallbackLevel() != level)
            return "Для другого уровня нужен новый лист бумаги";
        ItemStack result;
        if (stack.is(net.minecraft.world.item.Items.PAPER)) {
            result = new ItemStack(level == 1 ? PcbItems.L1_MASK.get() : PcbItems.PCB_DESIGN.get());
            stack.shrink(1);
        } else {
            result = stack.copy();
        }
        PcbTopology.attach(result, topology);
        items.setItem(0, result);
        sync();
        return null;
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.ee_extras.pcb_design_table");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new PcbEditorMenu(id, inventory, ContainerLevelAccess.create(level, worldPosition), this);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        topology = PcbTopology.normalize(tag.getCompound("Topology"), 1);
        items.removeListener(this);
        items.clearContent();
        if (tag.contains("Items")) items.fromTag(tag.getList("Items", net.minecraft.nbt.Tag.TAG_COMPOUND), registries);
        items.addListener(this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Topology", topology.copy());
        tag.put("Items", items.createTag(registries));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.put("Topology", topology.copy());
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection connection, ClientboundBlockEntityDataPacket packet,
            HolderLookup.Provider registries) {
        CompoundTag tag = packet.getTag();
        if (tag.contains("Topology")) topology = PcbTopology.normalize(tag.getCompound("Topology"), 1);
    }

    @Override
    public void containerChanged(Container container) {
        sync();
    }

    @Override public int getContainerSize() { return items.getContainerSize(); }
    @Override public boolean isEmpty() { return items.isEmpty(); }
    @Override public ItemStack getItem(int slot) { return items.getItem(slot); }
    @Override public ItemStack removeItem(int slot, int count) { return items.removeItem(slot, count); }
    @Override public ItemStack removeItemNoUpdate(int slot) { return items.removeItemNoUpdate(slot); }
    @Override public void setItem(int slot, ItemStack stack) { items.setItem(slot, stack); }
    @Override public boolean stillValid(Player player) { return items.stillValid(player); }
    @Override public void clearContent() { items.clearContent(); }
}
