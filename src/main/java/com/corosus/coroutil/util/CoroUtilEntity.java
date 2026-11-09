package com.corosus.coroutil.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class CoroUtilEntity {
    public static String getName(Entity ent) {
        return ent != null ? ent.getName().getString() : "nullObject";
    }

    public static boolean canSee(Entity ent, BlockPos pos) {
        Vec3 start = new Vec3(ent.getX(), ent.getEyeY(), ent.getZ());
        Vec3 end = new Vec3(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
        if (end.distanceToSqr(start) > 16384.0D) {
            return false;
        }
        BlockHitResult hit = ent.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, ent));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos);
    }
}
