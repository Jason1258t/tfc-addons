package ie.extras.devtest;

import blusunrize.immersiveengineering.common.crafting.EarmuffsRecipe;
import blusunrize.immersiveengineering.common.register.IEDataComponents;
import blusunrize.immersiveengineering.common.register.IEItems;
import com.simibubi.create.AllItems;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;

final class EarmuffsCheck {
    static int run(CommandSourceStack source) {
        var recipe = new EarmuffsRecipe();
        var level = source.getLevel();
        var goggles = AllItems.GOGGLES.asStack();
        var earmuffs = new ItemStack(IEItems.Misc.EARMUFFS.asItem());
        earmuffs.set(DataComponents.CUSTOM_NAME, Component.literal("Проверка наушников"));
        var join = CraftingInput.of(2, 1, List.of(earmuffs, goggles));
        var attached = recipe.assemble(join, level.registryAccess());
        var split = CraftingInput.of(1, 1, List.of(attached));
        var repeat = CraftingInput.of(2, 1, List.of(attached, earmuffs));
        var extra = CraftingInput.of(3, 1, List.of(goggles, earmuffs, new ItemStack(Items.STICK)));
        var helmet = CraftingInput.of(2, 1, List.of(new ItemStack(Items.IRON_HELMET), earmuffs));
        if (!recipe.matches(join, level) || !recipe.matches(split, level)
                || !ItemStack.matches(goggles, recipe.assemble(split, level.registryAccess()))
                || !ItemStack.matches(earmuffs, recipe.getRemainingItems(split).getFirst())
                || recipe.matches(repeat, level) || recipe.matches(extra, level)
                || !recipe.matches(helmet, level)
                || !recipe.assemble(helmet, level.registryAccess()).has(IEDataComponents.CONTAINED_EARMUFF)) {
            source.sendFailure(Component.literal("Рецепты наушников: ошибка"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Рецепты наушников: крепление, снятие, запрет лишних предметов и обычный шлем — ОК"), false);
        return 1;
    }
}
