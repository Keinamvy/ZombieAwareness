package com.corosus.zombieawareness.heat;

import com.corosus.zombieawareness.bloodmoon.BloodmoonManager;
import com.corosus.zombieawareness.zday.ZDayProgressionManager;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
        ZDayProgressionManager.setForcedWorldDay(4); // Stage 2+ required for heat accumulation

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
    public void bloodmoonScheduleMatchesDay8AndEvery6DaysAtNight() {
        // Day schedule: starts at Day 8, repeats every 6 days (Day 8, 14, 20, 26, 32...)
        assertFalse(BloodmoonManager.isBloodmoonDay(1), "Day 1 must not be Bloodmoon");
        assertFalse(BloodmoonManager.isBloodmoonDay(7), "Day 7 must not be Bloodmoon");
        assertTrue(BloodmoonManager.isBloodmoonDay(8), "Day 8 must be Bloodmoon");
        assertFalse(BloodmoonManager.isBloodmoonDay(9), "Day 9 must not be Bloodmoon");
        assertFalse(BloodmoonManager.isBloodmoonDay(13), "Day 13 must not be Bloodmoon");
        assertTrue(BloodmoonManager.isBloodmoonDay(14), "Day 14 must be Bloodmoon");
        assertTrue(BloodmoonManager.isBloodmoonDay(20), "Day 20 must be Bloodmoon");
        assertTrue(BloodmoonManager.isBloodmoonDay(26), "Day 26 must be Bloodmoon");

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
        assertEquals(3600, ChunkHeatManager.computeHordeDurationTicks(1), "1 player should yield 3600 ticks (3 minutes)");
        assertEquals(4800, ChunkHeatManager.computeHordeDurationTicks(3), "3 players should yield 4800 ticks (3 min + 2x30s)");
        assertEquals(3600, ChunkHeatManager.computeHordeDurationTicks(0), "0 players fallback should yield 3600 ticks");
    }

    @Test
    public void hordePhaseResolvesAndRespectsWorldStageCaps() {
        // Progress 0.05 -> Phase 1A (uncapped across all stages)
        assertEquals(ChunkHeatManager.HordePhase.PHASE_1A_FAST_ZOMBIES, ChunkHeatManager.resolveHordePhase(0.05, 2));
        assertEquals(ChunkHeatManager.HordePhase.PHASE_1A_FAST_ZOMBIES, ChunkHeatManager.resolveHordePhase(0.05, 6));
        assertEquals(ChunkHeatManager.HordePhase.PHASE_1A_FAST_ZOMBIES, ChunkHeatManager.resolveHordePhase(0.05, 9));
        assertEquals(ChunkHeatManager.HordePhase.PHASE_1A_FAST_ZOMBIES, ChunkHeatManager.resolveHordePhase(0.05, 10));

        // Progress 0.25 -> Phase 1B (uncapped across all stages)
        assertEquals(ChunkHeatManager.HordePhase.PHASE_1B_VENOM_SPIDERS, ChunkHeatManager.resolveHordePhase(0.25, 2));
        assertEquals(ChunkHeatManager.HordePhase.PHASE_1B_VENOM_SPIDERS, ChunkHeatManager.resolveHordePhase(0.25, 6));
        assertEquals(ChunkHeatManager.HordePhase.PHASE_1B_VENOM_SPIDERS, ChunkHeatManager.resolveHordePhase(0.25, 9));
        assertEquals(ChunkHeatManager.HordePhase.PHASE_1B_VENOM_SPIDERS, ChunkHeatManager.resolveHordePhase(0.25, 10));

        // Progress 0.50 -> Raw Phase 2 (Breachers)
        assertEquals(ChunkHeatManager.HordePhase.PHASE_1B_VENOM_SPIDERS, ChunkHeatManager.resolveHordePhase(0.50, 2), "Stage 2 must cap at Phase 1B");
        assertEquals(ChunkHeatManager.HordePhase.PHASE_2_BREACHERS_SKELETONS, ChunkHeatManager.resolveHordePhase(0.50, 6));
        assertEquals(ChunkHeatManager.HordePhase.PHASE_2_BREACHERS_SKELETONS, ChunkHeatManager.resolveHordePhase(0.50, 9));
        assertEquals(ChunkHeatManager.HordePhase.PHASE_2_BREACHERS_SKELETONS, ChunkHeatManager.resolveHordePhase(0.50, 10));

        // Progress 0.70 -> Raw Phase 3 (Juggernauts)
        assertEquals(ChunkHeatManager.HordePhase.PHASE_1B_VENOM_SPIDERS, ChunkHeatManager.resolveHordePhase(0.70, 2), "Stage 2 must cap at Phase 1B");
        assertEquals(ChunkHeatManager.HordePhase.PHASE_2_BREACHERS_SKELETONS, ChunkHeatManager.resolveHordePhase(0.70, 6), "Stage 6 must cap at Phase 2");
        assertEquals(ChunkHeatManager.HordePhase.PHASE_3_JUGGERNAUTS_WITCHES, ChunkHeatManager.resolveHordePhase(0.70, 9));
        assertEquals(ChunkHeatManager.HordePhase.PHASE_3_JUGGERNAUTS_WITCHES, ChunkHeatManager.resolveHordePhase(0.70, 10));

        // Progress 0.90 -> Raw Phase 4 (Climax Sappers)
        assertEquals(ChunkHeatManager.HordePhase.PHASE_1B_VENOM_SPIDERS, ChunkHeatManager.resolveHordePhase(0.90, 2), "Stage 2 must cap at Phase 1B");
        assertEquals(ChunkHeatManager.HordePhase.PHASE_2_BREACHERS_SKELETONS, ChunkHeatManager.resolveHordePhase(0.90, 6), "Stage 6 must cap at Phase 2");
        assertEquals(ChunkHeatManager.HordePhase.PHASE_3_JUGGERNAUTS_WITCHES, ChunkHeatManager.resolveHordePhase(0.90, 9), "Stage 9 must cap at Phase 3");
        assertEquals(ChunkHeatManager.HordePhase.PHASE_4_CLIMAX_SAPPERS, ChunkHeatManager.resolveHordePhase(0.90, 10), "Stage 10 allows Climax Sappers");
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
        assertEquals(3600, horde1.totalDurationTicks);

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
        assertEquals(4200, horde.totalDurationTicks, "2 nearby players = 3600 + 1 * 600 = 4200 ticks");

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

        // Breather is 1800 ticks, so at tick 3600 + 1799 = 5399, still in breather
        assertTrue(ChunkHeatManager.isGroupInPostHordeBreather(group, 5399L), "Group must still be in breather at tick 5399");

        // At tick 3600 + 1800 = 5400, breather has expired
        assertFalse(ChunkHeatManager.isGroupInPostHordeBreather(group, 5400L), "Breather must expire at tick 5400");
        assertFalse(ChunkHeatManager.isGroupInPostHordeBreather(group, 5401L), "Breather must be expired at tick 5401");

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
        ChunkHeatManager.ActiveHorde horde = new ChunkHeatManager.ActiveHorde(center, Set.of(p1), 3600, 6, 10);

        assertEquals(0, horde.noTargetTicks, "noTargetTicks should initialize to 0");
        assertFalse(horde.climaxRoarPlayed, "climaxRoarPlayed should initialize to false");

        // Test Phase 4 Climax resolution at 0.90 progress
        assertEquals(ChunkHeatManager.HordePhase.PHASE_4_CLIMAX_SAPPERS, ChunkHeatManager.resolveHordePhase(0.90, 10));

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
}
