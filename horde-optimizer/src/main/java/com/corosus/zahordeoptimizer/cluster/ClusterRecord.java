package com.corosus.zahordeoptimizer.cluster;

import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Represents one spatial cluster of horde mobs sharing a leader, target cache, and path/waypoint cache.
 */
public final class ClusterRecord {

    private final SpatialCellKey cellKey;
    private final Set<Integer> memberIds = new LinkedHashSet<>();
    private int leaderId = -1;

    private UUID cachedTargetUuid;
    private long targetUpdatedTick = -1L;

    private Path cachedPath;
    private double cachedTargetX;
    private double cachedTargetY;
    private double cachedTargetZ;
    private boolean hasCachedTargetPos;
    private long pathUpdatedTick = -1L;

    private double cachedWaypointX;
    private double cachedWaypointY;
    private double cachedWaypointZ;
    private boolean hasCachedWaypoint;

    public ClusterRecord(SpatialCellKey cellKey) {
        this.cellKey = cellKey;
    }

    public SpatialCellKey getCellKey() {
        return cellKey;
    }

    public synchronized boolean addMember(int entityId) {
        return memberIds.add(entityId);
    }

    public synchronized boolean removeMember(int entityId) {
        boolean removed = memberIds.remove(entityId);
        if (removed && leaderId == entityId) {
            leaderId = -1;
            invalidatePathCache();
        }
        return removed;
    }

    public synchronized Set<Integer> getMemberIds() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(memberIds));
    }

    public synchronized int size() {
        return memberIds.size();
    }

    public synchronized boolean isEmpty() {
        return memberIds.isEmpty();
    }

    public synchronized int getLeaderId() {
        return leaderId;
    }

    public synchronized void setLeaderId(int leaderId) {
        if (this.leaderId != leaderId) {
            this.leaderId = leaderId;
        }
    }

    public synchronized boolean isLeader(int entityId) {
        return leaderId != -1 && leaderId == entityId;
    }

    public synchronized void cacheTarget(UUID targetUuid, double tx, double ty, double tz, long gameTick) {
        this.cachedTargetUuid = targetUuid;
        this.cachedTargetX = tx;
        this.cachedTargetY = ty;
        this.cachedTargetZ = tz;
        this.hasCachedTargetPos = true;
        this.targetUpdatedTick = gameTick;
    }

    public synchronized UUID getCachedTargetUuid() {
        return cachedTargetUuid;
    }

    public synchronized long getTargetUpdatedTick() {
        return targetUpdatedTick;
    }

    public synchronized void cachePath(Path path, double tx, double ty, double tz, long gameTick) {
        this.cachedPath = path != null ? path.copy() : null;
        this.cachedTargetX = tx;
        this.cachedTargetY = ty;
        this.cachedTargetZ = tz;
        this.hasCachedTargetPos = true;
        this.pathUpdatedTick = gameTick;
        this.hasCachedWaypoint = false;

        if (this.cachedPath != null && this.cachedPath.getNodeCount() > 0 && !this.cachedPath.isDone()) {
            int idx = Math.max(0, Math.min(this.cachedPath.getNextNodeIndex(), this.cachedPath.getNodeCount() - 1));
            Node nextNode = this.cachedPath.getNode(idx);
            this.cachedWaypointX = nextNode.x + 0.5;
            this.cachedWaypointY = nextNode.y;
            this.cachedWaypointZ = nextNode.z + 0.5;
            this.hasCachedWaypoint = true;
        }
    }

    public synchronized void cacheWaypoint(double wx, double wy, double wz, double tx, double ty, double tz, long gameTick) {
        this.cachedWaypointX = wx;
        this.cachedWaypointY = wy;
        this.cachedWaypointZ = wz;
        this.hasCachedWaypoint = true;
        this.cachedTargetX = tx;
        this.cachedTargetY = ty;
        this.cachedTargetZ = tz;
        this.hasCachedTargetPos = true;
        this.pathUpdatedTick = gameTick;
    }

    public synchronized boolean isPathCacheFresh(long gameTick, int ttlTicks, double reqX, double reqY, double reqZ, double targetTolerance) {
        if (cachedPath == null || cachedPath.isDone() || pathUpdatedTick < 0L) {
            return false;
        }
        if ((gameTick - pathUpdatedTick) > Math.max(1, ttlTicks)) {
            return false;
        }
        if (!hasCachedTargetPos) {
            return false;
        }
        double dx = reqX - cachedTargetX;
        double dy = reqY - cachedTargetY;
        double dz = reqZ - cachedTargetZ;
        return (dx * dx + dy * dy + dz * dz) <= (targetTolerance * targetTolerance);
    }

    public synchronized Path getCachedPathCopyIfFresh(long gameTick, int ttlTicks, double reqX, double reqY, double reqZ, double targetTolerance) {
        if (!isPathCacheFresh(gameTick, ttlTicks, reqX, reqY, reqZ, targetTolerance)) {
            return null;
        }
        return cachedPath != null ? cachedPath.copy() : null;
    }

    public synchronized Path getCachedPath() {
        return cachedPath;
    }

    public synchronized boolean hasCachedWaypoint() {
        return hasCachedWaypoint;
    }

    public synchronized double getCachedWaypointX() {
        return cachedWaypointX;
    }

    public synchronized double getCachedWaypointY() {
        return cachedWaypointY;
    }

    public synchronized double getCachedWaypointZ() {
        return cachedWaypointZ;
    }

    public synchronized boolean hasCachedTargetPos() {
        return hasCachedTargetPos;
    }

    public synchronized double getCachedTargetX() {
        return cachedTargetX;
    }

    public synchronized double getCachedTargetY() {
        return cachedTargetY;
    }

    public synchronized double getCachedTargetZ() {
        return cachedTargetZ;
    }

    public synchronized long getPathUpdatedTick() {
        return pathUpdatedTick;
    }

    public synchronized void invalidatePathCache() {
        this.cachedPath = null;
        this.pathUpdatedTick = -1L;
        this.hasCachedWaypoint = false;
    }

    public synchronized void invalidateAllCaches() {
        invalidatePathCache();
        this.cachedTargetUuid = null;
        this.hasCachedTargetPos = false;
        this.targetUpdatedTick = -1L;
    }

    /**
     * Produces a follower-specific copy of the leader's cached Path advanced to the closest node
     * within {@code maxSnapDistance} of the follower's current position.
     */
    public static Path adaptPathForFollower(Path sourcePath, double followerX, double followerY, double followerZ, double maxSnapDistance) {
        if (sourcePath == null || sourcePath.isDone() || sourcePath.getNodeCount() <= 0) {
            return null;
        }
        int nodeCount = sourcePath.getNodeCount();
        int startIdx = Math.max(0, sourcePath.getNextNodeIndex() - 2);
        int bestIdx = -1;
        double bestDistSq = Double.MAX_VALUE;

        for (int i = startIdx; i < nodeCount; i++) {
            Node node = sourcePath.getNode(i);
            double dx = (node.x + 0.5) - followerX;
            double dy = node.y - followerY;
            double dz = (node.z + 0.5) - followerZ;
            double dSq = dx * dx + dy * dy + dz * dz;
            if (dSq < bestDistSq) {
                bestDistSq = dSq;
                bestIdx = i;
            }
        }

        double maxDistSq = maxSnapDistance * maxSnapDistance;
        if (bestIdx < 0 || bestDistSq > maxDistSq) {
            return null;
        }

        Path copy = sourcePath.copy();
        copy.setNextNodeIndex(bestIdx);
        return copy;
    }
}
