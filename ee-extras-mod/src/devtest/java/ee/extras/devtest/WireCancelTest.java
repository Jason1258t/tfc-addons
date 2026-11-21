package ee.extras.devtest;

import com.george_vi.electroenergetics.CEEDataComponents;
import com.george_vi.electroenergetics.CEEItems;
import com.george_vi.electroenergetics.content.bundled_wire.BundledWireTerminationState;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import ee.extras.EeExtras;
import ee.extras.interaction.WireCancel;
import ee.extras.lines.Tension;
import ee.extras.panel.PanelModules;
import ee.extras.wire.WireComponents;
import ee.extras.wire.SurfaceWires;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

final class WireCancelTest {
    static int run(CommandSourceStack src) {
        var stacks = new ItemStack[]{new ItemStack(CEEItems.DUPLEX_WIRE_SPOOL.get(),4),
                new ItemStack(EeExtras.FOUR_CORE_SPOOL.get(),4), new ItemStack(EeExtras.THIN_SPOOL.get(),4),
                new ItemStack(EeExtras.THIN_SINGLE_SPOOL.get(),4),new ItemStack(EeExtras.DECORATIVE_SINGLE_SPOOL.get(),4),
                new ItemStack(SurfaceWires.DUPLEX_ITEM.get(),4),new ItemStack(Tension.CLAMP_ITEM.get(),4)};
        for(var stack:stacks) {
            seed(stack);
            var player=CapTest.player(src.getLevel(),stack);
            WireCancel.cancel(player,InteractionHand.MAIN_HAND);
            if (stack.has(CEEDataComponents.SELECTED_NODE) || stack.has(CEEDataComponents.SELECTED_WIRE_TERMINATION)
                    || stack.has(PanelModules.SELECTED_PORT.get()) || stack.has(WireComponents.SELECTION.get())
                    || stack.getCount()!=4 || !stack.get(DataComponents.CUSTOM_DATA).copyTag().getBoolean("keep")
                    || stack.get(DataComponents.CUSTOM_DATA).copyTag().contains("ee_extras_anchor_dimension"))
                throw new IllegalStateException("Отмена не очистила выбор или изменила предмет: "+stack);
        }
        var stone=new ItemStack(Items.STONE,4);seed(stone);
        if(WireCancel.clear(stone) || !stone.has(CEEDataComponents.SELECTED_NODE))
            throw new IllegalStateException("Отмена затронула неподходящий предмет");
        src.sendSuccess(()->Component.literal("ОТМЕНА PASS: 7 видов катушек/зажимов, все выборы очищены, расхода нет, чужие данные сохранены"),false);
        return 1;
    }
    private static void seed(ItemStack stack) {
        stack.set(CEEDataComponents.SELECTED_NODE,new InWorldNode(0,BlockPos.ZERO));
        stack.set(CEEDataComponents.SELECTED_WIRE_TERMINATION,new BundledWireTerminationState(BlockPos.ZERO,Direction.UP,false,false));
        stack.set(PanelModules.SELECTED_PORT.get(),new InWorldNode(2,BlockPos.ZERO));
        var tag=new CompoundTag();tag.putString("ee_extras_anchor_dimension","minecraft:overworld");tag.putBoolean("keep",true);
        stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
        stack.set(WireComponents.SELECTION.get(),CustomData.of(new CompoundTag()));
    }
    private WireCancelTest() {}
}
