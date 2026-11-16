package com.corosus.zahordeoptimizer.cluster;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * Pure-Java spatial cluster grid managing cluster membership, leader election,
 * density-scaled tick throttling, and stale-entity pruning for a single dimension.
 */
public final class SpatialClusterGrid {

    public record MobLocation(SpatialCellKey cellKey, double x, double y, double z, long lastSeenTick) {
    }

    public static final class MobTickResult {
        public ClusterRecord cluster;
        public boolean isLeader;
        public int clusterSize;
        public boolean isActiveTick;
        public boolean shouldSkipRecomputePath;
    }

    private static final class MutableMobEntry {
        long packedCellKey;
        SpatialCellKey cellKey;
        double x;
        double y;
        double z;
        long lastSeenTick;

        MutableMobEntry(long packedCellKey, SpatialCellKey cellKey, double x, double y, double z, long lastSeenTick) {
            this.packedCellKey = packedCellKey;
            this.cellKey = cellKey;
            this.x = x;
            this.y = y;
            this.z = z;
            this.lastSeenTick = lastSeenTick;
        }
    }

    private final Long2ObjectMap<ClusterRecord> clusters = new Long2ObjectOpenHashMap<>();
    private final Int2ObjectMap<MutableMobEntry> mobEntries = new Int2ObjectOpenHashMap<>();

    public synchronized ClusterRecord updateMob(
            int entityId,
            double x,
            double y,
            double z,
            long gameTick,
            int cellSizeXZ,
            int cellSizeY) {
        long newPacked = SpatialCellKey.packFromCoords(x, y, z, cellSizeXZ, cellSizeY);
        MutableMobEntry entry = mobEntries.get(entityId);

        if (entry != null && entry.packedCellKey == newPacked) {
            entry.x = x;
            entry.y = y;
            entry.z = z;
            entry.lastSeenTick = gameTick;
            ClusterRecord sameCluster = clusters.get(newPacked);
            if (sameCluster == null) {
                sameCluster = new ClusterRecord(entry.cellKey);
                clusters.put(newPacked, sameCluster);
                sameCluster.addMember(entityId);
            }
            if (sameCluster.getLeaderId() == -1 || !sameCluster.containsMember(sameCluster.getLeaderId())) {
                electLeader(sameCluster);
            }
            return sameCluster;
        }

        if (entry != null) {
            ClusterRecord oldCluster = clusters.get(entry.packedCellKey);
            if (oldCluster != null) {
                boolean wasLeader = oldCluster.isLeader(entityId);
                oldCluster.removeMember(entityId);
                if (oldCluster.isEmpty()) {
                    clusters.remove(entry.packedCellKey);
                } else if (wasLeader || oldCluster.getLeaderId() == -1) {
                    electLeader(oldCluster);
                }
            }
            entry.packedCellKey = newPacked;
            entry.cellKey = SpatialCellKey.fromCoords(x, y, z, cellSizeXZ, cellSizeY);
            entry.x = x;
            entry.y = y;
            entry.z = z;
            entry.lastSeenTick = gameTick;
        } else {
            SpatialCellKey keyObj = SpatialCellKey.fromCoords(x, y, z, cellSizeXZ, cellSizeY);
            entry = new MutableMobEntry(newPacked, keyObj, x, y, z, gameTick);
            mobEntries.put(entityId, entry);
        }

        ClusterRecord targetCluster = clusters.get(newPacked);
        if (targetCluster == null) {
            targetCluster = new ClusterRecord(entry.cellKey);
            clusters.put(newPacked, targetCluster);
        }
        targetCluster.addMember(entityId);
        if (targetCluster.getLeaderId() == -1 || !targetCluster.containsMember(targetCluster.getLeaderId())) {
            electLeader(targetCluster);
        }
        return targetCluster;
    }

    /**
     * Updates mob coordinates and evaluates leader/active-tick status under a single lock acquisition.
     */
    public synchronized void updateMobAndEvaluate(
            int entityId,
            double x,
            double y,
            double z,
            long gameTick,
            int cellSizeXZ,
            int cellSizeY,
            int minClusterSize,
            int maxThrottleInterval,
            MobTickResult out) {
        ClusterRecord cluster = updateMob(entityId, x, y, z, gameTick, cellSizeXZ, cellSizeY);
        int size = cluster.size();
        boolean isLeader = cluster.isLeader(entityId);
        boolean active = true;
        boolean skipRecompute = false;
        if (!isLeader && size >= minClusterSize) {
            int interval = computeThrottleInterval(size, minClusterSize, maxThrottleInterval);
            if (interval > 1) {
                active = Math.floorMod(gameTick + entityId, interval) == 0;
                skipRecompute = !active;
            }
        }
        out.cluster = cluster;
        out.isLeader = isLeader;
        out.clusterSize = size;
        out.isActiveTick = active;
        out.shouldSkipRecomputePath = skipRecompute;
    }

