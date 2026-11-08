package rw.hostfix.mixin;

import com.momosoftworks.coldsweat.core.init.TempModifierInit;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rw.hostfix.RwHostFix;

/**
 * Cold Sweat 2.4.2 перестраивает реестр модификаторов температуры (TempModifierRegistry.TEMP_MODIFIERS — статический
 * HashBiMap: flush() и заново register) дважды: на сервере при ServerAboutToStartEvent и на клиенте при входе в мир
 * (ClientJoinSetup.onJoin). В «мире хоста» клиент и встроенный сервер в одной программе, карта общая: клиентский
 * поток очищал её, пока серверный обходил в тике температуры игрока (TempModifierRegistry.getHolder) —
 * ConcurrentModificationException и падение сервера через несколько секунд после входа.
 * Пока в программе работает сервер, перестройку не из его потока пропускаем: реестр уже собран сервером тем же
 * событием TempModifierRegisterEvent (с теми же подписчиками, включая KubeJS). У гостя и на выделенном сервере
 * ничего не меняется. Без Cold Sweat миксин не применяется (нет цели).
 * Отключить: -Drw_host_fix.coldsweat.disabled=true.
 */
@Mixin(value = TempModifierInit.class, remap = false)
public class ColdSweatModifierInitMixin {
    @Inject(method = "buildModifierRegistries", at = @At("HEAD"), cancellable = true)
    private static void rwHostFix$skipOffThreadRebuild(CallbackInfo ci) {
        if (Boolean.getBoolean("rw_host_fix.coldsweat.disabled")) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && !server.isSameThread()) {
            RwHostFix.LOGGER.info("Реестр модификаторов Cold Sweat не перестроен в потоке {}: его уже собрал встроенный сервер",
                    Thread.currentThread().getName());
            ci.cancel();
        }
    }
}
