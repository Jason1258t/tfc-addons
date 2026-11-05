package tfcstudio.betterfluids;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.List;

final class IronTankItem extends BlockItem {
    IronTankItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        List<BlockPlaceContext> layer = findLayer(context);
        Set<BlockPos> group = attachmentGroup(context);
        InteractionResult result = placeSection(context, group);
        if (result.consumesAction()) {
            for (BlockPlaceContext extra : layer) {
                if (!placeSection(extra, group).consumesAction()) {
                    break;
                }
            }
        }
        return result;
    }

    private InteractionResult placeSection(BlockPlaceContext context, Set<BlockPos> group) {
        BlockPos pos = context.getClickedPos();
        InteractionResult result = super.place(context);
        if (result.consumesAction() && group != null && context.getLevel().getBlockEntity(pos) instanceof IronTankEntity part) {
            part.preserveBoundary(group);
            group.add(pos);
        }
        return result;
    }

    private Set<BlockPos> attachmentGroup(BlockPlaceContext context) {
        var level = context.getLevel();
        var support = context.getClickedPos().relative(context.getClickedFace().getOpposite());
        if (level.isClientSide || !(level.getBlockEntity(support) instanceof IronTankEntity part)) {
            return null;
        }
        var parts = part.networkParts();
        if (parts.stream().noneMatch(IronTankEntity::hasCutNeighbor)) {
            return null;
        }
        var group = new HashSet<BlockPos>();
        for (IronTankEntity member : parts) {
            group.add(member.getBlockPos());
        }
        return group;
    }

    private List<BlockPlaceContext> findLayer(BlockPlaceContext context) {
        var player = context.getPlayer();
        var face = context.getClickedFace();
        var level = context.getLevel();
        if (level.isClientSide || player == null || player.isShiftKeyDown() || !face.getAxis().isVertical()) {
            return List.of();
        }

        var pos = context.getClickedPos();
        if (!(level.getBlockEntity(pos.relative(face.getOpposite())) instanceof IronTankEntity part)) {
            return List.of();
        }

        int available = player.isCreative() ? Integer.MAX_VALUE : context.getItemInHand().getCount() - 1;
        var layer = new ArrayList<BlockPlaceContext>();
        for (IronTankEntity member : part.connectedParts(direction -> direction.getAxis().isHorizontal())) {
            BlockPos target = member.getBlockPos().relative(face);
            if (target.equals(pos) || level.getBlockState(target).is(getBlock())) {
                continue;
            }
            if (layer.size() >= available) {
                break;
            }
            var extra = BlockPlaceContext.at(context, target, face);
            if (!extra.getClickedPos().equals(target) || !extra.canPlace()
                || !level.mayInteract(player, target) || !player.mayUseItemAt(target, face, context.getItemInHand())
                || !canPlace(extra, getBlock().defaultBlockState())) {
                return List.of();
            }
            layer.add(extra);
        }
        return layer;
    }
}
