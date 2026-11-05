package ee.extras.wire;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

/**
 * Отрезок провода вдоль оси: направление и длина в шагах сетки 1/16 блока.
 *
 * Перенесено из Create: Power Grid 0.6.2 (BlockWireEntity.Point, Apache-2.0, авторы patryk3211, Da-Negy, rvndm,
 * casvara; см. NOTICE). Изменения: отдельный класс, добавлен {@link #reversed()}.
 */
public final class Point {
    public static final int GRID = 16;

    /** Начало отрезка в мире; считается в SurfaceWireEntity.bake */
    public Vec3 start = Vec3.ZERO;
    public final Direction direction;
    public final int gridLength;

    public Point(Direction direction, int gridLength) {
        this.direction = direction;
        this.gridLength = gridLength;
    }

    public Point(CompoundTag tag) {
        this(Direction.from3DDataValue(tag.getInt("Direction")), tag.getInt("Length"));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Direction", direction.get3DDataValue());
        tag.putInt("Length", gridLength);
        return tag;
    }

    public float length() {
        return (float) gridLength / GRID;
    }

    public Vec3 vector() {
        return Vec3.atLowerCornerOf(direction.getNormal()).scale(length());
    }

    public Point reversed() {
        return new Point(direction.getOpposite(), gridLength);
    }

    public static Point along(Direction.Axis axis, float length) {
        Direction d = Direction.fromAxisAndDirection(axis, length >= 0 ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
        return new Point(d, (int) (Math.abs(length) * GRID));
    }
}
