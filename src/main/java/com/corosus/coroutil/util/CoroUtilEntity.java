package com.corosus.coroutil.util;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class CoroUtilEntity {

    @FunctionalInterface
    public interface ChunkLoadedPredicate {
        boolean isChunkLoaded(int chunkX, int chunkZ);
    }

    public static String getName(Entity ent) {
        return ent != null ? ent.getName().getString() : "nullObject";
    }

    public static boolean areAllSampledRayChunksLoaded(
            double startX,
            double startZ,
            double endX,
            double endZ,
            ChunkLoadedPredicate predicate) {
        if (predicate == null) {
            return false;
        }
        double dx = endX - startX;
        double dz = endZ - startZ;
        double dist = Math.hypot(dx, dz);
        int steps = Math.max(1, (int) Math.ceil(dist / 8.0D));
        int prevChunkX = Integer.MIN_VALUE;
        int prevChunkZ = Integer.MIN_VALUE;
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / (double) steps;
            int chunkX = Mth.floor(startX + dx * t) >> 4;
            int chunkZ = Mth.floor(startZ + dz * t) >> 4;
            if (chunkX != prevChunkX || chunkZ != prevChunkZ) {
                if (!predicate.isChunkLoaded(chunkX, chunkZ)) {
                    return false;
                }
                prevChunkX = chunkX;
                prevChunkZ = chunkZ;
            }
        }
        return true;
    }

    public static boolean canSee(Entity ent, BlockPos pos) {
        if (ent == null || pos == null || ent.level() == null) {
            return false;
        }
        Level level = ent.level();
        if (!level.isLoaded(ent.blockPosition()) || !level.isLoaded(pos)) {
            return false;
        }
        Vec3 start = new Vec3(ent.getX(), ent.getEyeY(), ent.getZ());
        Vec3 end = new Vec3(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
        if (end.distanceToSqr(start) > 16384.0D) {
            return false;
        }
        if (!areAllSampledRayChunksLoaded(start.x, start.z, end.x, end.z, level::hasChunk)) {
            return false;
        }
        BlockHitResult hit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, ent));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos);
    }
}
