package com.corosus.zahordeoptimizer.collision;

import com.corosus.zahordeoptimizer.HordeMobDetector;
import com.corosus.zahordeoptimizer.config.HordeOptimizerConfig;
import net.minecraft.world.entity.Entity;

/**
 * Policy decisions for disabling entity-to-entity collision pushing and cramming among horde mobs.
 */
public final class HordeCollisionPolicy {

    private HordeCollisionPolicy() {
    }

    /**
     * Pure predicate determining whether a push between two entities should be suppressed.
     */
    public static boolean shouldSuppressPush(
            boolean selfIsHorde,
            boolean otherIsHorde,
            boolean enabled,
            boolean disableCollision,
            boolean hordeToHordeOnly) {
        if (!enabled || !disableCollision) {
            return false;
        }
        if (hordeToHordeOnly) {
            return selfIsHorde && otherIsHorde;
        }
        return selfIsHorde || otherIsHorde;
    }

    /**
     * Pure predicate determining whether a candidate entity should be excluded from a horde mob's
     * pushable-entities scan (preventing both O(N^2) push loops and entity cramming damage).
     */
    public static boolean shouldExcludeFromPushScan(
            boolean selfIsHorde,
            boolean candidateIsHorde,
            boolean enabled,
            boolean disableCollision,
            boolean preventCramming,
            boolean hordeToHordeOnly) {
        if (!enabled || (!disableCollision && !preventCramming)) {
            return false;
        }
        if (!selfIsHorde) {
            return false;
        }
        return hordeToHordeOnly ? candidateIsHorde : true;
    }

    public static boolean shouldSuppressEntityPush(Entity self, Entity other) {
        if (self == null || other == null || self == other) {
            return false;
        }
        if (!HordeOptimizerConfig.enabled || !HordeOptimizerConfig.disableHordeCollision) {
            return false;
        }
        boolean selfHorde = HordeMobDetector.isHordeEntity(self);
        if (HordeOptimizerConfig.hordeToHordeCollisionOnly && !selfHorde) {
            return false;
        }
        boolean otherHorde = HordeMobDetector.isHordeEntity(other);
        return shouldSuppressPush(
                selfHorde,
                otherHorde,
                HordeOptimizerConfig.enabled,
                HordeOptimizerConfig.disableHordeCollision,
                HordeOptimizerConfig.hordeToHordeCollisionOnly
        );
    }

    public static boolean shouldSkipPushEntitiesScan(Entity self) {
        if (!HordeOptimizerConfig.enabled || !HordeOptimizerConfig.disableHordeCollision) {
            return false;
        }
        return !HordeOptimizerConfig.hordeToHordeCollisionOnly && HordeMobDetector.isHordeEntity(self);
    }

    public static boolean shouldFilterPushScanForSelf(Entity self) {
        if (!HordeOptimizerConfig.enabled) {
            return false;
        }
        if (!HordeOptimizerConfig.disableHordeCollision && !HordeOptimizerConfig.preventHordeEntityCramming) {
            return false;
        }
        return HordeMobDetector.isHordeEntity(self);
    }

    public static boolean shouldMakeUnpushable(Entity self) {
        return HordeOptimizerConfig.enabled
                && HordeOptimizerConfig.disableHordeCollision
                && HordeOptimizerConfig.makeHordeMobsUnpushable
                && HordeMobDetector.isHordeEntity(self);
    }
}
