package com.corosus.zahordeoptimizer.cluster;

import com.corosus.zahordeoptimizer.HordeMobDetector;
import com.corosus.zahordeoptimizer.config.HordeOptimizerConfig;
import com.corosus.zombieawareness.ZAUtil;
import com.corosus.zombieawareness.heat.ChunkHeatManager;
import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.pathfinder.Path;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime Minecraft integration for spatial clustering, leader election, shared path/target caching,
 * and density-based AI/navigation tick throttling.
 */
public final class HordeClusterService {

    private static final Map<ServerLevel, LevelClusterRuntime> LEVEL_RUNTIMES = new ConcurrentHashMap<>();
    private static final Set<ServerLevel> UNLOADED_LEVELS = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private static final ThreadLocal<SpatialClusterGrid.MobTickResult> TICK_RESULT_SCRATCH =
            ThreadLocal.withInitial(SpatialClusterGrid.MobTickResult::new);

    private static final class LevelClusterRuntime {
        final SpatialClusterGrid grid = new SpatialClusterGrid();
        final Int2LongMap lastMobTick = new Int2LongOpenHashMap();
        volatile long lastPruneTick = -1L;

        LevelClusterRuntime() {
            lastMobTick.defaultReturnValue(Long.MIN_VALUE);
        }

        synchronized long putLastTick(int entityId, long gameTick) {
            return lastMobTick.put(entityId, gameTick);
        }

        synchronized void removeMobTick(int entityId) {
            lastMobTick.remove(entityId);
        }

        synchronized int lastMobTickSize() {
            return lastMobTick.size();
        }

        synchronized void removeStaleTicks(long gameTime, long maxAge) {
            lastMobTick.int2LongEntrySet().removeIf(e -> (gameTime - e.getLongValue()) > maxAge);
        }

        synchronized void clear() {
            grid.clear();
            lastMobTick.clear();
            lastPruneTick = -1L;
        }
    }

    private HordeClusterService() {
    }

    private static LevelClusterRuntime getOrCreateRuntime(ServerLevel level) {
        if (level == null || UNLOADED_LEVELS.contains(level)) {
            return null;
        }
        return LEVEL_RUNTIMES.computeIfAbsent(level, k -> new LevelClusterRuntime());
    }

    public static SpatialClusterGrid getGrid(ServerLevel level) {
        LevelClusterRuntime runtime = LEVEL_RUNTIMES.get(level);
        return runtime != null ? runtime.grid : null;
    }

    public static void onHordeMobTick(Mob mob) {
        if (mob == null || !HordeOptimizerConfig.enabled || !HordeOptimizerConfig.enableClusterOptimization) {
            return;
        }
        if (!(mob.level() instanceof ServerLevel level)) {
            return;
        }
        long gameTick = level.getGameTime();
        if (mob instanceof HordeMobTickCache cache && cache.zaho$getClusterStateTick() == gameTick) {
            return;
        }

        boolean activeEligible = isEligibleClusterMob(mob);

        if (!activeEligible) {
            if (mob instanceof HordeMobTickCache cache) {
                cache.zaho$setClusterTickState(gameTick, false, false, false, false);
            }
            return;
        }

        LevelClusterRuntime runtime = getOrCreateRuntime(level);
        if (runtime == null) {
            return;
        }

        long prevTick = runtime.putLastTick(mob.getId(), gameTick);
        if (prevTick == gameTick && !(mob instanceof HordeMobTickCache)) {
            return;
        }

        SpatialClusterGrid.MobTickResult tickResult = TICK_RESULT_SCRATCH.get();
        runtime.grid.updateMobAndEvaluate(
                mob.getId(),
                mob.getX(),
                mob.getY(),
                mob.getZ(),
                gameTick,
                HordeOptimizerConfig.clusterCellSizeXZ,
                HordeOptimizerConfig.clusterCellSizeY,
                HordeOptimizerConfig.minClusterSizeToOptimize,
                HordeOptimizerConfig.maxThrottleInterval,
                tickResult
        );

        ClusterRecord cluster = tickResult.cluster;
        LivingEntity currentTarget = mob.getTarget();
        if (currentTarget != null && ZAUtil.isVillagerEntity(currentTarget)) {
            mob.setTarget(null);
            currentTarget = null;
        }

        if (tickResult.isLeader) {
            if (!isValidClusterTarget(currentTarget) || !(currentTarget instanceof Player)) {
                ServerPlayer hordePlayer = ChunkHeatManager.resolveHordeTargetPlayer(level, mob.getX(), mob.getY(), mob.getZ());
                if (hordePlayer != null) {
                    mob.setTarget(hordePlayer);
                    currentTarget = hordePlayer;
                }
            }
            if (isValidClusterTarget(currentTarget)) {
                if (cluster.hasCachedTargetPos()
                        && hasTargetMovedBeyondTolerance(
                                cluster.getCachedTargetX(),
                                cluster.getCachedTargetY(),
                                cluster.getCachedTargetZ(),
                                currentTarget.getX(),
                                currentTarget.getY(),
                                currentTarget.getZ(),
                                HordeOptimizerConfig.pathCacheTargetTolerance)) {
                    cluster.invalidatePathCache();
                }
                cluster.cacheTarget(currentTarget.getUUID(), currentTarget.getX(), currentTarget.getY(), currentTarget.getZ(), gameTick);
            }
        } else if (tickResult.clusterSize >= HordeOptimizerConfig.minClusterSizeToOptimize) {
            syncFollowerTargetFromLeader(mob, level, cluster);
        }

        if (mob instanceof HordeMobTickCache cache) {
            boolean throttleAll = !tickResult.isActiveTick;
            cache.zaho$setClusterTickState(
                    gameTick,
                    true,
                    throttleAll,
                    tickResult.shouldSkipRecomputePath,
                    throttleAll
            );
        }
    }

