package tfcstudio.betterfluids;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;

final class IronTankItem extends BlockItem {
    IronTankItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        List<BlockPlaceContext> layer = findLayer(context);
        InteractionResult result = super.place(context);
        if (result.consumesAction()) {
            for (BlockPlaceContext extra : layer) {
                if (!super.place(extra).consumesAction()) {
                    break;
                }
            }
        }
        return result;
    }

    private List<BlockPlaceContext> findLayer(BlockPlaceContext context) {
        var player = context.getPlayer();
        var face = context.getClickedFace();
        var level = context.getLevel();
        if (level.isClientSide || player == null || player.isShiftKeyDown() || !face.getAxis().isVertical()) {
            return List.of();
        }

        var pos = context.getClickedPos();
        if (!(level.getBlockEntity(pos.relative(face.getOpposite())) instanceof IronTankEntity part)
            || !(part.getControllerBE() instanceof IronTankEntity controller)) {
            return List.of();
        }

        BlockPos base = controller.getBlockPos();
        BlockPos start = face == Direction.UP ? base.above(controller.getHeight()) : base.below();
        int width = controller.getWidth();
        if (pos.getY() != start.getY() || controller.getHeight() >= controller.getMaxLength(Direction.Axis.Y, width)) {
            return List.of();
        }

        var layer = new ArrayList<BlockPlaceContext>();
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < width; z++) {
                BlockPos target = start.offset(x, 0, z);
                if (target.equals(pos) || level.getBlockState(target).is(getBlock())) {
                    continue;
                }
                var extra = BlockPlaceContext.at(context, target, face);
                if (!extra.getClickedPos().equals(target) || !extra.canPlace()
                    || !level.mayInteract(player, target) || !player.mayUseItemAt(target, face, context.getItemInHand())
                    || !canPlace(extra, getBlock().defaultBlockState())) {
                    return List.of();
                }
                layer.add(extra);
            }
        }
        return player.isCreative() || context.getItemInHand().getCount() > layer.size() ? layer : List.of();
    }
}
