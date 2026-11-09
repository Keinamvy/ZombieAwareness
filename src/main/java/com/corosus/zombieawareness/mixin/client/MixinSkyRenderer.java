package com.corosus.zombieawareness.mixin.client;

import com.corosus.zombieawareness.bloodmoon.BloodmoonManager;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRenderer.class)
public abstract class MixinSkyRenderer {

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    public void onExtractRenderState(ClientLevel level, float partialTick, Camera camera, SkyRenderState state, CallbackInfo ci) {
        if (state != null && BloodmoonManager.isBloodmoonActive(level)) {
            state.skyColor = new Vector3f(0.38f, 0.02f, 0.02f);
            state.sunriseAndSunsetColor = new Vector4f(0.70f, 0.04f, 0.04f, 0.75f);
        }
    }
}