    public static boolean isEligibleClusterTarget(
            boolean alive,
            boolean removed,
            boolean isVillagerOrNpc,
            boolean isPlayer,
            boolean isCreativeOrSpectator) {
        if (!alive || removed || isVillagerOrNpc) {
            return false;
        }
        return !isPlayer || !isCreativeOrSpectator;
    }

    public static boolean isValidClusterTarget(LivingEntity target) {
        if (target == null) {
            return false;
        }
        boolean isVillagerOrNpc = ZAUtil.isVillagerEntity(target);
        boolean isPlayer = target instanceof Player;
        boolean isCreativeOrSpectator = target instanceof Player p && (p.isCreative() || p.isSpectator());
        return isEligibleClusterTarget(target.isAlive(), target.isRemoved(), isVillagerOrNpc, isPlayer, isCreativeOrSpectator);
    }

    public static boolean hasTargetMovedBeyondTolerance(
            double cachedX,
            double cachedY,
            double cachedZ,
            double currentX,
            double currentY,
            double currentZ,
            double tolerance) {
        double dx = currentX - cachedX;
        double dy = currentY - cachedY;
        double dz = currentZ - cachedZ;
        return (dx * dx + dy * dy + dz * dz) > (tolerance * tolerance);
    }

    private static void syncFollowerTargetFromLeader(Mob follower, ServerLevel level, ClusterRecord cluster) {
        LivingEntity currentTarget = follower.getTarget();
        if (isValidClusterTarget(currentTarget) && currentTarget instanceof Player) {
            return;
        }
        UUID targetUuid = cluster.getCachedTargetUuid();
        if (targetUuid != null) {
            Entity leaderTarget = level.getEntity(targetUuid);
            if (leaderTarget instanceof LivingEntity living && isValidClusterTarget(living) && follower.canAttack(living)) {
                follower.setTarget(living);
                return;
            }
        }
        ServerPlayer hordePlayer = ChunkHeatManager.resolveHordeTargetPlayer(level, follower.getX(), follower.getY(), follower.getZ());
        if (hordePlayer != null && follower.canAttack(hordePlayer)) {
            follower.setTarget(hordePlayer);
        }
    }

    public static boolean isExcludedFromClusterThrottling(Collection<String> tags, boolean isPassenger) {
        if (tags == null || tags.isEmpty()) {
            return false;
        }
        return tags.contains(ZAUtil.BLOODMOON_WARDEN_TAG)
                || tags.contains(ZAUtil.KNIGHT_MOUNT_TAG)
                || tags.contains(ZAUtil.THROWN_PROJECTILE_TAG)
                || (isPassenger && tags.contains(ZAUtil.KNIGHT_TAG));
    }

    private static boolean isEligibleClusterMob(Mob mob) {
        return mob != null
                && mob.isAlive()
                && !mob.isRemoved()
                && !isExcludedFromClusterThrottling(mob.entityTags(), mob.isPassenger())
                && HordeMobDetector.isHordeMob(mob);
    }

    private static boolean isClusterOptimizationActive(Mob mob) {
        if (mob == null || !HordeOptimizerConfig.enabled || !HordeOptimizerConfig.enableClusterOptimization) {
            return false;
        }
        if (mob.entityTags().contains(ZAUtil.THROWN_PROJECTILE_TAG)) {
            return false;
        }
        if (mob instanceof HordeMobTickCache cache && mob.level() instanceof ServerLevel level) {
            if (cache.zaho$getClusterStateTick() == level.getGameTime()) {
                return cache.zaho$isClusterActiveCached();
            }
        }
        return mob.level() instanceof ServerLevel && isEligibleClusterMob(mob);
    }

