package com.corosus.zahordeoptimizer.mixin;

import com.corosus.zahordeoptimizer.cluster.HordeClusterService;
import com.corosus.zahordeoptimizer.cluster.HordeMobTickCache;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.sensing.Sensing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public abstract class MixinMobAI implements HordeMobTickCache {

    @Unique
    private long zaho$clusterStateTick = Long.MIN_VALUE;

    @Unique
    private boolean zaho$clusterActiveCached = false;

    @Unique
    private boolean zaho$throttleGoalAndSensingCached = false;

    @Unique
    private boolean zaho$skipRecomputePathCached = false;

    @Unique
    private boolean zaho$throttleZaAiCached = false;

    @Override
    public long zaho$getClusterStateTick() {
        return zaho$clusterStateTick;
    }

    @Override
    public boolean zaho$isClusterActiveCached() {
        return zaho$clusterActiveCached;
    }

    @Override
    public boolean zaho$shouldThrottleGoalAndSensingCached() {
        return zaho$throttleGoalAndSensingCached;
    }

    @Override
    public boolean zaho$shouldSkipRecomputePathCached() {
        return zaho$skipRecomputePathCached;
    }

    @Override
    public boolean zaho$shouldThrottleZaAiCached() {
        return zaho$throttleZaAiCached;
    }

    @Override
    public void zaho$setClusterTickState(
            long tick,
            boolean clusterActive,
            boolean throttleGoalAndSensing,
            boolean skipRecomputePath,
            boolean throttleZaAi) {
        this.zaho$clusterStateTick = tick;
        this.zaho$clusterActiveCached = clusterActive;
        this.zaho$throttleGoalAndSensingCached = throttleGoalAndSensing;
        this.zaho$skipRecomputePathCached = skipRecomputePath;
        this.zaho$throttleZaAiCached = throttleZaAi;
    }

    @Inject(method = "serverAiStep()V", at = @At("HEAD"))
    private void zaho$trackHordeClusterMember(CallbackInfo ci) {
        HordeClusterService.onHordeMobTick((Mob) (Object) this);
    }

    @Redirect(
            method = "serverAiStep()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/sensing/Sensing;tick()V"
            )
    )
    private void zaho$throttleSensingTick(Sensing sensing) {
        Mob self = (Mob) (Object) this;
        if (HordeClusterService.shouldThrottleSensing(self)) {
            return;
        }
        sensing.tick();
    }

    @Redirect(
            method = "serverAiStep()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/goal/GoalSelector;tick()V"
            )
    )
    private void zaho$throttleGoalSelectorTick(GoalSelector selector) {
        Mob self = (Mob) (Object) this;
        if (HordeClusterService.shouldThrottleGoalSelector(self)) {
            selector.tickRunningGoals(false);
            return;
        }
        selector.tick();
    }
}
