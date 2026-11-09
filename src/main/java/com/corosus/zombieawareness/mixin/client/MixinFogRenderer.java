package com.corosus.zombieawareness.mixin.client;

import com.corosus.zombieawareness.bloodmoon.BloodmoonManager;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FogRenderer.class)
public abstract class MixinFogRenderer {

    @Inject(method = "setupFog", at = @At("RETURN"), cancellable = false)
    public void onSetupFog(Camera camera, int renderDistance, DeltaTracker deltaTracker, float darkenWorldAmount, ClientLevel level, CallbackInfoReturnable<FogData> cir) {
        FogData data = cir.getReturnValue();
        if (data != null && BloodmoonManager.isBloodmoonActive(level)) {
            data.color.set(0.48f, 0.03f, 0.03f, data.color.w);
            data.environmentalStart = Math.min(data.environmentalStart, 8.0f);
            data.environmentalEnd = Math.min(data.environmentalEnd, 48.0f);
            data.renderDistanceEnd = Math.min(data.renderDistanceEnd, 48.0f);
        }
    }
}
