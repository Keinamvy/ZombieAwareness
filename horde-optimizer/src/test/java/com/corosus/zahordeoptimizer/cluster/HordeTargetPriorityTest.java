package com.corosus.zahordeoptimizer.cluster;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class HordeTargetPriorityTest {

    @Test
    void clusterRejectsVillagersAndIneligiblePlayersAsSharedTargets() {
        // Villagers/NPCs must NEVER be cached or propagated across a horde cluster
        assertFalse(HordeClusterService.isEligibleClusterTarget(true, false, true, false, false),
                "Villager/NPC must be rejected as a cluster target");

        // Dead, removed, creative, or spectator players must be rejected
        assertFalse(HordeClusterService.isEligibleClusterTarget(false, false, false, true, false));
        assertFalse(HordeClusterService.isEligibleClusterTarget(true, true, false, true, false));
        assertFalse(HordeClusterService.isEligibleClusterTarget(true, false, false, true, true));

        // Alive survival player is eligible
        assertTrue(HordeClusterService.isEligibleClusterTarget(true, false, false, true, false));
    }

    @Test
    void clusterInvalidatesStalePathWhenPlayerMovesAwayFromVillagePosition() {
        ClusterRecord cluster = new ClusterRecord(new SpatialCellKey(0, 8, 0));
        UUID playerUuid = UUID.randomUUID();
        cluster.addMember(1);
        cluster.addMember(2);
        cluster.setLeaderId(1);

        // Player originally at (100, 64, 100) near a village
        cluster.cacheTarget(playerUuid, 100.5, 64.0, 100.5, 10L);
        List<Node> initialNodes = List.of(new Node(60, 64, 100), new Node(80, 64, 100), new Node(100, 64, 100));
        cluster.cachePath(new Path(initialNodes, new BlockPos(100, 64, 100), true), 100.5, 64.0, 100.5, 10L);

        assertTrue(cluster.isPathCacheFresh(15L, 20, 101.0, 64.0, 101.0, 4.0));

        // Player runs to (125, 64, 118) -> moved beyond 4-block tolerance
        assertTrue(HordeClusterService.hasTargetMovedBeyondTolerance(
                cluster.getCachedTargetX(),
                cluster.getCachedTargetY(),
                cluster.getCachedTargetZ(),
                125.5,
                64.0,
                118.5,
                4.0
        ));

        cluster.invalidatePathCache();
        cluster.cacheTarget(playerUuid, 125.5, 64.0, 118.5, 16L);

        assertNull(cluster.getCachedPath(), "Stale path to old village position must be cleared when player moves");
        assertFalse(cluster.isPathCacheFresh(16L, 20, 125.5, 64.0, 118.5, 4.0));
        assertEquals(125.5, cluster.getCachedTargetX(), 1e-6);
        assertEquals(118.5, cluster.getCachedTargetZ(), 1e-6);
    }
}
