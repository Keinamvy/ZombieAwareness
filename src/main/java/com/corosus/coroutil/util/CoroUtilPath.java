package com.corosus.coroutil.util;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Random;

public class CoroUtilPath {

    public static boolean tryMoveToEntityLivingLongDist(Mob entSource, Entity entityTo, double moveSpeedAmp) {
        return tryMoveToXYZLongDist(entSource, entityTo.getX(), entityTo.getBoundingBox().minY, entityTo.getZ(), moveSpeedAmp);
    }

    public static boolean tryMoveToXYZLongDist(Mob ent, double x, double y, double z, double moveSpeedAmp) {
        Level world = ent.level();
        boolean success = false;

        try {
            if (ent.getNavigation().isDone()) {
                double distToPlayer = Math.sqrt(ent.distanceToSqr(x, y, z));
                AttributeInstance followRangeAttr = ent.getAttribute(Attributes.FOLLOW_RANGE);
                double followDist = followRangeAttr != null ? followRangeAttr.getValue() : 16.0D;

                if (distToPlayer <= followDist) {
                    return ent.getNavigation().moveTo(Mth.floor(x), Mth.floor(y), Mth.floor(z), moveSpeedAmp);
                } else {
                    double d0 = x + 0.5D - ent.getX();
                    double d2 = z + 0.5D - ent.getZ();
                    float yaw = (float) (Math.atan2(d2, d0) * 180.0D / Math.PI) - 90.0F;
                    Random rand = CU.rand();
                    float randYaw = rand.nextInt(90) - 45;
                    int bound = Math.max(1, (int) followDist / 2);
                    double dist = (followDist * 0.75D) + rand.nextInt(bound);

                    int tryX = (int) Math.floor(ent.getX() + (-Math.sin((yaw + randYaw) / 180.0F * (float) Math.PI) * dist));
                    int tryZ = (int) Math.floor(ent.getZ() + (Math.cos((yaw + randYaw) / 180.0F * (float) Math.PI) * dist));
                    int tryY = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, tryX, tryZ);

                    BlockPos pos = new BlockPos(tryX, tryY, tryZ);
                    if (!world.isLoaded(pos)) {
                        return false;
                    }
                    BlockState state = world.getBlockState(pos);
                    if (CoroUtilBlock.isAir(state.getBlock())) {
                        success = ent.getNavigation().moveTo(tryX, tryY, tryZ, moveSpeedAmp);
                    }
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return success;
    }
}
