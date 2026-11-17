package aero.extra;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;

final class FuelHoseItem extends Item {
    FuelHoseItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        var level = context.getLevel();
        var player = context.getPlayer();
        BlockPos pos = context.getClickedPos();
        if (player == null || !FuelHoses.endpoint(level.getBlockState(pos))) return InteractionResult.PASS;
        if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;

        FuelHoses hoses = FuelHoses.get(server);
        if (player.isShiftKeyDown()) {
            stack.remove(DataComponents.CUSTOM_DATA);
            FuelHoses.drop(server, pos, hoses.disconnect(server, pos));
            return InteractionResult.CONSUME;
        }

        String dimension = server.dimension().location().toString();
        CompoundTag selection = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!dimension.equals(selection.getString("Dimension"))) {
            selection.putString("Dimension", dimension);
            selection.putLong("Start", pos.asLong());
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(selection));
            player.displayClientMessage(Component.translatable(AeroExtra.MOD_ID + ".fuel_hose.selected"), true);
            return InteractionResult.CONSUME;
        }

        BlockPos first = BlockPos.of(selection.getLong("Start"));
        stack.remove(DataComponents.CUSTOM_DATA);
        int spent = hoses.connect(server, first, pos, player.isCreative() ? Integer.MAX_VALUE : stack.getCount());
        if (spent == 0) {
            player.displayClientMessage(Component.translatable(AeroExtra.MOD_ID + ".fuel_hose.rejected"), true);
        } else if (!player.isCreative()) {
            stack.shrink(spent);
        }
        return InteractionResult.CONSUME;
    }
}
