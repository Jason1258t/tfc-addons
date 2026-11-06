package perf.fix;

import net.dries007.tfc.common.blocks.TFCBlocks;
import net.dries007.tfc.common.entities.IGlow;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Снятие блока света светящегося существа TFC без подгрузки чанков. */
public final class GlowLight {
    /** -Dperf_fix.glow.disabled=true — как у самого TFC (для сравнения на сервере разработки). */
    public static final boolean ENABLED = !Boolean.getBoolean("perf_fix.glow.disabled");

    private GlowLight() {
    }

    /**
     * Как IGlow.tryRemoveLight, но проверяется чанк блока света. Позиция (0,0,0) — свет ещё не ставился. Если чанк не
     * загружен, блок света остаётся: TFCLightBlock сам исчезает на случайном тике (1 из 20).
     */
    public static void removeIfLoaded(IGlow glow) {
        Level level = glow.getEntity().level();
        BlockPos light = glow.getLightPos();
        if (light.equals(BlockPos.ZERO) || !level.isLoaded(light)) {
            return;
        }
        BlockState state = level.getBlockState(light);
        if (state.is(TFCBlocks.LIGHT.get())) {
            level.setBlockAndUpdate(light, state.getFluidState().createLegacyBlock());
        }
    }
}
