package small.diesel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidUtil;

public final class FuelContainers {
    private static final ResourceLocation JERRYCAN = ResourceLocation.fromNamespaceAndPath("immersiveengineering", "jerrycan");

    private FuelContainers() {}

    public static boolean interact(Player player, InteractionHand hand, Level level, BlockPos pos) {
        ItemStack stack = player.getItemInHand(hand);
        if (!JERRYCAN.equals(BuiltInRegistries.ITEM.getKey(stack.getItem())) || FluidUtil.getFluidContained(stack).isEmpty()) {
            return FluidUtil.interactWithFluidHandler(player, hand, level, pos, null);
        }

        // Канистра с топливом заправляет бак, даже если в канистре ещё есть свободное место.
        return FluidUtil.getFluidHandler(level, pos, null).map(tank -> {
            var transfer = FluidUtil.tryEmptyContainerAndStow(stack, tank,
                    player.getCapability(Capabilities.ItemHandler.ENTITY), Integer.MAX_VALUE, player, true);
            if (transfer.isSuccess()) player.setItemInHand(hand, transfer.getResult());
            return transfer.isSuccess();
        }).orElse(false);
    }
}
