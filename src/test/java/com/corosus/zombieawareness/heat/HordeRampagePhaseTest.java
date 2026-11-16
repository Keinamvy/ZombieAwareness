package com.corosus.zombieawareness.heat;

import com.corosus.zombieawareness.ZAUtil;
import com.corosus.zombieawareness.config.ZAConfigZDay;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class HordeRampagePhaseTest {

    @BeforeEach
    void setUp() {
        ChunkHeatManager.clearAllHeat();
    }

    @AfterEach
    void tearDown() {
        ChunkHeatManager.clearAllHeat();
    }

    @Test
    void rampageConfigDefaultsAndPrimaryTimerEndTransition() {
        assertEquals(6000, ZAConfigZDay.hordeBaseDurationTicks);
        assertEquals(6000, ChunkHeatManager.DEFAULT_HORDE_BASE_DURATION_TICKS);
        assertEquals(2400, ZAConfigZDay.hordeRampageDurationTicks);
        assertEquals(2400, ChunkHeatManager.DEFAULT_HORDE_RAMPAGE_DURATION_TICKS);
        assertEquals(0, ChunkHeatManager.RAMPAGE_SPEED_AMPLIFIER, "Rampage Speed I must use amplifier 0");
        assertTrue(ZAConfigZDay.hordeRampageArrowSpeedMultiplier >= 1.65);
        assertEquals(8, ZAConfigZDay.hordeRampageSkeletonAttackIntervalTicks);
        assertTrue(ZAConfigZDay.hordeRampageSpiderStepHeight >= 1.05);

        // Confirm progressive mob-pruning methods were completely removed
        for (Method m : ChunkHeatManager.class.getDeclaredMethods()) {
            assertFalse(m.getName().contains("Prune") && !m.getName().equals("pruneExpiredMaps"),
                    "Progressive pruning method must not remain: " + m.getName());
        }

        UUID playerId = UUID.randomUUID();
        BlockPos center = new BlockPos(100, 64, 100);

        // Case 1: 0 mobs remaining when 5-minute (6000-tick) primary timer finishes -> immediate victory
        ChunkHeatManager.ActiveHorde emptyHorde = new ChunkHeatManager.ActiveHorde(center, Set.of(playerId), 6000, 16, 5);
        emptyHorde.elapsedTicks = 6000;
        assertFalse(ChunkHeatManager.evaluatePrimaryTimerEnd(emptyHorde, 0, ZAConfigZDay.hordeRampageDurationTicks));
        assertFalse(emptyHorde.rampagePhaseActive);
        assertTrue(emptyHorde.isFullyExpired());

        // Case 2: > 0 mobs remaining -> remains reinforcing through tick 3600, enters 2-minute Rampage overtime at 6000
        assertTrue(ChunkHeatManager.tryRegisterHordeForGroup(center, Set.of(playerId), 1, 16, 10));
        ChunkHeatManager.ActiveHorde horde = ChunkHeatManager.getActiveHordes().get(0);
        assertEquals(6000, horde.totalDurationTicks);
        assertTrue(ChunkHeatManager.shouldReinforceHorde(horde));

        horde.elapsedTicks = 3600;
        assertFalse(horde.isFullyExpired());
        assertTrue(ChunkHeatManager.shouldReinforceHorde(horde));
        assertEquals(
                ChunkHeatManager.HordePhase.PHASE_4_CLIMAX_SAPPERS,
                ChunkHeatManager.resolveHordePhase((double) horde.elapsedTicks / horde.totalDurationTicks, horde.worldStageNum)
        );

        horde.elapsedTicks = horde.totalDurationTicks;
        assertTrue(ChunkHeatManager.evaluatePrimaryTimerEnd(horde, 40, ZAConfigZDay.hordeRampageDurationTicks));
        assertTrue(horde.rampagePhaseActive);
        assertEquals(0, horde.rampageElapsedTicks);
        assertEquals(2400, horde.rampageDurationTicks);
        assertEquals(40, horde.rampageInitialMobCount);

        // Reinforcements stop during Rampage while active horde locks overlapping hordes
        assertFalse(ChunkHeatManager.shouldReinforceHorde(horde));
        assertFalse(horde.isFullyExpired());
        assertEquals(1, ChunkHeatManager.getActiveHordeCount());
        assertFalse(ChunkHeatManager.canTriggerHordeForGroup(center, Set.of(playerId)));

        // Exhausting the 2-minute Rampage timer marks the horde expired
        horde.rampageElapsedTicks = 2400;
        assertTrue(horde.isFullyExpired());
        assertEquals(0, ChunkHeatManager.getActiveHordeCount());
    }

    @Test
    void rampageSkeletonAndSpiderStatHelpersAccelerateCombat() {
        assertTrue(ZAUtil.hasHordeMobTag(Set.of(ZAUtil.RAMPAGE_TAG)));
        assertEquals(1.70F, ChunkHeatManager.getRampageArrowVelocityMultiplier(), 1e-4F);
        assertEquals(8, ChunkHeatManager.computeRampageBowInterval(20));
        assertEquals(8, ChunkHeatManager.computeRampageBowInterval(40));
        assertEquals(6, ChunkHeatManager.computeRampageBowInterval(6));
        assertTrue(ChunkHeatManager.getRampageSpiderStepHeight() >= 1.05D);
    }
}
