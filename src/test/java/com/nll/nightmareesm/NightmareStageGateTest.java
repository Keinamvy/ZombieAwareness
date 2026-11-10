package com.nll.nightmareesm;

import net.minecraft.world.entity.EntitySpawnReason;
import com.nll.nightmareesm.MobBehavs.MobDigDown;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class NightmareStageGateTest {

   private static final List<String> ALL_BEHAVIORS = List.of(
      "BLACKOUT_STALKER",
      "PICKAXE_DIG",
      "SPIDER_WEB",
      "BLOODLUST",
      "SCREAMER_SCOUT",
      "BRIDGE_PILLAR",
      "JUGGERNAUT_CRAWLER",
      "TNT_SAPPER",
      "CREEPER_BREACH",
      "START_FIRES"
   );

   @Test
   @DisplayName("Stages 1 and 2 disallow all siege behaviors")
   void testStage1And2DisallowAllBehaviors() {
      for (int stage : new int[]{0, 1, 2}) {
         for (String behavior : ALL_BEHAVIORS) {
            assertFalse(
               nightmareesm.canUseBehaviorForStage(behavior, stage),
               "Behavior " + behavior + " should be locked at stage " + stage
            );
         }
      }
   }

   @ParameterizedTest
   @ValueSource(ints = {3, 4})
   @DisplayName("Stages 3 and 4 allow BLACKOUT_STALKER only")
   void testBlackoutStalkerUnlockedAtStage3(int stage) {
      assertTrue(nightmareesm.canUseBehaviorForStage("BLACKOUT_STALKER", stage));
      assertFalse(nightmareesm.canUseBehaviorForStage("PICKAXE_DIG", stage));
      assertFalse(nightmareesm.canUseBehaviorForStage("SPIDER_WEB", stage));
      assertFalse(nightmareesm.canUseBehaviorForStage("BLOODLUST", stage));
      assertFalse(nightmareesm.canUseBehaviorForStage("SCREAMER_SCOUT", stage));
      assertFalse(nightmareesm.canUseBehaviorForStage("BRIDGE_PILLAR", stage));
      assertFalse(nightmareesm.canUseBehaviorForStage("JUGGERNAUT_CRAWLER", stage));
      assertFalse(nightmareesm.canUseBehaviorForStage("TNT_SAPPER", stage));
      assertFalse(nightmareesm.canUseBehaviorForStage("CREEPER_BREACH", stage));
      assertFalse(nightmareesm.canUseBehaviorForStage("START_FIRES", stage));
   }

   @Test
   @DisplayName("Stage 5 unlocks PICKAXE_DIG")
   void testPickaxeDigUnlockedAtStage5() {
      assertFalse(nightmareesm.canUseBehaviorForStage("PICKAXE_DIG", 4));
      assertTrue(nightmareesm.canUseBehaviorForStage("PICKAXE_DIG", 5));
      assertFalse(nightmareesm.canUseBehaviorForStage("SPIDER_WEB", 5));
      assertFalse(nightmareesm.canUseBehaviorForStage("BLOODLUST", 5));
   }

   @Test
   @DisplayName("Stage 6 unlocks SPIDER_WEB and BLOODLUST")
   void testSpiderWebAndBloodlustUnlockedAtStage6() {
      assertFalse(nightmareesm.canUseBehaviorForStage("SPIDER_WEB", 5));
      assertFalse(nightmareesm.canUseBehaviorForStage("BLOODLUST", 5));

      assertTrue(nightmareesm.canUseBehaviorForStage("SPIDER_WEB", 6));
      assertTrue(nightmareesm.canUseBehaviorForStage("BLOODLUST", 6));

      assertFalse(nightmareesm.canUseBehaviorForStage("SCREAMER_SCOUT", 6));
   }

   @Test
   @DisplayName("Stage 7 unlocks SCREAMER_SCOUT")
   void testScreamerScoutUnlockedAtStage7() {
      assertFalse(nightmareesm.canUseBehaviorForStage("SCREAMER_SCOUT", 6));
      assertTrue(nightmareesm.canUseBehaviorForStage("SCREAMER_SCOUT", 7));
      assertFalse(nightmareesm.canUseBehaviorForStage("BRIDGE_PILLAR", 7));
   }

   @Test
   @DisplayName("Stage 8 unlocks BRIDGE_PILLAR")
   void testBridgePillarUnlockedAtStage8() {
      assertFalse(nightmareesm.canUseBehaviorForStage("BRIDGE_PILLAR", 7));
      assertTrue(nightmareesm.canUseBehaviorForStage("BRIDGE_PILLAR", 8));
      assertFalse(nightmareesm.canUseBehaviorForStage("JUGGERNAUT_CRAWLER", 8));
   }

   @Test
   @DisplayName("Stage 9 unlocks JUGGERNAUT_CRAWLER")
   void testJuggernautCrawlerUnlockedAtStage9() {
      assertFalse(nightmareesm.canUseBehaviorForStage("JUGGERNAUT_CRAWLER", 8));
      assertTrue(nightmareesm.canUseBehaviorForStage("JUGGERNAUT_CRAWLER", 9));
      assertFalse(nightmareesm.canUseBehaviorForStage("TNT_SAPPER", 9));
      assertFalse(nightmareesm.canUseBehaviorForStage("CREEPER_BREACH", 9));
      assertFalse(nightmareesm.canUseBehaviorForStage("START_FIRES", 9));
   }

   @Test
   @DisplayName("Stage 10 Demolition Sappers unlocks TNT, Creeper breaching, and Fires")
   void testStage10CollapseEnablesAllSiegeBehaviors() {
      for (String behavior : ALL_BEHAVIORS) {
         assertTrue(
            nightmareesm.canUseBehaviorForStage(behavior, 10),
            "Behavior " + behavior + " must be unlocked at Stage 10"
         );
      }
   }

   @Test
   @DisplayName("Unknown or null behaviors return false for all stages")
   void testUnknownAndNullBehaviorsReturnFalse() {
      for (int stage = 1; stage <= 10; stage++) {
         assertFalse(nightmareesm.canUseBehaviorForStage(null, stage));
         assertFalse(nightmareesm.canUseBehaviorForStage("", stage));
         assertFalse(nightmareesm.canUseBehaviorForStage("UNKNOWN_BEHAVIOR", stage));
         assertFalse(nightmareesm.canUseBehaviorForStage("NUCLEAR_LAUNCH", stage));
      }
   }

   @Test
   @DisplayName("Behavior key matching is case-insensitive")
   void testCaseInsensitivity() {
      assertTrue(nightmareesm.canUseBehaviorForStage("blackout_stalker", 3));
      assertTrue(nightmareesm.canUseBehaviorForStage("Pickaxe_Dig", 5));
      assertTrue(nightmareesm.canUseBehaviorForStage("spider_web", 6));
      assertTrue(nightmareesm.canUseBehaviorForStage("BloodLust", 6));
      assertTrue(nightmareesm.canUseBehaviorForStage("screamer_scout", 7));
      assertTrue(nightmareesm.canUseBehaviorForStage("Bridge_Pillar", 8));
      assertTrue(nightmareesm.canUseBehaviorForStage("Juggernaut_Crawler", 9));
      assertTrue(nightmareesm.canUseBehaviorForStage("tnt_sapper", 10));
      assertTrue(nightmareesm.canUseBehaviorForStage("creeper_breach", 10));
      assertTrue(nightmareesm.canUseBehaviorForStage("start_fires", 10));
   }

   @Test
   @DisplayName("canRollAsScreamer blocks MOB_SUMMONED and REINFORCEMENT clones while allowing NATURAL and EVENT at stage 7+")
   void testCanRollAsScreamer() {
      // Locked before stage 7
      for (int stage = 0; stage < 7; stage++) {
         assertFalse(nightmareesm.canRollAsScreamer(EntitySpawnReason.NATURAL, stage));
         assertFalse(nightmareesm.canRollAsScreamer(EntitySpawnReason.EVENT, stage));
         assertFalse(nightmareesm.canRollAsScreamer(EntitySpawnReason.MOB_SUMMONED, stage));
         assertFalse(nightmareesm.canRollAsScreamer(EntitySpawnReason.REINFORCEMENT, stage));
      }

      // At stage 7+: clone / summon reasons blocked to avoid scream duplicate cascades
      for (int stage : new int[]{7, 8, 9, 10}) {
         assertFalse(nightmareesm.canRollAsScreamer(EntitySpawnReason.MOB_SUMMONED, stage),
            "MOB_SUMMONED must not become a screamer at stage " + stage);
         assertFalse(nightmareesm.canRollAsScreamer(EntitySpawnReason.REINFORCEMENT, stage),
            "REINFORCEMENT must not become a screamer at stage " + stage);

         assertTrue(nightmareesm.canRollAsScreamer(EntitySpawnReason.NATURAL, stage),
            "NATURAL spawn can roll as screamer at stage " + stage);
         assertTrue(nightmareesm.canRollAsScreamer(EntitySpawnReason.EVENT, stage),
            "EVENT spawn can roll as screamer at stage " + stage);
         assertTrue(nightmareesm.canRollAsScreamer(EntitySpawnReason.SPAWNER, stage),
            "SPAWNER spawn can roll as screamer at stage " + stage);
      }
   }

   @Test
   @DisplayName("GetNearestTarget.isValidSurvivalPlayerTarget only targets non-creative, non-spectator survival players")
   void testIsValidSurvivalPlayerTarget() {
      assertTrue(GetNearestTarget.isValidSurvivalPlayerTarget(false, false),
         "Survival player (!creative && !spectator) must be a valid target");
      assertFalse(GetNearestTarget.isValidSurvivalPlayerTarget(true, false),
         "Creative player must NOT be a valid target");
      assertFalse(GetNearestTarget.isValidSurvivalPlayerTarget(false, true),
         "Spectator player must NOT be a valid target");
      assertFalse(GetNearestTarget.isValidSurvivalPlayerTarget(true, true),
         "Creative spectator player must NOT be a valid target");
   }

   @Test
   @DisplayName("MobDigDown.computeHorizontalDistance accurately calculates distance across all quadrants and offsets")
   void testComputeHorizontalDistance() {
      // Basic 3-4-5 right triangle
      assertEquals(5.0, MobDigDown.computeHorizontalDistance(0.0, 0.0, 3.0, 4.0), 1e-6);

      // Same position
      assertEquals(0.0, MobDigDown.computeHorizontalDistance(100.5, -200.5, 100.5, -200.5), 1e-6);

      // Negative coordinates in same quadrant
      assertEquals(5.0, MobDigDown.computeHorizontalDistance(-10.0, -20.0, -13.0, -24.0), 1e-6);

      // Across quadrants (+, +) to (-, -)
      assertEquals(10.0, MobDigDown.computeHorizontalDistance(3.0, 4.0, -3.0, -4.0), 1e-6);

      // Quadrant offset (+, -) to (-, +)
      assertEquals(Math.sqrt(200.0), MobDigDown.computeHorizontalDistance(5.0, -5.0, -5.0, 5.0), 1e-6);
   }

   @Test
   @DisplayName("ZAUtil portal block check and friendly fire handling null safety")
   void testZAUtilHelpers() {
      assertFalse(com.corosus.zombieawareness.ZAUtil.isPortalBlock(null));
      assertFalse(com.corosus.zombieawareness.ZAUtil.shouldBlockHostileFriendlyFireOrAggro(null, null));
   }

   @Test
   @DisplayName("Super-late-game Stage 10 Apex Horde phase resolution and ZAUtil apex corrosive mechanics")
   void testApexCorrosiveStageGateAndMechanics() {
      assertEquals(com.corosus.zombieawareness.heat.ChunkHeatManager.HordePhase.PHASE_5_APEX_CORROSION,
         com.corosus.zombieawareness.heat.ChunkHeatManager.resolveHordePhase(0.95, 10));
      assertEquals(com.corosus.zombieawareness.heat.ChunkHeatManager.HordePhase.PHASE_3_JUGGERNAUTS_WITCHES,
         com.corosus.zombieawareness.heat.ChunkHeatManager.resolveHordePhase(0.95, 9));
      assertEquals(com.corosus.zombieawareness.heat.ChunkHeatManager.HordePhase.PHASE_4_CLIMAX_SAPPERS,
         com.corosus.zombieawareness.heat.ChunkHeatManager.resolveHordePhase(0.90, 10));

      float[] split = com.corosus.zombieawareness.ZAUtil.splitApexArmorBypassDamage(20.0f);
      assertEquals(12.0f, split[0], 1e-4f);
      assertEquals(8.0f, split[1], 1e-4f);

      java.util.UUID uuid = java.util.UUID.randomUUID();
      com.corosus.zombieawareness.ZAUtil.clearNecroticTrauma();
      assertEquals(20.0f, com.corosus.zombieawareness.ZAUtil.computeModifiedHealAmount(uuid, 100L, 20.0f));
      com.corosus.zombieawareness.ZAUtil.applyNecroticTrauma(uuid, 100L);
      assertTrue(com.corosus.zombieawareness.ZAUtil.hasNecroticTrauma(uuid, 100L));
      assertEquals(10.0f, com.corosus.zombieawareness.ZAUtil.computeModifiedHealAmount(uuid, 100L, 20.0f));
      assertFalse(com.corosus.zombieawareness.ZAUtil.hasNecroticTrauma(uuid, 261L));
      assertEquals(20.0f, com.corosus.zombieawareness.ZAUtil.computeModifiedHealAmount(uuid, 261L, 20.0f));

      assertEquals(20, com.corosus.zombieawareness.ZAUtil.computeArmorDurabilityShred(400));
      assertEquals(12, com.corosus.zombieawareness.ZAUtil.computeArmorDurabilityShred(100));
   }

   @Test
   @DisplayName("Monster distress scent gate/strength, phantom aerial horde support, and horde mob loot reduction")
   void testMonsterDistressScentPhantomAerialHordeAndLootReductionStageGate() {
      // 1. Monster Distress Scent Gate (Stage 5+) & Strength Scaling (80..140)
      for (int stage = 0; stage <= 4; stage++) {
         assertFalse(com.corosus.zombieawareness.ZAUtil.canSpawnMonsterDistressScent(stage));
         assertEquals(0, com.corosus.zombieawareness.ZAUtil.computeMonsterDistressScentStrength(stage));
      }
      for (int stage = 5; stage <= 10; stage++) {
         assertTrue(com.corosus.zombieawareness.ZAUtil.canSpawnMonsterDistressScent(stage));
      }
      assertEquals(80, com.corosus.zombieawareness.ZAUtil.computeMonsterDistressScentStrength(5));
      assertEquals(140, com.corosus.zombieawareness.ZAUtil.computeMonsterDistressScentStrength(10));
      assertEquals(140, com.corosus.zombieawareness.ZAUtil.computeMonsterDistressScentStrength(12));

      // 2. Horde Phantom Aerial Spawning Gate & Y Offset
      for (int stage = 1; stage <= 4; stage++) {
         for (com.corosus.zombieawareness.heat.ChunkHeatManager.HordePhase phase : com.corosus.zombieawareness.heat.ChunkHeatManager.HordePhase.values()) {
            assertFalse(com.corosus.zombieawareness.heat.ChunkHeatManager.canHordeSpawnPhantoms(phase, stage));
         }
      }
      assertFalse(com.corosus.zombieawareness.heat.ChunkHeatManager.canHordeSpawnPhantoms(com.corosus.zombieawareness.heat.ChunkHeatManager.HordePhase.PHASE_1A_FAST_ZOMBIES, 5));
      assertFalse(com.corosus.zombieawareness.heat.ChunkHeatManager.canHordeSpawnPhantoms(com.corosus.zombieawareness.heat.ChunkHeatManager.HordePhase.PHASE_1B_VENOM_SPIDERS, 5));
      assertTrue(com.corosus.zombieawareness.heat.ChunkHeatManager.canHordeSpawnPhantoms(com.corosus.zombieawareness.heat.ChunkHeatManager.HordePhase.PHASE_2_BREACHERS_SKELETONS, 5));
      assertTrue(com.corosus.zombieawareness.heat.ChunkHeatManager.canHordeSpawnPhantoms(com.corosus.zombieawareness.heat.ChunkHeatManager.HordePhase.PHASE_3_JUGGERNAUTS_WITCHES, 7));
      assertTrue(com.corosus.zombieawareness.heat.ChunkHeatManager.canHordeSpawnPhantoms(com.corosus.zombieawareness.heat.ChunkHeatManager.HordePhase.PHASE_4_CLIMAX_SAPPERS, 9));
      assertTrue(com.corosus.zombieawareness.heat.ChunkHeatManager.canHordeSpawnPhantoms(com.corosus.zombieawareness.heat.ChunkHeatManager.HordePhase.PHASE_5_APEX_CORROSION, 10));

      assertEquals(80, com.corosus.zombieawareness.heat.ChunkHeatManager.computePhantomSpawnY(64));

      // 3. 80% Drop Reduction for Horde Mobs
      assertTrue(com.corosus.zombieawareness.ZAUtil.shouldHordeMobDropLoot(false, 0.99f));
      assertTrue(com.corosus.zombieawareness.ZAUtil.shouldHordeMobDropLoot(true, 0.10f));
      assertFalse(com.corosus.zombieawareness.ZAUtil.shouldHordeMobDropLoot(true, 0.20f));
      assertFalse(com.corosus.zombieawareness.ZAUtil.shouldHordeMobDropLoot(true, 0.50f));
   }
}
