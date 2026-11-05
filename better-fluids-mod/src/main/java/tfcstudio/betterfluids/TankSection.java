package tfcstudio.betterfluids;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

enum TankSection {
    FULL(14_000, Shapes.block()),
    PLATE(7_000, Block.box(0, 0, 0, 16, 8, 16)),
    QUARTER(3_500, Block.box(4, 0, 4, 12, 16, 12)),
    BEVEL(7_000, bevel());

    final int capacity;
    final VoxelShape shape;

    TankSection(int capacity, VoxelShape shape) {
        this.capacity = capacity;
        this.shape = shape;
    }

    private static VoxelShape bevel() {
        var result = Shapes.empty();
        for (int x = 0; x < 16; x++) {
            double inset = Math.abs(x + 0.5 - 8);
            result = Shapes.or(result, Block.box(x, 0, inset, x + 1, 16, 16 - inset));
        }
        return result.optimize();
    }
}
