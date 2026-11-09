package com.corosus.zombieawareness.mixin;

import com.corosus.zombieawareness.ZAUtil;
import com.corosus.zombieawareness.ZombieAwareness;
import com.corosus.zombieawareness.client.SoundProfileEntry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerExplosion.class)
public abstract class MixinExplosion {

    @Shadow
    public abstract ServerLevel level();

    @Shadow
    public abstract Vec3 center();

    @Inject(method = "explode",
            at = @At(value = "HEAD"), cancellable = true)
    public void hook(CallbackInfoReturnable<Integer> cir) {
        ZombieAwareness.unitTest("4");
        ZAUtil.initSoundHookDataIfEmpty();
        SoundProfileEntry entry = ZAUtil.getSoundIDEntry(SoundEvents.GENERIC_EXPLODE.value().location().toString());
        if (entry != null) {
            Vec3 pos = center();
            ServerLevel level = level();
            Player closestPlayer = ZAUtil.getClosestPlayer(level, pos.x, pos.y, pos.z, 128);
            if (closestPlayer != null) {
                ZAUtil.handleSoundProfileEvent(level, entry, new Vector3d(pos.x, pos.y, pos.z), closestPlayer);
            }
        }
    }
}
