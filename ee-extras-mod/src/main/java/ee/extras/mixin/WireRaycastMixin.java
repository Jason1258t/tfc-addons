package ee.extras.mixin;

import ee.extras.wire.SurfaceWireEntity;
import ee.extras.wire.SurfaceWireItem;
import ee.extras.wire.WireEvents;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Наведение на декоративный провод: луч взгляда игрока проверяется по рамкам отрезков (у сущности одна общая рамка
 * на весь маршрут — по ней одной провод мешал бы наводиться на блоки). Только для своего игрока и только с проводом,
 * кусачками или красителем в руке — иначе провод не загораживает блок под ним.
 *
 * Перенесено из Create: Power Grid 0.6.2 (mixin.client.ComplexEntityRaycastMixin, Apache-2.0, см. NOTICE).
 * Изменения: без Sable; только камера клиента и нужный предмет в руке. Create и Sable миксуют тот же метод другими
 * способами (@WrapOperation/@Redirect внутри), наш @Inject в RETURN с ними не пересекается.
 */
@Mixin(ProjectileUtil.class)
public abstract class WireRaycastMixin {
    @Inject(method = "getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)Lnet/minecraft/world/phys/EntityHitResult;",
            at = @At("RETURN"), cancellable = true)
    private static void eeExtras$wireRaycast(Entity shooter, Vec3 start, Vec3 end, AABB box, Predicate<Entity> filter, double distance,
                                            CallbackInfoReturnable<EntityHitResult> cir) {
        if (!shooter.level().isClientSide || shooter != Minecraft.getInstance().getCameraEntity() || !(shooter instanceof Player p)) return;
        ItemStack held = p.getMainHandItem();
        if (!(held.getItem() instanceof SurfaceWireItem) && !held.is(WireEvents.CUTTERS) && !(held.getItem() instanceof DyeItem)) return;
        EntityHitResult base = cir.getReturnValue();
        double best = base != null ? start.distanceToSqr(base.getLocation()) : distance;
        Entity bestEntity = null;
        Vec3 bestHit = null;
        for (SurfaceWireEntity w : shooter.level().getEntitiesOfClass(SurfaceWireEntity.class, box)) {
            Vec3 hit = w.raycast(start, end);
            if (hit == null) continue;
            double d = start.distanceToSqr(hit);
            if (d < best) {
                best = d;
                bestEntity = w;
                bestHit = hit;
            }
        }
        if (bestEntity != null) cir.setReturnValue(new EntityHitResult(bestEntity, bestHit));
    }
}
