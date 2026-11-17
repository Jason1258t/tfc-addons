package aero.extra;

import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

final class SableFuelGeometry {
    static boolean samePlot(ServerLevel level, BlockPos a, BlockPos b) {
        return Sable.HELPER.getContaining(level, a) == Sable.HELPER.getContaining(level, b);
    }
}
