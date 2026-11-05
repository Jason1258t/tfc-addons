package ee.extras.cable;

import ee.extras.TooltipBlockItem;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Крышка двухжильного кабеля: клик по прибору EE с двумя контактами ставит на него крышку (CableCap, CableEvents) */
public class CableCapItem extends Item {
    public CableCapItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        TooltipBlockItem.tooltip(getDescriptionId(), tooltip);
    }
}
