package com.corosus.zombieawareness;

import com.corosus.zombieawareness.config.ZAConfigZDay;
import com.corosus.zombieawareness.heat.ChunkHeatManager;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class HordeVillagerTargetRegressionTest {

    @BeforeEach
    void setUp() {
        ChunkHeatManager.clearAllHeat();
    }

    @AfterEach
    void tearDown() {
        ChunkHeatManager.clearAllHeat();
    }

    @Test
    void hordeMobsBlockVillagerTargetingWhileNonHordeMobsPreserveVanillaBehavior() {
        // Horde mob must NEVER target a villager or another hostile mob
        assertTrue(ZAUtil.shouldBlockHordeMobTarget(true, true, false),
                "Horde mob targeting a villager must be blocked");
        assertTrue(ZAUtil.shouldBlockHordeMobTarget(true, false, true),
                "Horde mob friendly fire on hostile mobs must be blocked");

        // Horde mob MUST be allowed to target survival players
        assertFalse(ZAUtil.shouldBlockHordeMobTarget(true, false, false),
                "Horde mob targeting a player must be allowed");

        // Non-horde mob keeps vanilla villager targeting unaffected
        assertFalse(ZAUtil.shouldBlockHordeMobTarget(false, true, false),
                "Normal non-horde zombie should keep vanilla villager targeting");

        // Null safety checks on entity helpers
        assertFalse(ZAUtil.isHordeMob(null));
        assertFalse(ZAUtil.isVillagerEntity(null));
        assertFalse(ZAUtil.shouldBlockHordeTarget(null, null));
    }

    @Test
    void recognizesAllHordeTagsAndFiltersWildTags() {
        assertTrue(ZAUtil.hasHordeMobTag(Set.of("za_horde_mob")));
        assertTrue(ZAUtil.hasHordeMobTag(Set.of("za_tnt_sapper")));
        assertTrue(ZAUtil.hasHordeMobTag(Set.of("za_juggernaut")));
        assertTrue(ZAUtil.hasHordeMobTag(Set.of("za_crawler")));
        assertTrue(ZAUtil.hasHordeMobTag(Set.of("za_apex_corrosive")));
        assertTrue(ZAUtil.hasHordeMobTag(Set.of("za_screamer")));
        assertTrue(ZAUtil.hasHordeMobTag(Set.of("za_airdrop_passenger")));

        assertFalse(ZAUtil.hasHordeMobTag(Collections.emptySet()));
        assertFalse(ZAUtil.hasHordeMobTag(List.of("wild_zombie", "village_spawn")));
    }

    @Test
    void activeHordeLookupAndEligiblePlayerFilteringPrioritizeLiveSurvivalPlayers() {
        assertTrue(ChunkHeatManager.isEligibleHordeTargetPlayer(true, false, false, false));
        assertFalse(ChunkHeatManager.isEligibleHordeTargetPlayer(false, false, false, false));
        assertFalse(ChunkHeatManager.isEligibleHordeTargetPlayer(true, true, false, false));
        assertFalse(ChunkHeatManager.isEligibleHordeTargetPlayer(true, false, true, false));
        assertFalse(ChunkHeatManager.isEligibleHordeTargetPlayer(true, false, false, true));

        BlockPos villageEdge = new BlockPos(200, 64, -150);
        UUID playerId = UUID.randomUUID();
        assertTrue(ChunkHeatManager.tryRegisterHordeForGroup(villageEdge, Set.of(playerId), 1, 12, 4));

        ChunkHeatManager.ActiveHorde matched = ChunkHeatManager.findNearestActiveHorde(240.0, 64.0, -150.0);
        assertNotNull(matched, "Horde mob spawned 40 blocks from horde center near village must resolve the active horde");
        assertTrue(matched.targetGroupPlayerIds.contains(playerId));

        // Expired horde must not match
        matched.elapsedTicks = matched.totalDurationTicks;
        assertNull(ChunkHeatManager.findNearestActiveHorde(240.0, 64.0, -150.0));
    }

    @Test
    void allowHordeFriendlyExplosiveFireConfigTogglesExplosionDamageWithoutAllowingMeleeFriendlyFire() {
        assertFalse(ZAConfigZDay.allowHordeFriendlyExplosiveFire,
                "allowHordeFriendlyExplosiveFire must default to false");

        // When false (default), BOTH explosion and non-explosion friendly fire are blocked
        assertTrue(ZAUtil.shouldBlockHostileFriendlyFireDamage(true, true, true, false));
        assertTrue(ZAUtil.shouldBlockHostileFriendlyFireDamage(true, true, false, false));

        // When true, ONLY explosions between hostile/horde mobs are permitted; melee/arrows/sonic boom stay blocked
        assertFalse(ZAUtil.shouldBlockHostileFriendlyFireDamage(true, true, true, true));
        assertTrue(ZAUtil.shouldBlockHostileFriendlyFireDamage(true, true, false, true));

        // Non-hostile victims (e.g., players) are never blocked by friendly-fire rule
        assertFalse(ZAUtil.shouldBlockHostileFriendlyFireDamage(true, false, false, false));
        assertFalse(ZAUtil.shouldBlockHostileFriendlyFireDamage(true, false, true, false));
    }
}
