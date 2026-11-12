package tfc.destroy.extras.devtest;

import blusunrize.immersiveengineering.api.multiblocks.blocks.logic.IMultiblockBE;
import blusunrize.immersiveengineering.api.multiblocks.blocks.util.MultiblockOrientation;
import blusunrize.immersiveengineering.api.multiblocks.blocks.util.RelativeBlockFace;
import com.simibubi.create.AllBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import tfc.destroy.extras.DestroyExtras;
import tfc.destroy.extras.electrolyzer.ElectrolyzerLogic;
import tfc.destroy.extras.electrolyzer.ElectrolyzerMultiblock;
import tfc.destroy.extras.electrolyzer.MembraneElectrolyzerBlock;

/**
 * Стенд мультиблока электролизёра: ставит блоки шаблона вокруг ячейки и собирает машину так же, как удар молотом IE
 * по грани {@code side} (игрок стоит с этой стороны). Координаты шаблона — как в {@link ElectrolyzerMultiblock}.
 */
final class ElectrolyzerRig {
    private ElectrolyzerRig() {}

    /** Мировая позиция клетки шаблона при ячейке {@code cell} и ударе по грани {@code side}. */
    static BlockPos world(BlockPos cell, Direction side, int x, int y, int z) {
        MultiblockOrientation orientation = new MultiblockOrientation(side.getOpposite(), false);
        return cell.offset(orientation.getAbsoluteOffset(new BlockPos(x, y, z).subtract(ElectrolyzerMultiblock.CELL)));
    }

    /** Сторона клетки шаблона (машина лицом к игроку на юге) → сторона в мире. */
    static Direction worldSide(Direction side, Direction templateSide) {
        if (templateSide.getAxis().isVertical()) return templateSide;
        RelativeBlockFace relative = RelativeBlockFace.from(new MultiblockOrientation(Direction.NORTH, false), templateSide);
        return relative.forFront(new MultiblockOrientation(side.getOpposite(), false));
    }

    static BlockPos power(BlockPos cell, Direction side) {
        BlockPos p = ElectrolyzerLogic.POWER;
        return world(cell, side, p.getX(), p.getY(), p.getZ());
    }

    /** Клетка с электропомпой — вход жидкости. */
    static BlockPos input(BlockPos cell, Direction side) {
        return world(cell, side, 2, 0, 0);
    }

    static void place(ServerLevel level, BlockPos cell, Direction side) {
        // Уже собранную машину сначала разобрать целиком (как игрок, сломав блок), потом ставить шаблон заново.
        if (level.getBlockEntity(cell) instanceof IMultiblockBE<?> be) be.getHelper().disassemble();
        Block pump = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("electroenergetics", "electric_pump"));
        for (int x = 0; x <= 2; x++) {
            for (int y = 0; y <= 2; y++) {
                for (int z = 0; z <= 1; z++) {
                    BlockState state;
                    if (x == 1) {
                        state = y == 1 && z == 1
                            ? DestroyExtras.ELECTROLYZER_BLOCK.get().defaultBlockState().setValue(MembraneElectrolyzerBlock.FACING, side)
                            : AllBlocks.COPPER_CASING.getDefaultState();
                    } else if (y == 2) {
                        state = AllBlocks.FLUID_PIPE.getDefaultState();
                    } else if (x == 2 && y == 0 && z == 0) {
                        state = pump.defaultBlockState();
                    } else {
                        state = AllBlocks.FLUID_TANK.getDefaultState();
                    }
                    level.setBlock(world(cell, side, x, y, z), state, Block.UPDATE_ALL);
                }
            }
        }
    }

    /** Собрать, как молот IE: true — структура совпала. */
    static boolean form(ServerLevel level, BlockPos cell, Direction side) {
        return DestroyExtras.ELECTROLYZER_MULTIBLOCK.createStructure(level, cell, side, null);
    }

    /** Какие клетки не совпали с шаблоном (для отчёта, когда сборка не удалась). */
    static String mismatches(ServerLevel level, BlockPos cell, Direction side) {
        StringBuilder out = new StringBuilder();
        for (var info : DestroyExtras.ELECTROLYZER_MULTIBLOCK.getStructure(level)) {
            BlockPos pos = world(cell, side, info.pos().getX(), info.pos().getY(), info.pos().getZ());
            BlockState found = level.getBlockState(pos);
            if (found.getBlock() != info.state().getBlock()) out.append(' ').append(info.pos().toShortString()).append('=').append(found);
        }
        return out.toString();
    }

    static ElectrolyzerLogic.State state(ServerLevel level, BlockPos cell) {
        if (level.getBlockEntity(cell) instanceof IMultiblockBE<?> be && be.getHelper().getState() instanceof ElectrolyzerLogic.State state) {
            return state;
        }
        throw new IllegalStateException("Электролизёр не собран у " + cell);
    }
}
