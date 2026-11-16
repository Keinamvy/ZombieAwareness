package com.corosus.zombieawareness;

import com.corosus.zombieawareness.config.ZAConfigZDay;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class AdrenalineAndEliteLootTest {

    @BeforeEach
    void setUp() {
        ZAUtil.resetAdrenalineState();
        ZAUtil.resetEliteLootWindowState();
    }

    @AfterEach
    void tearDown() {
        ZAUtil.resetAdrenalineState();
        ZAUtil.resetEliteLootWindowState();
    }

    @Test
    void oneHeartClutchTriggersOnLethalOrLowHealthThresholdAndRespectsGuards() {
        assertEquals(6000L, ZAUtil.ONE_HEART_CLUTCH_COOLDOWN_TICKS);
        assertEquals(0.25D, ZAUtil.ONE_HEART_CLUTCH_CHANCE, 1e-6);
        assertEquals(2.0F, ZAUtil.ONE_HEART_CLUTCH_HEALTH, 1e-6f);
        assertEquals(40L, ZAUtil.ONE_HEART_CLUTCH_INVUL_TICKS);
        assertFalse(ZAUtil.CLUTCH_EPILOGUE_LINES.isEmpty());

        assertTrue(ZAUtil.canTriggerOneHeartClutch(true, false, false, 6.0F, 4.0F, Long.MIN_VALUE, 100L, 0.0D));
        assertTrue(ZAUtil.canTriggerOneHeartClutch(true, false, false, 5.0F, 12.0F, Long.MIN_VALUE, 100L, 0.2499D));
        assertTrue(ZAUtil.canTriggerOneHeartClutch(true, false, false, 2.0F, 1.0F, -1L, 0L, 0.10D));

        assertFalse(ZAUtil.canTriggerOneHeartClutch(true, false, false, 6.0F, 3.5F, Long.MIN_VALUE, 100L, 0.0D));
        assertFalse(ZAUtil.canTriggerOneHeartClutch(true, false, false, 5.0F, 10.0F, Long.MIN_VALUE, 100L, 0.25D));
        assertFalse(ZAUtil.canTriggerOneHeartClutch(true, false, false, 5.0F, 10.0F, Long.MIN_VALUE, 100L, 0.80D));

        assertFalse(ZAUtil.canTriggerOneHeartClutch(false, false, false, 5.0F, 10.0F, Long.MIN_VALUE, 100L, 0.0D));
        assertFalse(ZAUtil.canTriggerOneHeartClutch(true, true, false, 5.0F, 10.0F, Long.MIN_VALUE, 100L, 0.0D));
        assertFalse(ZAUtil.canTriggerOneHeartClutch(true, false, true, 5.0F, 10.0F, Long.MIN_VALUE, 100L, 0.0D));
        assertFalse(ZAUtil.canTriggerOneHeartClutch(true, false, false, 2.0F, 0.0F, Long.MIN_VALUE, 100L, 0.0D));
    }

    @Test
    void oneHeartClutchEnforcesFiveMinuteCooldownAndClearEntityDataResetsState() {
        UUID playerId = UUID.randomUUID();
        long triggerTick = 1000L;
        ZAUtil.recordClutchAndAdrenaline(playerId, triggerTick);

        long recorded = ZAUtil.getLastClutchTick(playerId);
        assertEquals(triggerTick, recorded);

        assertFalse(ZAUtil.canTriggerOneHeartClutch(true, false, false, 5.0F, 10.0F, recorded, 1000L, 0.05D));
        assertFalse(ZAUtil.canTriggerOneHeartClutch(true, false, false, 5.0F, 10.0F, recorded, 6999L, 0.05D));
        assertTrue(ZAUtil.canTriggerOneHeartClutch(true, false, false, 5.0F, 10.0F, recorded, 7000L, 0.05D));

        assertTrue(ZAUtil.hasActiveAdrenaline(playerId, triggerTick + 100L));
        ZombieAwareness.clearEntityData();
        assertEquals(Long.MIN_VALUE, ZAUtil.getLastClutchTick(playerId));
        assertFalse(ZAUtil.hasActiveAdrenaline(playerId, triggerTick + 100L));
    }

    @Test
    void adrenalineSurgeGrantsTwelveSecondsAndLifeOnHostileKillOnlyWhileActive() {
        UUID playerId = UUID.randomUUID();
        long startTick = 500L;
        ZAUtil.recordClutchAndAdrenaline(playerId, startTick);

        assertEquals(240, ZAUtil.ADRENALINE_SURGE_DURATION_TICKS);
        assertEquals(2.0F, ZAUtil.ADRENALINE_LIFE_ON_KILL_HEAL, 1e-6f);

        assertTrue(ZAUtil.hasActiveAdrenaline(playerId, 500L));
        assertTrue(ZAUtil.hasActiveAdrenaline(playerId, 739L));
        assertFalse(ZAUtil.hasActiveAdrenaline(playerId, 740L));

        assertEquals(2.0F, ZAUtil.computeAdrenalineKillHeal(true, ZAUtil.hasActiveAdrenaline(playerId, 600L)), 1e-6f);
        assertEquals(0.0F, ZAUtil.computeAdrenalineKillHeal(false, ZAUtil.hasActiveAdrenaline(playerId, 600L)), 1e-6f);
        assertEquals(0.0F, ZAUtil.computeAdrenalineKillHeal(true, ZAUtil.hasActiveAdrenaline(playerId, 740L)), 1e-6f);
    }

    @Test
    void eliteHordeTagsExemptFromSuppressionRestoredMultiBundleMineralLootAndCloneGuard() {
        List<String> supportTags = List.of("za_screamer", "za_tnt_sapper", ZAUtil.ENGINEER_TAG);
        List<String> frontlineTags = List.of(ZAUtil.TANKER_TAG, ZAUtil.KNIGHT_TAG);
        List<String> allEliteTags = List.of(
                ZAUtil.TANKER_TAG,
                ZAUtil.KNIGHT_TAG,
                "za_juggernaut",
                ZAUtil.ENGINEER_TAG,
                "za_tnt_sapper",
                "za_screamer",
                ZAUtil.BLOODMOON_WARDEN_TAG
        );

        for (String tag : allEliteTags) {
            assertTrue(ZAUtil.hasEliteHordeTag(Set.of(tag)));
            assertTrue(ZAUtil.hasHordeMobTag(Set.of(tag)));
            assertTrue(ZAUtil.shouldHordeMobDropLoot(true, true, 0.99F));
            assertFalse(ZAUtil.getEliteMineralDropTable(Set.of(tag)).isEmpty());
        }

        // Standard horde mobs remain subject to 80% drop suppression
        assertFalse(ZAUtil.hasEliteHordeTag(Set.of("za_horde_mob")));
        assertFalse(ZAUtil.shouldHordeMobDropLoot(true, false, 0.99F));

        // Damage-clone 25% drop multiplier & vanilla loot rates
        assertEquals(0.25D, ZAUtil.DAMAGE_CLONE_LOOT_MULTIPLIER, 1e-6);
        assertEquals(1.0D, ZAUtil.computeLootDropRate(false, false, false), 1e-6);
        assertEquals(0.25D, ZAUtil.computeLootDropRate(false, false, true), 1e-6);
        assertEquals(ZAUtil.HORDE_MOB_LOOT_DROP_CHANCE, ZAUtil.computeLootDropRate(true, false, false), 1e-6);
        assertEquals(0.25D, ZAUtil.computeLootDropRate(true, false, true), 1e-6);
        assertEquals(1.0D, ZAUtil.computeLootDropRate(true, true, false), 1e-6);
        assertEquals(0.25D, ZAUtil.computeLootDropRate(true, true, true), 1e-6);

        assertTrue(ZAUtil.shouldHordeMobDropLoot(false, false, true, 0.249F));
        assertFalse(ZAUtil.shouldHordeMobDropLoot(false, false, true, 0.25F));
        assertTrue(ZAUtil.shouldHordeMobDropLoot(true, true, true, 0.249F));
        assertFalse(ZAUtil.shouldHordeMobDropLoot(true, true, true, 0.25F));

        // Elite mineral loot eligibility & 25% clone rate
        assertTrue(ZAUtil.isEligibleForEliteMineralLoot(true, false, 0.99D));
        assertTrue(ZAUtil.isEligibleForEliteMineralLoot(true, true, 0.249D));
        assertFalse(ZAUtil.isEligibleForEliteMineralLoot(true, true, 0.25D));
        assertFalse(ZAUtil.isEligibleForEliteMineralLoot(false, false, 0.0D));

        Random cloneRng = new Random(404L);
        int cloneWithDrops = 0;
        for (int i = 0; i < 400; i++) {
            List<ZAUtil.EliteDropRoll> cloneRolls = ZAUtil.rollEliteMineralDrops(
                    Set.of("za_horde_mob", "za_juggernaut", ZAUtil.DAMAGE_CLONE_TAG),
                    cloneRng
            );
            if (!cloneRolls.isEmpty()) {
                cloneWithDrops++;
            }
        }
        assertTrue(cloneWithDrops >= 70 && cloneWithDrops <= 135,
                "Expected ~25% (100/400) elite clone mineral drop rate, got " + cloneWithDrops);

        // Support elites: guaranteed 5-bundle crafting materials, strictly 0% diamonds
        Random supportRng = new Random(123L);
        for (String tag : supportTags) {
            List<ZAUtil.EliteDropRoll> rolls = ZAUtil.rollEliteMineralDrops(Set.of("za_horde_mob", tag), supportRng);
            Map<String, Integer> counts = rolls.stream()
                    .collect(Collectors.toMap(ZAUtil.EliteDropRoll::itemId, ZAUtil.EliteDropRoll::count));
            assertEquals(5, rolls.size(), "Support elite should drop all 5 crafting bundles");
            assertTrue(counts.get("minecraft:iron_ingot") >= 2 && counts.get("minecraft:iron_ingot") <= 4);
            assertTrue(counts.get("minecraft:copper_ingot") >= 2 && counts.get("minecraft:copper_ingot") <= 4);
            assertTrue(counts.get("minecraft:gunpowder") >= 2 && counts.get("minecraft:gunpowder") <= 4);
            assertTrue(counts.get("minecraft:coal") >= 2 && counts.get("minecraft:coal") <= 5);
            assertTrue(counts.get("minecraft:redstone") >= 2 && counts.get("minecraft:redstone") <= 4);
            assertFalse(counts.containsKey("minecraft:diamond"));
        }

        // Frontline (Tanker/Knight): multi-bundle metals + gunpowder + ~12% single diamond
        Random frontlineRng = new Random(77L);
        int frontlineDiamonds = 0;
        for (int i = 0; i < 200; i++) {
            String tag = frontlineTags.get(i % frontlineTags.size());
            List<ZAUtil.EliteDropRoll> rolls = ZAUtil.rollEliteMineralDrops(Set.of("za_horde_mob", tag), frontlineRng);
            Map<String, Integer> counts = rolls.stream()
                    .collect(Collectors.toMap(ZAUtil.EliteDropRoll::itemId, ZAUtil.EliteDropRoll::count));
            assertTrue(counts.get("minecraft:iron_ingot") >= 4 && counts.get("minecraft:iron_ingot") <= 7);
            assertTrue(counts.get("minecraft:copper_ingot") >= 3 && counts.get("minecraft:copper_ingot") <= 6);
            assertTrue(counts.get("minecraft:gold_ingot") >= 2 && counts.get("minecraft:gold_ingot") <= 4);
            assertTrue(counts.get("minecraft:gunpowder") >= 2 && counts.get("minecraft:gunpowder") <= 5);
            if (counts.containsKey("minecraft:diamond")) {
                assertEquals(1, counts.get("minecraft:diamond"));
                frontlineDiamonds++;
            }
        }
        assertTrue(frontlineDiamonds >= 10 && frontlineDiamonds <= 45, "Expected ~12% diamond rate over 200 frontline kills, got " + frontlineDiamonds);

        // Heavy (Juggernaut): multi-bundle iron/gold/redstone + ~25% single diamond
        Random jugRng = new Random(88L);
        int jugDiamonds = 0;
        for (int i = 0; i < 200; i++) {
            List<ZAUtil.EliteDropRoll> rolls = ZAUtil.rollEliteMineralDrops(Set.of("za_horde_mob", "za_juggernaut"), jugRng);
            Map<String, Integer> counts = rolls.stream()
                    .collect(Collectors.toMap(ZAUtil.EliteDropRoll::itemId, ZAUtil.EliteDropRoll::count));
            assertTrue(counts.get("minecraft:iron_ingot") >= 6 && counts.get("minecraft:iron_ingot") <= 10);
            assertTrue(counts.get("minecraft:gold_ingot") >= 3 && counts.get("minecraft:gold_ingot") <= 5);
            assertTrue(counts.get("minecraft:redstone") >= 4 && counts.get("minecraft:redstone") <= 8);
            if (counts.containsKey("minecraft:diamond")) {
                assertEquals(1, counts.get("minecraft:diamond"));
                jugDiamonds++;
            }
        }
        assertTrue(jugDiamonds >= 30 && jugDiamonds <= 75, "Expected ~25% diamond rate over 200 juggernaut kills, got " + jugDiamonds);
    }

    @Test
    void day40HordeDiamondWindowCapLimitsNonWardenDiamondsToThirtyTwoAndResetsCleanly() {
        assertEquals(32, ZAConfigZDay.maxBonusDiamondsPerHordeWindow);
        assertEquals(6000L, ZAUtil.ELITE_DIAMOND_WINDOW_TICKS);

        Random rng = new Random(999L);
        int nonWardenDiamonds = 0;
        int metalAndEmeraldDrops = 0;
        long startTick = 1000L;

        // Simulate 250 Day 40 Juggernaut + Tanker kills within a single 6000-tick horde window
        for (int i = 0; i < 250; i++) {
            String eliteTag = (i % 2 == 0) ? "za_juggernaut" : ZAUtil.TANKER_TAG;
            List<ZAUtil.EliteDropRoll> rolls = ZAUtil.rollEliteMineralDrops(
                    Set.of("za_horde_mob", eliteTag),
                    rng,
                    startTick + (i * 20L),
                    true
            );
            assertFalse(rolls.isEmpty());
            for (ZAUtil.EliteDropRoll roll : rolls) {
                if ("minecraft:diamond".equals(roll.itemId())) {
                    nonWardenDiamonds += roll.count();
                } else {
                    metalAndEmeraldDrops += roll.count();
                }
            }
        }

        assertEquals(32, nonWardenDiamonds, "250 Day 40 heavy/frontline kills should cap cleanly at 32 bonus diamonds");
        assertEquals(32, ZAUtil.getBonusDiamondsInCurrentWindow(startTick + 4980L));
        assertTrue(metalAndEmeraldDrops > 0, "Expected rich crafting drops + fallback conversions");

        // After 6000 ticks elapse, rolling window expires cleanly back to 0 used slots
        assertEquals(0, ZAUtil.getBonusDiamondsInCurrentWindow(startTick + 4980L + 6000L));

        // Bloodmoon Warden boss always drops full 5 boss bundles (2-4 diamonds + netherite scrap + echo shards + emeralds + gunpowder)
        Random wardenRng = new Random(2026L);
        for (int i = 0; i < 10; i++) {
            List<ZAUtil.EliteDropRoll> wardenDrops = ZAUtil.rollEliteMineralDrops(
                    Set.of("za_horde_mob", ZAUtil.BLOODMOON_WARDEN_TAG),
                    wardenRng,
                    startTick + 100L,
                    true
            );
            Map<String, Integer> counts = new HashMap<>();
            for (ZAUtil.EliteDropRoll roll : wardenDrops) {
                counts.merge(roll.itemId(), roll.count(), Integer::sum);
            }
            assertEquals(5, wardenDrops.size());
            int scrap = counts.getOrDefault("minecraft:netherite_scrap", 0);
            int d = counts.getOrDefault("minecraft:diamond", 0);
            int echo = counts.getOrDefault("minecraft:echo_shard", 0);
            int em = counts.getOrDefault("minecraft:emerald", 0);
            int gp = counts.getOrDefault("minecraft:gunpowder", 0);
            assertTrue(scrap >= 1 && scrap <= 2);
            assertTrue(d >= 2 && d <= 4, "Warden should drop 2-4 diamonds, got " + d);
            assertTrue(echo >= 2 && echo <= 4);
            assertTrue(em >= 4 && em <= 6);
            assertTrue(gp >= 6 && gp <= 10);
        }

        // ZombieAwareness.clearEntityData() clears diamond window tracking
        ZombieAwareness.clearEntityData();
        assertEquals(0, ZAUtil.getBonusDiamondsInCurrentWindow(startTick + 100L));
    }

    @Test
    void normalHordeMobsDropExtraGunpowderScaledForClones() {
        assertEquals(0.50D, ZAUtil.NORMAL_HORDE_GUNPOWDER_DROP_CHANCE, 1e-6);

        // Normal (non-elite) horde mob: 50% chance for 1-3 gunpowder
        assertEquals(1, ZAUtil.rollNormalHordeGunpowderDrop(true, false, false, 0.0D, 0));
        assertEquals(2, ZAUtil.rollNormalHordeGunpowderDrop(true, false, false, 0.25D, 1));
        assertEquals(3, ZAUtil.rollNormalHordeGunpowderDrop(true, false, false, 0.4999D, 2));
        assertEquals(0, ZAUtil.rollNormalHordeGunpowderDrop(true, false, false, 0.50D, 2));

        // Normal horde damage clone: scaled by 0.25x -> 12.5% chance
        assertEquals(2, ZAUtil.rollNormalHordeGunpowderDrop(true, false, true, 0.1249D, 1));
        assertEquals(0, ZAUtil.rollNormalHordeGunpowderDrop(true, false, true, 0.125D, 1));

        // Non-horde wild mobs & elite horde mobs receive 0 from normal-horde gunpowder bonus
        assertEquals(0, ZAUtil.rollNormalHordeGunpowderDrop(false, false, false, 0.0D, 0));
        assertEquals(0, ZAUtil.rollNormalHordeGunpowderDrop(true, true, false, 0.0D, 0));
    }
}
