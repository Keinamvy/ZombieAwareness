package com.corosus.zombieawareness.heat;

import com.corosus.zombieawareness.ZAUtil;
import com.corosus.zombieawareness.config.ZAConfigZDay;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

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

    @Test
    void fortificationCacheDefaultsAndWardenStateTrackingWorkCleanly() {
        ChunkHeatManager.ActiveHorde horde = new ChunkHeatManager.ActiveHorde(
                new BlockPos(10, 64, 10), Collections.emptySet(), 6000, 12, 10);
        assertEquals(-1L, horde.lastFortifiedCheckTick);
        assertNull(horde.lastFortifiedCheckPos);
        assertFalse(HordeSpecialUnitHelper.hasActiveWardenStates());
        assertFalse(HordeSpecialUnitHelper.isTaczTurretEntity(null));
        assertTrue(HordeSpecialUnitHelper.isTaczTurretEntityTypeId("tacz_turrets", "turret"),
                "Turret ID helper remains functional regardless of FabricLoader mod-loaded short-circuit");
    }

    @Test
    void engineerLoadoutTagsIndicatorAndJuggernautThrowerPriorityAndSapperFuse() {
        // 1. Siege Engineer constants & indicator
        assertEquals("za_engineer", HordeSpecialUnitHelper.ENGINEER_TAG);
        assertEquals("§3§l[ENGINEER]", HordeSpecialUnitHelper.resolveSpecialIndicatorLabel(
                Set.of("za_horde_mob", HordeSpecialUnitHelper.ENGINEER_TAG)));
        assertEquals(1.05D, HordeSpecialUnitHelper.resolveSpecialIndicatorScale(
                Set.of(HordeSpecialUnitHelper.ENGINEER_TAG)), 1e-6);

        // 2. Thrower Juggernaut config & target range selection (turret priority over player)
        assertEquals(200, ZAConfigZDay.juggernautThrowIntervalTicks);
        assertEquals(8.0, ZAConfigZDay.juggernautThrowMinRange, 1e-6);
        assertEquals(28.0, ZAConfigZDay.juggernautThrowMaxRange, 1e-6);
        assertEquals(50, ZAConfigZDay.thrownSapperFuseTicks);

        double[] tx = { 5.0, 22.0, 14.0 };
        double[] ty = { 64.0, 64.0, 64.0 };
        double[] tz = { 0.0, 0.0, 0.0 };
        boolean[] enabled = { true, true, false };
        // Index 0 is < 8m minRange, index 2 is disabled -> index 1 (22m) wins
        assertEquals(1, HordeSpecialUnitHelper.selectThrowerTurretIndex(
                0.0, 64.0, 0.0, tx, ty, tz, enabled, 8.0, 28.0));

        // Elevated player at 4m (< 8m horiz, +2m Y) is eligible fallback
        double[] px = { 4.0, 15.0 };
        double[] py = { 66.0, 64.0 };
        double[] pz = { 0.0, 0.0 };
        boolean[] eligible = { true, true };
        assertEquals(0, HordeSpecialUnitHelper.selectThrowerPlayerIndex(
                0.0, 64.0, 0.0, px, py, pz, eligible, 8.0, 28.0));

        // 3. Ballistic throw velocity
        Vec3 vel = HordeSpecialUnitHelper.computeBallisticThrowVelocity(
                new Vec3(0.0, 64.0, 0.0), new Vec3(20.0, 68.0, 0.0));
        assertTrue(vel.x > 0.0 && vel.y >= 0.42 && Math.abs(vel.z) < 1e-6,
                "Throw velocity must propel toward target with upward arc: " + vel);

        // 4. Plain horde zombie ammo conversion rules
        assertTrue(HordeSpecialUnitHelper.canBeConvertedToThrowAmmo(Set.of("za_horde_mob")));
        assertFalse(HordeSpecialUnitHelper.canBeConvertedToThrowAmmo(Set.of("za_horde_mob", "za_tanker")));
        assertFalse(HordeSpecialUnitHelper.canBeConvertedToThrowAmmo(Set.of("za_horde_mob", "za_engineer")));
        assertFalse(HordeSpecialUnitHelper.canBeConvertedToThrowAmmo(Set.of("za_horde_mob", "za_juggernaut")));
        assertFalse(HordeSpecialUnitHelper.canBeConvertedToThrowAmmo(Set.of("za_horde_mob", "za_knight")));

        // 5. Thrown Sapper 50-tick landing fuse
        HordeSpecialUnitHelper.ThrownSapperFuseState fuse = new HordeSpecialUnitHelper.ThrownSapperFuseState(50);
        for (int t = 0; t < 5; t++) {
            assertFalse(HordeSpecialUnitHelper.stepThrownSapperFuse(fuse, false), "Airborne ticks must not start fuse early");
        }
        assertFalse(fuse.landed);
        // Landing tick triggers tick #1 of 50
        for (int t = 1; t < 50; t++) {
            assertFalse(HordeSpecialUnitHelper.stepThrownSapperFuse(fuse, true), "Sapper must not detonate before 50 landed ticks (tick " + t + ")");
        }
        assertTrue(fuse.landed);
        assertTrue(HordeSpecialUnitHelper.stepThrownSapperFuse(fuse, true), "Sapper must detonate on tick 50 after landing");
    }

    @Test
    void earlyStageEliteSpawnMultiplierAndStageUnlockGatesThrottleStagesOneThroughEight() {
        assertEquals(0.0D, ChunkHeatManager.computeStageEliteSpawnMultiplier(1), 1e-6);
        assertEquals(0.15D, ChunkHeatManager.computeStageEliteSpawnMultiplier(2), 1e-6);
        assertEquals(0.30D, ChunkHeatManager.computeStageEliteSpawnMultiplier(3), 1e-6);
        assertEquals(0.40D, ChunkHeatManager.computeStageEliteSpawnMultiplier(4), 1e-6);
        assertEquals(0.55D, ChunkHeatManager.computeStageEliteSpawnMultiplier(5), 1e-6);
        assertEquals(0.70D, ChunkHeatManager.computeStageEliteSpawnMultiplier(6), 1e-6);
        assertEquals(0.80D, ChunkHeatManager.computeStageEliteSpawnMultiplier(7), 1e-6);
        assertEquals(0.90D, ChunkHeatManager.computeStageEliteSpawnMultiplier(8), 1e-6);
        assertEquals(1.0D, ChunkHeatManager.computeStageEliteSpawnMultiplier(9), 1e-6);
        assertEquals(1.0D, ChunkHeatManager.computeStageEliteSpawnMultiplier(10), 1e-6);

        // Stage 1: zero elites
        assertFalse(ChunkHeatManager.canSpawnEliteInStage(HordeSpecialUnitHelper.TANKER_TAG, 1));
        assertFalse(ChunkHeatManager.canSpawnEliteInStage(HordeSpecialUnitHelper.CRAWLER_TAG, 1));
        assertEquals(0.0D, ChunkHeatManager.computeScaledEliteChance(0.20D, HordeSpecialUnitHelper.TANKER_TAG, 1), 1e-6);

        // Stage 2: rare Tanker/Crawler only; Engineers/Knights/Screamers/Juggernauts/Sappers locked
        assertTrue(ChunkHeatManager.canSpawnEliteInStage(HordeSpecialUnitHelper.TANKER_TAG, 2));
        assertTrue(ChunkHeatManager.canSpawnEliteInStage(HordeSpecialUnitHelper.CRAWLER_TAG, 2));
        assertEquals(0.0225D, ChunkHeatManager.computeScaledEliteChance(0.15D, HordeSpecialUnitHelper.TANKER_TAG, 2), 1e-6);
        assertFalse(ChunkHeatManager.canSpawnEliteInStage(HordeSpecialUnitHelper.ENGINEER_TAG, 2));
        assertFalse(ChunkHeatManager.canSpawnEliteInStage(HordeSpecialUnitHelper.KNIGHT_TAG, 2));
        assertFalse(ChunkHeatManager.canSpawnEliteInStage("za_screamer", 2));
        assertFalse(ChunkHeatManager.canSpawnEliteInStage(HordeSpecialUnitHelper.JUGGERNAUT_TAG, 2));
        assertFalse(ChunkHeatManager.canSpawnEliteInStage(HordeSpecialUnitHelper.SAPPER_TAG, 2));

        // Progressive unlocks across Stages 3-8
        assertTrue(ChunkHeatManager.canSpawnEliteInStage(HordeSpecialUnitHelper.ENGINEER_TAG, 3));
        assertFalse(ChunkHeatManager.canSpawnEliteInStage(HordeSpecialUnitHelper.KNIGHT_TAG, 4));
        assertTrue(ChunkHeatManager.canSpawnEliteInStage(HordeSpecialUnitHelper.KNIGHT_TAG, 5));
        assertFalse(ChunkHeatManager.canSpawnEliteInStage("za_screamer", 5));
        assertTrue(ChunkHeatManager.canSpawnEliteInStage("za_screamer", 6));
        assertFalse(ChunkHeatManager.canSpawnEliteInStage(HordeSpecialUnitHelper.JUGGERNAUT_TAG, 6));
        assertTrue(ChunkHeatManager.canSpawnEliteInStage(HordeSpecialUnitHelper.JUGGERNAUT_TAG, 7));
        assertFalse(ChunkHeatManager.canSpawnEliteInStage(HordeSpecialUnitHelper.SAPPER_TAG, 7));
        assertTrue(ChunkHeatManager.canSpawnEliteInStage(HordeSpecialUnitHelper.SAPPER_TAG, 8));
        assertEquals(0.14D, ChunkHeatManager.computeScaledEliteChance(0.14D, HordeSpecialUnitHelper.SAPPER_TAG, 10), 1e-6);
    }

    @Test
    void earlyStageSprinterAndRandomSpeedBoostNerfedToPlayerWalkingSpeedInStagesOneAndTwo() {
        // ZAUtil random speed boost scale keeps Stages 1-2 zombies near vanilla 0.23 (~4.3 blocks/s) walking speed
        assertEquals(0.0D, ZAUtil.computeStageSpeedBoostMultiplier(1), 1e-6);
        assertTrue(ZAUtil.computeStageSpeedBoostMultiplier(2) <= 0.10D);
        assertEquals(0.08D, ZAUtil.computeStageSpeedBoostMultiplier(2), 1e-6);
        assertEquals(0.35D, ZAUtil.computeStageSpeedBoostMultiplier(3), 1e-6);
        assertEquals(0.50D, ZAUtil.computeStageSpeedBoostMultiplier(4), 1e-6);
        assertEquals(0.75D, ZAUtil.computeStageSpeedBoostMultiplier(5), 1e-6);
        assertEquals(0.75D, ZAUtil.computeStageSpeedBoostMultiplier(6), 1e-6);
        assertEquals(1.0D, ZAUtil.computeStageSpeedBoostMultiplier(7), 1e-6);
        assertEquals(1.0D, ZAUtil.computeStageSpeedBoostMultiplier(10), 1e-6);

        // Stages 1-2 grant zero MobEffects.SPEED sprinters
        assertFalse(ChunkHeatManager.canGrantSprinterSpeedEffect(1, 0.0D));
        assertFalse(ChunkHeatManager.canGrantSprinterSpeedEffect(2, 0.0D));

        // Stages 3-4 give gentle short Speed I transition at modest rates (25% / 40%)
        assertTrue(ChunkHeatManager.canGrantSprinterSpeedEffect(3, 0.24D));
        assertFalse(ChunkHeatManager.canGrantSprinterSpeedEffect(3, 0.25D));
        assertTrue(ChunkHeatManager.canGrantSprinterSpeedEffect(4, 0.39D));
        assertFalse(ChunkHeatManager.canGrantSprinterSpeedEffect(4, 0.40D));
        assertEquals(0, ChunkHeatManager.computeSprinterSpeedAmplifier(3));
        assertEquals(1200, ChunkHeatManager.computeSprinterSpeedDurationTicks(4));

        // Stages 5-6 ramp to 75% with Speed II; Stages 7+ grant 100% Speed II
        assertTrue(ChunkHeatManager.canGrantSprinterSpeedEffect(5, 0.74D));
        assertFalse(ChunkHeatManager.canGrantSprinterSpeedEffect(5, 0.75D));
        assertEquals(1, ChunkHeatManager.computeSprinterSpeedAmplifier(5));
        assertEquals(7200, ChunkHeatManager.computeSprinterSpeedDurationTicks(5));
        assertTrue(ChunkHeatManager.canGrantSprinterSpeedEffect(7, 0.99D));
        assertTrue(ChunkHeatManager.canGrantSprinterSpeedEffect(10, 0.99D));
    }

    @Test
    void onlyScreamerInHordesGlowsAndOtherHordeOrNonHordeUnitsDoNotGlow() throws Exception {
        // 1. Screamer in hordes must glow (base, Phase 5 Corrosive, Rampage overtime, labeled)
        assertTrue(HordeSpecialUnitHelper.shouldHordeScreamerGlow(
                Set.of("za_horde_mob", HordeSpecialUnitHelper.SCREAMER_TAG)));
        assertTrue(HordeSpecialUnitHelper.shouldHordeScreamerGlow(
                Set.of("za_horde_mob", HordeSpecialUnitHelper.SCREAMER_TAG, "za_apex_corrosive")));
        assertTrue(HordeSpecialUnitHelper.shouldHordeScreamerGlow(
                Set.of("za_horde_mob", HordeSpecialUnitHelper.SCREAMER_TAG, ZAUtil.RAMPAGE_TAG)));
        assertTrue(HordeSpecialUnitHelper.shouldHordeScreamerGlow(
                Set.of("za_horde_mob", HordeSpecialUnitHelper.SCREAMER_TAG, HordeSpecialUnitHelper.LABELED_MOB_TAG)));

        // 2. Non-horde Screamers must NOT glow
        assertFalse(HordeSpecialUnitHelper.shouldHordeScreamerGlow(
                Set.of(HordeSpecialUnitHelper.SCREAMER_TAG)));
        assertFalse(HordeSpecialUnitHelper.shouldHordeScreamerGlow(
                Set.of(HordeSpecialUnitHelper.SCREAMER_TAG, HordeSpecialUnitHelper.LABELED_MOB_TAG)));

        // 3. All other horde mobs, elites, Apex variants, Bloodmoon/Rampage mobs, and clones must NOT glow
        assertFalse(HordeSpecialUnitHelper.shouldHordeScreamerGlow(null));
        assertFalse(HordeSpecialUnitHelper.shouldHordeScreamerGlow(Collections.emptySet()));
        List<Set<String>> nonGlowingTagSets = List.of(
                Set.of("za_horde_mob"),
                Set.of("za_horde_mob", HordeSpecialUnitHelper.TANKER_TAG),
                Set.of("za_horde_mob", HordeSpecialUnitHelper.KNIGHT_TAG),
                Set.of("za_horde_mob", HordeSpecialUnitHelper.KNIGHT_MOUNT_TAG),
                Set.of("za_horde_mob", HordeSpecialUnitHelper.ENGINEER_TAG),
                Set.of("za_horde_mob", HordeSpecialUnitHelper.JUGGERNAUT_TAG),
                Set.of("za_horde_mob", HordeSpecialUnitHelper.CRAWLER_TAG),
                Set.of("za_horde_mob", HordeSpecialUnitHelper.SAPPER_TAG),
                Set.of("za_horde_mob", HordeSpecialUnitHelper.BLOODMOON_WARDEN_TAG, "za_apex_corrosive"),
                Set.of("za_horde_mob", "za_apex_corrosive"),
                Set.of("za_horde_mob", ZAUtil.RAMPAGE_TAG),
                Set.of("za_horde_mob", "za_airdrop_passenger"),
                Set.of("za_horde_mob", ZAUtil.DAMAGE_CLONE_TAG, ZAUtil.CLONED_MOB_TAG),
                Set.of("za_horde_mob", HordeSpecialUnitHelper.TANKER_TAG, HordeSpecialUnitHelper.SCREAMER_TAG),
                Set.of("za_horde_mob", HordeSpecialUnitHelper.KNIGHT_TAG, HordeSpecialUnitHelper.SCREAMER_TAG),
                Set.of("za_horde_mob", HordeSpecialUnitHelper.ENGINEER_TAG, HordeSpecialUnitHelper.SCREAMER_TAG),
                Set.of("za_horde_mob", HordeSpecialUnitHelper.JUGGERNAUT_TAG, HordeSpecialUnitHelper.SCREAMER_TAG),
                Set.of("za_horde_mob", HordeSpecialUnitHelper.SAPPER_TAG, HordeSpecialUnitHelper.SCREAMER_TAG),
                Set.of("za_horde_mob", HordeSpecialUnitHelper.BLOODMOON_WARDEN_TAG, HordeSpecialUnitHelper.SCREAMER_TAG)
        );
        for (Set<String> tags : nonGlowingTagSets) {
            assertFalse(HordeSpecialUnitHelper.shouldHordeScreamerGlow(tags),
                    "Non-Screamer or non-[SCREAMER]-labeled mob must not glow: " + tags);
        }

        // 4. Bytecode invariant: only HordeSpecialUnitHelper#applySpecialMobIndicator references MobEffects.GLOWING
        // and it must gate glowing through shouldHordeScreamerGlow; no class calls Entity#setGlowingTag.
        Path mainClassesRoot = Path.of(HordeSpecialUnitHelper.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        assertTrue(Files.isDirectory(mainClassesRoot), "Expected compiled main classes directory at " + mainClassesRoot);
        Set<String> glowCallSites = new LinkedHashSet<>();
        boolean applySpecialIndicatorCallsGate = false;

        try (Stream<Path> walk = Files.walk(mainClassesRoot)) {
            for (Path classFile : walk.filter(p -> p.toString().endsWith(".class")).toList()) {
                try (InputStream is = Files.newInputStream(classFile)) {
                    ClassNode cn = new ClassNode();
                    new ClassReader(is).accept(cn, 0);
                    for (MethodNode mn : cn.methods) {
                        for (AbstractInsnNode insn : mn.instructions) {
                            if (insn instanceof FieldInsnNode fin
                                    && "net/minecraft/world/effect/MobEffects".equals(fin.owner)
                                    && "GLOWING".equals(fin.name)) {
                                glowCallSites.add(cn.name + "#" + mn.name);
                            }
                            if (insn instanceof MethodInsnNode min) {
                                assertFalse("setGlowingTag".equals(min.name),
                                        "Unexpected setGlowingTag call in " + cn.name + "#" + mn.name);
                                if ("com/corosus/zombieawareness/heat/HordeSpecialUnitHelper".equals(cn.name)
                                        && "applySpecialMobIndicator".equals(mn.name)
                                        && "shouldHordeScreamerGlow".equals(min.name)) {
                                    applySpecialIndicatorCallsGate = true;
                                }
                            }
                        }
                    }
                }
            }
        }

        assertEquals(
                Set.of("com/corosus/zombieawareness/heat/HordeSpecialUnitHelper#applySpecialMobIndicator"),
                glowCallSites,
                "Only HordeSpecialUnitHelper#applySpecialMobIndicator may apply MobEffects.GLOWING"
        );
        assertTrue(applySpecialIndicatorCallsGate,
                "HordeSpecialUnitHelper#applySpecialMobIndicator must gate glowing via shouldHordeScreamerGlow");
    }
}
