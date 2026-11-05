package ee.extras.cable;

import com.george_vi.electroenergetics.content.bundled_wire.BundledWireItem;
import com.george_vi.electroenergetics.content.bundled_wire.BundledWireType;
import java.util.List;
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
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
    }
}
