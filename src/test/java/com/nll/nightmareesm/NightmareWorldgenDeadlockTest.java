package com.nll.nightmareesm;

import com.nll.nightmareesm.MobBehavs.CreeperBreachWalls;
import com.nll.nightmareesm.MobBehavs.MobBreakLightSources;
import com.nll.nightmareesm.MobBehavs.MobScreamerAlert;
import com.nll.nightmareesm.helpers.ConfigHelper;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class NightmareWorldgenDeadlockTest {

   @Test
   @DisplayName("Duplication is blocked during WorldGenRegion (non-ServerLevel) or off-thread finalizeSpawn")
   void testBlocksWorldGenRegionAndOffThread() {
      assertFalse(
         nightmareesm.canDuplicateOnFinalizeSpawn(EntitySpawnReason.NATURAL, false, true),
         "WorldGenRegion (non-ServerLevel) must never duplicate synchronously"
      );
      assertFalse(
         nightmareesm.canDuplicateOnFinalizeSpawn(EntitySpawnReason.NATURAL, true, false),
         "Off-thread (e.g. C2ME worker) calls must never duplicate synchronously"
      );
      assertFalse(
         nightmareesm.canDuplicateOnFinalizeSpawn(EntitySpawnReason.SPAWNER, false, false),
         "Worldgen spawner off-thread must not duplicate synchronously"
      );
   }

   @Test
   @DisplayName("Duplication is blocked for STRUCTURE, CHUNK_GENERATION, and MOB_SUMMONED spawn reasons")
   void testBlocksStructureChunkGenAndSummoned() {
      assertFalse(
         nightmareesm.canDuplicateOnFinalizeSpawn(EntitySpawnReason.STRUCTURE, true, true),
         "STRUCTURE spawns (e.g. Ocean Ruins) must not duplicate during finalizeSpawn"
      );
      assertFalse(
         nightmareesm.canDuplicateOnFinalizeSpawn(EntitySpawnReason.CHUNK_GENERATION, true, true),
         "CHUNK_GENERATION spawns must not duplicate during finalizeSpawn"
      );
      assertFalse(
         nightmareesm.canDuplicateOnFinalizeSpawn(EntitySpawnReason.MOB_SUMMONED, true, true),
         "MOB_SUMMONED clones must not recursively duplicate"
      );
   }

   @Test
   @DisplayName("Duplication is allowed for live ServerLevel natural spawns on the server thread")
   void testAllowsLiveServerThreadNaturalSpawn() {
      assertTrue(
         nightmareesm.canDuplicateOnFinalizeSpawn(EntitySpawnReason.NATURAL, true, true),
         "Live ServerLevel NATURAL spawns on the server thread should still duplicate"
      );
      assertTrue(
         nightmareesm.canDuplicateOnFinalizeSpawn(EntitySpawnReason.REINFORCEMENT, true, true),
         "Live ServerLevel REINFORCEMENT spawns on the server thread should be allowed"
      );
   }

   @Test
   @DisplayName("onFinalizeSpawn safety guards block worldgen spawn reasons and null/non-live levels")
   void testFinalizeSpawnAndScreamerWorldgenGuards() {
      assertFalse(nightmareesm.isSafeLiveServerFinalizeSpawn(null, true));
      assertFalse(nightmareesm.isSafeLiveServerFinalizeSpawn(null, false));
      assertDoesNotThrow(() -> nightmareesm.onFinalizeSpawn(null, EntitySpawnReason.STRUCTURE));
      assertDoesNotThrow(() -> nightmareesm.onFinalizeSpawn(null, EntitySpawnReason.NATURAL, 10, true));

      assertFalse(nightmareesm.canRollAsScreamer(null, 10));
      assertFalse(nightmareesm.canRollAsScreamer(EntitySpawnReason.STRUCTURE, 10));
      assertFalse(nightmareesm.canRollAsScreamer(EntitySpawnReason.CHUNK_GENERATION, 10));
      assertFalse(nightmareesm.canRollAsScreamer(EntitySpawnReason.MOB_SUMMONED, 10));
      assertFalse(nightmareesm.canRollAsScreamer(EntitySpawnReason.REINFORCEMENT, 10));
      assertTrue(nightmareesm.canRollAsScreamer(EntitySpawnReason.NATURAL, 10));
      assertTrue(nightmareesm.canRollAsScreamer(EntitySpawnReason.SPAWNER, 10));
   }

   @Test
   @DisplayName("MobScreamerAlert handles cooldowns, world-switch clock wraps, and resets")
   void testMobScreamerAlertClockWrapAndReset() {
      assertFalse(MobScreamerAlert.shouldThrottleScream(0L, -999999L));
      assertFalse(MobScreamerAlert.shouldThrottleScream(100L, -1L));
      assertTrue(MobScreamerAlert.shouldThrottleScream(150L, 100L));
      assertFalse(MobScreamerAlert.shouldThrottleScream(200L, 100L));
      assertFalse(
         MobScreamerAlert.shouldThrottleScream(50L, 500000L),
         "New world with smaller gameTime must not be throttled by prior world's scream tick"
      );

      MobScreamerAlert.setLastGlobalScreamTickForTest(500000L);
      assertEquals(500000L, MobScreamerAlert.getLastGlobalScreamTick());
      MobScreamerAlert.resetLastGlobalScreamTick();
      assertTrue(MobScreamerAlert.getLastGlobalScreamTick() < 0L);
   }

   @Test
   @DisplayName("ConfigHelper fast whitelist caching, epoch invalidation, and ESMConfig list immutability")
   void testConfigHelperEmptyListAndESMConfigImmutability() {
      assertFalse(ConfigHelper.ConfigHasValue(null, "zombie", false));
      assertFalse(ConfigHelper.ConfigHasValue(ESMConfig.DuplicatedMobs, (String) null, false));
      assertNull(ConfigHelper.GetEntityTypeFromName(null));
      assertNull(ConfigHelper.GetEntityTypeFromName("  "));

      assertTrue(ConfigHelper.matchesPreNormalized("oak_planks", new String[]{"plank", "stair"}, true));
      assertFalse(ConfigHelper.matchesPreNormalized("stone", new String[]{"plank", "stair"}, true));
      assertTrue(ConfigHelper.matchesPreNormalized("zombie", new String[]{"zombie", "creeper"}, false));
      assertFalse(ConfigHelper.matchesPreNormalized("zombie_villager", new String[]{"zombie", "creeper"}, false));

      List<? extends String> original = ESMConfig.DuplicatedMobs.get();
      int epochBefore = ESMConfig.getConfigEpoch();
      try {
         List<String> mutable = new ArrayList<>();
         ESMConfig.DuplicatedMobs.set(mutable);
         assertTrue(ESMConfig.getConfigEpoch() > epochBefore, "Updating config value must increment config epoch");
         assertFalse(ConfigHelper.ConfigHasValue(ESMConfig.DuplicatedMobs, "zombie", false));

         mutable.add("zombie");
         assertTrue(
            ESMConfig.DuplicatedMobs.get().isEmpty(),
            "ESMConfig list must be stored as an immutable snapshot isolated from caller mutations"
         );
         assertThrows(UnsupportedOperationException.class, () -> ESMConfig.DuplicatedMobs.get().clear());

         ESMConfig.DuplicatedMobs.set(List.of("creeper", "zombie_villager"));
         assertTrue(ConfigHelper.ConfigHasValue(ESMConfig.DuplicatedMobs, "Zombie Villager", false));
         assertFalse(ConfigHelper.ConfigHasValue(ESMConfig.DuplicatedMobs, "zombie", false));
      } finally {
         ESMConfig.DuplicatedMobs.set(original);
      }
   }

   @Test
   @DisplayName("LivingMobTick and MobBreakLightSources stagger work evenly across ticks by entity ID")
   void testStaggeredTickAndLightScanCadence() {
      int[] activePerTick = new int[20];
      for (int id = 0; id < 200; id++) {
         int fireCount = 0;
         for (long t = 0; t < 20; t++) {
            if (nightmareesm.isStaggeredTick(t, id)) {
               activePerTick[(int) t]++;
               fireCount++;
            }
         }
         assertEquals(1, fireCount, "Entity " + id + " must tick exactly once per 20-tick window");
      }
      for (int t = 0; t < 20; t++) {
         assertEquals(10, activePerTick[t], "Tick " + t + " should receive an equal 1/20th share of mobs");
      }

      int lightScans = 0;
      for (long t = 0; t < 80; t++) {
         if (MobBreakLightSources.shouldScanLightThisTick(t, 42)) {
            lightScans++;
         }
      }
      assertEquals(1, lightScans, "Light source scan must run once per 80 ticks per mob");
   }

   @Test
   @DisplayName("GetBlockingBlock, MobBreakLightSources, and MobInfo guards reject client, null, and unloaded positions")
   void testChunkAndNullGuards() {
      assertTrue(GetBlockingBlock.canQueryBlock(true, false, true));
      assertFalse(GetBlockingBlock.canQueryBlock(false, false, true));
      assertFalse(GetBlockingBlock.canQueryBlock(true, true, true));
      assertFalse(GetBlockingBlock.canQueryBlock(true, false, false));

      assertTrue(MobBreakLightSources.canInspectLightBlock(true, false, true));
      assertFalse(MobBreakLightSources.canInspectLightBlock(false, false, true));
      assertFalse(MobBreakLightSources.canInspectLightBlock(true, true, true));
      assertFalse(MobBreakLightSources.canInspectLightBlock(true, false, false));

      assertFalse(MobInfo.isServerPosLoaded(null, null));
      assertFalse(MobInfo.isSameDimension(null, null));
      assertNull(MobInfo.GetMobServerLevel((Mob) null));
      assertNull(MobInfo.GetMobLevel((Mob) null));
      assertFalse(MobBreakLightSources.tryBreakNearbyLight(null));
      assertFalse(new CannotReachPlayer().CannotReach(null));
      assertDoesNotThrow(CreeperBreachWalls::clearTickMap);
   }
}
