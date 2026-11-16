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

    /**
     * Packs 3D cell coordinates (26-bit X, 26-bit Z, 12-bit Y) into a primitive long with zero allocation.
     */
    public static long packCellCoords(int cx, int cy, int cz) {
        return (((long) cx & 0x3FFFFFFL) << 38)
                | (((long) cz & 0x3FFFFFFL) << 12)
                | ((long) cy & 0xFFFL);
    }

    public static long packFromCoords(double x, double y, double z, int cellSizeXZ, int cellSizeY) {
        int safeXZ = Math.max(1, cellSizeXZ);
        int safeY = Math.max(1, cellSizeY);
        int cx = Math.floorDiv((int) Math.floor(x), safeXZ);
        int cy = Math.floorDiv((int) Math.floor(y), safeY);
        int cz = Math.floorDiv((int) Math.floor(z), safeXZ);
        return packCellCoords(cx, cy, cz);
    }

    public static SpatialCellKey unpack(long packed, double x, double y, double z, int cellSizeXZ, int cellSizeY) {
        return fromCoords(x, y, z, cellSizeXZ, cellSizeY);
    }
}
