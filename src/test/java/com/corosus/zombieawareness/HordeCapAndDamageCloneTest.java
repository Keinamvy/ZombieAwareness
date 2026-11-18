package com.corosus.zombieawareness;

import com.corosus.zombieawareness.config.ZAConfigSpawning;
import com.corosus.zombieawareness.config.ZAConfigZDay;
import com.corosus.zombieawareness.heat.ChunkHeatManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class HordeCapAndDamageCloneTest {

    @BeforeEach
    void setUp() {
        ZAUtil.resetDamageCloneState();
    }

    @AfterEach
    void tearDown() {
        ZAUtil.resetDamageCloneState();
    }

    @Test
    void hordeMobCapAndSpawningDefaultsAreSignificantlyIncreased() {
        assertEquals(48, ZAConfigZDay.baseSwarmWaveSize);
        assertEquals(180, ZAConfigZDay.hordeMaxAliveNearPlayer);
        assertEquals(70, ZAConfigSpawning.maxZombiesNight);
        assertEquals(100, ZAConfigSpawning.extraSpawningSurfaceMaxCount);
        assertEquals(100, ZAConfigSpawning.extraSpawningCavesMaxCount);
        assertEquals(6, ZAConfigSpawning.extraSpawningSurfaceMaxGroupSize);

        assertEquals(180, ChunkHeatManager.computeEffectiveHordeMobCap(0, 1, false));
        assertEquals(260, ChunkHeatManager.computeEffectiveHordeMobCap(180, 3, false));
        assertEquals(360, ChunkHeatManager.computeEffectiveHordeMobCap(180, 1, true));
        assertEquals(520, ChunkHeatManager.computeEffectiveHordeMobCap(180, 3, true));
    }

    @Test
    void hordeSubWaveSpawnCountRespectsEffectiveMobCap() {
        assertEquals(30, ChunkHeatManager.computeAllowedSubWaveSpawnCount(30, 100, 180));
        assertEquals(10, ChunkHeatManager.computeAllowedSubWaveSpawnCount(30, 170, 180));
        assertEquals(0, ChunkHeatManager.computeAllowedSubWaveSpawnCount(30, 180, 180));
        assertEquals(0, ChunkHeatManager.computeAllowedSubWaveSpawnCount(30, 200, 180));
        assertEquals(0, ChunkHeatManager.computeAllowedSubWaveSpawnCount(0, 10, 180));
    }

    @Test
    void damageCloneConfigDefaultsAndTriggerGuards() {
        assertEquals(0.08, ZAConfigZDay.mobDamageCloneChance, 1e-6);
        assertEquals(2, ZAConfigZDay.mobDamageCloneCount);
        assertEquals(200, ZAConfigZDay.mobDamageCloneCooldownTicks);
        assertEquals(48, ZAConfigZDay.mobDamageCloneMaxNearby);
        assertEquals("za_damage_clone", ZAUtil.DAMAGE_CLONE_TAG);

        // Eligible horde hostile mob damaged by combat attacker with roll < 0.08 triggers cloning
        assertTrue(ZAUtil.canTriggerDamageClone(
                true, true, true, false, true, 5.0f,
                -1L, 500L, 200L, 10, 48, 0.08, 0.05));

        // Non-horde wild mobs must never trigger damage cloning
        assertFalse(ZAUtil.canTriggerDamageClone(
                false, true, true, false, true, 5.0f,
                -1L, 500L, 200L, 10, 48, 0.08, 0.01));

        // Roll >= chance does not trigger
        assertFalse(ZAUtil.canTriggerDamageClone(
                true, true, true, false, true, 5.0f,
                -1L, 500L, 200L, 10, 48, 0.08, 0.08));
        assertFalse(ZAUtil.canTriggerDamageClone(
                true, true, true, false, true, 5.0f,
                -1L, 500L, 200L, 10, 48, 0.08, 0.50));

        // Environmental damage (no attacker entity) must not trigger clones
        assertFalse(ZAUtil.canTriggerDamageClone(
                true, true, true, false, false, 5.0f,
                -1L, 500L, 200L, 10, 48, 0.08, 0.01));

        // Already-cloned mobs (za_damage_clone) must never clone recursively
        assertFalse(ZAUtil.canTriggerDamageClone(
                true, true, true, true, true, 5.0f,
                -1L, 500L, 200L, 10, 48, 0.08, 0.01));

        // Cooldown prevents repeated triggers until cooldownTicks elapses
        assertFalse(ZAUtil.canTriggerDamageClone(
                true, true, true, false, true, 5.0f,
                400L, 550L, 200L, 10, 48, 0.08, 0.01));
        assertTrue(ZAUtil.canTriggerDamageClone(
                true, true, true, false, true, 5.0f,
                400L, 600L, 200L, 10, 48, 0.08, 0.01));

        // Nearby hostile density cap stops further cloning
        assertFalse(ZAUtil.canTriggerDamageClone(
                true, true, true, false, true, 5.0f,
                -1L, 500L, 200L, 48, 48, 0.08, 0.01));

        // Non-hostile, dead, or zero-damage hits are rejected
        assertFalse(ZAUtil.canTriggerDamageClone(
                true, false, true, false, true, 5.0f,
                -1L, 500L, 200L, 10, 48, 0.08, 0.01));
        assertFalse(ZAUtil.canTriggerDamageClone(
                true, true, false, false, true, 5.0f,
                -1L, 500L, 200L, 10, 48, 0.08, 0.01));
        assertFalse(ZAUtil.canTriggerDamageClone(
                true, true, true, false, true, 0.0f,
                -1L, 500L, 200L, 10, 48, 0.08, 0.01));

        // Large-HP mobs (Tanker 40 HP, Knight 30 HP, Warden 500 HP) and restricted elites must not clone
        assertEquals(25.0f, ZAUtil.MAX_CLONE_SOURCE_MAX_HEALTH, 1e-6f);
        assertEquals("za_cloned_mob", ZAUtil.CLONED_MOB_TAG);
        assertFalse(ZAUtil.canTriggerDamageClone(
                true, true, true, false, true, 5.0f,
                -1L, 500L, 200L, 10, 48, 0.08, 0.01, 40.0f, false));
        assertFalse(ZAUtil.canTriggerDamageClone(
                true, true, true, false, true, 5.0f,
                -1L, 500L, 200L, 10, 48, 0.08, 0.01, 500.0f, false));
        assertFalse(ZAUtil.canTriggerDamageClone(
                true, true, true, false, true, 5.0f,
                -1L, 500L, 200L, 10, 48, 0.08, 0.01, 20.0f, true));
        assertTrue(ZAUtil.canTriggerDamageClone(
                true, true, true, false, true, 5.0f,
                -1L, 500L, 200L, 10, 48, 0.08, 0.01, 20.0f, false));
    }
}
