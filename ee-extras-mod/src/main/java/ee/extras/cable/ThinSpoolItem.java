package ee.extras.cable;

import com.george_vi.electroenergetics.CEEDataComponents;
import com.george_vi.electroenergetics.CEEItems;
import com.george_vi.electroenergetics.config.CEEConfigs;
import com.george_vi.electroenergetics.content.bundled_wire.BundledWireItem;
import com.george_vi.electroenergetics.content.bundled_wire.BundledWireType;
import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.simibubi.create.AllSoundEvents;
import ee.extras.pcb.PcbCircuitBlock;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Катушка тонкого изолированного провода (#205): висячий двухжильный кабель с жилами обычного провода EE, тонкий на вид —
 * для подводки к приборам. Прокладывается как двухжильный кабель (CableEvents → CableLaying, вид DUPLEX), поэтому
 * подходит ко всем его концам, включая приборы с крышкой (CableCap). Наследник катушки EE — ради «пунктира» EE.
 */
public class ThinSpoolItem extends BundledWireItem {
    public ThinSpoolItem(Properties properties) {
        super(properties, BundledWireType.DUPLEX);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        InteractionResult result = useLead(context);
        return result == null ? super.useOn(context) : result;
    }

    InteractionResult useLead(UseOnContext context) {
        var level = context.getLevel();
        var pos = context.getClickedPos();
        var state = level.getBlockState(pos);
        var stack = context.getItemInHand();
        var player = context.getPlayer();
        InWorldNode first = stack.get(CEEDataComponents.SELECTED_NODE);
        if (player == null || (first == null && !(state.getBlock() instanceof PcbCircuitBlock))) return null;
        if (player.isShiftKeyDown()) {
            stack.remove(CEEDataComponents.SELECTED_NODE);
            stack.remove(CEEDataComponents.SELECTED_WIRE_TERMINATION);
            return InteractionResult.SUCCESS;
        }
        if (first == null && state.getBlock() instanceof PcbCircuitBlock
                && (stack.has(CEEDataComponents.SELECTED_WIRE_TERMINATION) || ee.extras.pcb.PcbPowerInlet.hit(level,pos,state,context.getClickLocation()))) return null;
        if (!(state.getBlock() instanceof ElectricalDeviceBlock<?> device) || CableCap.present(level, pos))
            return InteractionResult.FAIL;
        Vec3 hit = context.getClickLocation().subtract(Vec3.atLowerCornerOf(pos));
        InWorldNode target = null;
        double distance = state.getBlock() instanceof PcbCircuitBlock ? 1.0 / 64 : 1.5 * 1.5;
        for (var entry : device.getNodePositions(level, pos, state).entrySet()) {
            if (!device.isNodeAccessible(level,pos,state,entry.getKey())) continue;
            double candidate = hit.distanceToSqr(entry.getValue());
            if (candidate >= distance) continue;
            distance = candidate;
            target = new InWorldNode(entry.getKey(), pos);
        }
        if (target == null) return InteractionResult.FAIL;
        if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
        if (first == null) {
            stack.remove(CEEDataComponents.SELECTED_WIRE_TERMINATION);
            stack.set(CEEDataComponents.SELECTED_NODE, target);
            AllSoundEvents.WRENCH_ROTATE.playOnServer(level, pos);
            return InteractionResult.SUCCESS;
        }
        var origin = first.sourcePos();
        if (!level.isLoaded(origin)) return InteractionResult.FAIL;
        var originState = level.getBlockState(origin);
        if (!(originState.getBlock() instanceof PcbCircuitBlock board)
                || board.getNodePosition(level, origin, originState, first.id()) == null
                || !board.isNodeAccessible(level,origin,originState,first.id())) {
            stack.remove(CEEDataComponents.SELECTED_NODE);
            return InteractionResult.FAIL;
        }
        var wire = CableWires.PCB_LEAD.get();
        var network = InfrastructureSavedData.load(server);
        double limit = wire.getMaxLength();
        if (first.equals(target) || origin.distSqr(pos) > limit * limit || network.isConnected(first, target))
            return InteractionResult.FAIL;
        board.ensureNodesExist(server, origin, originState);
        device.ensureNodesExist(server, pos, state);
        if (!network.getNodes().contains(first) || !network.getNodes().contains(target)) return InteractionResult.FAIL;
        network.connect(first, target, wire);
        stack.remove(CEEDataComponents.SELECTED_NODE);
        AllSoundEvents.WRENCH_REMOVE.playOnServer(level, pos);
        if (!player.isCreative()) {
            stack.shrink(1);
            if (!CEEConfigs.server().alternateWirePlacement.get())
                player.getInventory().placeItemBackInInventory(CEEItems.EMPTY_SPOOL.asStack());
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
    }
}
