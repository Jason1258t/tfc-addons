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
        int owner = PcbFootprint.owner(topology, layer, cell);
        if (owner >= 0) {
            if (value != PcbTopology.EMPTY) return;
            PcbTopology.set(topology, layer, owner % PcbTopology.CELLS, PcbTopology.EMPTY);
        } else {
            PcbTopology.set(topology, layer, cell, value);
        }
        sync();
    }

    public boolean setPart(int layer, int cell, int definition) {
        if (!PcbFootprint.canPlace(topology, layer, cell, definition, -1)) return false;
        PcbTopology.set(topology, layer, cell, PcbTopology.COMPONENT);
        PcbTopology.setPart(topology, layer, cell, definition);
        sync();
        return true;
    }

    boolean updatePart(int layer, int anchor, int definition) {
        int index = layer * PcbTopology.CELLS + anchor;
        int[] definitions = topology.getIntArray(PcbWiring.KEY);
        if (index < 0 || index >= definitions.length || definitions[index] == 0
                || !PcbFootprint.canPlace(topology, layer, anchor, definition, index)) return false;
        PcbTopology.setPart(topology, layer, anchor, definition);
        sync();
        return true;
    }

    boolean movePart(int layer, int anchor, int target) {
        int[] definitions = topology.getIntArray(PcbWiring.KEY);
        int index = layer * PcbTopology.CELLS + anchor;
        if (index >= definitions.length || definitions[index] == 0
                || !PcbFootprint.canPlace(topology, layer, target, definitions[index], index)) return false;
        int definition = definitions[index];
        CompoundTag parameters = PcbParameters.get(topology, index).copy();
        long instance = PcbModuleIds.get(topology, index);
        PcbTopology.set(topology, layer, anchor, PcbTopology.EMPTY);
        PcbTopology.set(topology, layer, target, PcbTopology.COMPONENT);
        PcbTopology.setPart(topology, layer, target, definition);
        PcbParameters.put(topology, layer * PcbTopology.CELLS + target, parameters);
        PcbModuleIds.put(topology, layer * PcbTopology.CELLS + target, instance);
        PcbModuleIds.ensure(topology, layer * PcbTopology.CELLS + target);
        sync();
        return true;
    }

    boolean setParameters(int index, CompoundTag parameters) {
        int[] definitions = topology.getIntArray(PcbWiring.KEY);
        if (index < 0 || index >= definitions.length || !PcbWiring.valid(definitions[index])
                || !PcbParameters.valid(definitions[index], parameters)) return false;
        PcbParameters.put(topology, index, parameters);
        sync();
        return true;
    }

    boolean toggleEdge(int layer, int cell, int direction) {
        int next = PcbTopology.neighbor(cell, direction);
        if (next < 0 || PcbTopology.cut(topology, cell) || PcbTopology.cut(topology, next)
                || PcbFootprint.owner(topology, layer, cell) >= 0 || PcbFootprint.owner(topology, layer, next) >= 0) return false;
        PcbTopology.setEdgeCut(topology, layer, cell, direction, !PcbTopology.edgeCut(topology, layer, cell, direction));
        sync();
        return true;
    }

    void toggleCut(int cell) {
        for (int layer = 0; layer < PcbTopology.level(topology); layer++) {
            int owner = PcbFootprint.owner(topology, layer, cell);
            if (owner >= 0) PcbTopology.set(topology, layer, owner % PcbTopology.CELLS, PcbTopology.EMPTY);
        }
        PcbTopology.toggleCut(topology, cell);
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
        int fallback = stack.getItem() instanceof PcbDesignItem design
                ? design.fallbackLevel() : PcbBoardItem.level(stack);
        if (fallback == 0) return;
        CompoundTag tag = stack.get(PcbItems.PCB_TOPOLOGY.get());
        topology = tag == null ? PcbTopology.create(fallback) : PcbTopology.normalize(tag, fallback);
        sync();
    }

    public String saveDesign() {
        return saveDesign(null);
    }

    public String saveDesign(Player player) {
        String error = PcbTopology.validate(topology);
        if (error != null) return error;
        ItemStack stack = items.getItem(0);
        int level = PcbTopology.level(topology);
        boolean fromInventory = stack.isEmpty() && player != null && PcbWiring.active(topology);
        if (fromInventory) {
            stack = player.getInventory().items.stream()
                    .filter(candidate -> PcbBoardItem.isClean(candidate, level))
                    .findFirst().orElse(ItemStack.EMPTY);
        }
        if (fromInventory || PcbBoardItem.level(stack) != 0) {
            if (!PcbBoardItem.isClean(stack, level)) return PcbBoardItem.EDITOR_ERROR;
            if (!PcbWiring.active(topology)) return PcbWiring.ERROR;
            if (!fromInventory && stack.getCount() > 1 && player == null) return PcbBoardItem.EDITOR_ERROR;
            var parts = PcbWiring.compile(topology);
            if (parts == null || !PcbAssembly.consume(player, parts)) return PcbAssembly.MISSING;
            ItemStack result = stack.copyWithCount(1);
            stack.shrink(1);
            if (fromInventory) player.getInventory().setChanged();
            else if (!stack.isEmpty()) player.getInventory().placeItemBackInInventory(stack.copy());
            PcbTopology.attach(result, topology);
            CompoundTag assembled = result.get(PcbItems.PCB_TOPOLOGY.get()).copy();
            assembled.putBoolean(PcbAssembly.ASSEMBLED, true);
            result.set(PcbItems.PCB_TOPOLOGY.get(), assembled);
            items.setItem(0, result);
            sync();
            return null;
        }
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

    String salvage(Player player) {
        ItemStack stack = items.getItem(0);
        if (!stack.is(PcbItems.BURNT_BOARD.get()) || player == null) return PcbAssembly.NO_RECOVERY;
        CompoundTag design = stack.get(PcbItems.PCB_TOPOLOGY.get());
        var parts = design == null ? null : PcbWiring.compile(design);
        items.removeItem(0, 1);
        if (parts == null || !design.getBoolean(PcbAssembly.ASSEMBLED)) return PcbAssembly.NO_RECOVERY;
        return PcbAssembly.salvage(player, parts, design.getLong("BlownFuses")) > 0
                ? PcbAssembly.RECOVERED : PcbAssembly.NO_RECOVERY;
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
