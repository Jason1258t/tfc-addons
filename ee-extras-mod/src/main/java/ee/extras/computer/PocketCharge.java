package ee.extras.computer;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

public final class PocketCharge {
    private static final String KEY = "ee_extras:pocket_charge";
    // 18 Вт·ч, в единицах Вт·тик симуляции EE.
    public static final int CAPACITY = 18 * 3600 * 20;

    private PocketCharge() {}

    public static boolean isPocket(ItemStack stack) {
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id.getNamespace().equals("computercraft")
                && (id.getPath().equals("pocket_computer_normal") || id.getPath().equals("pocket_computer_advanced"));
    }

    public static void addTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (isPocket(stack)) {
            event.getToolTip().add(Component.translatable("ee_extras.computer_power.battery",
                    100 * get(stack) / CAPACITY));
        }
    }

    public static int get(ItemStack stack) {
        return Math.clamp(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getInt(KEY),
                0, CAPACITY);
    }

    public static void set(ItemStack stack, int charge) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(KEY, Math.clamp(charge, 0, CAPACITY)));
    }
}
