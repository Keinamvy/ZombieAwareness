package com.corosus.zombieawareness.mixin;

import com.corosus.zombieawareness.heat.ChunkHeatManager;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(AbstractSkeleton.class)
public abstract class MixinAbstractSkeleton {

    @ModifyArg(
            method = "performRangedAttack",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/projectile/Projectile;spawnProjectileUsingShoot(Lnet/minecraft/world/entity/projectile/Projectile;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;DDDFF)Lnet/minecraft/world/entity/projectile/Projectile;"
            ),
            index = 6
    )
    private float za$boostRampageArrowVelocity(float originalVelocity) {
        AbstractSkeleton self = (AbstractSkeleton) (Object) this;
        if (ChunkHeatManager.isRampageMob(self)) {
            return originalVelocity * ChunkHeatManager.getRampageArrowVelocityMultiplier();
        }
        return originalVelocity;
    }
}
