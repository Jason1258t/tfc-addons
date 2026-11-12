package tfc.destroy.extras.electrolyzer;

import blusunrize.immersiveengineering.api.multiblocks.BlockMatcher;
import blusunrize.immersiveengineering.common.blocks.multiblocks.IETemplateMultiblock;
import java.util.List;
import net.minecraft.core.BlockPos;
import tfc.destroy.extras.DestroyExtras;

/**
 * Шаблон 3×3×2 (data/destroy_extras/structure/multiblocks/electrolyzer.nbt). Координаты шаблона: x — слева направо
 * для игрока, стоящего перед машиной, y — вверх, z = 1 — передний ряд (сторона удара молотом), z = 0 — задний.
 */
public final class ElectrolyzerMultiblock extends IETemplateMultiblock {
    public static final BlockPos CELL = new BlockPos(1, 1, 1);
    public static final BlockPos SIZE = new BlockPos(3, 3, 2);

    /**
     * Баки, трубы Create и помпа EE меняют состояние от соседей (форма бака, соединения трубы, направление помпы),
     * поэтому для этих клеток достаточно того же блока. Сила 2 перекрывает точное сравнение IE (сила 1).
     */
    private static final BlockMatcher.MatcherPredicate SAME_BLOCK = (expected, found, level, pos) ->
        expected.getBlock() == found.getBlock() ? BlockMatcher.Result.allow(2) : BlockMatcher.Result.DEFAULT;

    public ElectrolyzerMultiblock() {
        super(DestroyExtras.rl("multiblocks/electrolyzer"), CELL, CELL, SIZE, ElectrolyzerLogic.REGISTRATION, List.of(SAME_BLOCK));
    }

    @Override
    public float getManualScale() {
        return 16.0F;
    }
}
