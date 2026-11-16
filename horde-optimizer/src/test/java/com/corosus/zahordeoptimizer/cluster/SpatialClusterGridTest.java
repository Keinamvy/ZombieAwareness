package com.corosus.zahordeoptimizer.cluster;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class SpatialClusterGridTest {

    @Test
    void spatialCellKeyHandlesPositiveAndNegativeCoordinatesConsistently() {
        SpatialCellKey k1 = SpatialCellKey.fromCoords(0.5, 64.0, 5.9, 6, 8);
        SpatialCellKey k2 = SpatialCellKey.fromCoords(5.9, 71.9, 0.1, 6, 8);
        assertEquals(k1, k2);
        assertEquals(new SpatialCellKey(0, 8, 0), k1);

        // Negative coordinates must floor-divide cleanly so [-6, -1] is cell -1, not 0
        SpatialCellKey neg = SpatialCellKey.fromCoords(-0.1, -1.0, -6.0, 6, 8);
        assertEquals(new SpatialCellKey(-1, -1, -1), neg);
    }

    @Test
    void assignsClustersAndReassignsLeaderWhenLeaderDiesOrMovesOut() {
        SpatialClusterGrid grid = new SpatialClusterGrid();

        // Spawn 4 horde zombies in the same 6x8x6 cell
        grid.updateMob(10, 1.0, 64.0, 1.0, 100L, 6, 8);
        grid.updateMob(11, 3.0, 64.0, 3.0, 100L, 6, 8);
        grid.updateMob(12, 3.1, 64.0, 3.1, 100L, 6, 8); // closest to centroid
        ClusterRecord cluster = grid.updateMob(13, 5.0, 64.0, 5.0, 100L, 6, 8);

        assertEquals(1, grid.getClusterCount());
        assertEquals(4, cluster.size());
        int initialLeader = cluster.getLeaderId();
        assertTrue(cluster.getMemberIds().contains(initialLeader));

        // Cache a path on the cluster from the leader
        List<Node> nodes = List.of(new Node(3, 64, 3), new Node(10, 64, 10), new Node(20, 64, 20));
        cluster.cachePath(new Path(nodes, new BlockPos(20, 64, 20), true), 20.5, 64.0, 20.5, 100L);
        assertNotNull(cluster.getCachedPath());

        // Remove the leader (e.g., killed by player)
        grid.removeMob(initialLeader);
        assertEquals(3, cluster.size());
        int newLeader = cluster.getLeaderId();
        assertNotEquals(-1, newLeader);
        assertNotEquals(initialLeader, newLeader);
        assertTrue(cluster.getMemberIds().contains(newLeader));
        assertNull(cluster.getCachedPath(), "Stale leader path cache should invalidate on leader loss");

        // Move the new leader into a distant cell -> remaining 2 members elect a third leader
        grid.updateMob(newLeader, 30.0, 64.0, 30.0, 105L, 6, 8);
        assertEquals(2, grid.getClusterCount());
        assertEquals(2, cluster.size());
        int thirdLeader = cluster.getLeaderId();
        assertNotEquals(-1, thirdLeader);
        assertNotEquals(newLeader, thirdLeader);
        assertTrue(cluster.getMemberIds().contains(thirdLeader));
    }

    @Test
    void sharedPathCacheAdaptsNodesForFollowersAndRespectsExpiry() {
        List<Node> nodes = new ArrayList<>();
        for (int i = 0; i <= 10; i++) {
            nodes.add(new Node(i * 2, 64, 0));
        }
        Path leaderPath = new Path(nodes, new BlockPos(20, 64, 0), true);

        ClusterRecord cluster = new ClusterRecord(new SpatialCellKey(0, 8, 0));
        cluster.setLeaderId(1);
        cluster.addMember(1);
        cluster.addMember(2);
        cluster.cacheTarget(UUID.randomUUID(), 20.5, 64.0, 0.5, 200L);
        cluster.cachePath(leaderPath, 20.5, 64.0, 0.5, 200L);

        assertTrue(cluster.hasCachedWaypoint());
        assertTrue(cluster.isPathCacheFresh(210L, 20, 21.0, 64.0, 1.0, 4.0));
        // Expired after TTL
        assertFalse(cluster.isPathCacheFresh(225L, 20, 20.5, 64.0, 0.5, 4.0));
        // Invalidated if requested target moved outside tolerance
        assertFalse(cluster.isPathCacheFresh(205L, 20, 30.0, 64.0, 0.5, 4.0));

        // Follower standing near node index 3 (x=6.2) gets an independent Path snapped to index 3
        Path followerPath = ClusterRecord.adaptPathForFollower(cluster.getCachedPath(), 6.2, 64.0, 0.4, 14.0);
        assertNotNull(followerPath);
        assertEquals(3, followerPath.getNextNodeIndex());

        // Advancing follower's path must NOT mutate the cached leader path
        followerPath.advance();
        assertEquals(4, followerPath.getNextNodeIndex());
        assertEquals(0, cluster.getCachedPath().getNextNodeIndex());

        // Follower far away (> maxSnapDistance) gets null so it computes its own A* path
        assertNull(ClusterRecord.adaptPathForFollower(cluster.getCachedPath(), 50.0, 64.0, 50.0, 14.0));
    }

    @Test
    void throttlingScalesWithDensityLeavesSingletonsAndLeadersUnthrottled() {
        SpatialClusterGrid grid = new SpatialClusterGrid();

        // Singleton cluster (size 1 < minClusterSize=2) is NEVER throttled
        grid.updateMob(1, 2.0, 64.0, 2.0, 1L, 6, 8);
        for (long tick = 1; tick <= 20; tick++) {
            assertTrue(grid.isActiveTick(1, tick, 2, 8), "Singleton mob must run every tick");
        }

        // Dense horde cluster of 40 zombies in one cell
        for (int id = 100; id < 140; id++) {
            grid.updateMob(id, 20.0 + (id % 5) * 0.2, 64.0, 20.0 + (id % 4) * 0.2, 1L, 6, 8);
        }
        ClusterRecord dense = grid.getClusterForMob(100);
        assertNotNull(dense);
        assertEquals(40, dense.size());
        int leaderId = dense.getLeaderId();

        // Leader runs 100% of ticks
        for (long tick = 1; tick <= 40; tick++) {
            assertTrue(grid.isActiveTick(leaderId, tick, 2, 8), "Cluster leader must never be throttled");
        }

        // Followers run staggered 1/8 of ticks (interval = 8 for >24 mobs)
        int totalFollowerTicks = 0;
        int activeFollowerTicks = 0;
        for (int id = 100; id < 140; id++) {
            if (id == leaderId) continue;
            for (long tick = 1; tick <= 40; tick++) {
                totalFollowerTicks++;
                if (grid.isActiveTick(id, tick, 2, 8)) {
                    activeFollowerTicks++;
                }
            }
        }
        double activeRatio = (double) activeFollowerTicks / (double) totalFollowerTicks;
        assertEquals(0.125, activeRatio, 0.01, "Dense cluster followers (>24) should run ~12.5% of AI/repath ticks");
    }

    @Test
    void prunesStaleDeadMobsAndCleansEmptyClusters() {
        SpatialClusterGrid grid = new SpatialClusterGrid();
        grid.updateMob(1, 2.0, 64.0, 2.0, 10L, 6, 8);
        grid.updateMob(2, 2.5, 64.0, 2.5, 10L, 6, 8);
        grid.updateMob(3, 50.0, 64.0, 50.0, 10L, 6, 8);
        assertEquals(2, grid.getClusterCount());
        assertEquals(3, grid.getTrackedMobCount());

        // Mob 1 dies, mob 3 times out at tick 200; mob 2 was seen at tick 180 and is alive
        grid.updateMob(2, 2.5, 64.0, 2.5, 180L, 6, 8);
        int pruned = grid.pruneStale(200L, 100L, id -> id != 1);

        assertEquals(2, pruned);
        assertEquals(1, grid.getTrackedMobCount());
        assertEquals(1, grid.getClusterCount());
        assertEquals(2, grid.getClusterForMob(2).getLeaderId());

        grid.clear();
        assertEquals(0, grid.getClusterCount());
        assertEquals(0, grid.getTrackedMobCount());
    }

    @Test
    void pruneStaleDoesNotHoldGridLockDuringIsAliveCallback() throws Exception {
        SpatialClusterGrid grid = new SpatialClusterGrid();
        grid.updateMob(1, 2.0, 64.0, 2.0, 10L, 6, 8);
        grid.updateMob(2, 2.5, 64.0, 2.5, 10L, 6, 8);

        AtomicBoolean lockHeldInCallback = new AtomicBoolean(false);
        AtomicBoolean concurrentUpdateSucceeded = new AtomicBoolean(false);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            int pruned = grid.pruneStale(200L, 100L, id -> {
                if (Thread.holdsLock(grid)) {
                    lockHeldInCallback.set(true);
                }
                if (id == 2) {
                    Future<?> f = executor.submit(() -> {
                        // Must not deadlock against pruneStale while callback runs; refreshes mob 2 to tick 200
                        grid.updateMob(2, 2.5, 64.0, 2.5, 200L, 6, 8);
                        concurrentUpdateSucceeded.set(true);
                    });
                    try {
                        f.get(2, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
                return id != 1;
            });

            assertFalse(lockHeldInCallback.get(), "SpatialClusterGrid lock must NOT be held during isAlive callback");
            assertTrue(concurrentUpdateSucceeded.get(), "Concurrent updateMob during isAlive callback must not deadlock");
            assertEquals(1, pruned, "Only dead mob 1 should be pruned; concurrently refreshed mob 2 must survive");
            assertEquals(1, grid.getTrackedMobCount());
            assertNotNull(grid.getClusterForMob(2));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void clusterRecordSnapshotsAndConcurrentCacheMutationsAreSafe() throws Exception {
        ClusterRecord cluster = new ClusterRecord(new SpatialCellKey(0, 4, 0));
        cluster.addMember(1);
        cluster.addMember(2);
        cluster.setLeaderId(1);

        // Snapshot returned by getMemberIds() must tolerate subsequent mutations during iteration
        Set<Integer> snapshot = cluster.getMemberIds();
        cluster.addMember(3);
        cluster.removeMember(2);
        List<Integer> seen = new ArrayList<>();
        for (int id : snapshot) {
            seen.add(id);
        }
        assertEquals(List.of(1, 2), seen);

        List<Node> nodes = List.of(new Node(0, 64, 0), new Node(10, 64, 10));
        Path basePath = new Path(nodes, new BlockPos(10, 64, 10), true);
        cluster.cachePath(basePath, 10.5, 64.0, 10.5, 100L);

        Path copy = cluster.getCachedPathCopyIfFresh(105L, 20, 10.5, 64.0, 10.5, 4.0);
        assertNotNull(copy);
        copy.advance();
        assertEquals(0, cluster.getCachedPath().getNextNodeIndex());
        assertNull(cluster.getCachedPathCopyIfFresh(150L, 20, 10.5, 64.0, 10.5, 4.0));

        // Concurrent readers & writers must complete without ConcurrentModificationException or torn state
        int threads = 4;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                final int tid = t;
                futures.add(pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < 200; i++) {
                        int memberId = 10 + (i % 8);
                        if ((tid & 1) == 0) {
                            cluster.addMember(memberId);
                            cluster.cacheTarget(UUID.randomUUID(), 10.5, 64.0, 10.5, 100L + i);
                            cluster.cachePath(basePath, 10.5, 64.0, 10.5, 100L + i);
                        } else {
                            for (int id : cluster.getMemberIds()) {
                                assertTrue(id >= 0);
                            }
                            Path p = cluster.getCachedPathCopyIfFresh(100L + i, 20, 10.5, 64.0, 10.5, 4.0);
                            if (p != null) {
                                assertEquals(2, p.getNodeCount());
                            }
                            cluster.removeMember(memberId);
                        }
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> f : futures) {
                f.get(5, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void singleLockTickEvalAndZeroAllocContainsMemberAndSingleCopyPathAdapt() {
        SpatialClusterGrid grid = new SpatialClusterGrid();
        SpatialClusterGrid.MobTickResult out = new SpatialClusterGrid.MobTickResult();

        grid.updateMobAndEvaluate(1, 2.0, 64.0, 2.0, 100L, 6, 8, 2, 8, out);
        assertTrue(out.isLeader);
        assertEquals(1, out.clusterSize);
        assertTrue(out.isActiveTick);
        assertFalse(out.shouldSkipRecomputePath);
        assertTrue(out.cluster.containsMember(1));
        assertFalse(out.cluster.containsMember(999));

        grid.updateMobAndEvaluate(2, 2.5, 64.0, 2.5, 100L, 6, 8, 2, 8, out);
        assertFalse(out.isLeader);
        assertEquals(2, out.clusterSize);
        assertTrue(out.cluster.containsMember(2));

        // Same-cell move reuses existing cell & updates coordinates in place
        grid.updateMobAndEvaluate(2, 3.0, 64.0, 3.0, 101L, 6, 8, 2, 8, out);
        assertEquals(101L, grid.getMobLocation(2).lastSeenTick());

        // Single-copy path adaptation
        List<Node> nodes = List.of(new Node(0, 64, 0), new Node(6, 64, 0), new Node(12, 64, 0));
        Path leaderPath = new Path(nodes, new BlockPos(12, 64, 0), true);
        out.cluster.cachePath(leaderPath, 12.5, 64.0, 0.5, 100L);

        // Fresh & within snap tolerance -> adapts in place onto closest node (#1)
        Path adapted = out.cluster.adaptCachedPathIfFresh(105L, 20, 12.5, 64.0, 0.5, 4.0, 6.2, 64.0, 0.2, 8.0);
        assertNotNull(adapted);
        assertEquals(1, adapted.getNextNodeIndex());

        // Expired or out-of-range -> returns null without allocating Path copy
        assertNull(out.cluster.adaptCachedPathIfFresh(150L, 20, 12.5, 64.0, 0.5, 4.0, 6.2, 64.0, 0.2, 8.0));
        assertNull(out.cluster.adaptCachedPathIfFresh(105L, 20, 12.5, 64.0, 0.5, 4.0, 100.0, 64.0, 100.0, 8.0));
    }
}
