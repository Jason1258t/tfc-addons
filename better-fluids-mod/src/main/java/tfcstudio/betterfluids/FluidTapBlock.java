package tfcstudio.betterfluids;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.fluids.tank.FluidTankBlock;
import net.createmod.catnip.math.VoxelShaper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidUtil;

final class FluidTapBlock extends DirectionalBlock {
    private static final MapCodec<FluidTapBlock> CODEC = simpleCodec(FluidTapBlock::new);
    private static final VoxelShaper SHAPE = VoxelShaper.forDirectional(box(4, 4, 6, 12, 12, 16), Direction.NORTH);

    @Override
    protected MapCodec<FluidTapBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE.get(state.getValue(FACING));
    }

    FluidTapBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        var state = defaultBlockState().setValue(FACING, context.getClickedFace());
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.relative(state.getValue(FACING).getOpposite())).getBlock() instanceof FluidTankBlock;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction side, BlockState neighbor,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return side == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos)
            ? Blocks.AIR.defaultBlockState()
            : super.updateShape(state, side, neighbor, level, pos, neighborPos);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (FluidUtil.getFluidHandler(stack.copyWithCount(1)).isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            var face = state.getValue(FACING);
            FluidUtil.getFluidHandler(level, pos.relative(face.getOpposite()), face).ifPresent(tank -> {
                var transfer = FluidUtil.tryFillContainerAndStow(stack, tank,
                    player.getCapability(Capabilities.ItemHandler.ENTITY), Integer.MAX_VALUE, player, true);
                if (transfer.isSuccess()) player.setItemInHand(hand, transfer.getResult());
            });
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
}
