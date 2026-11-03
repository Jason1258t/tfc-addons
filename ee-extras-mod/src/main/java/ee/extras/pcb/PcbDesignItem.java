package ee.extras.pcb;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Чертёж или ручная маска с проверенной топологией; самих компонентов в предмете нет. */
public class PcbDesignItem extends Item {
    private final int fallbackLevel;

    public PcbDesignItem(Properties properties, int fallbackLevel) {
        super(properties);
        this.fallbackLevel = fallbackLevel;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag tag = stack.get(PcbItems.PCB_TOPOLOGY.get());
        if (tag == null) {
            tooltip.add(Component.translatable("item.ee_extras.pcb_design.empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        int level = PcbTopology.level(tag);
        tooltip.add(Component.translatable("item.ee_extras.pcb_design.level", level).withStyle(ChatFormatting.GRAY));
        String error = PcbTopology.validate(tag);
        tooltip.add((error == null
                        ? Component.translatable("item.ee_extras.pcb_design.valid")
                        : Component.literal(error))
                .withStyle(error == null ? ChatFormatting.GREEN : ChatFormatting.RED));
    }

    public int fallbackLevel() {
        return fallbackLevel;
    }
}
