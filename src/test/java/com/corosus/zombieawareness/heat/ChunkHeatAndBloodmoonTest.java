package com.corosus.zombieawareness.heat;

import com.corosus.zombieawareness.ZAUtil;
import com.corosus.zombieawareness.bloodmoon.BloodmoonManager;
import com.corosus.zombieawareness.config.ZAConfigZDay;
import com.corosus.zombieawareness.zday.ZDayProgressionManager;
import net.minecraft.core.BlockPos;
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
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class ChunkHeatAndBloodmoonTest {

    @BeforeEach
    public void setUp() {
        ChunkHeatManager.clearAllHeat();
        BloodmoonManager.resetState();
    }

    @AfterEach
    public void tearDown() {
        ChunkHeatManager.clearAllHeat();
        BloodmoonManager.resetState();
    }

    @Test
    public void taczSoundClassificationDistinguishesGunfireFromReload() {
        assertTrue(ChunkHeatManager.isFirearmOrExplosionSound("tacz", "gun.shoot"), "tacz gunfire shoot sound should be classified as firearm sound");
        assertFalse(ChunkHeatManager.isFirearmOrExplosionSound("tacz", "gun.reload"), "tacz reload sound should not trigger gunfire heat");
        assertTrue(ChunkHeatManager.isQuietFirearmHandlingSound("tacz", "gun.reload"), "tacz reload should be recognized as quiet firearm handling");
        assertTrue(ChunkHeatManager.isQuietFirearmHandlingSound("tacz", "weapon.inspect"), "tacz inspect should be recognized as quiet firearm handling");

        // Additional firearm sound checks
        assertFalse(ChunkHeatManager.isFirearmOrExplosionSound("minecraft", "entity.generic.explode"), "generic explode should not trigger gunfire/explosion heat");
        assertFalse(ChunkHeatManager.isFirearmOrExplosionSound("minecraft", "entity.creeper.primed"), "creeper explosion should not trigger gunfire heat");
        assertFalse(ChunkHeatManager.isFirearmOrExplosionSound("minecraft", "entity.tnt.primed"), "tnt explosion should not trigger gunfire heat");
        assertTrue(ChunkHeatManager.isFirearmOrExplosionSound("tacz", "ak47_shoot"), "tacz ak47_shoot should be firearm sound");
        assertTrue(ChunkHeatManager.isFirearmOrExplosionSound("tacz", "grenade_explode"), "tacz grenade explosion should be recognized");
        assertFalse(ChunkHeatManager.isFirearmOrExplosionSound("minecraft", "entity.zombie.ambient"), "ambient zombie noise should not be firearm sound");
    }

    @Test
    public void taczSuppressedAndLoudGunfireHeatAccumulation() {
        ChunkHeatManager.clearAllHeat();
        ZDayProgressionManager.setForcedWorldDay(21); // Stage 2+ (Day 21+) required for heat accumulation

        try {
            // Verify isSuppressedTaCZSound classification
            assertTrue(ChunkHeatManager.isSuppressedTaCZSound("silence"), "'silence' must be recognized as suppressed");
            assertTrue(ChunkHeatManager.isSuppressedTaCZSound("silence_3p"), "'silence_3p' must be recognized as suppressed");
            assertTrue(ChunkHeatManager.isSuppressedTaCZSound("custom_silence_shot"), "contains 'silence' must be recognized as suppressed");
            assertFalse(ChunkHeatManager.isSuppressedTaCZSound("shoot"), "'shoot' must not be recognized as suppressed");
            assertFalse(ChunkHeatManager.isSuppressedTaCZSound("shoot_3p"), "'shoot_3p' must not be recognized as suppressed");
            assertFalse(ChunkHeatManager.isSuppressedTaCZSound(null), "null must not be recognized as suppressed");

            // Test onTaCZGunSound with "silence" -> adds +4 heat
            ChunkHeatManager.onTaCZGunSound(null, 100, 64, 100, 20, "silence");
            int heatAfter1Silenced = ChunkHeatManager.getHeatAtBlockPos(100, 100, 0L);
            assertEquals(4, heatAfter1Silenced, "Silenced shot must add +4 Chunk Heat");

            // Second silenced shot -> adds another +4 heat (total 8)
            ChunkHeatManager.onTaCZGunSound(null, 100, 64, 100, 20, "silence_3p");
            int heatAfter2Silenced = ChunkHeatManager.getHeatAtBlockPos(100, 100, 0L);
            assertEquals(8, heatAfter2Silenced, "Second silenced shot must bring heat to 8 (+4 heat)");

            // Test onTaCZGunSound with "shoot" -> adds +25 heat (total 33)
            ChunkHeatManager.onTaCZGunSound(null, 100, 64, 100, 64, "shoot");
            int heatAfterShoot = ChunkHeatManager.getHeatAtBlockPos(100, 100, 0L);
            assertEquals(33, heatAfterShoot, "Unsuppressed shot must add +25 Chunk Heat (8 + 25 = 33)");

            // Tactical handling sounds (reload, dry_fire, bolt) must add 0 heat
            ChunkHeatManager.onTaCZGunSound(null, 100, 64, 100, 14, "reload_empty");
            ChunkHeatManager.onTaCZGunSound(null, 100, 64, 100, 14, "dry_fire");
            ChunkHeatManager.onTaCZGunSound(null, 100, 64, 100, 14, "bolt");
            int heatAfterHandling = ChunkHeatManager.getHeatAtBlockPos(100, 100, 0L);
            assertEquals(33, heatAfterHandling, "Tactical handling noises must add 0 Chunk Heat");

            // Prove suppressors reduce heat by > 80%: (25 - 4) / 25 = 21/25 = 84% reduction
            double heatReduction = (25.0 - 4.0) / 25.0;
            assertTrue(heatReduction > 0.80, "Suppressors (+4 heat vs +25 heat) must reduce chunk heat by > 80% (got " + (heatReduction * 100) + "%)");

            // Verify suppressed shot caps at chunkHeatThreshold - 1 (99)
            ChunkHeatManager.addHeatAtBlockPos(200, 200, 98, 0L);
            ChunkHeatManager.onTaCZGunSound(null, 200, 64, 200, 20, "silence");
            int cappedHeat = ChunkHeatManager.getHeatAtBlockPos(200, 200, 0L);
            assertEquals(99, cappedHeat, "Suppressed shot must cap at chunkHeatThreshold - 1");
        } finally {
            ZDayProgressionManager.setForcedWorldDay(-1);
            ChunkHeatManager.clearAllHeat();
        }
    }

    @Test
    public void chunkHeatAccumulatesLocallyAndDecaysWithoutAffectingDistantChunks() {
        ChunkHeatManager.clearAllHeat();

        // 4 shots of 25 heat at tick 0 = 100 heat
        for (int i = 0; i < 4; i++) {
            ChunkHeatManager.addHeatAtBlockPos(100, 100, 25, 0L);
        }

        int centerHeat = ChunkHeatManager.getHeatAtBlockPos(100, 100, 0L);
        assertTrue(centerHeat >= 100, "Expected heat >= 100 at shot position after 4 shots, got: " + centerHeat);

        // Surrounding 3x3 chunk cluster should also receive heat
        int neighborHeat = ChunkHeatManager.getHeatAtBlockPos(100 + 16, 100, 0L);
        assertTrue(neighborHeat >= 100, "Expected neighbor chunk in 3x3 cluster to have heat >= 100, got: " + neighborHeat);

        // Distant chunk at (2000, 2000) must remain unaffected at 0 heat
        int distantHeat = ChunkHeatManager.getHeatAtBlockPos(2000, 2000, 0L);
        assertEquals(0, distantHeat, "Distant chunk at (2000, 2000) must remain at 0 heat");

        // Heat decays over time (5 heat per 20 ticks = 25 heat decay after 100 ticks)
        int decayedHeat5s = ChunkHeatManager.getHeatAtBlockPos(100, 100, 100L);
        assertEquals(75, decayedHeat5s, "Heat should decay to 75 after 100 ticks (5 seconds)");

        // After 400 ticks (20 seconds), heat should fully decay to 0
        int decayedHeat20s = ChunkHeatManager.getHeatAtBlockPos(100, 100, 400L);
        assertEquals(0, decayedHeat20s, "Heat should fully decay to 0 after 400 ticks");
    }

    @Test
    public void bloodmoonScheduleMatchesDay49AndEvery6DaysAtNight() {
        // Day schedule: starts at Day 49 (Stage 4), repeats every 6 days (Day 49, 55, 61, 67...)
        assertFalse(BloodmoonManager.isBloodmoonDay(1), "Day 1 must not be Bloodmoon");
        assertFalse(BloodmoonManager.isBloodmoonDay(48), "Day 48 must not be Bloodmoon");
        assertTrue(BloodmoonManager.isBloodmoonDay(49), "Day 49 must be Bloodmoon");
        assertFalse(BloodmoonManager.isBloodmoonDay(50), "Day 50 must not be Bloodmoon");
        assertFalse(BloodmoonManager.isBloodmoonDay(54), "Day 54 must not be Bloodmoon");
        assertTrue(BloodmoonManager.isBloodmoonDay(55), "Day 55 must be Bloodmoon");
        assertTrue(BloodmoonManager.isBloodmoonDay(61), "Day 61 must be Bloodmoon");
        assertTrue(BloodmoonManager.isBloodmoonDay(67), "Day 67 must be Bloodmoon");

        // Night time window: 12000 to 23500 ticks
        assertFalse(BloodmoonManager.isBloodmoonNightTime(0L), "Dawn tick 0 must not be night");
        assertFalse(BloodmoonManager.isBloodmoonNightTime(6000L), "Noon tick 6000 must not be night");
        assertFalse(BloodmoonManager.isBloodmoonNightTime(11999L), "Pre-dusk tick 11999 must not be night");
        assertTrue(BloodmoonManager.isBloodmoonNightTime(12000L), "Dusk tick 12000 must be night");
        assertTrue(BloodmoonManager.isBloodmoonNightTime(13000L), "Night tick 13000 must be night");
        assertTrue(BloodmoonManager.isBloodmoonNightTime(18000L), "Midnight tick 18000 must be night");
        assertTrue(BloodmoonManager.isBloodmoonNightTime(23500L), "Pre-dawn tick 23500 must be night");
        assertFalse(BloodmoonManager.isBloodmoonNightTime(23501L), "Dawn tick 23501 must not be Bloodmoon night");
        assertFalse(BloodmoonManager.isBloodmoonNightTime(23600L), "Dawn tick 23600 must not be Bloodmoon night");

        // Forced override testing
        BloodmoonManager.setForcedBloodmoon(true);
        assertTrue(BloodmoonManager.isBloodmoonActive(null), "Forced bloodmoon=true must return true even with null level");
        BloodmoonManager.setForcedBloodmoon(false);
        assertFalse(BloodmoonManager.isBloodmoonActive(null), "Forced bloodmoon=false must return false");
        BloodmoonManager.setForcedBloodmoon(null);
        assertFalse(BloodmoonManager.isBloodmoonActive(null), "Null level without forced override must return false");
    }

    @Test
    public void squadWaveScalingIncreasesWithPlayersAndBloodmoon() {
        int soloNormal = ChunkHeatManager.computeSquadScaledWaveSize(16, 10, 1, false);
        int squadNormal = ChunkHeatManager.computeSquadScaledWaveSize(16, 10, 4, false);

        assertTrue(squadNormal > soloNormal, "Wave count must scale up with squad size");

        int soloBloodmoon = ChunkHeatManager.computeSquadScaledWaveSize(16, 10, 1, true);
        assertEquals(soloNormal * 2, soloBloodmoon, "Bloodmoon wave size must double at Stage 10");

        int squadBloodmoon = ChunkHeatManager.computeSquadScaledWaveSize(16, 10, 4, true);
        assertEquals(squadNormal * 2, squadBloodmoon, "Bloodmoon wave size must double for squads at Stage 10");

        // Verify escalation across stages
        int stage1 = ChunkHeatManager.computeSquadScaledWaveSize(16, 1, 1, false);
        int stage10 = ChunkHeatManager.computeSquadScaledWaveSize(16, 10, 1, false);
        assertTrue(stage10 > stage1, "Stage 10 swarm wave size must exceed Stage 1");
    }

    @Test
    public void hordeDurationScalesWithNearbyPlayerCount() {
        assertEquals(6000, ZAConfigZDay.hordeBaseDurationTicks, "Default base main horde timer must be 6000 ticks (5 minutes)");
        assertEquals(6000, ChunkHeatManager.DEFAULT_HORDE_BASE_DURATION_TICKS, "DEFAULT_HORDE_BASE_DURATION_TICKS must be 6000 ticks");
        assertEquals(6000, ChunkHeatManager.computeHordeDurationTicks(1), "1 player should yield 6000 ticks (5 minutes)");
        assertEquals(7200, ChunkHeatManager.computeHordeDurationTicks(3), "3 players should yield 7200 ticks (5 min + 2x30s)");
        assertEquals(6000, ChunkHeatManager.computeHordeDurationTicks(0), "0 players fallback should yield 6000 ticks");

        int prevBase = ZAConfigZDay.hordeBaseDurationTicks;
        try {
            ZAConfigZDay.hordeBaseDurationTicks = 0;
            assertEquals(6000, ChunkHeatManager.computeHordeDurationTicks(1), "Non-positive config should fall back to 6000 ticks");
        } finally {
            ZAConfigZDay.hordeBaseDurationTicks = prevBase;
        }
    }

    @Test
    public void hordePhaseResolvesAndRespectsWorldStageCaps() {
        // Extended Phase 4 (0.45..0.72 = 27%) & Phase 5 (0.72..1.00 = 28%) must each exceed Phases 1A (10%), 1B (10%), 2 (12%), 3 (13%)
        double p1aShare = ChunkHeatManager.PHASE_1A_END;
        double p1bShare = ChunkHeatManager.PHASE_1B_END - ChunkHeatManager.PHASE_1A_END;
        double p2Share = ChunkHeatManager.PHASE_2_END - ChunkHeatManager.PHASE_1B_END;
        double p3Share = ChunkHeatManager.PHASE_3_END - ChunkHeatManager.PHASE_2_END;
        double p4Share = ChunkHeatManager.PHASE_4_END - ChunkHeatManager.PHASE_3_END;
        double p5Share = 1.0 - ChunkHeatManager.PHASE_4_END;
        assertTrue(p4Share > p1aShare && p4Share > p1bShare && p4Share > p2Share && p4Share > p3Share,
                "Phase 4 share must exceed every earlier phase share");
        assertTrue(p5Share > p1aShare && p5Share > p1bShare && p5Share > p2Share && p5Share > p3Share,
                "Phase 5 share must exceed every earlier phase share");

        // Progress 0.05 -> Phase 1A (uncapped across all stages)
        assertEquals(ChunkHeatManager.HordePhase.PHASE_1A_FAST_ZOMBIES, ChunkHeatManager.resolveHordePhase(0.05, 2));
        assertEquals(ChunkHeatManager.HordePhase.PHASE_1A_FAST_ZOMBIES, ChunkHeatManager.resolveHordePhase(0.05, 6));
        assertEquals(ChunkHeatManager.HordePhase.PHASE_1A_FAST_ZOMBIES, ChunkHeatManager.resolveHordePhase(0.05, 9));
        assertEquals(ChunkHeatManager.HordePhase.PHASE_1A_FAST_ZOMBIES, ChunkHeatManager.resolveHordePhase(0.05, 10));

        // Progress 0.15 -> Phase 1B (uncapped across all stages)
        assertEquals(ChunkHeatManager.HordePhase.PHASE_1B_VENOM_SPIDERS, ChunkHeatManager.resolveHordePhase(0.15, 2));
        assertEquals(ChunkHeatManager.HordePhase.PHASE_1B_VENOM_SPIDERS, ChunkHeatManager.resolveHordePhase(0.15, 6));
        assertEquals(ChunkHeatManager.HordePhase.PHASE_1B_VENOM_SPIDERS, ChunkHeatManager.resolveHordePhase(0.15, 9));
        assertEquals(ChunkHeatManager.HordePhase.PHASE_1B_VENOM_SPIDERS, ChunkHeatManager.resolveHordePhase(0.15, 10));

        // Progress 0.25 -> Raw Phase 2 (Breachers)
        assertEquals(ChunkHeatManager.HordePhase.PHASE_1B_VENOM_SPIDERS, ChunkHeatManager.resolveHordePhase(0.25, 2), "Stage 2 must cap at Phase 1B");
        assertEquals(ChunkHeatManager.HordePhase.PHASE_2_BREACHERS_SKELETONS, ChunkHeatManager.resolveHordePhase(0.25, 6));
        assertEquals(ChunkHeatManager.HordePhase.PHASE_2_BREACHERS_SKELETONS, ChunkHeatManager.resolveHordePhase(0.25, 9));
        assertEquals(ChunkHeatManager.HordePhase.PHASE_2_BREACHERS_SKELETONS, ChunkHeatManager.resolveHordePhase(0.25, 10));

        // Progress 0.38 -> Raw Phase 3 (Juggernauts)
        assertEquals(ChunkHeatManager.HordePhase.PHASE_1B_VENOM_SPIDERS, ChunkHeatManager.resolveHordePhase(0.38, 2), "Stage 2 must cap at Phase 1B");
        assertEquals(ChunkHeatManager.HordePhase.PHASE_2_BREACHERS_SKELETONS, ChunkHeatManager.resolveHordePhase(0.38, 6), "Stage 6 must cap at Phase 2");
        assertEquals(ChunkHeatManager.HordePhase.PHASE_3_JUGGERNAUTS_WITCHES, ChunkHeatManager.resolveHordePhase(0.38, 9));
        assertEquals(ChunkHeatManager.HordePhase.PHASE_3_JUGGERNAUTS_WITCHES, ChunkHeatManager.resolveHordePhase(0.38, 10));

        // Progress 0.58 -> Raw Phase 4 (Extended Climax Sappers)
        assertEquals(ChunkHeatManager.HordePhase.PHASE_1B_VENOM_SPIDERS, ChunkHeatManager.resolveHordePhase(0.58, 2), "Stage 2 must cap at Phase 1B");
        assertEquals(ChunkHeatManager.HordePhase.PHASE_2_BREACHERS_SKELETONS, ChunkHeatManager.resolveHordePhase(0.58, 6), "Stage 6 must cap at Phase 2");
        assertEquals(ChunkHeatManager.HordePhase.PHASE_3_JUGGERNAUTS_WITCHES, ChunkHeatManager.resolveHordePhase(0.58, 9), "Stage 9 must cap at Phase 3");
        assertEquals(ChunkHeatManager.HordePhase.PHASE_4_CLIMAX_SAPPERS, ChunkHeatManager.resolveHordePhase(0.58, 10), "Stage 10 allows Climax Sappers");

        // Progress 0.80 -> Raw Phase 5 (Extended Apex Corrosion)
        assertEquals(ChunkHeatManager.HordePhase.PHASE_1B_VENOM_SPIDERS, ChunkHeatManager.resolveHordePhase(0.80, 2), "Stage 2 must cap at Phase 1B");
        assertEquals(ChunkHeatManager.HordePhase.PHASE_2_BREACHERS_SKELETONS, ChunkHeatManager.resolveHordePhase(0.80, 6), "Stage 6 must cap at Phase 2");
        assertEquals(ChunkHeatManager.HordePhase.PHASE_3_JUGGERNAUTS_WITCHES, ChunkHeatManager.resolveHordePhase(0.80, 9), "Stage 9 must cap at Phase 3");
        assertEquals(ChunkHeatManager.HordePhase.PHASE_5_APEX_CORROSION, ChunkHeatManager.resolveHordePhase(0.80, 10), "Stage 10 progress 0.80 resolves to Phase 5 Apex Corrosion");
    }

    @Test
    public void oneHordePerGroupLimitAndMultiGroupScaling() {
        ChunkHeatManager.clearAllHeat();
        UUID uuidA = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID uuidB = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID uuidC = UUID.fromString("00000000-0000-0000-0000-000000000003");

        BlockPos group1Pos = new BlockPos(0, 64, 0);

        // 1. 1 Group (Solo 1 Player or Squad) = Max 1 Horde
        // Group 1 (solo player UUID_A at (0, 64, 0)) triggers a horde -> succeeds
        assertTrue(ChunkHeatManager.canTriggerHordeForGroup(group1Pos, List.of(uuidA)),
                "Initial horde for Group 1 (UUID_A) should be allowed");
        boolean registeredG1 = ChunkHeatManager.tryRegisterHordeForGroup(group1Pos, List.of(uuidA), 1, 6, 1);
        assertTrue(registeredG1, "Registration of Group 1 horde should succeed");
        assertEquals(1, ChunkHeatManager.getActiveHordeCount(), "Active horde count should be 1 after Group 1 triggers");

        // Verify ActiveHorde state
        List<ChunkHeatManager.ActiveHorde> hordes = ChunkHeatManager.getActiveHordes();
        assertEquals(1, hordes.size());
        ChunkHeatManager.ActiveHorde horde1 = hordes.get(0);
        assertEquals(group1Pos, horde1.center);
        assertTrue(horde1.targetGroupPlayerIds.contains(uuidA));
        assertEquals(6000, horde1.totalDurationTicks);

        // Group 1 keeps shooting at (0, 64, 0) -> blocked
        assertFalse(ChunkHeatManager.canTriggerHordeForGroup(group1Pos, List.of(uuidA)),
                "Group 1 shooting again at same pos while horde is active should be blocked");
        assertFalse(ChunkHeatManager.tryRegisterHordeForGroup(group1Pos, List.of(uuidA), 1, 6, 1),
                "tryRegisterHordeForGroup for Group 1 at same pos should fail");
        assertEquals(1, ChunkHeatManager.getActiveHordeCount(), "Active horde count should remain 1");

        // Group 1 kites 150 blocks away to (150, 64, 0) while horde is still active -> blocked by UUID match!
        BlockPos group1KitedPos = new BlockPos(150, 64, 0);
        assertFalse(ChunkHeatManager.canTriggerHordeForGroup(group1KitedPos, List.of(uuidA)),
                "Group 1 kited 150 blocks away should still be blocked by player UUID tracking");
        assertFalse(ChunkHeatManager.tryRegisterHordeForGroup(group1KitedPos, List.of(uuidA), 1, 6, 1),
                "tryRegisterHordeForGroup for kited Group 1 should fail");
        assertEquals(1, ChunkHeatManager.getActiveHordeCount(), "Active horde count should remain 1");

        // Another player UUID_B standing inside Group 1's 96-block cluster at (40, 64, 0) tries to trigger a horde -> blocked by area cluster
        BlockPos nearPos = new BlockPos(40, 64, 0);
        assertFalse(ChunkHeatManager.canTriggerHordeForGroup(nearPos, List.of(uuidB)),
                "New player UUID_B within 96-block radius of active horde should be blocked");
        assertFalse(ChunkHeatManager.tryRegisterHordeForGroup(nearPos, List.of(uuidB), 1, 6, 1),
                "tryRegisterHordeForGroup for near player should fail");
        assertEquals(1, ChunkHeatManager.getActiveHordeCount(), "Active horde count should remain 1");

        // 2. 2 Groups (Even if each group is just 1 player) = 2 Hordes
        // Group 2 (solo player UUID_C at (300, 64, 300), >96 blocks away) triggers a horde -> succeeds!
        BlockPos group2Pos = new BlockPos(300, 64, 300);
        assertTrue(ChunkHeatManager.canTriggerHordeForGroup(group2Pos, List.of(uuidC)),
                "Group 2 (>96 blocks away with different UUID) should be allowed to trigger its own horde");
        boolean registeredG2 = ChunkHeatManager.tryRegisterHordeForGroup(group2Pos, List.of(uuidC), 1, 6, 1);
        assertTrue(registeredG2, "Registration of Group 2 horde should succeed");
        assertEquals(2, ChunkHeatManager.getActiveHordeCount(), "Active horde count should now be 2 (1 horde per group scaling)");

        // Group 2 tries to trigger another horde while their horde is active -> blocked
        assertFalse(ChunkHeatManager.canTriggerHordeForGroup(group2Pos, List.of(uuidC)),
                "Group 2 trying to trigger overlapping horde while active must be blocked");
        assertFalse(ChunkHeatManager.tryRegisterHordeForGroup(group2Pos, List.of(uuidC), 1, 6, 1),
                "tryRegisterHordeForGroup for Group 2 must fail while their horde is active");
        assertEquals(2, ChunkHeatManager.getActiveHordeCount(), "Active horde count should remain 2");

        // 3. Horde Expiration Frees the Group
        // When Group 1's horde expires (elapsedTicks >= totalDurationTicks), Group 1 can trigger a new horde again
        horde1.elapsedTicks = horde1.totalDurationTicks;

        // Group 1 is now free to trigger a new horde
        assertTrue(ChunkHeatManager.canTriggerHordeForGroup(group1Pos, List.of(uuidA)),
                "Group 1 should be allowed to trigger a horde after previous horde expires");
        boolean registeredG1Renewed = ChunkHeatManager.tryRegisterHordeForGroup(group1Pos, List.of(uuidA), 1, 6, 1);
        assertTrue(registeredG1Renewed, "Group 1 new horde registration should succeed");
        assertEquals(2, ChunkHeatManager.getActiveHordeCount(),
                "Active horde count should be 2 (Group 2 active + Group 1 renewed)");
    }

    @Test
    public void squadGroupAndCenterTrackingVerification() {
        ChunkHeatManager.clearAllHeat();
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        UUID p3 = UUID.randomUUID();
        Set<UUID> squad = Set.of(p1, p2);

        BlockPos center = new BlockPos(100, 70, 100);
        assertTrue(ChunkHeatManager.tryRegisterHordeForGroup(center, squad, 2, 8, 3));
        assertEquals(1, ChunkHeatManager.getActiveHordeCount());

        ChunkHeatManager.ActiveHorde horde = ChunkHeatManager.getActiveHordes().get(0);
        assertEquals(center, horde.center);
        assertEquals(6600, horde.totalDurationTicks, "2 nearby players = 6000 + 1 * 600 = 6600 ticks");

        // Individual squad member p2 cannot trigger a second horde even at distant position
        assertFalse(ChunkHeatManager.canTriggerHordeForGroup(new BlockPos(500, 70, 500), List.of(p2)));

        // Center is mutable for dynamic group tracking
        BlockPos updatedPos = new BlockPos(120, 70, 150);
        horde.center = updatedPos;
        assertEquals(updatedPos, horde.center);

        // Position near new center is blocked
        assertFalse(ChunkHeatManager.canTriggerHordeForGroup(new BlockPos(130, 70, 160), List.of(p3)));
        // Position > 96 blocks away from updated center is allowed
        assertTrue(ChunkHeatManager.canTriggerHordeForGroup(new BlockPos(300, 70, 150), List.of(p3)));
    }

    @Test
    public void chunkHeatCapAt150AndResetHeatAround() {
        ChunkHeatManager.clearAllHeat();
        // 10 shots of 25 heat = 250 uncapped, should cap at 150
        for (int i = 0; i < 10; i++) {
            ChunkHeatManager.addHeatAtBlockPos(100, 100, 25, 0L);
        }
        int heat = ChunkHeatManager.getHeatAtBlockPos(100, 100, 0L);
        assertEquals(ChunkHeatManager.MAX_CHUNK_HEAT, heat, "Chunk heat must be clamped at MAX_CHUNK_HEAT (150)");

        int neighborHeat = ChunkHeatManager.getHeatAtBlockPos(100 + 16, 100, 0L);
        assertEquals(ChunkHeatManager.MAX_CHUNK_HEAT, neighborHeat, "Neighbor chunk heat must also be clamped at MAX_CHUNK_HEAT (150)");

        // Reset heat around
        ChunkHeatManager.resetHeatAround(100, 100, 50L);
        assertEquals(0, ChunkHeatManager.getHeatAtBlockPos(100, 100, 50L), "Center chunk heat must reset to 0");
        assertEquals(0, ChunkHeatManager.getHeatAtBlockPos(100 + 16, 100, 50L), "Neighbor chunk heat must reset to 0");
    }

    @Test
    public void postHordeBreatherCooldownBlocksRetriggerAndExpires() {
        ChunkHeatManager.clearAllHeat();
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();
        List<UUID> group = List.of(player1, player2);

        assertFalse(ChunkHeatManager.isGroupInPostHordeBreather(group, 0L), "Initial group should not be in breather");

        // Horde finishes at tick 3600
        ChunkHeatManager.markGroupPostHordeBreather(group, 3600L);

        // At tick 3601 (1 tick later), group must be in breather
        assertTrue(ChunkHeatManager.isGroupInPostHordeBreather(group, 3601L), "Group must be in breather at tick 3601");
        assertTrue(ChunkHeatManager.isGroupInPostHordeBreather(List.of(player1), 3601L), "Player 1 must be in breather");
        assertTrue(ChunkHeatManager.isGroupInPostHordeBreather(List.of(player2), 3601L), "Player 2 must be in breather");

        // Breather is 4200 ticks (POST_HORDE_BREATHER_TICKS = 4200L = 3.5 minutes), so at tick 3600 + 4199 = 7799, still in breather
        assertTrue(ChunkHeatManager.isGroupInPostHordeBreather(group, 7799L), "Group must still be in breather at tick 7799");

        // At tick 3600 + 4200 = 7800, breather has expired
        assertFalse(ChunkHeatManager.isGroupInPostHordeBreather(group, 7800L), "Breather must expire at tick 7800");
        assertFalse(ChunkHeatManager.isGroupInPostHordeBreather(group, 7801L), "Breather must be expired at tick 7801");

        // clearAllHeat also clears breather
        ChunkHeatManager.markGroupPostHordeBreather(group, 6000L);
        assertTrue(ChunkHeatManager.isGroupInPostHordeBreather(group, 6001L));
        ChunkHeatManager.clearAllHeat();
        assertFalse(ChunkHeatManager.isGroupInPostHordeBreather(group, 6001L), "clearAllHeat must reset player breather cooldowns");
    }

    @Test
    public void activeHordeClimaxRoarAndNoTargetTracking() {
        ChunkHeatManager.clearAllHeat();
        UUID p1 = UUID.randomUUID();
        BlockPos center = new BlockPos(0, 64, 0);
        ChunkHeatManager.ActiveHorde horde = new ChunkHeatManager.ActiveHorde(center, Set.of(p1), 6000, 6, 10);

        assertEquals(0, horde.noTargetTicks, "noTargetTicks should initialize to 0");
        assertFalse(horde.climaxRoarPlayed, "climaxRoarPlayed should initialize to false");

        // Test Phase 4 Climax resolution at 0.60 progress
        assertEquals(ChunkHeatManager.HordePhase.PHASE_4_CLIMAX_SAPPERS, ChunkHeatManager.resolveHordePhase(0.60, 10));

        // Simulate 100 ticks without target
        horde.noTargetTicks = 100;
        assertTrue(horde.noTargetTicks >= 100, "100 ticks without target exceeds the auto-cancellation threshold");
    }

    @Test
    public void directionalCappedAttenuationRoarVolumeMultiplier() {
        // Unobstructed tests: max 10% dropoff over 128m
        float vol0m = ChunkHeatManager.computeRoarVolumeMultiplier(0.0, false);
        assertEquals(1.0f, vol0m, 0.0001f, "0m unobstructed volume must be 1.0 (no dropoff)");

        float vol64m = ChunkHeatManager.computeRoarVolumeMultiplier(64.0, false);
        assertEquals(0.95f, vol64m, 0.0001f, "64m unobstructed volume must be 0.95 (5% dropoff)");

        float vol128m = ChunkHeatManager.computeRoarVolumeMultiplier(128.0, false);
        assertEquals(0.90f, vol128m, 0.0001f, "128m unobstructed volume must be 0.90 (capped at 10% dropoff)");

        float vol200m = ChunkHeatManager.computeRoarVolumeMultiplier(200.0, false);
        assertEquals(0.90f, vol200m, 0.0001f, ">128m distance must still clamp to max 10% dropoff (0.90)");

        // Obstructed tests: max 20% wall dropoff (0.80 multiplier)
        float vol0mObstructed = ChunkHeatManager.computeRoarVolumeMultiplier(0.0, true);
        assertEquals(0.80f, vol0mObstructed, 0.0001f, "0m obstructed volume must be 0.80 (20% wall obstruction penalty)");

        float vol128mObstructed = ChunkHeatManager.computeRoarVolumeMultiplier(128.0, true);
        assertEquals(0.72f, vol128mObstructed, 0.0001f, "128m obstructed volume must be 0.72 (0.90 * 0.80)");
    }

    @Test
    public void computeDirectionalSoundPositionPlacesEmitterAtFourBlocks() {
        // Player at (0, 64, 0), roar origin 100 blocks away in +X direction (100, 64, 0)
        double[] posEast = ChunkHeatManager.computeDirectionalSoundPosition(0.0, 64.0, 0.0, 100.0, 64.0, 0.0);
        assertEquals(4.0, posEast[0], 0.0001, "Emitter X should be placed 4.0m along unit vector towards roar");
        assertEquals(64.0, posEast[1], 0.0001, "Emitter Y should match player Y");
        assertEquals(0.0, posEast[2], 0.0001, "Emitter Z should match player Z");

        // Player at (0, 0, 0), roar origin in diagonal (+30, 0, +40) with distance 50m
        double[] posDiag = ChunkHeatManager.computeDirectionalSoundPosition(0.0, 0.0, 0.0, 30.0, 0.0, 40.0);
        assertEquals(4.0 * (30.0 / 50.0), posDiag[0], 0.0001, "Emitter X should be 2.4m");
        assertEquals(0.0, posDiag[1], 0.0001, "Emitter Y should be 0.0m");
        assertEquals(4.0 * (40.0 / 50.0), posDiag[2], 0.0001, "Emitter Z should be 3.2m");

        // Coincident position (dist < 0.001) fallback to player pos
        double[] posCoincident = ChunkHeatManager.computeDirectionalSoundPosition(10.0, 20.0, 30.0, 10.0, 20.0, 30.0);
        assertEquals(10.0, posCoincident[0], 0.0001);
        assertEquals(20.0, posCoincident[1], 0.0001);
        assertEquals(30.0, posCoincident[2], 0.0001);
    }

    @Test
    public void computeDirectionalSpawnOffsetSpansConeAndDistance() {
        // Approach angle 0 (East along +X axis)
        double[] centerOffset = ChunkHeatManager.computeDirectionalSpawnOffset(0.0, 0.0, 0.0);
        assertEquals(38.0, centerOffset[0], 0.0001, "Minimum distance offset should be 38.0m");
        assertEquals(0.0, centerOffset[1], 0.0001, "Angle offset 0 should have Z=0");

        double[] maxDistOffset = ChunkHeatManager.computeDirectionalSpawnOffset(0.0, 0.0, 1.0);
        assertEquals(52.0, maxDistOffset[0], 0.0001, "Maximum distance offset should be 52.0m");
        assertEquals(0.0, maxDistOffset[1], 0.0001);

        // Clamping check for normalizedAngle and normalizedDist
        double[] clampedOffset = ChunkHeatManager.computeDirectionalSpawnOffset(0.0, 5.0, 5.0);
        double expectedAngle = Math.toRadians(40.0);
        assertEquals(Math.cos(expectedAngle) * 52.0, clampedOffset[0], 0.0001, "Clamped to +40 degrees and 52m");
        assertEquals(Math.sin(expectedAngle) * 52.0, clampedOffset[1], 0.0001);

        // Staging origin on ActiveHorde
        ChunkHeatManager.ActiveHorde horde = new ChunkHeatManager.ActiveHorde(new BlockPos(100, 70, 100), Collections.emptySet(), 3600, 6, 10, 0.0);
        BlockPos staging = horde.getStagingOrigin();
        assertEquals(145, staging.getX(), "Staging origin should be center X + 45 along approach angle 0");
        assertEquals(70, staging.getY());
        assertEquals(100, staging.getZ());
    }

    @Test
    public void isPlayerLockedByHordeOrBloodmoonReflectsThreatState() {
        ChunkHeatManager.clearAllHeat();
        UUID player = UUID.randomUUID();
        BlockPos pos = new BlockPos(500, 64, 500);

        // 1. In peace time with no active bloodmoon or horde -> not locked
        assertFalse(ChunkHeatManager.isPlayerLockedByHordeOrBloodmoon(player, pos, false),
                "Player should not be locked when no horde or bloodmoon is active");

        // 2. Active bloodmoon -> unconditionally locked
        assertTrue(ChunkHeatManager.isPlayerLockedByHordeOrBloodmoon(player, pos, true),
                "Player must be locked when bloodmoon is active");

        // 3. Active horde for this player group -> locked
        assertTrue(ChunkHeatManager.tryRegisterHordeForGroup(pos, List.of(player), 1, 6, 5));
        assertTrue(ChunkHeatManager.isPlayerLockedByHordeOrBloodmoon(player, pos, false),
                "Player must be locked while fighting an active horde");

        // Distant player at (5000, 64, 5000) is NOT locked by player's horde
        UUID otherPlayer = UUID.randomUUID();
        BlockPos distantPos = new BlockPos(5000, 64, 5000);
        assertFalse(ChunkHeatManager.isPlayerLockedByHordeOrBloodmoon(otherPlayer, distantPos, false),
                "Distant uninvolved player should not be locked");
    }

    @Test
    public void deathGroupForgivenessFullWipeAndPartialSquadDownscale() {
        ChunkHeatManager.clearAllHeat();
        UUID uuidSolo = UUID.randomUUID();
        BlockPos center = new BlockPos(0, 64, 0);

        // 1. Solo Player Death (Full Wipe)
        assertTrue(ChunkHeatManager.tryRegisterHordeForGroup(center, Collections.singleton(uuidSolo), 1, 8, 4),
                "Should register horde for solo player");
        assertEquals(1, ChunkHeatManager.getActiveHordeCount(), "Active horde count should be 1");

        boolean soloWipeResult = ChunkHeatManager.onPlayerDeath(null, uuidSolo, center, 1000L);
        assertTrue(soloWipeResult, "Solo player death must trigger full group wipe forgiveness");
        assertEquals(0, ChunkHeatManager.getActiveHordeCount(), "Horde count must be 0 after full wipe");
        assertTrue(ChunkHeatManager.isGroupInPostHordeBreather(Collections.singleton(uuidSolo), 1000L + 3500L),
                "Solo player must have 3-minute / 3600-tick corpse-run grace period");
        assertFalse(ChunkHeatManager.isGroupInPostHordeBreather(Collections.singleton(uuidSolo), 1000L + 3601L),
                "Corpse-run grace period should expire after 3600 ticks");

        // 2. Multi-Player Squad Partial Death -> Then Full Wipe
        UUID uuidP1 = UUID.randomUUID();
        UUID uuidP2 = UUID.randomUUID();
        assertTrue(ChunkHeatManager.tryRegisterHordeForGroup(center, List.of(uuidP1, uuidP2), 2, 10, 4),
                "Should register horde for 2-player squad");
        assertEquals(1, ChunkHeatManager.getActiveHordeCount(), "Active horde count should be 1");

        ChunkHeatManager.ActiveHorde squadHorde = ChunkHeatManager.getActiveHordes().get(0);
        assertEquals(10, squadHorde.subWaveCount, "Initial subWaveCount should be 10");

        // UUID_P1 dies at tick 2000
        boolean p1DeathResult = ChunkHeatManager.onPlayerDeath(null, uuidP1, center, 2000L);
        assertFalse(p1DeathResult, "Partial squad death must return false (horde continues for survivors)");
        assertEquals(1, ChunkHeatManager.getActiveHordeCount(), "Horde must remain active for surviving squadmate");
        assertFalse(squadHorde.targetGroupPlayerIds.contains(uuidP1), "Dead player UUID_P1 must be removed from targetGroupPlayerIds");
        assertTrue(squadHorde.targetGroupPlayerIds.contains(uuidP2), "Surviving player UUID_P2 must remain targeted");
        assertTrue(ChunkHeatManager.isGroupInPostHordeBreather(Collections.singleton(uuidP1), 5000L),
                "UUID_P1 must have corpse-run grace period active at tick 5000");
        assertTrue(squadHorde.subWaveCount < 10, "subWaveCount must be scaled down after squadmate death");
        assertEquals(5, squadHorde.subWaveCount, "subWaveCount should scale down to 5 (ceil(10 * 1/2))");

        // UUID_P2 dies at tick 2100 (Full Group Wipe)
        boolean p2DeathResult = ChunkHeatManager.onPlayerDeath(null, uuidP2, center, 2100L);
        assertTrue(p2DeathResult, "Final squadmate death must trigger full group wipe forgiveness");
        assertEquals(0, ChunkHeatManager.getActiveHordeCount(), "Horde count must be 0 after full squad wipe");
        assertTrue(ChunkHeatManager.isGroupInPostHordeBreather(Collections.singleton(uuidP1), 5000L),
                "UUID_P1 must still have corpse-run grace period");
        assertTrue(ChunkHeatManager.isGroupInPostHordeBreather(Collections.singleton(uuidP2), 5000L),
                "UUID_P2 must have corpse-run grace period");
    }

    @Test
    public void apexCorrosiveMechanicsAndArmorDurabilityShred() throws Exception {
        // 1. Apex Corrosive must NOT deal true damage or armor-bypass damage
        for (Field f : ZAUtil.class.getDeclaredFields()) {
            assertFalse(f.getName().contains("BYPASS") && f.getName().contains("APEX"),
                    "Apex armor bypass ratio constant must not exist: " + f.getName());
        }
        for (Method m : ZAUtil.class.getDeclaredMethods()) {
            assertFalse(m.getName().contains("ApexArmorBypass") || m.getName().contains("ApexTrueDamage"),
                    "Apex true/bypass damage helper must not exist: " + m.getName());
        }

        ClassLoader cl = ChunkHeatAndBloodmoonTest.class.getClassLoader();
        try (InputStream is = cl.getResourceAsStream("com/corosus/zombieawareness/mixin/MixinLivingEntity.class")) {
            assertNotNull(is, "MixinLivingEntity.class must be present on classpath");
            ClassNode mixinNode = new ClassNode();
            new ClassReader(is).accept(mixinNode, 0);
            assertTrue(mixinNode.fields.stream().noneMatch(f -> "za$processingApexBypass".equals(f.name)),
                    "MixinLivingEntity must not retain za$processingApexBypass state");
            for (MethodNode mn : mixinNode.methods) {
                for (AbstractInsnNode insn : mn.instructions) {
                    if (insn instanceof MethodInsnNode min) {
                        assertFalse("splitApexArmorBypassDamage".equals(min.name),
                                "MixinLivingEntity must not invoke splitApexArmorBypassDamage");
                        if ("za$blockHostileFriendlyFire".equals(mn.name)) {
                            assertFalse("setHealth".equals(min.name) || "setAbsorptionAmount".equals(min.name) || "hurtServer".equals(min.name),
                                    "za$blockHostileFriendlyFire must not directly reduce health/absorption or re-dispatch hurtServer: " + min.name);
                        }
                    } else if (insn instanceof FieldInsnNode fin) {
                        assertFalse("APEX_ARMOR_BYPASS_RATIO".equals(fin.name),
                                "MixinLivingEntity must not reference APEX_ARMOR_BYPASS_RATIO");
                    }
                }
            }
        }

        // 2. Necrotic Trauma: Reduced Regeneration Efficiency (50%) & 160-Tick Duration
        UUID playerUuid = UUID.randomUUID();
        ZAUtil.clearNecroticTrauma();
        assertFalse(ZAUtil.hasNecroticTrauma(playerUuid, 100L));
        assertEquals(10.0f, ZAUtil.computeModifiedHealAmount(playerUuid, 100L, 10.0f), 1e-4f, "Heal amount unaffected when no trauma");

        ZAUtil.applyNecroticTrauma(playerUuid, 100L);
        assertTrue(ZAUtil.hasNecroticTrauma(playerUuid, 100L), "Trauma must be active immediately");
        assertTrue(ZAUtil.hasNecroticTrauma(playerUuid, 259L), "Trauma must remain active within 160 ticks");
        assertEquals(5.0f, ZAUtil.computeModifiedHealAmount(playerUuid, 150L, 10.0f), 1e-4f, "Heal amount must be halved (50% efficiency) under trauma");

        assertFalse(ZAUtil.hasNecroticTrauma(playerUuid, 260L + 1L), "Trauma must expire after 160 ticks");
        assertEquals(10.0f, ZAUtil.computeModifiedHealAmount(playerUuid, 261L, 10.0f), 1e-4f, "Heal amount restored to 10.0f after trauma expires");

        // Edge cases
        assertEquals(-5.0f, ZAUtil.computeModifiedHealAmount(playerUuid, 100L, -5.0f), 1e-4f, "Non-positive heal amounts unaffected");
        assertEquals(10.0f, ZAUtil.computeModifiedHealAmount(null, 100L, 10.0f), 1e-4f, "Null player UUID handled safely");

        // 3. Direct Armor Durability Shredding (preserved non-true-damage behavior)
        assertEquals(20, ZAUtil.computeArmorDurabilityShred(400), "5% of 400 is 20 (>= 12 min)");
        assertEquals(12, ZAUtil.computeArmorDurabilityShred(100), "5% of 100 is 5, must clamp to minimum 12");
        assertEquals(12, ZAUtil.computeArmorDurabilityShred(0), "0 max durability must clamp to minimum 12");
        assertEquals(0, ZAUtil.shredPlayerArmorDurability(null, null), "Null player must safely shred 0 durability");
    }

    @Test
    public void ambientNightHordeAndFortifiedBaseSurroundSiege() {
        // 1. Spontaneous Late-Game Night Horde Awakening
        // canAwakenLateGameAmbientHorde(worldStageNum, dayTime, isBloodmoon, canTriggerForGroup, inBreather)
        assertFalse(ChunkHeatManager.canAwakenLateGameAmbientHorde(6, 15000L, false, true, false), "Stage 6 normal night must NOT awaken spontaneous ambient horde");
        assertTrue(ChunkHeatManager.canAwakenLateGameAmbientHorde(6, 15000L, true, true, false), "Bloodmoon night allows ambient horde even at Stage 6");
        assertTrue(ChunkHeatManager.canAwakenLateGameAmbientHorde(7, 15000L, false, true, false), "Stage 7 night window (13000..22500) must allow ambient horde");
        assertTrue(ChunkHeatManager.canAwakenLateGameAmbientHorde(10, 18000L, false, true, false), "Stage 10 midnight must allow ambient horde");

        // Daytime (e.g. 6000L = noon) must never awaken ambient horde
        assertFalse(ChunkHeatManager.canAwakenLateGameAmbientHorde(10, 6000L, false, true, false), "Daytime (6000L) must not awaken ambient horde");
        assertFalse(ChunkHeatManager.canAwakenLateGameAmbientHorde(10, 15000L, false, false, false), "Cannot awaken if group cannot trigger horde");
        assertFalse(ChunkHeatManager.canAwakenLateGameAmbientHorde(10, 15000L, false, true, true), "Cannot awaken if group is in post-horde breather");

        // 2. Fortified Wall / Base Detection
        assertTrue(ChunkHeatManager.isFortifiedFromWallCount(5, false), "5 or more blocked directions out of 8 is fortified even without roof/base");
        assertFalse(ChunkHeatManager.isFortifiedFromWallCount(4, false), "4 blocked directions without roof/base is NOT fortified");
        assertTrue(ChunkHeatManager.isFortifiedFromWallCount(3, true), "3 blocked directions WITH roof or base block is fortified");
        assertFalse(ChunkHeatManager.isFortifiedFromWallCount(2, true), "2 blocked directions with roof/base is NOT fortified");

        // 3. Fortified Wave Count Scaling (+40% more mobs)
        assertEquals(10, ChunkHeatManager.computeFortifiedWaveCount(10, false), "Unfortified wave count remains 10");
        assertEquals(14, ChunkHeatManager.computeFortifiedWaveCount(10, true), "Fortified wave count scales up to 14 (+40%)");
        assertEquals(28, ChunkHeatManager.computeFortifiedWaveCount(20, true), "Fortified wave count scales 20 -> 28 (+40%)");

        // 4. Directional Cone vs 360° Surround Spawn Offsets
        // Directional cone (surroundMode = false): angle offset is normalizedAngle * 40°
        double[] coneOffsetCenter = ChunkHeatManager.computeSpawnOffset(0.0, 0.0, 0.0, false);
        assertEquals(38.0, coneOffsetCenter[0], 0.01, "Angle 0 distance 38m -> x=38");
        assertEquals(0.0, coneOffsetCenter[1], 0.01, "Angle 0 -> y=0");

        double[] coneOffsetMaxAngle = ChunkHeatManager.computeSpawnOffset(0.0, 1.0, 0.0, false);
        double angleCone = Math.atan2(coneOffsetMaxAngle[1], coneOffsetMaxAngle[0]);
        assertEquals(Math.toRadians(40.0), angleCone, 1e-4, "Directional cone max angle must be 40 degrees");

        // Surround mode (surroundMode = true): angle offset is normalizedAngle * PI (full 180° = 360° surround)
        double[] surroundOpposite = ChunkHeatManager.computeSpawnOffset(0.0, 1.0, 0.0, true);
        assertEquals(-38.0, surroundOpposite[0], 0.01, "Surround mode max angle (180°) must place mobs behind player (x = -38)");
        assertEquals(0.0, surroundOpposite[1], 0.01);
    }

    @Test
    public void monsterDistressScentPhantomHordeSpawningAndLootReduction() {
        // 1. Monster Distress Scent Gate & Strength Scaling
        for (int stage = 0; stage <= 4; stage++) {
            assertFalse(ZAUtil.canSpawnMonsterDistressScent(stage), "Stage " + stage + " must not spawn monster distress scent");
            assertEquals(0, ZAUtil.computeMonsterDistressScentStrength(stage), "Stage " + stage + " distress scent strength must be 0");
        }
        for (int stage = 5; stage <= 10; stage++) {
            assertTrue(ZAUtil.canSpawnMonsterDistressScent(stage), "Stage " + stage + " must allow monster distress scent");
        }
        assertEquals(80, ZAUtil.computeMonsterDistressScentStrength(5), "Stage 5 distress scent strength must be 80");
        assertEquals(92, ZAUtil.computeMonsterDistressScentStrength(6), "Stage 6 distress scent strength must be 92");
        assertEquals(104, ZAUtil.computeMonsterDistressScentStrength(7), "Stage 7 distress scent strength must be 104");
        assertEquals(116, ZAUtil.computeMonsterDistressScentStrength(8), "Stage 8 distress scent strength must be 116");
        assertEquals(128, ZAUtil.computeMonsterDistressScentStrength(9), "Stage 9 distress scent strength must be 128");
        assertEquals(140, ZAUtil.computeMonsterDistressScentStrength(10), "Stage 10 distress scent strength must be 140");
        assertEquals(140, ZAUtil.computeMonsterDistressScentStrength(11), "Stage >10 distress scent strength must cap at 140");

        // 2. Horde Phantom Aerial Spawning Gate & Y Offset
        // Stages 1-4 must disallow phantoms for all phases
        for (int stage = 1; stage <= 4; stage++) {
            for (ChunkHeatManager.HordePhase phase : ChunkHeatManager.HordePhase.values()) {
                assertFalse(ChunkHeatManager.canHordeSpawnPhantoms(phase, stage),
                        "Stage " + stage + " phase " + phase + " must not spawn Phantoms");
            }
        }
        // Early phases (1A, 1B) must disallow phantoms even at Stage 5+
        for (int stage = 5; stage <= 10; stage++) {
            assertFalse(ChunkHeatManager.canHordeSpawnPhantoms(ChunkHeatManager.HordePhase.PHASE_1A_FAST_ZOMBIES, stage));
            assertFalse(ChunkHeatManager.canHordeSpawnPhantoms(ChunkHeatManager.HordePhase.PHASE_1B_VENOM_SPIDERS, stage));
            // Later phases (Phase 2..5) allow phantoms
            assertTrue(ChunkHeatManager.canHordeSpawnPhantoms(ChunkHeatManager.HordePhase.PHASE_2_BREACHERS_SKELETONS, stage));
            assertTrue(ChunkHeatManager.canHordeSpawnPhantoms(ChunkHeatManager.HordePhase.PHASE_3_JUGGERNAUTS_WITCHES, stage));
            assertTrue(ChunkHeatManager.canHordeSpawnPhantoms(ChunkHeatManager.HordePhase.PHASE_4_CLIMAX_SAPPERS, stage));
            assertTrue(ChunkHeatManager.canHordeSpawnPhantoms(ChunkHeatManager.HordePhase.PHASE_5_APEX_CORROSION, stage));
        }

        // Phantom spawn Y calculation
        assertEquals(80, ChunkHeatManager.computePhantomSpawnY(64), "Phantom spawn Y must be ground + 16 (64 + 16 = 80)");
        assertEquals(116, ChunkHeatManager.computePhantomSpawnY(100), "Phantom spawn Y must be ground + 16 (100 + 16 = 116)");

        // 3. 80% Drop Reduction for Horde Mobs
        // Non-horde mob: always drops loot (100%)
        assertTrue(ZAUtil.shouldHordeMobDropLoot(false, 0.0f));
        assertTrue(ZAUtil.shouldHordeMobDropLoot(false, 0.19f));
        assertTrue(ZAUtil.shouldHordeMobDropLoot(false, 0.20f));
        assertTrue(ZAUtil.shouldHordeMobDropLoot(false, 0.50f));
        assertTrue(ZAUtil.shouldHordeMobDropLoot(false, 0.99f));

        // Horde mob: only drops loot if roll < 0.20f (20% drop chance, 80% drop reduction)
        assertTrue(ZAUtil.shouldHordeMobDropLoot(true, 0.0f));
        assertTrue(ZAUtil.shouldHordeMobDropLoot(true, 0.10f));
        assertTrue(ZAUtil.shouldHordeMobDropLoot(true, 0.199f));
        assertFalse(ZAUtil.shouldHordeMobDropLoot(true, 0.20f), "Roll of 0.20f must not drop loot");
        assertFalse(ZAUtil.shouldHordeMobDropLoot(true, 0.21f), "Roll of 0.21f must not drop loot");
        assertFalse(ZAUtil.shouldHordeMobDropLoot(true, 0.50f), "Roll of 0.50f must not drop loot");
        assertFalse(ZAUtil.shouldHordeMobDropLoot(true, 0.80f), "Roll of 0.80f must not drop loot");
        assertFalse(ZAUtil.shouldHordeMobDropLoot(true, 0.99f), "Roll of 0.99f must not drop loot");
    }

    @Test
    public void hordeMobHealthReductionIncreasedParticipationAndWaterSpawnPrevention() {
        // 1. Horde Mob Health Reduction & Juggernaut Capping (Bug 42 HP fix)
        assertEquals(0.75, ChunkHeatManager.HORDE_MOB_HEALTH_MULTIPLIER, 0.001);
        assertEquals(20.0, ChunkHeatManager.HORDE_JUGGERNAUT_MAX_HEALTH_CAP, 0.001);
        assertEquals(15.0, ChunkHeatManager.computeHordeMobMaxHealth(20.0, false), 0.001,
                "Regular 20 HP zombie must scale to 15 HP (reduced from 20 HP, and never 42 HP)");
        assertEquals(20.0, ChunkHeatManager.computeHordeMobMaxHealth(20.0, true), 0.001,
                "Juggernaut max health must cap at 20 HP");
        assertEquals(20.0, ChunkHeatManager.computeHordeMobMaxHealth(42.0, true), 0.001,
                "42 HP bloated Juggernaut must cap at 20 HP");
        assertEquals(12.0, ChunkHeatManager.computeHordeMobMaxHealth(16.0, false), 0.001,
                "16 HP spider must scale to 12 HP");
        assertEquals(9.0, ChunkHeatManager.computeHordeMobMaxHealth(12.0, false), 0.001,
                "12 HP cave spider must scale to 9 HP");
        assertEquals(15.0, ChunkHeatManager.computeHordeMobMaxHealth(0.0, false), 0.001,
                "<= 0 base health fallback must return 15.0 HP");

        // 2. Increased Participation in Horde (Base 48, Squad/Stage scaling, SubWave 55%)
        assertEquals(48, ChunkHeatManager.computeSquadScaledWaveSize(0, 1, 1, false),
                "Base squad wave size with default 0 fallback must be 48");
        assertEquals(75, ChunkHeatManager.computeSquadScaledWaveSize(48, 10, 1, false),
                "Stage 10 solo wave size: 48 + (1-1)*6 + (10-1)*3 = 75");
        assertEquals(150, ChunkHeatManager.computeSquadScaledWaveSize(48, 10, 1, true),
                "Bloodmoon doubles Stage 10 wave size: 75 * 2 = 150");
        assertEquals(93, ChunkHeatManager.computeSquadScaledWaveSize(48, 10, 4, false),
                "Stage 10 4-player squad: 48 + 3*6 + 9*3 = 48 + 18 + 27 = 93");
        assertEquals(27, ChunkHeatManager.computeSubWaveCount(48),
                "Subwave count for wave 48: ceil(48 * 0.55) = ceil(26.4) = 27");
        assertEquals(12, ChunkHeatManager.computeSubWaveCount(10),
                "Subwave count floor must be 12");
        assertEquals(42, ChunkHeatManager.computeSubWaveCount(75),
                "Subwave count for wave 75: ceil(75 * 0.55) = ceil(41.25) = 42");

        // 3. Water-Body Spawn Prevention Constants & Helpers
        assertEquals(Integer.MIN_VALUE, ChunkHeatManager.NO_DRY_SPAWN_Y);
        assertFalse(ChunkHeatManager.isDrySolidSpawnColumn(null, null, null));
        assertFalse(ChunkHeatManager.isWaterSpawnColumn(null, null));

        // 4. Compass Direction Resolution (North = -Z, South = +Z, East = +X, West = -X)
        assertEquals("East", ChunkHeatManager.getCompassDirectionFromAngle(0.0),
                "Angle 0.0 (dx=1, dz=0) must resolve to East (+X)");
        assertEquals("South", ChunkHeatManager.getCompassDirectionFromAngle(Math.PI / 2.0),
                "Angle PI/2 (dx=0, dz=1) must resolve to South (+Z)");
        assertEquals("West", ChunkHeatManager.getCompassDirectionFromAngle(Math.PI),
                "Angle PI (dx=-1, dz=0) must resolve to West (-X)");
        assertEquals("North", ChunkHeatManager.getCompassDirectionFromAngle(-Math.PI / 2.0),
                "Angle -PI/2 (dx=0, dz=-1) must resolve to North (-Z)");
        assertEquals("South-East", ChunkHeatManager.getCompassDirectionFromAngle(Math.PI / 4.0),
                "Angle PI/4 (dx>0, dz>0) must resolve to South-East");
        assertEquals("South-West", ChunkHeatManager.getCompassDirectionFromAngle(3.0 * Math.PI / 4.0),
                "Angle 3*PI/4 (dx<0, dz>0) must resolve to South-West");
        assertEquals("North-West", ChunkHeatManager.getCompassDirectionFromAngle(-3.0 * Math.PI / 4.0),
                "Angle -3*PI/4 (dx<0, dz<0) must resolve to North-West");
        assertEquals("North-East", ChunkHeatManager.getCompassDirectionFromAngle(-Math.PI / 4.0),
                "Angle -PI/4 (dx>0, dz<0) must resolve to North-East");

        // 5. Phase 4 Horde Decimation (<= 2% of Phase 4 start count)
        assertFalse(ChunkHeatManager.isHordeDecimated(10, 0), "Invalid starting count <= 0 must return false");
        assertTrue(ChunkHeatManager.isHordeDecimated(0, 100), "0 alive mobs is always decimated");
        assertTrue(ChunkHeatManager.isHordeDecimated(2, 100), "2 mobs left of 100 is 2.0% -> decimated");
        assertFalse(ChunkHeatManager.isHordeDecimated(3, 100), "3 mobs left of 100 is 3.0% -> NOT decimated");
        assertTrue(ChunkHeatManager.isHordeDecimated(1, 50), "1 mob left of 50 is 2.0% -> decimated");
        assertFalse(ChunkHeatManager.isHordeDecimated(2, 50), "2 mobs left of 50 is 4.0% -> NOT decimated");
        assertTrue(ChunkHeatManager.isHordeDecimated(4, 200), "4 mobs left of 200 is 2.0% -> decimated");
        assertFalse(ChunkHeatManager.isHordeDecimated(5, 200), "5 mobs left of 200 is 2.5% -> NOT decimated");
    }

    @Test
    public void dawnVictoryConditionDaytimeGunfireHeatReductionAndPhantomParatroopers() {
        // 1. Daytime vs Night Gunfire Heat Rates
        assertEquals(10, ChunkHeatManager.computeGunfireHeatAmount(true, false),
                "Daytime unsuppressed gunfire must accumulate 10 heat (reduced from 25)");
        assertEquals(2, ChunkHeatManager.computeGunfireHeatAmount(true, true),
                "Daytime suppressed gunfire must accumulate 2 heat (reduced from 4)");
        assertEquals(25, ChunkHeatManager.computeGunfireHeatAmount(false, false),
                "Night unsuppressed gunfire must accumulate full 25 heat");
        assertEquals(4, ChunkHeatManager.computeGunfireHeatAmount(false, true),
                "Night suppressed gunfire must accumulate 4 heat");

        // 2. Extended Post-Horde Breather Cooldown
        assertEquals(4200L, ChunkHeatManager.POST_HORDE_BREATHER_TICKS,
                "POST_HORDE_BREATHER_TICKS must be extended to 4200 ticks (210s / 3.5 mins)");

        // 3. Dawn Victory Condition Logic (isDawnArrival)
        // If not preceded by night -> false
        assertFalse(ChunkHeatManager.isDawnArrival(false, 0L, false), "wasNight=false must not trigger dawn victory");
        assertFalse(ChunkHeatManager.isDawnArrival(false, 6000L, false), "wasNight=false at noon must not trigger dawn victory");

        // If Bloodmoon is active -> dawn never arrives / victory inhibited
        assertFalse(ChunkHeatManager.isDawnArrival(true, 0L, true), "Bloodmoon active must inhibit dawn victory");
        assertFalse(ChunkHeatManager.isDawnArrival(true, 6000L, true), "Bloodmoon active at noon must inhibit dawn victory");

        // Preceded by night, no Bloodmoon:
        // Day window: 0 <= dayTime < 12000 ticks (or >= 23500 ticks) -> true
        assertTrue(ChunkHeatManager.isDawnArrival(true, 0L, false), "Tick 0 (dawn) must trigger dawn victory");
        assertTrue(ChunkHeatManager.isDawnArrival(true, 1000L, false), "Tick 1000 (morning) must trigger dawn victory");
        assertTrue(ChunkHeatManager.isDawnArrival(true, 6000L, false), "Tick 6000 (midday) must trigger dawn victory");
        assertTrue(ChunkHeatManager.isDawnArrival(true, 11999L, false), "Tick 11999 (pre-dusk) must trigger dawn victory");

        // Night window: 12000 <= dayTime < 23500 -> false
        assertFalse(ChunkHeatManager.isDawnArrival(true, 12000L, false), "Tick 12000 (dusk) must NOT trigger dawn victory");
        assertFalse(ChunkHeatManager.isDawnArrival(true, 18000L, false), "Tick 18000 (midnight) must NOT trigger dawn victory");
        assertFalse(ChunkHeatManager.isDawnArrival(true, 23499L, false), "Tick 23499 (pre-dawn) must NOT trigger dawn victory");

        // 4. Phantom Aerial Passenger Safety
        assertFalse(ChunkHeatManager.checkAndDropPhantomPassenger(null, null),
                "Null phantom and target handled safely returning false");
    }

    @Test
    public void victoryCloseCombatRetentionAndSeventyThirtyPincerHelpersWorkCleanly() {
        assertEquals(24.0, ChunkHeatManager.VICTORY_RETAIN_RADIUS_BLOCKS, 1e-6);
        assertFalse(ChunkHeatManager.shouldDisperseOnVictory(0.0));
        assertFalse(ChunkHeatManager.shouldDisperseOnVictory(24.0 * 24.0),
                "Mobs at <= 24m must stay alive on victory for close-quarters combat");
        assertTrue(ChunkHeatManager.shouldDisperseOnVictory(24.1 * 24.1),
                "Mobs > 24m from nearest alive player must disperse on victory");

        // 70/30 pincer ratio across indices 0..19
        int flankCount = 0;
        for (int i = 0; i < 20; i++) {
            if (ChunkHeatManager.isFlankSpawnIndex(i)) {
                flankCount++;
            }
        }
        assertEquals(6, flankCount, "30% of spawns (6 out of 20) must use silent flank approach");

        double front = 0.0;
        double flankCenter = ChunkHeatManager.computeFlankApproachAngle(front, 0.0);
        double flankMin = ChunkHeatManager.computeFlankApproachAngle(front, -1.0);
        double flankMax = ChunkHeatManager.computeFlankApproachAngle(front, 1.0);
        assertEquals(Math.PI, flankCenter, 1e-6);
        assertEquals(Math.PI * 2.0 / 3.0, flankMin, 1e-6, "120 deg offset");
        assertEquals(Math.PI * 4.0 / 3.0, flankMax, 1e-6, "240 deg offset");
    }
}
