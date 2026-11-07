package schematic.fix;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(SchematicFix.MOD_ID)
public final class SchematicFix {
    public static final String MOD_ID = "schematic_fix";
    public static final Logger LOGGER = LogUtils.getLogger();

    // Список «нельзя ломать» мода Create: Fast Schematic Cannon (Config.blocks_unbreak, Set<String> id блоков)
    private static final String CFC_MOD = "createfastschematiccannon";
    private static final String CFC_CONFIG = "com.Pink_Cats.createfastschematiccannon.Config";
    private static Field cfcForbidden;
    private static boolean cfcChecked;

    public SchematicFix() {
    }

    /** Блок в мире пушка Fast Schematic Cannon ломать не даст — тогда и ставить на его место нечего. */
    public static boolean isForbiddenByFastCannon(Block block) {
        Field field = cfcField();
        if (field == null) {
            return false;
        }
        try {
            Object set = field.get(null);
            return set instanceof Set<?> ids && ids.contains(BuiltInRegistries.BLOCK.getKey(block).toString());
        } catch (IllegalAccessException e) {
            return false;
        }
    }

    private static Field cfcField() {
        if (!cfcChecked) {
            cfcChecked = true;
            if (ModList.get().isLoaded(CFC_MOD)) {
                try {
                    cfcForbidden = Class.forName(CFC_CONFIG).getField("blocks_unbreak");
                } catch (ReflectiveOperationException e) {
                    LOGGER.warn("Schematic Fix: список запретных блоков Fast Schematic Cannon не найден", e);
                }
            }
        }
        return cfcForbidden;
    }
}
