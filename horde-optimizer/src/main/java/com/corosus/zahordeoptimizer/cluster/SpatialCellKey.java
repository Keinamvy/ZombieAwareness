package com.corosus.zahordeoptimizer.cluster;

/**
 * Immutable 3D grid cell key used to group nearby horde mobs into spatial clusters.
 */
public record SpatialCellKey(int cx, int cy, int cz) {

    public static SpatialCellKey fromCoords(double x, double y, double z, int cellSizeXZ, int cellSizeY) {
        int safeXZ = Math.max(1, cellSizeXZ);
        int safeY = Math.max(1, cellSizeY);
        return new SpatialCellKey(
                Math.floorDiv((int) Math.floor(x), safeXZ),
                Math.floorDiv((int) Math.floor(y), safeY),
                Math.floorDiv((int) Math.floor(z), safeXZ)
        );
    }
}
