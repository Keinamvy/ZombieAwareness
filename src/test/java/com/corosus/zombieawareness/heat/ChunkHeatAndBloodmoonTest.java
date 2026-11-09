package com.corosus.zombieawareness.heat;

import com.corosus.zombieawareness.bloodmoon.BloodmoonManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
        assertTrue(ChunkHeatManager.isFirearmOrExplosionSound("minecraft", "entity.generic.explode"), "generic explode should trigger gunfire/explosion heat");
        assertTrue(ChunkHeatManager.isFirearmOrExplosionSound("tacz", "ak47_shoot"), "tacz ak47_shoot should be firearm sound");
        assertFalse(ChunkHeatManager.isFirearmOrExplosionSound("minecraft", "entity.zombie.ambient"), "ambient zombie noise should not be firearm sound");
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
}
