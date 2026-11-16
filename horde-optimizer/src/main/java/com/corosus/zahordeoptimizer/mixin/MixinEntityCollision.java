package com.corosus.zahordeoptimizer.mixin;

import com.corosus.zahordeoptimizer.HordeEntityCache;
import com.corosus.zahordeoptimizer.collision.HordeCollisionPolicy;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class MixinEntityCollision implements HordeEntityCache {

    @Unique
    private long zaho$hordeCacheTick = Long.MIN_VALUE;

    @Unique
    private int zaho$hordeCacheTagCount = Integer.MIN_VALUE;

    @Unique
    private boolean zaho$hordeCachedValue = false;

    @Override
    public long zaho$getHordeCacheTick() {
        return zaho$hordeCacheTick;
    }

    @Override
    public int zaho$getHordeCacheTagCount() {
        return zaho$hordeCacheTagCount;
    }

    @Override
    public boolean zaho$getHordeCachedValue() {
        return zaho$hordeCachedValue;
    }

    @Override
    public void zaho$setHordeCache(long tick, int tagCount, boolean value) {
        this.zaho$hordeCacheTick = tick;
        this.zaho$hordeCacheTagCount = tagCount;
        this.zaho$hordeCachedValue = value;
    }

    @Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void zaho$suppressHordeEntityPush(Entity other, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (HordeCollisionPolicy.shouldSuppressEntityPush(self, other)) {
            ci.cancel();
        }
    }
}
