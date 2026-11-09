package com.nll.nightmareesm.MobBehavs;

import com.nll.nightmareesm.ESMConfig;
import com.nll.nightmareesm.GetBlockingBlock;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Creeper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreeperBreachWalls {
   private static final Logger LOGGER = LoggerFactory.getLogger("CreeperBreachWalls");
   private static final Map<Creeper, Integer> TICK_MAP = new WeakHashMap<>();
   public boolean Debug_ExplodeImmediately = false;

   public CreeperBreachWalls(Creeper C) {
      if (this.Debug_ExplodeImmediately) {
         new CreeperExplode(C);
      } else {
         Entity Creeper_Target = C.getTarget();
         if (Creeper_Target != null) {
            Entity Creeper_Entity = C;
            if (Creeper_Target instanceof ServerPlayer Plyr) {
               if (!Plyr.isCreative()) {
                  float Dist_To_Tgt = C.distanceTo(Creeper_Target);
                  boolean isCloseEnough = Dist_To_Tgt < ((Integer)ESMConfig.CreeperBreachingDistance.get()).intValue();
                  int Ticks = this.GetTicks(C);
                  if (!this.isAtWall(Creeper_Entity) && !this.isWithinStrikingDistance(C, Plyr)) {
                     this.ResetTicks(C);
                  } else if (isCloseEnough) {
                     C.lookAt(C, 1.0F, 1.0F);
                     this.IncTicks(C);
                     if (Ticks > (Integer)ESMConfig.CreeperObstructedExplodeTicks.get()) {
                        new CreeperExplode(C);
                     }
                  } else {
                     this.ResetTicks(C);
                  }

                  if (this.isHoveringAbove(C, Plyr)) {
                     new CreeperExplode(C);
                  }
               } else {
                  this.ResetTicks(C);
               }
            }
         }
      }
   }

   void IncTicks(Creeper C) {
      int CurrTicks = this.GetTicks(C);
      this.SetTicks(C, ++CurrTicks);
   }

   void SetTicks(Creeper C, int Amt) {
      if (Amt <= 0) {
         TICK_MAP.remove(C);
      } else {
         TICK_MAP.put(C, Amt);
      }
   }

   void ResetTicks(Creeper C) {
      this.SetTicks(C, 0);
   }

   int GetTicks(Creeper C) {
      return TICK_MAP.getOrDefault(C, 0);
   }

   boolean isAtWall(Entity Entity_Class) {
      GetBlockingBlock Blocking_Block = new GetBlockingBlock(Entity_Class);
      return Blocking_Block.isFootBlocked && Blocking_Block.isBlocking;
   }

   boolean isHoveringAbove(Creeper C, ServerPlayer Player_Target) {
      BlockPos Creeper_Pos = C.blockPosition();
      BlockPos Player_Pos = Player_Target.blockPosition();
      float x_diff = Math.abs(Creeper_Pos.getX() - Player_Pos.getX());
      float z_diff = Math.abs(Creeper_Pos.getZ() - Player_Pos.getZ());
      return x_diff < ((Integer)ESMConfig.CreeperAboveExplodeDistance.get()).intValue()
         && z_diff < ((Integer)ESMConfig.CreeperAboveExplodeDistance.get()).intValue()
         && Player_Pos.getY() < Creeper_Pos.getY();
   }

   boolean isWithinStrikingDistance(Creeper C, ServerPlayer Player_Target) {
      float Creeper_Dist = C.distanceTo(Player_Target);
      return Creeper_Dist < ((Integer)ESMConfig.CreeperStrikingDistance.get()).intValue();
   }
}
