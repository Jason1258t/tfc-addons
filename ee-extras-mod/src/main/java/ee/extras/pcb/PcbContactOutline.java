package ee.extras.pcb;

import com.george_vi.electroenergetics.CEEDataComponents;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;

/** Штатные цвета и выбор EE, но размер рамки соответствует маленькой площадке платы. */
public final class PcbContactOutline {
    private static final java.util.Map<String, Float> originalWidths = new java.util.HashMap<>();
    private PcbContactOutline() {}

    public static void update() {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        var held = mc.player.getMainHandItem();
        if (!(held.getItem() instanceof com.george_vi.electroenergetics.content.wire_spool.WireSpoolItem)
                && !(held.getItem() instanceof com.george_vi.electroenergetics.content.voxel_wire.VoxelWireItem)
                && !com.george_vi.electroenergetics.CEEItems.EMPTY_SPOOL.isIn(held)
                && !held.is(com.george_vi.electroenergetics.CEETags.SEE_NODE_DATA)) {
            reset("electroenergetics_selected_node", true);
            reset("electroenergetics_node_selection", true);
            return;
        }
        var selected = held.get(CEEDataComponents.SELECTED_NODE);
        resize(mc, "electroenergetics_selected_node", selected);
        if (mc.hitResult instanceof BlockHitResult hit)
            resize(mc, "electroenergetics_node_selection", InWorldNode.getHitNode(hit, mc.level));
        else reset("electroenergetics_node_selection", true);
    }

    private static void resize(Minecraft mc, String key, InWorldNode node) {
        if (node == null) { reset(key, true); return; }
        var state = mc.level.getBlockState(node.sourcePos());
        if (!(state.getBlock() instanceof PcbCircuitBlock) || !state.getValue(PcbCircuitBlock.CUSTOM)
                || !(mc.level.getBlockEntity(node.sourcePos()) instanceof PcbCircuitBlockEntity board)) { reset(key, false); return; }
        if (PcbConnector.contains(node.id())) {
            var point=((PcbCircuitBlock)state.getBlock()).getNodePosition(mc.level,node.sourcePos(),state,node.id());
            if(point==null) { reset(key,false); return; }
            var bounds=new net.minecraft.world.phys.AABB(point,point).inflate(.017).move(node.sourcePos());
            Outliner.getInstance().edit(key).ifPresent(parameters -> {
                originalWidths.putIfAbsent(key,parameters.getLineWidth());
                Outliner.getInstance().showAABB(key,bounds,3).lineWidth(.003f);
            });
            return;
        }
        int[] contacts = board.positionedContacts();
        if (contacts == null || node.id() < 0 || node.id() >= contacts.length) { reset(key, false); return; }
        var bounds = PcbCircuitBlock.contactBounds(contacts[node.id()], state.getValue(PcbCircuitBlock.FACING))
                .move(node.sourcePos()).inflate(.002);
        Outliner.getInstance().edit(key).ifPresent(parameters -> {
            originalWidths.putIfAbsent(key, parameters.getLineWidth());
            Outliner.getInstance().showAABB(key, bounds, 3).lineWidth(.003f);
        });
    }
    private static void reset(String key, boolean remove) {
        Float width = originalWidths.remove(key);
        if (width == null) return;
        if (remove) Outliner.getInstance().remove(key);
        else Outliner.getInstance().edit(key).ifPresent(parameters -> parameters.lineWidth(width));
    }
}
