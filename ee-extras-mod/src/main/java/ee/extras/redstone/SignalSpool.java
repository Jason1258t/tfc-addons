package ee.extras.redstone;

import com.george_vi.electroenergetics.CEEDataComponents;
import com.george_vi.electroenergetics.content.wire_spool.WireSpoolItem;
import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.network.chat.Component;

public class SignalSpool extends WireSpoolItem {
    public SignalSpool(Properties properties) { super(properties, () -> Signals.WIRE.get()); }

    @Override public InteractionResult useOn(UseOnContext context) {
        var player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        var stack = context.getItemInHand();
        if (player.isShiftKeyDown()) {
            ee.extras.interaction.WireCancel.clear(stack);
            return InteractionResult.SUCCESS;
        }
        var level = context.getLevel();
        var target = Signals.hit(level, context.getClickedPos(), context.getClickedFace(), context.getClickLocation());
        if (target == null) return InteractionResult.FAIL;
        if (!(level instanceof ServerLevel sl)) return InteractionResult.SUCCESS;
        if (!player.mayUseItemAt(context.getClickedPos(), context.getClickedFace(), stack)) return InteractionResult.FAIL;
        var first = stack.get(CEEDataComponents.SELECTED_NODE);
        if (first == null) {
            stack.set(CEEDataComponents.SELECTED_NODE, target);
            net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, stack,
                    tag -> tag.putString("ee_extras_anchor_dimension", sl.dimension().location().toString()));
            player.displayClientMessage(Component.translatable("ee_extras.signal.selected"), true);
            return InteractionResult.SUCCESS;
        }
        if (!stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY).copyTag().getString("ee_extras_anchor_dimension")
                .equals(sl.dimension().location().toString())) return InteractionResult.FAIL;
        if (!Signals.valid(sl, first) || !Signals.valid(sl, target) || first.equals(target)
                || first.getPosition(sl).distanceTo(target.getPosition(sl)) > 32) return InteractionResult.FAIL;
        var data = InfrastructureSavedData.load(sl);
        if (data.isConnected(first, target)) return InteractionResult.FAIL;
        for (var node : new com.george_vi.electroenergetics.foundation.nodes.InWorldNode[]{first, target}) {
            var state = sl.getBlockState(node.sourcePos());
            ((ElectricalDeviceBlock<?>) state.getBlock()).ensureNodesExist(sl, node.sourcePos(), state);
        }
        data.connect(first, target, Signals.WIRE.get());
        ee.extras.interaction.WireCancel.clear(stack);
        if (!player.isCreative()) {
            stack.shrink(1);
            if (!com.george_vi.electroenergetics.config.CEEConfigs.server().alternateWirePlacement.get())
                player.getInventory().placeItemBackInInventory(com.george_vi.electroenergetics.CEEItems.EMPTY_SPOOL.asStack());
        }
        return InteractionResult.SUCCESS;
    }

    // Отсоединённые силовые узлы не являются сигнальными контактами.
    @Override public void interactDetachedNode(com.george_vi.electroenergetics.foundation.nodes.InWorldNode node,
            net.minecraft.world.entity.player.Player player, net.minecraft.world.level.Level level,
            net.minecraft.world.item.ItemStack stack) {}
}
