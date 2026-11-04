package debug.tools;

import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(DebugTools.MOD_ID)
public final class DebugTools {
    public static final String MOD_ID = "debug_tools";
    private static final Logger LOGGER = LogUtils.getLogger();

    public DebugTools() {
        NeoForge.EVENT_BUS.addListener(DebugCommands::register);
    }

    /** Итог долгой команды: в чат и в лог — RCON такие сообщения уже не получит, а лог можно приложить к отчёту. */
    static void report(CommandSourceStack source, Component message) {
        source.sendSuccess(() -> message, false);
        LOGGER.info("[tfcdebug] {}", message.getString());
    }
}
