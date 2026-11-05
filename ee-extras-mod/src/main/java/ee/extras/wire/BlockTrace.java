package ee.extras.wire;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Поиск пути провода по поверхностям: A* по сетке 1/16 блока, ходы вдоль осей до ближайшей преграды или границы
 * блока; клетка с опорой — рядом блок-проводник (isRedstoneConductor), без опоры — не больше 16 шагов подряд; не
 * дальше 160 шагов и 150 клеток за клик. Прямой путь (alternatePath) — до 16 шагов по каждой оси, без поиска.
 *
 * Перенесено из Create: Power Grid 0.6.2 (org.patryk3211.powergrid.utility.BlockTrace, Apache-2.0, авторы patryk3211,
 * Da-Negy, rvndm, casvara; см. NOTICE). Изменения: ITerminalPlacement → Terminal (точка кабеля прибора: луч может
 * заходить в его форму не дальше TERMINAL_REACH от неё), Pair из catnip → record Trace.
 */
public final class BlockTrace {
    public static final int GRID_SIZE = 16;
    public static final float UNIT_SIZE = 0.0625f;
    public static final float HALF_UNIT = 0.03125f;
    static final double TERMINAL_REACH = 0.5;
    /** Наибольшая длина пути за один клик, шагов сетки (10 блоков) */
    public static final int MAX_DISTANCE = 160;

    /** Прибор на конце: луч пропускается внутрь его блока рядом с точкой кабеля */
    public record Terminal(BlockPos pos, Vec3 origin) {
        boolean check(BlockPos block, Vec3 hit) {
            return block.equals(pos) && hit.distanceTo(origin) <= TERMINAL_REACH;
        }
    }

    public record Result(List<Point> points, boolean reachedTarget) {
        public float length() {
            float l = 0;
            for (Point p : points) l += p.length();
            return l;
        }
    }

    public record Trace(State state, Result result) {
    }

    public static BlockHitResult raycast(Level world, Vec3 start, Vec3 end, Terminal passThrough) {
        return BlockGetter.traverseBlocks(start, end, null, (ctx, pos) -> {
            BlockState blockState = world.getBlockState(pos);
            VoxelShape shape = blockState.getShape(world, pos);
            BlockHitResult hit = world.clipWithInteractionOverride(start, end, pos, shape, blockState);
            return hit != null && passThrough != null && passThrough.check(pos, hit.getLocation()) ? null : hit;
        }, ctx -> {
            Vec3 heading = start.subtract(end);
            return BlockHitResult.miss(end, Direction.getNearest(heading.x, heading.y, heading.z), BlockPos.containing(end));
        });
    }

    private static Point makePoint(Vec3 start, Vec3 end) {
        double dx = end.x - start.x, dy = end.y - start.y, dz = end.z - start.z;
        double lx = Math.abs(dx), ly = Math.abs(dy), lz = Math.abs(dz);
        if (lx > ly && lx > lz) return Point.along(Direction.Axis.X, (float) dx);
        return ly > lz ? Point.along(Direction.Axis.Y, (float) dy) : Point.along(Direction.Axis.Z, (float) dz);
    }

