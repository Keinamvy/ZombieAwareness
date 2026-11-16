package com.corosus.zahordeoptimizer;

/**
 * Per-Entity fast cache interface mixed onto net.minecraft.world.entity.Entity
 * to avoid repeating 10+ string-tag lookups across push, sensing, and AI hooks within the same tick.
 */
public interface HordeEntityCache {

    long zaho$getHordeCacheTick();

    int zaho$getHordeCacheTagCount();

    boolean zaho$getHordeCachedValue();

    void zaho$setHordeCache(long tick, int tagCount, boolean value);
}
