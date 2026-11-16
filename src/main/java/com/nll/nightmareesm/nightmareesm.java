package com.nll.nightmareesm;

import com.nll.nightmareesm.Base.NightmareCreeper;
import com.nll.nightmareesm.Base.NightmareSkeleton;
import com.nll.nightmareesm.Base.NightmareSpider;
import com.nll.nightmareesm.Base.NightmareZombie;
import com.nll.nightmareesm.MobBehavs.CreeperBreachWalls;
import com.nll.nightmareesm.MobBehavs.MobBloodlust;
import com.nll.nightmareesm.MobBehavs.MobBreakLightSources;
import com.nll.nightmareesm.MobBehavs.MobBuildBridge;
import com.nll.nightmareesm.MobBehavs.MobBuildUp;
import com.nll.nightmareesm.MobBehavs.MobDig;
import com.nll.nightmareesm.MobBehavs.MobDigDown;
import com.nll.nightmareesm.MobBehavs.MobDigUp;
import com.nll.nightmareesm.MobBehavs.MobPlaceTNT;
import com.nll.nightmareesm.MobBehavs.MobScreamerAlert;
import com.nll.nightmareesm.MobBehavs.MobStartFires;
import com.nll.nightmareesm.MobBehavs.MobTargetPlayer;
import com.nll.nightmareesm.MobBehavs.SpiderShootWeb;
import com.nll.nightmareesm.helpers.ConfigHelper;
import java.util.Locale;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.spider.CaveSpider;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class nightmareesm implements ModInitializer {
   public static final String MODID = "nightmareesm";
   private static final Logger LOGGER = LoggerFactory.getLogger("nightmareesm");
   private static MinecraftServer currentServer;

   public static void init() {
      ESMConfig.loadConfig();
   }

   @Override
   public void onInitialize() {
      ServerLifecycleEvents.SERVER_STARTING.register(server -> {
         currentServer = server;
         MobScreamerAlert.resetLastGlobalScreamTick();
         MobPlaceTNT.clearCooldowns();
         CreeperBreachWalls.clearTickMap();
      });
      ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
         if (currentServer == server) {
            currentServer = null;
         }
         MobScreamerAlert.resetLastGlobalScreamTick();
         MobPlaceTNT.clearCooldowns();
         CreeperBreachWalls.clearTickMap();
      });
      init();
      LOGGER.info("Epic Siege Mod - Arming (Fabric 26.3)!");
   }

   public static MinecraftServer getCurrentServer() {
      return currentServer;
   }

   public static boolean ShouldDuplicate() {
      int RNG_Val = new RNG().GetInt(0, 100);
      return RNG_Val < (Integer)ESMConfig.DuplicationChance.get();
   }

   public static boolean canUseBehaviorForStage(String behaviorKey, int stageNumber) {
      if (behaviorKey == null) {
         return false;
      }
      switch (behaviorKey.toUpperCase(Locale.ROOT)) {
         case "BLACKOUT_STALKER":
            return stageNumber >= 3;
         case "PICKAXE_DIG":
            return stageNumber >= 5;
         case "SPIDER_WEB":
            return stageNumber >= 6;
         case "BLOODLUST":
            return stageNumber >= 6;
         case "SCREAMER_SCOUT":
            return stageNumber >= 7;
         case "BRIDGE_PILLAR":
            return false;
         case "JUGGERNAUT_CRAWLER":
            return stageNumber >= 9;
         case "TNT_SAPPER":
            return stageNumber >= 10;
         case "CREEPER_BREACH":
            return stageNumber >= 10;
         case "START_FIRES":
            return stageNumber >= 10;
         default:
            return false;
      }
   }

   public static void LivingMobTick(Entity Entity_Class) {
      LivingMobTick(Entity_Class, 1, false);
   }

   public static void LivingMobTick(Entity Entity_Class, int currentStage, boolean isBloodmoon) {
      if (Entity_Class instanceof Mob Entity_Mob) {
         if (Entity_Mob.entityTags().contains("za_crawler")) {
            Entity_Mob.setPose(net.minecraft.world.entity.Pose.SWIMMING);
         }
         Level Lvl = MobInfo.GetMobLevel(Entity_Mob);
         if (isTick(Entity_Mob) && (isBloodmoon || isSiegeDay(Entity_Mob) || Entity_Mob.entityTags().contains("za_horde_mob") || Entity_Mob.entityTags().contains("za_tnt_sapper")) && Lvl != null && !Lvl.isClientSide()) {
            new MobTargetPlayer(Entity_Class, isBloodmoon);

            if (Entity_Class instanceof Creeper C) {
               if (canUseBehaviorForStage("CREEPER_BREACH", currentStage) && (Boolean)ESMConfig.isCreeperBreachingAllowed.get()) {
                  new CreeperBreachWalls(C);
               }
               NightmareCreeper.CreeperTick(C);
            }

            if (Entity_Class instanceof Zombie zombie) {
               if (!(zombie instanceof net.minecraft.world.entity.monster.zombie.Drowned) && zombie.entityTags().contains("za_horde_mob")) {
                  if (zombie.isInWater() || zombie.isInShallowWater() || zombie.isUnderWater() || zombie.level().getFluidState(zombie.blockPosition()).is(net.minecraft.tags.FluidTags.WATER)) {
                     com.corosus.zombieawareness.heat.ChunkHeatManager.convertHordeZombieToDrowned(zombie);
                     return;
                  }
               }

               if (canUseBehaviorForStage("BLACKOUT_STALKER", currentStage)) {
                  MobBreakLightSources.tryBreakNearbyLight(zombie);
               }

               if (canUseBehaviorForStage("SCREAMER_SCOUT", currentStage)) {
                  MobScreamerAlert.tickScreamer(zombie);
               }

               if (canUseBehaviorForStage("PICKAXE_DIG", currentStage)) {
                  boolean isMobGriefingAllowed = (Boolean)ESMConfig.AllowZombieGriefing.get();
                  if (isMobGriefingAllowed) {
                     boolean isPickaxeRule = (Boolean)ESMConfig.AllowZombieGriefingOnlyIfPickaxe.get();
                     boolean isBlockedByPickaxeRule = false;
                     if (isPickaxeRule) {
                        isBlockedByPickaxeRule = true;
                        ItemStack I = Entity_Mob.getItemBySlot(EquipmentSlot.MAINHAND);
                        if (I.getItem() == Items.STONE_PICKAXE
                           || I.getItem() == Items.WOODEN_PICKAXE
                           || I.getItem() == Items.IRON_PICKAXE
                           || I.getItem() == Items.DIAMOND_PICKAXE) {
                           isBlockedByPickaxeRule = false;
                        }
                     }

                     if (!isBlockedByPickaxeRule) {
                        int Curr_Dig_Cycle = Clocker.GetIndexFromTime(3);
                        switch (Curr_Dig_Cycle) {
                           case 0:
                              new MobDig(Entity_Class);
                              break;
                           case 1:
                              new MobDigUp(Entity_Mob);
                              break;
                           case 2:
                              new MobDigDown(Entity_Mob);
                              break;
                        }
                     }
                  }
               }


               if (canUseBehaviorForStage("TNT_SAPPER", currentStage) && (Boolean)ESMConfig.ZombiesLayTNT.get()) {
                  new MobPlaceTNT(Entity_Class);
               }

               if (canUseBehaviorForStage("START_FIRES", currentStage) && (Boolean)ESMConfig.ZombiesLightFires.get()) {
                  Mob Mob_Class = (Monster)Entity_Class;
                  new MobStartFires(Mob_Class);
               }
            }

            if (Entity_Class instanceof net.minecraft.world.entity.monster.Phantom phantom
                  && !phantom.getPassengers().isEmpty()
                  && phantom.entityTags().contains("za_horde_mob")) {
               net.minecraft.world.entity.LivingEntity target = phantom.getTarget();
               if (target == null && phantom.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                  target = sl.getNearestPlayer(phantom, 96.0);
               }
               if (target != null) {
                  com.corosus.zombieawareness.heat.ChunkHeatManager.checkAndDropPhantomPassenger(phantom, target);
               }
            }

            if ((Entity_Class instanceof Spider || Entity_Class instanceof CaveSpider)
               && canUseBehaviorForStage("SPIDER_WEB", currentStage)
               && (Boolean)ESMConfig.SpidersShootWebs.get()) {
               new SpiderShootWeb(Entity_Mob);
            }
         }
      }
   }

   public static void onFinalizeSpawn(Mob M, EntitySpawnReason Spawn_Reason) {
      onFinalizeSpawn(M, Spawn_Reason, 1, false, false);
   }

   public static void onFinalizeSpawn(Mob M, EntitySpawnReason Spawn_Reason, int currentStage, boolean isBloodmoon) {
      onFinalizeSpawn(M, Spawn_Reason, currentStage, isBloodmoon, false);
   }

   /**
    * Returns true when immediate synchronous duplication during finalizeSpawn is safe.
    * Blocks worldgen regions (non-ServerLevel), off-thread workers (e.g. C2ME), and
    * structure/chunk-generation/summoned spawns to prevent ServerChunkCache.getChunkOffThread deadlocks.
    */
   public static boolean canDuplicateOnFinalizeSpawn(EntitySpawnReason spawnReason, boolean isLiveServerLevel, boolean isServerThread) {
      if (!isLiveServerLevel || !isServerThread) {
         return false;
      }
      return spawnReason != EntitySpawnReason.MOB_SUMMONED
         && spawnReason != EntitySpawnReason.CHUNK_GENERATION
         && spawnReason != EntitySpawnReason.STRUCTURE;
   }

   public static boolean isSafeLiveServerFinalizeSpawn(Level lvl, boolean isLiveServerLevel) {
      if (!isLiveServerLevel || !(lvl instanceof net.minecraft.server.level.ServerLevel serverLvl)) {
         return false;
      }
      MinecraftServer server = serverLvl.getServer();
      return server != null && server.isSameThread();
   }

   public static void onFinalizeSpawn(Mob M, EntitySpawnReason Spawn_Reason, int currentStage, boolean isBloodmoon, boolean isLiveServerLevel) {
      if ((Boolean)ESMConfig.isSiegeModeEnabled.get() && M != null) {
         Level Lvl = MobInfo.GetMobLevel(M);
         if (Lvl != null && !Lvl.isClientSide() && isSafeLiveServerFinalizeSpawn(Lvl, isLiveServerLevel)) {
            Entity Entity_Class = M;
            EntityType<?> resolvedType = Entity_Class.getType();
            if (resolvedType != null && Entity_Class instanceof Zombie z) {
               boolean allowPickaxe = canUseBehaviorForStage("PICKAXE_DIG", currentStage);
               new NightmareZombie(z, allowPickaxe);

               if (canRollAsScreamer(Spawn_Reason, currentStage)) {
                  if (new RNG().GetInt(0, 100) < 8) {
                     makeScreamer(z);
                  }
               }

               if (canUseBehaviorForStage("JUGGERNAUT_CRAWLER", currentStage)) {
                  if (new RNG().GetInt(0, 100) < 12) {
                     applyJuggernautLoadout(z);
                  }
               }
            }

            if (Entity_Class instanceof Creeper && (Boolean)ESMConfig.isChargedCreeperAllowed.get()) {
               new NightmareCreeper(Entity_Class, (Double)ESMConfig.ChargedCreeperChance.get());
            }

            if (Entity_Class instanceof Skeleton) {
               new NightmareSkeleton(M);
            } else if (Entity_Class instanceof Spider) {
               new NightmareSpider(Entity_Class);
            }

            if ((Boolean)ESMConfig.Entity_Duplication.get()) {
               int MaxDupes = (Integer)ESMConfig.MaxDuplicationClones.get();
               int MinDupes = (Integer)ESMConfig.MinDuplicationClones.get();
               if (MinDupes > MaxDupes) {
                  MinDupes = MaxDupes;
               }

               boolean isServerThread = Lvl instanceof net.minecraft.server.level.ServerLevel serverLvl
                  && serverLvl.getServer() != null
                  && serverLvl.getServer().isSameThread();

               if (canDuplicateOnFinalizeSpawn(Spawn_Reason, isLiveServerLevel, isServerThread)) {
                  int TotalDupes = new RNG().GetInt(MinDupes, MaxDupes + 1);
                  if (nightmareesm.ShouldDuplicate()) {
                     new DuplicateMob(M, TotalDupes);
                  }
               }

               if (Spawn_Reason == EntitySpawnReason.SPAWNER && isLiveServerLevel && isServerThread) {
                  int MaxDungeonDupes = (Integer)ESMConfig.MaxDungeonDuplicationClones.get();
                  int MinDungeonDupes = (Integer)ESMConfig.MinDungeonDuplicationClones.get();
                  if (MinDungeonDupes > MaxDungeonDupes) {
                     MinDungeonDupes = MaxDungeonDupes;
                  }

                  int TotalDupes = new RNG().GetInt(MinDungeonDupes, MaxDungeonDupes + 1);
                  new DuplicateMob(M, TotalDupes);
               }
            }

            if ((Boolean)ESMConfig.AngryEntities.get() && canUseBehaviorForStage("BLOODLUST", currentStage)) {
               float Bloodlust_RNG = new RNG().GetInt(0, 100);
               if (Bloodlust_RNG < ((Integer)ESMConfig.AngryEntityChance.get()).intValue()) {
                  new MobBloodlust(Entity_Class);
               }
            }
         }
      }
   }

   public static boolean canRollAsScreamer(EntitySpawnReason reason, int currentStage) {
      return reason != null
         && reason != EntitySpawnReason.MOB_SUMMONED
         && reason != EntitySpawnReason.REINFORCEMENT
         && reason != EntitySpawnReason.STRUCTURE
         && reason != EntitySpawnReason.CHUNK_GENERATION
         && canUseBehaviorForStage("SCREAMER_SCOUT", currentStage);
   }

   public static void makeScreamer(Zombie z) {
      if (z != null) {
         z.addTag("za_screamer");
         com.corosus.zombieawareness.heat.HordeSpecialUnitHelper.applySpecialMobIndicator(z);
      }
   }

   public static void applyJuggernautLoadout(Zombie z) {
      if (z != null) {
         z.addTag("za_juggernaut");
         new GiveRandomArmor(z, true);
         var attr = z.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
         if (attr != null) {
            attr.setBaseValue(0.85D);
         }
         com.corosus.zombieawareness.heat.HordeSpecialUnitHelper.applySpecialMobIndicator(z);
      }
   }

   public static void onMobDamaged(Mob mob, float amount, int currentStage) {
      if (mob == null) {
         return;
      }
      if (canUseBehaviorForStage("BLOODLUST", currentStage)) {
         if (mob instanceof Zombie) {
            new MobBloodlust(mob);
         }
      }

      if (canUseBehaviorForStage("JUGGERNAUT_CRAWLER", currentStage)) {
         if (mob instanceof Zombie z && !z.entityTags().contains("za_crawler")) {
            if (z.getHealth() > 0 && (z.getHealth() - amount) <= z.getMaxHealth() * 0.30f) {
               z.addTag("za_crawler");
               z.addEffect(new MobEffectInstance(MobEffects.SPEED, 600, 1));
               com.corosus.zombieawareness.heat.HordeSpecialUnitHelper.applySpecialMobIndicator(z);
            }
         }
      }
   }

   public static boolean isSiegeDay(Entity Entity_Class) {
      Level Lvl = Entity_Class.level();
      if (Lvl != null && !Lvl.isClientSide()) {
         long Uptime = Lvl.getOverworldClockTime();
         long GracePeriod = ((Integer)ESMConfig.StartInvasionAfterXTicks.get()).intValue();
         if (Uptime < GracePeriod) {
            return false;
         }

         long DayCount = Uptime / 24000L;
         return DayCount % ((Integer)ESMConfig.InvadeEveryXDays.get()).intValue() == 0L;
      } else {
         return false;
      }
   }

   public static boolean isTick(Mob Curr_Mob) {
      Level Lvl = MobInfo.GetMobLevel(Curr_Mob);
      if (Lvl != null && !Lvl.isClientSide()) {
         long Uptime = Lvl.getGameTime();
         return Uptime % 20L == 0L;
      } else {
         return false;
      }
   }

   public static EntityType<?>[] GetEntitiesFromSerialized(String SerialList) {
      String[] Entity_String_List = SerialList.split(",");
      EntityType<?>[] RetVal = new EntityType<?>[Entity_String_List.length];

      for (int i = 0; i < Entity_String_List.length; i++) {
         String Entity_Str = Entity_String_List[i];
         EntityType<?> Entity_Type_Class = ConfigHelper.GetEntityTypeFromName(Entity_Str);
         RetVal[i] = Entity_Type_Class;
      }

      return RetVal;
   }
}
