package com.corosus.zahordeoptimizer.cluster;

/**
 * Per-Mob single-tick cluster decision cache mixed onto net.minecraft.world.entity.Mob
 * so serverAiStep redirects, navigation hooks, and ZAUtil.tickAI read precomputed booleans
 * with zero repeated synchronized(grid) or tag scans.
 */
public interface HordeMobTickCache {

    long zaho$getClusterStateTick();

    boolean zaho$isClusterActiveCached();

    boolean zaho$shouldThrottleGoalAndSensingCached();

    boolean zaho$shouldSkipRecomputePathCached();

    boolean zaho$shouldThrottleZaAiCached();

    void zaho$setClusterTickState(
            long tick,
            boolean clusterActive,
            boolean throttleGoalAndSensing,
            boolean skipRecomputePath,
            boolean throttleZaAi
    );
}
