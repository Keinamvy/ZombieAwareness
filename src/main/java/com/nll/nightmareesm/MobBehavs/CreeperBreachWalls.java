package com.nll.nightmareesm.MobBehavs;

import com.nll.nightmareesm.ESMConfig;
import com.nll.nightmareesm.GetBlockingBlock;
import com.nll.nightmareesm.MobInfo;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Creeper;

public class CreeperBreachWalls {
   private static final Map<Creeper, Integer> TICK_MAP = Collections.synchronizedMap(new WeakHashMap<>());
   public boolean Debug_ExplodeImmediately = false;

   public CreeperBreachWalls(Creeper C) {
      if (this.Debug_ExplodeImmediately) {
         CreeperExplode.ignite(C);
      } else {
         tick(C);
      }
   }

   public static void tick(Creeper C) {
      if (C == null) {
         return;
      }
      Entity creeperTarget = C.getTarget();
      if (creeperTarget instanceof ServerPlayer plyr && MobInfo.isSameDimension(C, plyr)) {
         if (!plyr.isCreative() && !plyr.isSpectator()) {
            float distSq = (float)C.distanceToSqr(plyr);
            int breachDist = (Integer)ESMConfig.CreeperBreachingDistance.get();
            boolean isCloseEnough = distSq < (float)(breachDist * breachDist);
            int ticks = getTicks(C);
            if (!isWithinStrikingDistance(distSq) && !isAtWall(C)) {
               resetTicks(C);
            } else if (isCloseEnough) {
               C.lookAt(plyr, 30.0F, 30.0F);
               incTicks(C);
               if (ticks > (Integer)ESMConfig.CreeperObstructedExplodeTicks.get()) {
                  CreeperExplode.ignite(C);
               }
            } else {
               resetTicks(C);
            }

            if (isHoveringAbove(C, plyr)) {
               CreeperExplode.ignite(C);
            }
         } else {
            resetTicks(C);
         }
      }
   }

   public static void clearTickMap() {
      TICK_MAP.clear();
   }

   private static void incTicks(Creeper C) {
      synchronized (TICK_MAP) {
         int currTicks = getTicks(C);
         setTicks(C, ++currTicks);
      }
   }

   private static void setTicks(Creeper C, int amt) {
      if (amt <= 0) {
         TICK_MAP.remove(C);
      } else {
         TICK_MAP.put(C, amt);
      }
   }

   private static void resetTicks(Creeper C) {
      setTicks(C, 0);
   }

   private static int getTicks(Creeper C) {
      return TICK_MAP.getOrDefault(C, 0);
   }

   private static boolean isAtWall(Entity entity) {
      return GetBlockingBlock.isWallBlocked(entity);
   }

   private static boolean isHoveringAbove(Creeper C, ServerPlayer playerTarget) {
      BlockPos creeperPos = C.blockPosition();
      BlockPos playerPos = playerTarget.blockPosition();
      int xDiff = Math.abs(creeperPos.getX() - playerPos.getX());
      int zDiff = Math.abs(creeperPos.getZ() - playerPos.getZ());
      int aboveDist = (Integer)ESMConfig.CreeperAboveExplodeDistance.get();
      int yDiff = creeperPos.getY() - playerPos.getY();
      return xDiff < aboveDist && zDiff < aboveDist && yDiff > 0 && yDiff <= 10;
   }

   private static boolean isWithinStrikingDistance(float distSq) {
      int strikeDist = (Integer)ESMConfig.CreeperStrikingDistance.get();
      return distSq < (float)(strikeDist * strikeDist);
   }
}