    private static boolean isCloseToTarget(Mob mob) {
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) {
            return false;
        }
        double r = HordeOptimizerConfig.closeRangeDirectPathDist;
        return mob.distanceToSqr(target) <= (r * r);
    }

    public static boolean shouldThrottleGoalSelector(Mob mob) {
        if (mob == null || mob.tickCount <= 2 || !HordeOptimizerConfig.enabled || !HordeOptimizerConfig.enableClusterOptimization) {
            return false;
        }
        if (mob.entityTags().contains(ZAUtil.THROWN_PROJECTILE_TAG)) {
            return false;
        }
        if (!(mob.level() instanceof ServerLevel level)) {
            return false;
        }
        if (mob instanceof HordeMobTickCache cache && cache.zaho$getClusterStateTick() == level.getGameTime()) {
            return cache.zaho$isClusterActiveCached()
                    && cache.zaho$shouldThrottleGoalAndSensingCached()
                    && !isCloseToTarget(mob);
        }
        if (!isClusterOptimizationActive(mob) || isCloseToTarget(mob)) {
            return false;
        }
        LevelClusterRuntime runtime = LEVEL_RUNTIMES.get(level);
        if (runtime == null) {
            return false;
        }
        return !runtime.grid.isActiveTick(
                mob.getId(),
                level.getGameTime(),
                HordeOptimizerConfig.minClusterSizeToOptimize,
                HordeOptimizerConfig.maxThrottleInterval
        );
    }

    public static boolean shouldThrottleSensing(Mob mob) {
        return shouldThrottleGoalSelector(mob);
    }

    public static boolean shouldSkipRecomputePath(Mob mob) {
        if (mob == null || !HordeOptimizerConfig.enabled || !HordeOptimizerConfig.enableClusterOptimization) {
            return false;
        }
        if (mob.entityTags().contains(ZAUtil.THROWN_PROJECTILE_TAG)) {
            return false;
        }
        if (!(mob.level() instanceof ServerLevel level)) {
            return false;
        }
        if (mob instanceof HordeMobTickCache cache && cache.zaho$getClusterStateTick() == level.getGameTime()) {
            return cache.zaho$isClusterActiveCached()
                    && cache.zaho$shouldSkipRecomputePathCached()
                    && !isCloseToTarget(mob);
        }
        if (!isClusterOptimizationActive(mob) || isCloseToTarget(mob)) {
            return false;
        }
        LevelClusterRuntime runtime = LEVEL_RUNTIMES.get(level);
        if (runtime == null) {
            return false;
        }
        ClusterRecord cluster = runtime.grid.getClusterForMob(mob.getId());
        if (cluster == null || cluster.size() < HordeOptimizerConfig.minClusterSizeToOptimize || cluster.isLeader(mob.getId())) {
            return false;
        }
        return !runtime.grid.isActiveTick(
                mob.getId(),
                level.getGameTime(),
                HordeOptimizerConfig.minClusterSizeToOptimize,
                HordeOptimizerConfig.maxThrottleInterval
        );
    }

    public static boolean shouldThrottleZombieAwarenessAI(Mob mob) {
        if (mob == null || !HordeOptimizerConfig.enabled || !HordeOptimizerConfig.enableClusterOptimization) {
            return false;
        }
        if (mob.entityTags().contains(ZAUtil.THROWN_PROJECTILE_TAG)) {
            return false;
        }
        if (!(mob.level() instanceof ServerLevel level)) {
            return false;
        }
        if (mob instanceof HordeMobTickCache cache) {
            if (cache.zaho$getClusterStateTick() != level.getGameTime()) {
                onHordeMobTick(mob);
            }
            return cache.zaho$isClusterActiveCached()
                    && cache.zaho$shouldThrottleZaAiCached()
                    && !isCloseToTarget(mob);
        }
        if (!isClusterOptimizationActive(mob)) {
            return false;
        }
        onHordeMobTick(mob);
        if (isCloseToTarget(mob)) {
            return false;
        }
        LevelClusterRuntime runtime = LEVEL_RUNTIMES.get(level);
        if (runtime == null) {
            return false;
        }
        return !runtime.grid.isActiveTick(
                mob.getId(),
                level.getGameTime(),
                HordeOptimizerConfig.minClusterSizeToOptimize,
                HordeOptimizerConfig.maxThrottleInterval
        );
    }

    public static Path tryGetCachedFollowerPath(Mob mob, BlockPos requestedTarget, int accuracy) {
        if (requestedTarget == null || !isClusterOptimizationActive(mob)) {
            return null;
        }
        double closeR = HordeOptimizerConfig.closeRangeDirectPathDist;
        double reqX = requestedTarget.getX() + 0.5;
        double reqY = requestedTarget.getY();
        double reqZ = requestedTarget.getZ() + 0.5;
        if (mob.distanceToSqr(reqX, reqY, reqZ) <= (closeR * closeR)) {
            return null;
        }

        ServerLevel level = (ServerLevel) mob.level();
        LevelClusterRuntime runtime = LEVEL_RUNTIMES.get(level);
        if (runtime == null) {
            return null;
        }

        ClusterRecord cluster = runtime.grid.getClusterForMob(mob.getId());
        if (cluster == null
                || cluster.size() < HordeOptimizerConfig.minClusterSizeToOptimize
                || cluster.isLeader(mob.getId())) {
            return null;
        }

        return cluster.adaptCachedPathIfFresh(
                level.getGameTime(),
                HordeOptimizerConfig.pathCacheTtlTicks,
                reqX,
                reqY,
                reqZ,
                HordeOptimizerConfig.pathCacheTargetTolerance,
                mob.getX(),
                mob.getY(),
                mob.getZ(),
                HordeOptimizerConfig.maxFollowerPathReuseDistance
        );
    }

    public static void onPathComputed(Mob mob, Path path, BlockPos targetPos) {
        if (path == null || targetPos == null || !isClusterOptimizationActive(mob)) {
            return;
        }
        LivingEntity mobTarget = mob.getTarget();
        if (mobTarget != null && ZAUtil.isVillagerEntity(mobTarget)) {
            return;
        }
        ServerLevel level = (ServerLevel) mob.level();
        LevelClusterRuntime runtime = getOrCreateRuntime(level);
        if (runtime == null) {
            return;
        }
        ClusterRecord cluster = runtime.grid.getClusterForMob(mob.getId());
        if (cluster == null) {
            return;
        }
        if (mobTarget == null && cluster.getCachedTargetUuid() != null && cluster.hasCachedTargetPos()) {
            double tol = HordeOptimizerConfig.pathCacheTargetTolerance;
            if (hasTargetMovedBeyondTolerance(
                    cluster.getCachedTargetX(),
                    cluster.getCachedTargetY(),
                    cluster.getCachedTargetZ(),
                    targetPos.getX() + 0.5,
                    targetPos.getY(),
                    targetPos.getZ() + 0.5,
                    tol)) {
                return;
            }
        }

        long gameTime = level.getGameTime();
        boolean cacheMissingOrExpired = !cluster.isPathCacheFresh(
                gameTime,
                HordeOptimizerConfig.pathCacheTtlTicks,
                targetPos.getX() + 0.5,
                targetPos.getY(),
                targetPos.getZ() + 0.5,
                HordeOptimizerConfig.pathCacheTargetTolerance
        );

        if (cluster.isLeader(mob.getId()) || cacheMissingOrExpired) {
            cluster.cachePath(path, targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5, gameTime);
        }
    }

    public static void onEntityRemoved(Entity entity) {
        if (entity == null || !(entity.level() instanceof ServerLevel level)) {
            return;
        }
        LevelClusterRuntime runtime = LEVEL_RUNTIMES.get(level);
        if (runtime != null) {
            runtime.grid.removeMob(entity.getId());
            runtime.removeMobTick(entity.getId());
        }
    }

    public static void onEndLevelTick(ServerLevel level) {
        if (level == null) {
            return;
        }
        LevelClusterRuntime runtime = LEVEL_RUNTIMES.get(level);
        if (runtime == null) {
            return;
        }
        long gameTime = level.getGameTime();
        if (runtime.lastPruneTick >= 0L && (gameTime - runtime.lastPruneTick) < 20L) {
            return;
        }
        runtime.lastPruneTick = gameTime;
        List<Integer> removedIds = runtime.grid.pruneStaleAndGetRemoved(gameTime, HordeOptimizerConfig.staleMemberTimeoutTicks, id -> {
            Entity e = level.getEntity(id);
            return e != null && e.isAlive() && !e.isRemoved() && HordeMobDetector.isHordeMob(e);
        });
        for (int id : removedIds) {
            runtime.removeMobTick(id);
        }
        if (runtime.lastMobTickSize() > 4096) {
            runtime.removeStaleTicks(gameTime, HordeOptimizerConfig.staleMemberTimeoutTicks);
        }
    }

    public static void onLevelUnload(ServerLevel level) {
        if (level == null) {
            return;
        }
        UNLOADED_LEVELS.add(level);
        LevelClusterRuntime runtime = LEVEL_RUNTIMES.remove(level);
        if (runtime != null) {
            runtime.clear();
        }
    }

    public static void clearAll() {
        UNLOADED_LEVELS.clear();
        for (LevelClusterRuntime runtime : LEVEL_RUNTIMES.values()) {
            runtime.clear();
        }
        LEVEL_RUNTIMES.clear();
    }
}
