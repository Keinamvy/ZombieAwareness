package com.corosus.zahordeoptimizer.mixin;

import com.corosus.zahordeoptimizer.cluster.HordeClusterService;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

@Mixin(PathNavigation.class)
public abstract class MixinPathNavigation {

    @Shadow
    protected Mob mob;

    @Shadow
    protected Path path;

    @Shadow
    private BlockPos targetPos;

    @Shadow
    private int reachRange;

    @Invoker("resetStuckTimeout")
    protected abstract void zaho$resetStuckTimeout();

    @Inject(method = "shouldRecomputePath(Lnet/minecraft/core/BlockPos;)Z", at = @At("HEAD"), cancellable = true)
    private void zaho$suppressFollowerRecomputeCheck(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (HordeClusterService.shouldSkipRecomputePath(this.mob)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "recomputePath()V", at = @At("HEAD"), cancellable = true)
    private void zaho$suppressFollowerRecompute(CallbackInfo ci) {
        if (HordeClusterService.shouldSkipRecomputePath(this.mob)) {
            ci.cancel();
        }
    }

    @Inject(
            method = "createPath(Ljava/util/Set;IZIF)Lnet/minecraft/world/level/pathfinder/Path;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void zaho$reuseLeaderPathForFollower(
            Set<BlockPos> targets,
            int regionOffset,
            boolean offsetUpward,
            int accuracy,
            float followRange,
            CallbackInfoReturnable<Path> cir) {
        if (targets != null && targets.size() == 1) {
            BlockPos requestedTarget = targets.iterator().next();
            Path cached = HordeClusterService.tryGetCachedFollowerPath(this.mob, requestedTarget, accuracy);
            if (cached != null) {
                if (cached.getTarget() != null) {
                    this.targetPos = cached.getTarget();
                    this.reachRange = accuracy;
                    zaho$resetStuckTimeout();
                }
                cir.setReturnValue(cached);
                return;
            }
            if (this.path != null && !this.path.isDone() && HordeClusterService.shouldSkipRecomputePath(this.mob)) {
                cir.setReturnValue(this.path);
            }
        }
    }

    @Inject(
            method = "createPath(Ljava/util/Set;IZIF)Lnet/minecraft/world/level/pathfinder/Path;",
            at = @At("RETURN")
    )
    private void zaho$cacheLeaderComputedPath(
            Set<BlockPos> targets,
            int regionOffset,
            boolean offsetUpward,
            int accuracy,
            float followRange,
            CallbackInfoReturnable<Path> cir) {
        Path computed = cir.getReturnValue();
        if (computed != null && targets != null && targets.size() == 1) {
            BlockPos requestedTarget = targets.iterator().next();
            HordeClusterService.onPathComputed(this.mob, computed, requestedTarget);
        }
    }
}
