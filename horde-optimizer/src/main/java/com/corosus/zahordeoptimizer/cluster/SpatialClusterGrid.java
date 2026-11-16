package com.corosus.zahordeoptimizer.cluster;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;

/**
 * Pure-Java spatial cluster grid managing cluster membership, leader election,
 * density-scaled tick throttling, and stale-entity pruning for a single dimension.
 */
public final class SpatialClusterGrid {

    public record MobLocation(SpatialCellKey cellKey, double x, double y, double z, long lastSeenTick) {
    }

    private final Map<SpatialCellKey, ClusterRecord> clusters = new HashMap<>();
    private final Map<Integer, MobLocation> mobLocations = new HashMap<>();

    public synchronized ClusterRecord updateMob(
            int entityId,
            double x,
            double y,
            double z,
            long gameTick,
            int cellSizeXZ,
            int cellSizeY) {
        SpatialCellKey newKey = SpatialCellKey.fromCoords(x, y, z, cellSizeXZ, cellSizeY);
        MobLocation prev = mobLocations.get(entityId);
        mobLocations.put(entityId, new MobLocation(newKey, x, y, z, gameTick));

        if (prev != null && !prev.cellKey().equals(newKey)) {
            ClusterRecord oldCluster = clusters.get(prev.cellKey());
            if (oldCluster != null) {
                boolean wasLeader = oldCluster.isLeader(entityId);
                oldCluster.removeMember(entityId);
                if (oldCluster.isEmpty()) {
                    clusters.remove(prev.cellKey());
                } else if (wasLeader || oldCluster.getLeaderId() == -1) {
                    electLeader(oldCluster);
                }
            }
        }

        ClusterRecord targetCluster = clusters.computeIfAbsent(newKey, ClusterRecord::new);
        targetCluster.addMember(entityId);
        if (targetCluster.getLeaderId() == -1 || !targetCluster.getMemberIds().contains(targetCluster.getLeaderId())) {
            electLeader(targetCluster);
        }
        return targetCluster;
    }

    public synchronized void removeMob(int entityId) {
        MobLocation prev = mobLocations.remove(entityId);
        if (prev == null) {
            return;
        }
        ClusterRecord cluster = clusters.get(prev.cellKey());
        if (cluster == null) {
            return;
        }
        boolean wasLeader = cluster.isLeader(entityId);
        cluster.removeMember(entityId);
        if (cluster.isEmpty()) {
            clusters.remove(prev.cellKey());
        } else if (wasLeader || cluster.getLeaderId() == -1) {
            electLeader(cluster);
        }
    }

    public synchronized int electLeader(ClusterRecord cluster) {
        if (cluster == null || cluster.isEmpty()) {
            return -1;
        }
        double sumX = 0.0;
        double sumY = 0.0;
        double sumZ = 0.0;
        int count = 0;
        for (int id : cluster.getMemberIds()) {
            MobLocation loc = mobLocations.get(id);
            if (loc != null) {
                sumX += loc.x();
                sumY += loc.y();
                sumZ += loc.z();
                count++;
            }
        }
        if (count == 0) {
            int first = cluster.getMemberIds().iterator().next();
            cluster.setLeaderId(first);
            return first;
        }

        double cx = sumX / count;
        double cy = sumY / count;
        double cz = sumZ / count;

        int bestId = -1;
        double bestDistSq = Double.MAX_VALUE;
        for (int id : cluster.getMemberIds()) {
            MobLocation loc = mobLocations.get(id);
            if (loc == null) {
                continue;
            }
            double dx = loc.x() - cx;
            double dy = loc.y() - cy;
            double dz = loc.z() - cz;
            double dSq = dx * dx + dy * dy + dz * dz;
            if (bestId == -1 || dSq < bestDistSq - 1.0e-6 || (Math.abs(dSq - bestDistSq) <= 1.0e-6 && id < bestId)) {
                bestDistSq = dSq;
                bestId = id;
            }
        }
        cluster.setLeaderId(bestId);
        return bestId;
    }