    /**
     * Путь к прибору: сначала к точке кабеля (луч заходит в форму прибора у неё), а если она глубоко внутри формы и
     * туда не пройти (фонарь IE: гнездо в середине основания) — вплотную к ближайшей к началу грани формы, напротив
     * точки кабеля. Своё, в Power Grid клеммы лежат на поверхности приборов.
     */
    public static Result findPathToDevice(Level world, Vec3 start, BlockPos device, Vec3 node, Direction continueDirection) {
        Result r = findPath(world, start, node, new Terminal(device, node), continueDirection);
        if (r != null && r.reachedTarget()) return r;
        List<AABB> boxes = world.getBlockState(device).getShape(world, device).toAabbs();
        if (boxes.isEmpty()) return r;
        AABB box = boxes.get(0);
        for (AABB b : boxes) box = box.minmax(b);
        box = box.move(device);
        List<Vec3> faces = new ArrayList<>();
        for (Direction d : Direction.values()) {
            double x = Math.clamp(node.x, box.minX, box.maxX), y = Math.clamp(node.y, box.minY, box.maxY), z = Math.clamp(node.z, box.minZ, box.maxZ);
            Vec3 p = switch (d) {
                case DOWN -> new Vec3(x, box.minY, z);
                case UP -> new Vec3(x, box.maxY, z);
                case NORTH -> new Vec3(x, y, box.minZ);
                case SOUTH -> new Vec3(x, y, box.maxZ);
                case WEST -> new Vec3(box.minX, y, z);
                case EAST -> new Vec3(box.maxX, y, z);
            };
            faces.add(p.relative(d, HALF_UNIT));
        }
        faces.sort(Comparator.comparingDouble(start::distanceToSqr));
        for (Vec3 f : faces) {
            Result fr = findPath(world, start, f, null, continueDirection);
            if (fr != null && fr.reachedTarget()) return fr;
        }
        return r;
    }

    /** К центру клетки сетки */
    public static Vec3 alignPosition(Vec3 p) {
        return new Vec3(Math.round(p.x * 16.0) / 16.0 - HALF_UNIT, Math.round(p.y * 16.0) / 16.0 - HALF_UNIT,
                Math.round(p.z * 16.0) / 16.0 - HALF_UNIT);
    }

    public static Result findPath(Level world, Vec3 start, Vec3 end, Terminal terminal, Direction continueDirection) {
        Trace t = findPathWithState(world, start, end, terminal, continueDirection);
        return t == null ? null : t.result();
    }

    private static Cell testDirection(Direction direction, Cell cell, State state, PriorityQueue<Cell> queue, int scoreAdjust) {
        Vec3i next = state.findNextPosition(cell, direction);
        if (next == null) return null;
        Cell neighbor = state.getCell(next);
        int distance = cell.position.distManhattan(next);
        int newDistance = cell.originDistance + Math.max(distance + (neighbor.isSupported ? scoreAdjust : 0), 1);
        if (newDistance >= neighbor.originDistance) return null;
        if (!neighbor.isSupported) {
            int newUnsupported = cell.unsupportedDistance + distance;
            if (neighbor.unsupportedDistance != 0 && newUnsupported >= neighbor.unsupportedDistance) return null;
            neighbor.unsupportedDistance = newUnsupported;
        }
        if (neighbor.unsupportedDistance > 16) return null;
        neighbor.originDistance = newDistance;
        neighbor.backtrace = cell;
        queue.add(neighbor);
        return neighbor;
    }

    public static Trace findPathWithState(Level world, Vec3 start, Vec3 end, Terminal terminal, Direction continueDirection) {
        State state = new State(world, start, end, terminal);
        if (state.target.equals(state.originCell.position) || !state.getCell(state.target).isSupported) return null;
        PriorityQueue<Cell> queue = new PriorityQueue<>(Comparator.comparingInt(state::score));
        queue.add(state.originCell);
        int bestScore = Integer.MAX_VALUE;
        Cell bestCell = null;
        while (!queue.isEmpty()) {
            Cell cell = queue.poll();
            if (cell.originDistance > MAX_DISTANCE) continue;
            if (cell.position.distManhattan(state.target) <= 1) return new Trace(state, new Result(state.traceResult(cell), true));
            if (state.states.size() > 150) break;
            Direction prev = cell.cellDirection();
            if (prev == null) prev = continueDirection;
            if (prev != null) {
                Cell n = testDirection(prev, cell, state, queue, -1);
                if (n != null && state.targetDistance(n) < bestScore) {
                    bestScore = state.targetDistance(n);
                    bestCell = n;
                }
            }
            for (Direction d : Direction.values()) {
                if (d == prev || prev != null && d == prev.getOpposite()) continue;
                Cell n = testDirection(d, cell, state, queue, 0);
                if (n != null && state.targetDistance(n) < bestScore) {
                    bestScore = state.targetDistance(n);
                    bestCell = n;
                }
            }
        }
        return new Trace(state, bestCell != null ? new Result(state.traceResult(bestCell), false) : null);
    }

