package tfc.destroy.extras.electrolyzer;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.pump.PumpBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Форма 3×3×2: два боковых отсека Create, медная мембранная перегородка и настоящий насос. */
public final class ElectrolyzerStructure {
    public static final float MINIMUM_PUMP_RPM = 16.0F;

    private ElectrolyzerStructure() {}

    public static boolean hasShell(ServerLevel level, BlockPos controller, Direction facing) {
        for (int x = -1; x <= 1; x++) {
            for (int y = 0; y <= 2; y++) {
                for (int z = 0; z <= 1; z++) {
                    // Боковые отверстия нижнего яруса сохраняют существующие входной и NaOH-порты.
                    if ((y == 0 && z == 0) || (x == 1 && y == 1 && z == 1)) continue;
                    BlockPos pos = offset(controller, facing, x, y, z);
                    if (x != 0) {
                        if (!AllBlocks.FLUID_TANK.has(level.getBlockState(pos))) return false;
                    } else if (y == 1 && z == 1) {
                        if (!AllBlocks.MECHANICAL_PUMP.has(level.getBlockState(pos))) return false;
                    } else if (!AllBlocks.COPPER_CASING.has(level.getBlockState(pos))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public static float pumpRpm(ServerLevel level, BlockPos controller, Direction facing) {
        return level.getBlockEntity(offset(controller, facing, 0, 1, 1)) instanceof PumpBlockEntity pump
            ? Math.abs(pump.getSpeed()) : 0.0F;
    }

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
