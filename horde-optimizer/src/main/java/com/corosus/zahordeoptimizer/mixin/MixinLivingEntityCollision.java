package com.corosus.zahordeoptimizer.mixin;

import com.corosus.zahordeoptimizer.HordeMobDetector;
import com.corosus.zahordeoptimizer.collision.HordeCollisionPolicy;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.function.Predicate;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityCollision {

    @Inject(method = "pushEntities()V", at = @At("HEAD"), cancellable = true)
    private void zaho$skipHordePushEntities(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (HordeCollisionPolicy.shouldSkipPushEntitiesScan(self)) {
            ci.cancel();
        }
    }

    @Redirect(
            method = "pushEntities()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getPushableEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"
            )
    )
    private List<Entity> zaho$filterHordeFromPushScan(Level level, Entity entity, AABB box) {
        if (HordeCollisionPolicy.shouldFilterPushScanForSelf(entity)) {
            Predicate<Entity> basePredicate = EntitySelector.pushableBy(entity);
            return level.getEntities(entity, box, candidate ->
                    !HordeMobDetector.isHordeEntity(candidate) && basePredicate.test(candidate));
        }
        return level.getPushableEntities(entity, box);
    }

    @Inject(method = "doPush(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void zaho$suppressHordeDoPush(Entity other, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (HordeCollisionPolicy.shouldSuppressEntityPush(self, other)) {
            ci.cancel();
        }
    }

    @Inject(method = "isPushable()Z", at = @At("HEAD"), cancellable = true)
    private void zaho$overrideIsPushable(CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (HordeCollisionPolicy.shouldMakeUnpushable(self)) {
            cir.setReturnValue(false);
        }
    }
}
