package debug.tools.audit;

import com.google.common.collect.Table;
import com.mojang.logging.LogUtils;
import com.tterrag.registrate.builders.Builder;
import com.tterrag.registrate.util.entry.RegistryEntry;
import com.tterrag.registrate.util.nullness.NonNullFunction;
import com.tterrag.registrate.util.nullness.NonNullSupplier;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Диагностика dev #312: наблюдает исходную таблицу, не синхронизируя её запись. */
@Mixin(targets = "com.tterrag.registrate.AbstractRegistrate", remap = false)
public abstract class RegistrateAuditMixin {
    @Shadow @Final private Table<ResourceKey<?>, String, ?> registrations;
    @Shadow public abstract String getModid();
    @Unique private static final Logger AUDIT_LOG = LogUtils.getLogger();
    @Unique private final Map<ResourceKey<?>, Map<String, String>> auditCaptured = new ConcurrentHashMap<>();
    @Unique private final AtomicInteger auditActive = new AtomicInteger();
    @Unique private final Map<String, Boolean> auditThreads = new ConcurrentHashMap<>();

    @Inject(method = "accept", at = @At("HEAD"))
    private <R, T extends R> void auditBegin(String name, ResourceKey<? extends Registry<R>> type,
            Builder<R, T, ?, ?> builder, NonNullSupplier<? extends T> creator,
            NonNullFunction<DeferredHolder<R, T>, ? extends RegistryEntry<R, T>> entryFactory,
            CallbackInfoReturnable<RegistryEntry<R, T>> ci) {
        if (!getModid().equals("create")) return;
        String thread = Thread.currentThread().getName() + "#" + Thread.currentThread().threadId();
        if (auditThreads.putIfAbsent(thread, true) == null) {
            AUDIT_LOG.info("REGISTRATE AUDIT WRITER thread={} stack={}", thread,
                    StackWalker.getInstance().walk(frames -> frames.limit(14).map(Object::toString).toList()));
        }
        int concurrent = auditActive.incrementAndGet();
        auditCaptured.computeIfAbsent(type, ignored -> new ConcurrentHashMap<>()).put(name, thread);
        if (concurrent > 1) AUDIT_LOG.warn("REGISTRATE AUDIT OVERLAP mod=create registry={} name={} active={} thread={}",
                type.location(), name, concurrent, thread);
        if (name.equals("extendo_grip")) AUDIT_LOG.info("REGISTRATE AUDIT CAPTURE extendo_grip registry={} thread={}", type.location(), thread);
    }

    @Inject(method = "accept", at = @At("RETURN"))
    private void auditEnd(CallbackInfoReturnable<?> ci) {
        if (getModid().equals("create")) auditActive.decrementAndGet();
    }

    @Inject(method = "onRegister", at = @At("HEAD"))
    private void auditBeforeRegister(RegisterEvent event, CallbackInfo ci) {
        if (!getModid().equals("create")) return;
        if (event.getRegistryKey().equals(net.minecraft.core.registries.Registries.TRIGGER_TYPE)) {
            AUDIT_LOG.info("REGISTRATE AUDIT TRIGGERS extendoBound={} active={}",
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(
                            net.minecraft.resources.ResourceLocation.parse("create:extendo_grip")), auditActive.get());
        }
        var expected = auditCaptured.get(event.getRegistryKey());
        if (expected == null) return;
        var actual = registrations.row(event.getRegistryKey());
        var missing = new ArrayList<String>();
        for (String name : expected.keySet()) if (!actual.containsKey(name)) missing.add(name);
        AUDIT_LOG.info("REGISTRATE AUDIT TABLE registry={} tableClass={} captured={} actual={} missing={} writers={} active={}",
                event.getRegistryKey().location(), registrations.getClass().getName(), expected.size(), actual.size(), missing,
                auditThreads.keySet(), auditActive.get());
        if (!missing.isEmpty()) throw new IllegalStateException("REGISTRATE AUDIT LOST " + missing);
    }

    @Inject(method = "onRegister", at = @At("RETURN"))
    private void auditAfterRegister(RegisterEvent event, CallbackInfo ci) {
        if (!getModid().equals("create")) return;
        var expected = auditCaptured.get(event.getRegistryKey());
        if (expected == null) return;
        var missing = new ArrayList<String>();
        for (String name : expected.keySet()) {
            var id = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("create", name);
            if (!event.getRegistry().containsKey(id)) missing.add(name);
        }
        AUDIT_LOG.info("REGISTRATE AUDIT BOUND registry={} captured={} missing={}", event.getRegistryKey().location(), expected.size(), missing);
        if (!missing.isEmpty()) throw new IllegalStateException("REGISTRATE AUDIT UNBOUND " + missing);
    }
}
