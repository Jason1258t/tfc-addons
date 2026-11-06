package ee.extras.cable;

import com.george_vi.electroenergetics.content.bulb.BulbBlock;
import com.george_vi.electroenergetics.content.electric_motor.ElectricMotorBlock;
import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Координаты крышки и ввода относительно начала блока. */
public record CableCapGeometry(Vec3 front, Vec3 back, Vec3 outward, Vec3 length, Vec3 width, Vec3 inlet) {
    private static final double RIM = 2.5 / 16;
    private static final double HALF_WIDTH = 3.0 / 16;

    public static CableCapGeometry of(Level level, BlockPos pos, BlockState state, int[] contacts) {
        var device = (ElectricalDeviceBlock<?>) state.getBlock();
        Vec3 a = device.getNodePosition(level, pos, state, contacts[0]);
        Vec3 b = device.getNodePosition(level, pos, state, contacts[1]);
        if (a == null || b == null) return null;
        Vec3 midpoint = a.lerp(b, 0.5);
        Vec3 span = b.subtract(a);
        var shape = state.getShape(level, pos);
        Vec3 radial = midpoint.subtract(shape.isEmpty() ? new Vec3(0.5, 0.5, 0.5) : shape.bounds().getCenter());
        Vec3 along = span.lengthSqr() < 1e-8 ? new Vec3(1, 0, 0) : span.normalize();
        Vec3 outward = Vec3.ZERO;
        double best = Double.NEGATIVE_INFINITY;
        for (Direction direction : Direction.values()) {
            Vec3 axis = Vec3.atLowerCornerOf(direction.getNormal());
            double score = radial.dot(axis) - Math.abs(along.dot(axis));
            if (Math.abs(along.dot(axis)) < 0.99 && score > best) {
                best = score;
                outward = axis;
            }
        }
        // У лампы клеммы обращены туда же, куда колба, а центр общей формы лежит ближе к колбе.
        if (state.getBlock() instanceof BulbBlock)
            outward = Vec3.atLowerCornerOf(state.getValue(BulbBlock.FACING).getNormal());
        // Продольная ось проходит через оба контакта, бортики уходят под их плоскость.
        outward = outward.subtract(along.scale(outward.dot(along))).normalize();
        Vec3 across = outward.cross(along).normalize();
        Vec3 x = along.scale(span.length() / 2 + RIM);
        Vec3 y = across.scale(HALF_WIDTH);
        Vec3 front = midpoint.add(outward.scale(2.75 / 16));
        Vec3 back = midpoint.subtract(outward.scale(2.25 / 16));
        // У лампы и двигателя середину панели занимают колба и вал.
        Vec3 inlet = state.getBlock() instanceof BulbBlock || state.getBlock() instanceof ElectricMotorBlock
                ? front.add(x.scale(0.67)) : front;
        return new CableCapGeometry(front, back, outward, x, y, inlet);
    }
}
