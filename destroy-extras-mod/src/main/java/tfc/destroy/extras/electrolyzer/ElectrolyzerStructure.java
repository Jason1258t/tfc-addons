package tfc.destroy.extras.electrolyzer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** Повороты локальной схемы блока-машины с FACING (сейчас — травильная ванна): стороны портов и точки контактов EE. */
public final class ElectrolyzerStructure {
    private ElectrolyzerStructure() {}

    /** Смещение из локальной схемы (север — перед, восток — слева от передней грани) в мир. */
    public static BlockPos offset(BlockPos controller, Direction facing, int x, int y, int z) {
        Direction right = clockwise(facing, Direction.EAST);
        Direction back = clockwise(facing, Direction.SOUTH);
        return controller.offset(
            right.getStepX() * x + back.getStepX() * z,
            y,
            right.getStepZ() * x + back.getStepZ() * z);
    }

    /** Точка узла относительно угла блока (как у EE); локальная схема поворачивается вокруг центра блока. */
    public static Vec3 node(Direction facing, Vec3 local) {
        Direction right = clockwise(facing, Direction.EAST);
        Direction back = clockwise(facing, Direction.SOUTH);
        double lx = local.x - 0.5;
        double lz = local.z - 0.5;
        return new Vec3(
            0.5 + right.getStepX() * lx + back.getStepX() * lz,
            local.y,
            0.5 + right.getStepZ() * lx + back.getStepZ() * lz);
    }

    /** Сторона блока в мире → сторона в локальной схеме (верх и низ не поворачиваются). */
    public static Direction toLocal(Direction facing, Direction worldSide) {
        if (!worldSide.getAxis().isHorizontal()) return worldSide;
        Direction local = worldSide;
        for (int i = 0; i < steps(facing); i++) local = local.getCounterClockWise();
        return local;
    }

    private static Direction clockwise(Direction facing, Direction local) {
        Direction world = local;
        for (int i = 0; i < steps(facing); i++) world = world.getClockWise();
        return world;
    }

    private static int steps(Direction facing) {
        return switch (facing) {
            case NORTH -> 0;
            case EAST -> 1;
            case SOUTH -> 2;
            default -> 3;
        };
    }
}
