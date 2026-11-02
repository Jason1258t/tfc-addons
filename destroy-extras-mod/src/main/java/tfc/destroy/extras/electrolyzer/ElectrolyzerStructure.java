package tfc.destroy.extras.electrolyzer;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.pump.PumpBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Форма 3×3×2: два боковых отсека Create, медная мембранная перегородка и настоящий насос. */
public final class ElectrolyzerStructure {
    public static final float MINIMUM_PUMP_RPM = 16.0F;

    private ElectrolyzerStructure() {}

    public static boolean hasShell(ServerLevel level, BlockPos controller) {
        for (int x = -1; x <= 1; x++) {
            for (int y = 0; y <= 2; y++) {
                for (int z = 0; z <= 1; z++) {
                    // Боковые отверстия нижнего яруса сохраняют существующие входной и NaOH-порты.
                    if ((y == 0 && z == 0) || (x == 1 && y == 1 && z == 1)) continue;
                    BlockPos pos = controller.offset(x, y, z);
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

    public static float pumpRpm(ServerLevel level, BlockPos controller) {
        return level.getBlockEntity(controller.offset(0, 1, 1)) instanceof PumpBlockEntity pump
            ? Math.abs(pump.getSpeed()) : 0.0F;
    }
}