    public static Result alternatePath(Vec3 start, Vec3 end) {
        List<Point> points = new ArrayList<>();
        double x = Math.floor((end.x - start.x) * 16.0) / 16.0;
        double y = Math.floor((end.y - start.y) * 16.0) / 16.0;
        double z = Math.floor((end.z - start.z) * 16.0) / 16.0;
        if (Math.abs(x) > 16.0) x = Math.signum(x) * 16.0;
        if (Math.abs(y) > 16.0) y = Math.signum(y) * 16.0;
        if (Math.abs(z) > 16.0) z = Math.signum(z) * 16.0;
        if (x != 0) points.add(Point.along(Direction.Axis.X, (float) x));
        if (y != 0) points.add(Point.along(Direction.Axis.Y, (float) y));
        if (z != 0) points.add(Point.along(Direction.Axis.Z, (float) z));
        points.sort(Comparator.comparingInt(a -> a.gridLength));
        return new Result(points, true);
    }

    static final class Cell {
        final Vec3i position;
        int originDistance;
        Cell backtrace;
        final boolean isSupported;
        int unsupportedDistance;

        Cell(Vec3i position, int originDistance, boolean isSupported) {
            this.position = position;
            this.originDistance = originDistance;
            this.isSupported = isSupported;
        }

        Direction cellDirection() {
            if (backtrace == null) return null;
            Vec3i a = backtrace.position, b = position;
            return Direction.fromDelta(b.getX() - a.getX(), b.getY() - a.getY(), b.getZ() - a.getZ());
        }
    }

    public static final class State {
        final Map<Vec3i, Cell> states = new HashMap<>();
        final Level world;
        final Vec3 origin;
        final Vec3i target;
        final Cell originCell;
        final Terminal terminal;

        State(Level world, Vec3 origin, Vec3 target, Terminal terminal) {
            this.world = world;
            this.terminal = terminal;
            this.origin = new Vec3(Math.floor(origin.x), Math.floor(origin.y), Math.floor(origin.z));
            this.target = transform(target);
            Vec3i originPos = transform(origin);
            this.originCell = createCell(originPos, 0);
        }

        Vec3i transform(Vec3 p) {
            return new Vec3i((int) Math.floor((p.x - origin.x) * 16.0), (int) Math.floor((p.y - origin.y) * 16.0),
                    (int) Math.floor((p.z - origin.z) * 16.0));
        }

        Vec3 transform(Vec3i p) {
            return new Vec3(p.getX() * UNIT_SIZE + origin.x + HALF_UNIT, p.getY() * UNIT_SIZE + origin.y + HALF_UNIT,
                    p.getZ() * UNIT_SIZE + origin.z + HALF_UNIT);
        }

        boolean isSupport(Vec3i position) {
            BlockPos bp = BlockPos.containing(transform(position));
            return world.getBlockState(bp).isRedstoneConductor(world, bp);
        }

        Cell createCell(Vec3i position, int length) {
            boolean supported = false;
            for (Direction d : Direction.values()) {
                if (position.equals(target) || isSupport(position.offset(d.getNormal()))) {
                    supported = true;
                    break;
                }
            }
            Cell cell = new Cell(position, length, supported);
            states.put(position, cell);
            return cell;
        }

        Cell getCell(Vec3i pos) {
            Cell c = states.get(pos);
            return c != null ? c : createCell(pos, Integer.MAX_VALUE);
        }

        int score(Cell cell) {
            return cell.originDistance + targetDistance(cell);
        }

