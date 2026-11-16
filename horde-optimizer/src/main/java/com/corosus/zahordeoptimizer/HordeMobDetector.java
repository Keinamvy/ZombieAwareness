package com.corosus.zahordeoptimizer;

import com.corosus.zombieawareness.ZAUtil;
import com.corosus.zombieawareness.heat.ChunkHeatManager;
import com.corosus.zahordeoptimizer.config.HordeOptimizerConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

import java.util.Collection;
import java.util.Set;

/**
 * Identifies ZombieAwareness horde mobs via ZA's canonical entity tags and optional active-horde proximity.
 */
public final class HordeMobDetector {

    public static final String PRIMARY_HORDE_TAG = "za_horde_mob";

    public static final Set<String> HORDE_TAGS = Set.of(
            PRIMARY_HORDE_TAG,
            "za_tnt_sapper",
            "za_juggernaut",
            "za_crawler",
            "za_apex_corrosive",
            "za_screamer",
            "za_airdrop_passenger",
            ZAUtil.TANKER_TAG,
            ZAUtil.BLOODMOON_WARDEN_TAG,
            ZAUtil.KNIGHT_TAG,
            ZAUtil.KNIGHT_MOUNT_TAG,
            ZAUtil.RAMPAGE_TAG
    );

    private HordeMobDetector() {
    }

    public static boolean hasHordeTag(Collection<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return false;
        }
        for (String tag : HORDE_TAGS) {
            if (tags.contains(tag)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isWithinActiveHordeRadius(double x, double y, double z) {
        double radiusSq = ChunkHeatManager.GROUP_CLUSTER_RADIUS * ChunkHeatManager.GROUP_CLUSTER_RADIUS;
        for (ChunkHeatManager.ActiveHorde horde : ChunkHeatManager.getActiveHordes()) {
            if (horde == null || horde.center == null || horde.isFullyExpired()) {
                continue;
            }
            double dx = x - (horde.center.getX() + 0.5);
            double dy = y - (horde.center.getY() + 0.5);
            double dz = z - (horde.center.getZ() + 0.5);
            if ((dx * dx + dy * dy + dz * dz) <= radiusSq) {
                return true;
            }
        }
        return false;
    }

    public static boolean isHordeEntity(Entity entity) {
        if (entity == null) {
            return false;
        }
        if (hasHordeTag(entity.entityTags())) {
            return true;
        }
        if (HordeOptimizerConfig.includeNearbyHostilesInActiveHorde
                && entity instanceof Mob
                && ZAUtil.isHostileMob(entity)) {
            return isWithinActiveHordeRadius(entity.getX(), entity.getY(), entity.getZ());
        }
        return false;
    }

    public static boolean isHordeMob(Entity entity) {
        return entity instanceof Mob && isHordeEntity(entity);
    }
}