    public synchronized void removeMob(int entityId) {
        MutableMobEntry prev = mobEntries.remove(entityId);
        if (prev == null) {
            return;
        }
        ClusterRecord cluster = clusters.get(prev.packedCellKey);
        if (cluster == null) {
            return;
        }
        boolean wasLeader = cluster.isLeader(entityId);
        cluster.removeMember(entityId);
        if (cluster.isEmpty()) {
            clusters.remove(prev.packedCellKey);
        } else if (wasLeader || cluster.getLeaderId() == -1) {
            electLeader(cluster);
        }
    }

    public synchronized int electLeader(ClusterRecord cluster) {
        if (cluster == null || cluster.isEmpty()) {
            return -1;
        }
        final double[] sums = new double[4]; // sumX, sumY, sumZ, count
        final int[] firstId = new int[] { -1 };
        cluster.forEachMember(id -> {
            if (firstId[0] == -1) {
                firstId[0] = id;
            }
            MutableMobEntry loc = mobEntries.get(id);
            if (loc != null) {
                sums[0] += loc.x;
                sums[1] += loc.y;
                sums[2] += loc.z;
                sums[3] += 1.0;
            }
        });
        int count = (int) sums[3];
        if (count == 0) {
            cluster.setLeaderId(firstId[0]);
            return firstId[0];
        }

        final double cx = sums[0] / count;
        final double cy = sums[1] / count;
        final double cz = sums[2] / count;

        final int[] bestId = new int[] { -1 };
        final double[] bestDistSq = new double[] { Double.MAX_VALUE };
        cluster.forEachMember(id -> {
            MutableMobEntry loc = mobEntries.get(id);
            if (loc == null) {
                return;
            }
            double dx = loc.x - cx;
            double dy = loc.y - cy;
            double dz = loc.z - cz;
            double dSq = dx * dx + dy * dy + dz * dz;
            if (bestId[0] == -1 || dSq < bestDistSq[0] - 1.0e-6 || (Math.abs(dSq - bestDistSq[0]) <= 1.0e-6 && id < bestId[0])) {
                bestDistSq[0] = dSq;
                bestId[0] = id;
            }
        });
        cluster.setLeaderId(bestId[0]);
        return bestId[0];
    }

    public synchronized ClusterRecord getClusterForMob(int entityId) {
        MutableMobEntry loc = mobEntries.get(entityId);
        return loc != null ? clusters.get(loc.packedCellKey) : null;
    }

    public synchronized MobLocation getMobLocation(int entityId) {
        MutableMobEntry entry = mobEntries.get(entityId);
        return entry != null ? new MobLocation(entry.cellKey, entry.x, entry.y, entry.z, entry.lastSeenTick) : null;
    }

    public synchronized Collection<ClusterRecord> getClusters() {
        return Collections.unmodifiableList(new ArrayList<>(clusters.values()));
    }

    public synchronized int getClusterCount() {
        return clusters.size();
    }

    public synchronized int getTrackedMobCount() {
        return mobEntries.size();
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
        MutableMobEntry loc = mobEntries.get(entityId);
        if (loc == null) {
            return true;
        }
        ClusterRecord cluster = clusters.get(loc.packedCellKey);
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
            if (mobEntries.isEmpty()) {
                return Collections.emptyList();
            }
            snapshot = new ArrayList<>(mobEntries.size());
            for (Int2ObjectMap.Entry<MutableMobEntry> entry : mobEntries.int2ObjectEntrySet()) {
                snapshot.add(new MobAge(entry.getIntKey(), entry.getValue().lastSeenTick));
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
                MutableMobEntry currentLoc = mobEntries.get(candidate.id());
                if (currentLoc == null) {
                    continue;
                }
                if (candidate.dead() || (currentTick - currentLoc.lastSeenTick) > maxIdleTicks) {
                    removeMob(candidate.id());
                    removed.add(candidate.id());
                }
            }
        }
        return removed;
    }

    public synchronized void clear() {
        clusters.clear();
        mobEntries.clear();
    }
}
