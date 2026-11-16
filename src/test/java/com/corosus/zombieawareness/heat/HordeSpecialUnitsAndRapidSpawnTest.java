package com.corosus.zombieawareness.heat;

import com.corosus.zombieawareness.ZAUtil;
import com.corosus.zombieawareness.config.ZAConfigZDay;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class HordeSpecialUnitsAndRapidSpawnTest {

    @BeforeEach
    void setUp() {
        ChunkHeatManager.clearAllHeat();
    }

    @AfterEach
    void tearDown() {
        ChunkHeatManager.clearAllHeat();
    }

    @Test
    void phase4AndPhase5AreStrictlyLongerThanEarlierPhases() {
        double phase1a = ChunkHeatManager.PHASE_1A_END;
        double phase1b = ChunkHeatManager.PHASE_1B_END - ChunkHeatManager.PHASE_1A_END;
        double phase2 = ChunkHeatManager.PHASE_2_END - ChunkHeatManager.PHASE_1B_END;
        double phase3 = ChunkHeatManager.PHASE_3_END - ChunkHeatManager.PHASE_2_END;
        double phase4 = ChunkHeatManager.PHASE_4_END - ChunkHeatManager.PHASE_3_END;
        double phase5 = 1.0 - ChunkHeatManager.PHASE_4_END;

        assertTrue(phase4 > phase1a && phase4 > phase1b && phase4 > phase2 && phase4 > phase3,
                "Phase 4 duration share (" + phase4 + ") must exceed Phases 1A..3");
        assertTrue(phase5 > phase1a && phase5 > phase1b && phase5 > phase2 && phase5 > phase3,
                "Phase 5 duration share (" + phase5 + ") must exceed Phases 1A..3");
        assertTrue(phase4 + phase5 > 0.50, "Phase 4 + Phase 5 together should dominate the horde timeline");

        // Verify boundary transitions on Stage 10
        assertEquals(ChunkHeatManager.HordePhase.PHASE_3_JUGGERNAUTS_WITCHES,
                ChunkHeatManager.resolveHordePhase(0.44, 10));
        assertEquals(ChunkHeatManager.HordePhase.PHASE_4_CLIMAX_SAPPERS,
                ChunkHeatManager.resolveHordePhase(0.45, 10));
        assertEquals(ChunkHeatManager.HordePhase.PHASE_4_CLIMAX_SAPPERS,
                ChunkHeatManager.resolveHordePhase(0.71, 10));
        assertEquals(ChunkHeatManager.HordePhase.PHASE_5_APEX_CORROSION,
                ChunkHeatManager.resolveHordePhase(0.72, 10));
    }

    @Test
    void rapidSmallBatchSpawningUsesFastCadenceAndSmallBatchesAcrossAllPhases() {
        assertEquals(15, ZAConfigZDay.hordeRapidSpawnIntervalTicks);
        assertEquals(3, ZAConfigZDay.hordeRapidSpawnBatchSize);
        assertEquals(15, ChunkHeatManager.computeRapidSpawnIntervalTicks());
        assertTrue(ChunkHeatManager.computeRapidSpawnIntervalTicks() <= 20,
                "Rapid spawn interval must pulse rapidly (<= 20 ticks)");

        int soloBatch = ChunkHeatManager.computeRapidSpawnBatchSize(27, false, false);
        int fortifiedBloodmoonBatch = ChunkHeatManager.computeRapidSpawnBatchSize(60, true, true);
        assertTrue(soloBatch >= 2 && soloBatch < 12, "Solo rapid spawn batch must be small (< 12): " + soloBatch);
        assertTrue(fortifiedBloodmoonBatch >= soloBatch && fortifiedBloodmoonBatch < 12,
                "Bloodmoon+fortified rapid spawn batch must stay well below 12: " + fortifiedBloodmoonBatch);

        // Cap enforcement still applies to small rapid batches
        assertEquals(2, ChunkHeatManager.computeAllowedSubWaveSpawnCount(soloBatch, 178, 180));
        assertEquals(0, ChunkHeatManager.computeAllowedSubWaveSpawnCount(soloBatch, 180, 180));

        // Config fallback for invalid values
        int prevInterval = ZAConfigZDay.hordeRapidSpawnIntervalTicks;
        int prevBatch = ZAConfigZDay.hordeRapidSpawnBatchSize;
        try {
            ZAConfigZDay.hordeRapidSpawnIntervalTicks = 0;
            ZAConfigZDay.hordeRapidSpawnBatchSize = 0;
            assertEquals(ChunkHeatManager.DEFAULT_HORDE_RAPID_SPAWN_INTERVAL_TICKS,
                    ChunkHeatManager.computeRapidSpawnIntervalTicks());
            assertEquals(ChunkHeatManager.DEFAULT_HORDE_RAPID_SPAWN_BATCH_SIZE,
                    ChunkHeatManager.computeRapidSpawnBatchSize(12, false, false));
        } finally {
            ZAConfigZDay.hordeRapidSpawnIntervalTicks = prevInterval;
            ZAConfigZDay.hordeRapidSpawnBatchSize = prevBatch;
        }
    }

    @Test
    void bloodmoonPhase5WardenSpawnGateCountStandoffAndTurretPriority() {
        assertEquals(3, HordeSpecialUnitHelper.BLOODMOON_WARDEN_COUNT);
        assertEquals("za_bloodmoon_warden", HordeSpecialUnitHelper.BLOODMOON_WARDEN_TAG);
        assertTrue(ZAUtil.hasHordeMobTag(Set.of(HordeSpecialUnitHelper.BLOODMOON_WARDEN_TAG)));

        ChunkHeatManager.ActiveHorde horde = new ChunkHeatManager.ActiveHorde(
                new BlockPos(0, 64, 0), Collections.emptySet(), 6000, 12, 10);
        assertFalse(horde.bloodmoonWardensSpawned);

        // Gate must only open on Bloodmoon + Phase 5 + active reinforcement + not already spawned
        assertFalse(HordeSpecialUnitHelper.shouldSpawnBloodmoonPhase5Wardens(
                ChunkHeatManager.HordePhase.PHASE_4_CLIMAX_SAPPERS, true, false, true));
        assertFalse(HordeSpecialUnitHelper.shouldSpawnBloodmoonPhase5Wardens(
                ChunkHeatManager.HordePhase.PHASE_5_APEX_CORROSION, false, false, true));
        assertFalse(HordeSpecialUnitHelper.shouldSpawnBloodmoonPhase5Wardens(
                ChunkHeatManager.HordePhase.PHASE_5_APEX_CORROSION, true, true, true));
        assertFalse(HordeSpecialUnitHelper.shouldSpawnBloodmoonPhase5Wardens(
                ChunkHeatManager.HordePhase.PHASE_5_APEX_CORROSION, true, false, false));
        assertTrue(HordeSpecialUnitHelper.shouldSpawnBloodmoonPhase5Wardens(
                ChunkHeatManager.HordePhase.PHASE_5_APEX_CORROSION, true, false, true));

        // TACZ-Turrets entity ID recognition (zero hard dependency)
        assertTrue(HordeSpecialUnitHelper.isTaczTurretEntityTypeId("tacz_turrets", "turret"));
        assertTrue(HordeSpecialUnitHelper.isTaczTurretEntityTypeId("tacz_turrets", "sentry_turret"));
        assertFalse(HordeSpecialUnitHelper.isTaczTurretEntityTypeId("minecraft", "zombie"));
        assertFalse(HordeSpecialUnitHelper.isTaczTurretEntityTypeId("tacz_turrets", "turret_workbench"));

        // Turret priority selection ignores disabled turrets and selects closest enabled turret within 64m
        double[] tx = { 10.0, 40.0, 22.0 };
        double[] ty = { 64.0, 64.0, 64.0 };
        double[] tz = { 0.0, 0.0, 0.0 };
        boolean[] enabled = { false, true, true };
        assertEquals(2, HordeSpecialUnitHelper.selectClosestTurretIndex(
                0.0, 64.0, 0.0, tx, ty, tz, enabled, HordeSpecialUnitHelper.WARDEN_TURRET_SCAN_RADIUS));
        assertEquals(-1, HordeSpecialUnitHelper.selectClosestTurretIndex(
                200.0, 64.0, 0.0, tx, ty, tz, enabled, HordeSpecialUnitHelper.WARDEN_TURRET_SCAN_RADIUS),
                "Out-of-range turrets must return -1 so Warden falls back to player");

        // Standoff & distance Sonic Boom behavior
        assertEquals(HordeSpecialUnitHelper.WardenMovementAction.RETREAT_TO_STANDOFF,
                HordeSpecialUnitHelper.computeWardenTurretMovementAction(8.0));
        assertEquals(HordeSpecialUnitHelper.WardenMovementAction.HOLD_STANDOFF,
                HordeSpecialUnitHelper.computeWardenTurretMovementAction(16.0));
        assertEquals(HordeSpecialUnitHelper.WardenMovementAction.ADVANCE_TO_STANDOFF,
                HordeSpecialUnitHelper.computeWardenTurretMovementAction(34.0));
        assertTrue(HordeSpecialUnitHelper.canFireSonicBoomAtTurret(16.0));
        assertTrue(HordeSpecialUnitHelper.canFireSonicBoomAtTurret(48.0));
        assertFalse(HordeSpecialUnitHelper.canFireSonicBoomAtTurret(49.0));

        double[] standoff = HordeSpecialUnitHelper.computeWardenStandoffPos(32.0, 0.0, 0.0, 0.0, 16.0);
        assertEquals(16.0, standoff[0], 1e-6);
        assertEquals(0.0, standoff[1], 1e-6);
    }

    @Test
    void tankerZombieConstantsHordeRecognitionAndTurretFocusPriority() {
        assertEquals("za_tanker", HordeSpecialUnitHelper.TANKER_TAG);
        assertEquals(2, HordeSpecialUnitHelper.TANKER_RESISTANCE_AMPLIFIER,
                "Amplifier 2 corresponds to Resistance III in Minecraft");
        assertTrue(HordeSpecialUnitHelper.TANKER_MAX_HEALTH >= 40.0D);
        assertTrue(HordeSpecialUnitHelper.TANKER_KNOCKBACK_RESISTANCE >= 0.85D);
        assertTrue(HordeSpecialUnitHelper.TANKER_MOVEMENT_SPEED < 0.20D,
                "Tanker zombie must move slower than standard zombie speed (~0.23D)");
        assertTrue(HordeSpecialUnitHelper.TANKER_SCALE >= 1.30D,
                "Tanker zombie must have larger visual scale");
        assertEquals("§b§l[TANKER]", HordeSpecialUnitHelper.resolveSpecialIndicatorLabel(Set.of("za_horde_mob", "za_tanker")));
        assertEquals(1.35D, HordeSpecialUnitHelper.resolveSpecialIndicatorScale(Set.of("za_tanker")), 1e-6);
        assertTrue(ZAUtil.hasHordeMobTag(Set.of(HordeSpecialUnitHelper.TANKER_TAG)));

        // Nearest visible alive Tanker wins turret focus over non-visible closer ones
        double[] tankerX = { 6.0, 18.0, 11.0 };
        double[] tankerY = { 64.0, 64.0, 64.0 };
        double[] tankerZ = { 0.0, 0.0, 0.0 };
        boolean[] aliveVisible = { false, true, true };
        assertEquals(2, HordeSpecialUnitHelper.selectPriorityTankerIndex(
                0.0, 64.0, 0.0, tankerX, tankerY, tankerZ, aliveVisible, HordeSpecialUnitHelper.TURRET_FOCUS_RADIUS));
    }

    @Test
    void hordeWardenNeverTargetsOrDamagesOtherHordeMobs() {
        // Horde Warden -> Horde mob (including ZombieHorse mount) / Hostile / Villager must be blocked
        assertTrue(ZAUtil.shouldBlockWardenFriendlyAction(true, true, false, false));
        assertTrue(ZAUtil.shouldBlockWardenFriendlyAction(true, false, true, false));
        assertTrue(ZAUtil.shouldBlockWardenFriendlyAction(true, false, false, true));

        // Horde Warden -> TACZ-Turrets or Survival Players (not horde/hostile/villager) must remain allowed
        assertFalse(ZAUtil.shouldBlockWardenFriendlyAction(true, false, false, false));

        // Non-horde vanilla Warden retains vanilla behavior
        assertFalse(ZAUtil.shouldBlockWardenFriendlyAction(false, false, true, false));
    }

    @Test
    void allSpecialZombieIndicatorsAndKnightCavalryConstantsMatchSpec() throws Exception {
        assertEquals("§5§l[SIEGE WARDEN]", HordeSpecialUnitHelper.resolveSpecialIndicatorLabel(Set.of("za_horde_mob", "za_bloodmoon_warden")));
        assertEquals(1.15D, HordeSpecialUnitHelper.resolveSpecialIndicatorScale(Set.of("za_bloodmoon_warden")), 1e-6);

        assertEquals("§6§l[KNIGHT]", HordeSpecialUnitHelper.resolveSpecialIndicatorLabel(Set.of("za_horde_mob", "za_knight")));
        assertEquals(1.10D, HordeSpecialUnitHelper.resolveSpecialIndicatorScale(Set.of("za_knight")), 1e-6);

        assertEquals("§4§l[JUGGERNAUT]", HordeSpecialUnitHelper.resolveSpecialIndicatorLabel(Set.of("za_horde_mob", "za_juggernaut")));
        assertEquals(1.20D, HordeSpecialUnitHelper.resolveSpecialIndicatorScale(Set.of("za_juggernaut")), 1e-6);

        assertEquals("§e§l[CRAWLER]", HordeSpecialUnitHelper.resolveSpecialIndicatorLabel(Set.of("za_horde_mob", "za_crawler")));
        assertEquals(0.75D, HordeSpecialUnitHelper.resolveSpecialIndicatorScale(Set.of("za_crawler")), 1e-6);

        assertEquals("§d§l[SCREAMER]", HordeSpecialUnitHelper.resolveSpecialIndicatorLabel(Set.of("za_screamer")));
        assertEquals(1.05D, HordeSpecialUnitHelper.resolveSpecialIndicatorScale(Set.of("za_screamer")), 1e-6);

        assertEquals("§c§l[SAPPER]", HordeSpecialUnitHelper.resolveSpecialIndicatorLabel(Set.of("za_tnt_sapper")));
        assertEquals("§2§l[CORROSIVE]", HordeSpecialUnitHelper.resolveSpecialIndicatorLabel(Set.of("za_horde_mob", "za_apex_corrosive")));
        assertNull(HordeSpecialUnitHelper.resolveSpecialIndicatorLabel(Set.of("za_horde_mob", "za_knight_mount", "za_apex_corrosive")));

        // Knight Cavalry (MC 26.3 ZombieHorse + Iron/Diamond Spear + Resistance I)
        assertEquals("za_knight", HordeSpecialUnitHelper.KNIGHT_TAG);
        assertEquals("za_knight_mount", HordeSpecialUnitHelper.KNIGHT_MOUNT_TAG);
        assertEquals(0, HordeSpecialUnitHelper.KNIGHT_RESISTANCE_AMPLIFIER, "Amplifier 0 corresponds to Resistance I");
        assertTrue(ZAUtil.hasHordeMobTag(Set.of(HordeSpecialUnitHelper.KNIGHT_TAG)));
        assertTrue(ZAUtil.hasHordeMobTag(Set.of(HordeSpecialUnitHelper.KNIGHT_MOUNT_TAG)));
        ClassLoader cl = HordeSpecialUnitsAndRapidSpawnTest.class.getClassLoader();
        Class<?> itemsClass = Class.forName("net.minecraft.world.item.Items", false, cl);
        Class<?> entityTypesClass = Class.forName("net.minecraft.world.entity.EntityTypes", false, cl);
        assertNotNull(itemsClass.getField("IRON_SPEAR"));
        assertNotNull(itemsClass.getField("DIAMOND_SPEAR"));
        assertNotNull(itemsClass.getField("SHIELD"));
        assertNotNull(entityTypesClass.getField("ZOMBIE_HORSE"));
        assertNotNull(Class.forName("net.minecraft.world.entity.animal.equine.ZombieHorse", false, cl));
    }
}
