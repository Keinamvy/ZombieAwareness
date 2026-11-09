package com.nll.nightmareesm;

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
}
