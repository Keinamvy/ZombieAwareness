package com.corosus.zombieawareness.mixin;

import com.corosus.zombieawareness.compat.TaCZIntegration;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.tacz.guns.sound.SoundManager", remap = false)
public class MixinTaCZSoundManager {

    @Inject(
        method = "sendSoundToNearby(Lnet/minecraft/world/entity/LivingEntity;ILnet/minecraft/resources/Identifier;Lnet/minecraft/resources/Identifier;Ljava/lang/String;FF)V",
        at = @At("HEAD"),
        remap = false,
        require = 0
    )
    private static void onSendSoundToNearby(LivingEntity sourceEntity, int distance, Identifier gunId, Identifier gunDisplayId, String soundName, float volume, float pitch, CallbackInfo ci) {
        TaCZIntegration.onTaCZSoundSent(sourceEntity, distance, gunId, soundName, volume, pitch);
    }
}
