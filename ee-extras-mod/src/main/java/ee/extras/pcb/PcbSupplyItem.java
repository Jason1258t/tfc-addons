package ee.extras.pcb;

import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

final class PcbSupplyItem extends BlockItem {
    private static final String SETTING = "PcbSupplySetting";

    PcbSupplyItem(PcbSupplyBlock block) {
        super(block, new Properties().stacksTo(1));
    }

    static int setting(ItemStack stack) {
        var data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return data.contains(SETTING) ? Math.clamp(data.getInt(SETTING), 0, PcbSupplyBlock.VOLTS.length - 1)
                : PcbSupplyBlock.DEFAULT_SETTING;
    }

    static void configure(ItemStack stack, int setting) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, data -> data.putInt(SETTING, setting));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("ee_extras.pcb.supply.voltage", PcbSupplyBlock.VOLTS[setting(stack)]));
    }
}