    public synchronized ClusterRecord getClusterForMob(int entityId) {
        MobLocation loc = mobLocations.get(entityId);
        return loc != null ? clusters.get(loc.cellKey()) : null;
    }

    public synchronized MobLocation getMobLocation(int entityId) {
        return mobLocations.get(entityId);
    }

    public synchronized Collection<ClusterRecord> getClusters() {
        return Collections.unmodifiableList(new ArrayList<>(clusters.values()));
    }

    public synchronized int getClusterCount() {
        return clusters.size();
    }

    public synchronized int getTrackedMobCount() {
        return mobLocations.size();
    }

    public static int computeThrottleInterval(int clusterSize, int minClusterSize, int maxThrottleInterval) {
        if (clusterSize < minClusterSize || maxThrottleInterval <= 1) {
            return 1;
        }
        if (clusterSize <= 5) {
            return Math.min(maxThrottleInterval, 2);
        }
        if (clusterSize <= 12) {
            return Math.min(maxThrottleInterval, 4);
        }
        if (clusterSize <= 24) {
            return Math.min(maxThrottleInterval, 6);
        }
        return maxThrottleInterval;
    }

    public synchronized boolean isActiveTick(int entityId, long gameTick, int minClusterSize, int maxThrottleInterval) {
        MobLocation loc = mobLocations.get(entityId);
        if (loc == null) {
            return true;
        }
        ClusterRecord cluster = clusters.get(loc.cellKey());
        if (cluster == null || cluster.size() < minClusterSize || cluster.isLeader(entityId)) {
            return true;
        }
        int interval = computeThrottleInterval(cluster.size(), minClusterSize, maxThrottleInterval);
        if (interval <= 1) {
            return true;
        }
        return Math.floorMod(gameTick + entityId, interval) == 0;
    }

    public int pruneStale(long currentTick, long maxIdleTicks, IntPredicate isAlive) {
        return pruneStaleAndGetRemoved(currentTick, maxIdleTicks, isAlive).size();
    }

    /**
     * Snapshots tracked mob ages under the grid lock, evaluates {@code isAlive} outside the lock
     * to prevent lock-order inversions with level/entity lookups, then removes dead/stale mobs.
     */
    public List<Integer> pruneStaleAndGetRemoved(long currentTick, long maxIdleTicks, IntPredicate isAlive) {
        record MobAge(int id, long lastSeenTick) {}
        record PruneCandidate(int id, boolean dead) {}

        List<MobAge> snapshot;
        synchronized (this) {
            if (mobLocations.isEmpty()) {
                return Collections.emptyList();
            }
            snapshot = new ArrayList<>(mobLocations.size());
            for (Map.Entry<Integer, MobLocation> entry : mobLocations.entrySet()) {
                snapshot.add(new MobAge(entry.getKey(), entry.getValue().lastSeenTick()));
            }
        }

        List<PruneCandidate> candidates = new ArrayList<>();
        for (MobAge mob : snapshot) {
            long age = currentTick - mob.lastSeenTick();
            boolean alive = isAlive == null || isAlive.test(mob.id());
            if (!alive || age > maxIdleTicks) {
                candidates.add(new PruneCandidate(mob.id(), !alive));
            }
        }
        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }

        List<Integer> removed = new ArrayList<>(candidates.size());
        synchronized (this) {
            for (PruneCandidate candidate : candidates) {
                MobLocation currentLoc = mobLocations.get(candidate.id());
                if (currentLoc == null) {
                    continue;
                }
                if (candidate.dead() || (currentTick - currentLoc.lastSeenTick()) > maxIdleTicks) {
                    removeMob(candidate.id());
                    removed.add(candidate.id());
                }
            }
        }
        return removed;
    }

    public synchronized void clear() {
        clusters.clear();
        mobLocations.clear();
    }
}
