package com.corosus.zombieawareness.zday;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class ZDayProgressionTest {

    @BeforeEach
    void setUp() {
        ZDayProgressionManager.resetForTests();
    }

    @Test
    void emptyServerDoesNotAdvanceActivePlayTicks() {
        long currentActiveTicks = 1000L;
        long prevVanillaDayTime = 1000L;
        long currentVanillaDayTime = 1001L;
        int onlinePlayerCount = 0;

        long result = ZDayProgressionManager.advanceTickLogic(currentActiveTicks, prevVanillaDayTime, currentVanillaDayTime, onlinePlayerCount);
        assertEquals(1000L, result, "Active play ticks must freeze when online player count is zero");
    }

    @Test
    void populatedServerAdvancesActivePlayTicksAndHandlesSleepSkip() {
        // Normal single tick advance with online players
        long normalTick = ZDayProgressionManager.advanceTickLogic(1000L, 1000L, 1001L, 1);
        assertEquals(1001L, normalTick, "Active play ticks should advance by 1 on normal populated server tick");

        // Night-skip via bed sleep: clock jumps from tick 13000 to tick 24000 (dawn of Day 2)
        long currentActiveTicks = 13000L;
        long prevVanillaDayTime = 13000L;
        long currentVanillaDayTime = 24000L;
        int onlinePlayerCount = 2;

        long sleepSkipResult = ZDayProgressionManager.advanceTickLogic(currentActiveTicks, prevVanillaDayTime, currentVanillaDayTime, onlinePlayerCount);
        assertEquals(24000L, sleepSkipResult, "Active play ticks must advance by full delta (11000 ticks) when players sleep through the night");
    }

    @Test
    void stageUnlockScheduleMatchesSpec() {
        // Milestone unlock days from design spec
        assertEquals(ZDayStage.STAGE_1_OUTBREAK, ZDayStage.getStageForDay(1));
        assertEquals(ZDayStage.STAGE_1_OUTBREAK, ZDayStage.getStageForDay(2));
        assertEquals(ZDayStage.STAGE_1_OUTBREAK, ZDayStage.getStageForDay(3));

        assertEquals(ZDayStage.STAGE_2_TACZ_ACOUSTIC, ZDayStage.getStageForDay(4));
        assertEquals(ZDayStage.STAGE_2_TACZ_ACOUSTIC, ZDayStage.getStageForDay(5));

        assertEquals(ZDayStage.STAGE_3_BLACKOUT_STALKERS, ZDayStage.getStageForDay(6));
        assertEquals(ZDayStage.STAGE_3_BLACKOUT_STALKERS, ZDayStage.getStageForDay(7));

        assertEquals(ZDayStage.STAGE_4_BLOODMOON, ZDayStage.getStageForDay(8));
        assertEquals(ZDayStage.STAGE_4_BLOODMOON, ZDayStage.getStageForDay(10));

        assertEquals(ZDayStage.STAGE_5_PICKAXE_BREACHERS, ZDayStage.getStageForDay(11));
        assertEquals(ZDayStage.STAGE_5_PICKAXE_BREACHERS, ZDayStage.getStageForDay(13));

        assertEquals(ZDayStage.STAGE_6_WEBS_AND_SPRINTERS, ZDayStage.getStageForDay(14));
        assertEquals(ZDayStage.STAGE_6_WEBS_AND_SPRINTERS, ZDayStage.getStageForDay(17));

        assertEquals(ZDayStage.STAGE_7_SCREAMER_SCOUTS, ZDayStage.getStageForDay(18));
        assertEquals(ZDayStage.STAGE_7_SCREAMER_SCOUTS, ZDayStage.getStageForDay(21));

        assertEquals(ZDayStage.STAGE_8_COMBAT_ENGINEERS, ZDayStage.getStageForDay(22));
        assertEquals(ZDayStage.STAGE_8_COMBAT_ENGINEERS, ZDayStage.getStageForDay(25));

        assertEquals(ZDayStage.STAGE_9_JUGGERNAUTS_AND_CRAWLERS, ZDayStage.getStageForDay(26));
        assertEquals(ZDayStage.STAGE_9_JUGGERNAUTS_AND_CRAWLERS, ZDayStage.getStageForDay(29));

        assertEquals(ZDayStage.STAGE_10_TOTAL_COLLAPSE, ZDayStage.getStageForDay(30));
        assertEquals(ZDayStage.STAGE_10_TOTAL_COLLAPSE, ZDayStage.getStageForDay(100));
    }

    @Test
    void offlinePlayerCatchUpReturnsMissedStagesAndPersists(@TempDir Path tempDir) {
        ZDayPlayerLedger ledger = new ZDayPlayerLedger();
        UUID playerUuid = UUID.randomUUID();

        // Player last seen at Stage 1, Day 1
        ledger.recordPlayerSeen(playerUuid, 1, 1);
        assertEquals(1, ledger.getPlayerLastSeenStage(playerUuid, 1));
        assertEquals(1, ledger.getPlayerLastSeenDay(playerUuid, 1));

        // Player joins on Day 11 (Stage 5 Pickaxe Breachers)
        List<ZDayStage> missedStages = ledger.getCatchUpStagesAndMarkSeen(playerUuid, 11, 5);
        assertEquals(4, missedStages.size(), "Player must receive 4 missed stages (Stages 2, 3, 4, 5)");
        assertEquals(List.of(
            ZDayStage.STAGE_2_TACZ_ACOUSTIC,
            ZDayStage.STAGE_3_BLACKOUT_STALKERS,
            ZDayStage.STAGE_4_BLOODMOON,
            ZDayStage.STAGE_5_PICKAXE_BREACHERS
        ), missedStages);

        // Immediate subsequent query at Stage 5 returns empty list (already marked seen)
        List<ZDayStage> repeatQuery = ledger.getCatchUpStagesAndMarkSeen(playerUuid, 11, 5);
        assertTrue(repeatQuery.isEmpty(), "Subsequent catch-up query at the same stage must be empty");

        // Verify persistence to JSON file
        Path file = tempDir.resolve("test_zday_ledger.json");
        ledger.save(file);
        assertTrue(Files.exists(file), "Ledger file must exist after save");

        ZDayPlayerLedger reloaded = ZDayPlayerLedger.load(file);
        assertEquals(5, reloaded.getPlayerLastSeenStage(playerUuid, 1));
        assertEquals(11, reloaded.getPlayerLastSeenDay(playerUuid, 1));
    }

    @Test
    void forcedWorldDayOverridesCalculatedDay() {
        assertEquals(1, ZDayProgressionManager.getActiveWorldDay());
        assertEquals(ZDayStage.STAGE_1_OUTBREAK, ZDayProgressionManager.getCurrentStage());
        assertTrue(ZDayProgressionManager.isStageUnlocked(1));
        assertFalse(ZDayProgressionManager.isStageUnlocked(2));

        // Force World Day 8 (Bloodmoon)
        ZDayProgressionManager.setForcedWorldDay(8);
        assertEquals(8, ZDayProgressionManager.getActiveWorldDay());
        assertEquals(ZDayStage.STAGE_4_BLOODMOON, ZDayProgressionManager.getCurrentStage());
        assertTrue(ZDayProgressionManager.isStageUnlocked(1));
        assertTrue(ZDayProgressionManager.isStageUnlocked(2));
        assertTrue(ZDayProgressionManager.isStageUnlocked(3));
        assertTrue(ZDayProgressionManager.isStageUnlocked(4));
        assertFalse(ZDayProgressionManager.isStageUnlocked(5));

        // Reset override
        ZDayProgressionManager.setForcedWorldDay(-1);
        assertEquals(1, ZDayProgressionManager.getActiveWorldDay());
    }

    @Test
    void buildCatchUpReportFormatsExpectedLines() {
        UUID playerUuid = UUID.randomUUID();
        // Record at stage 1
        ZDayProgressionManager.getLedger().recordPlayerSeen(playerUuid, 1, 1);

        // Force World Day 11 (Stage 5)
        ZDayProgressionManager.setForcedWorldDay(11);
        List<String> report = ZDayProgressionManager.buildCatchUpReportLines(playerUuid);

        assertNotNull(report);
        assertTrue(report.size() > 5, "Report must contain briefing header, stage entries, and footer");
        assertTrue(report.get(1).contains("While You Were Away"));

        // Subsequent query should return 1-line up-to-date status
        List<String> upToDate = ZDayProgressionManager.buildCatchUpReportLines(playerUuid);
        assertEquals(1, upToDate.size());
        assertTrue(upToDate.get(0).contains("Stage 5/10"));
    }

    @Test
    void stageTransitionsAndBetweenQueries() {
        List<ZDayStage> allStages = ZDayStage.getAllUnlockedStages(3);
        assertEquals(3, allStages.size());
        assertEquals(ZDayStage.STAGE_1_OUTBREAK, allStages.get(0));
        assertEquals(ZDayStage.STAGE_2_TACZ_ACOUSTIC, allStages.get(1));
        assertEquals(ZDayStage.STAGE_3_BLACKOUT_STALKERS, allStages.get(2));

        List<ZDayStage> between = ZDayStage.getUnlockedStagesBetween(5, 7);
        assertEquals(2, between.size());
        assertEquals(ZDayStage.STAGE_6_WEBS_AND_SPRINTERS, between.get(0));
        assertEquals(ZDayStage.STAGE_7_SCREAMER_SCOUTS, between.get(1));

        List<ZDayStage> emptyBetween = ZDayStage.getUnlockedStagesBetween(7, 5);
        assertTrue(emptyBetween.isEmpty());
    }
}
