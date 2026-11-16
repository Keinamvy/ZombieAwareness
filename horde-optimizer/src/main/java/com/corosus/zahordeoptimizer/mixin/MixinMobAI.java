package com.corosus.zahordeoptimizer.mixin;

import com.corosus.zahordeoptimizer.cluster.HordeClusterService;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.sensing.Sensing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public abstract class MixinMobAI {

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