        int targetDistance(Cell cell) {
            return cell.position.distManhattan(target);
        }

        static int offsetToFullBlock(int c, Direction.AxisDirection dir) {
            if (dir == Direction.AxisDirection.POSITIVE) return c >= 0 ? 16 - c % 16 : -(c % 16);
            return c >= 0 ? -(c % 16) : -(16 + c % 16);
        }

        boolean insideShape(Vec3i cellPos) {
            Vec3 p = transform(cellPos);
            BlockPos pos = BlockPos.containing(p);
            Vec3 local = p.subtract(pos.getX(), pos.getY(), pos.getZ());
            for (AABB box : world.getBlockState(pos).getCollisionShape(world, pos).toAabbs()) if (box.contains(local)) return true;
            return false;
        }

        Vec3i raycastNextPosition(Cell cell, Direction dir) {
            Direction.Axis axis = dir.getAxis();
            Vec3i cur = cell.position;
            int axial = Math.abs(target.get(axis) - cur.get(axis));
            BlockHitResult hit = null;
            for (Direction off : Direction.values()) {
                if (off.getAxis() == axis) continue;
                Vec3 castStart = transform(cell.position).relative(off, HALF_UNIT);
                Vec3 castEnd = castStart.relative(dir, axial * UNIT_SIZE);
                hit = raycast(world, castStart, castEnd, terminal);
                if (!hit.isInside()) break;
            }
            if (hit == null) return null;
            if (hit.isInside()) {
                int offset = offsetToFullBlock(cur.get(axis), dir.getAxisDirection());
                Vec3i outside = cur.relative(axis, offset);
                if (Math.abs(offset) > 8) {
                    if (insideShape(outside)) return null;
                } else if (insideShape(outside)) {
                    return outside.relative(dir, 1);
                }
                return outside;
            }
            Vec3i cellPos = transform(hit.getLocation());
            if (hit.getType() != HitResult.Type.MISS && hit.getDirection().getAxisDirection() == Direction.AxisDirection.NEGATIVE)
                cellPos = cellPos.relative(hit.getDirection());
            return hit.getType() == HitResult.Type.ENTITY ? null : cellPos;
        }

        Vec3i findNextPosition(Cell cell, Direction dir) {
            Direction.Axis axis = dir.getAxis();
            Vec3i cur = cell.position;
            Vec3i cast = raycastNextPosition(cell, dir);
            if (cast == null || cur.equals(cast)) return null;
            int c = cur.get(axis);
            int boundary = offsetToFullBlock(c, dir.getAxisDirection());
            if (boundary == 0) boundary = -16;
            int next = c + boundary;
            int castC = cast.get(axis);
            return Math.abs(next - c) > Math.abs(castC - c) ? cur.relative(axis, castC - c) : cur.relative(axis, next - c);
        }

        List<Vec3i> traceback(Cell cell) {
            List<Vec3i> positions = new ArrayList<>();
            Direction.Axis currentAxis = null;
            Vec3i last = cell.position;
            while (cell.backtrace != null) {
                cell = cell.backtrace;
                Direction.Axis axis = cell.position.getX() != last.getX() ? Direction.Axis.X
                        : cell.position.getY() != last.getY() ? Direction.Axis.Y : Direction.Axis.Z;
                if (axis != currentAxis) {
                    positions.add(0, last);
                    currentAxis = axis;
                }
                last = cell.position;
            }
            positions.add(0, last);
            return positions;
        }

        List<Point> traceResult(Cell cell) {
            List<Point> result = new ArrayList<>();
            Vec3 current = transform(originCell.position);
            for (Vec3i p : traceback(cell)) {
                Vec3 point = transform(p);
                Point seg = makePoint(current, point);
                if (seg.gridLength > 0) result.add(seg);
                current = point;
            }
            return result;
        }
    }

    private BlockTrace() {
    }
}
