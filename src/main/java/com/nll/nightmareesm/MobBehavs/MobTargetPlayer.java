package com.nll.nightmareesm.MobBehavs;

import com.corosus.zombieawareness.ZAUtil;
import com.corosus.zombieawareness.heat.ChunkHeatManager;
import com.nll.nightmareesm.ESMConfig;
import com.nll.nightmareesm.GetNearestTarget;
import com.nll.nightmareesm.MobInfo;
import com.nll.nightmareesm.helpers.ConfigHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

public class MobTargetPlayer {
   private static final long TARGET_REVALIDATE_INTERVAL_TICKS = 100L;

   public MobTargetPlayer(Entity Attacker) {
      tickTarget(Attacker, false);
   }

   public MobTargetPlayer(Entity Attacker, boolean isBloodmoon) {
      tickTarget(Attacker, isBloodmoon);
   }

   public static void tickTarget(Entity Attacker, boolean isBloodmoon) {
      if (!(Attacker instanceof Mob Mob_Class)) {
         return;
      }
      boolean isHordeMob = ZAUtil.isHordeMob(Attacker);
      if (isHordeMob || ConfigHelper.ConfigHasValue(ESMConfig.SiegeEntityWhitelist, Attacker, false)) {
         LivingEntity Entity_Target = Mob_Class.getTarget();
         if (isHordeMob && ZAUtil.isVillagerEntity(Entity_Target)) {
            Mob_Class.setTarget(null);
            Entity_Target = null;
         }
         boolean isMonsterInfighting = (Boolean)ESMConfig.AllowMonsterInfighting.get();
         if (isMonsterInfighting && Entity_Target != null && (!isHordeMob || Entity_Target instanceof Player)) {
            return;
         }

         if (Entity_Target instanceof ServerPlayer sp
               && sp.isAlive()
               && MobInfo.isSameDimension(sp, Mob_Class)
               && GetNearestTarget.isValidSurvivalPlayerTarget(sp.isCreative(), sp.isSpectator())
               && withinRange(sp, Mob_Class)
               && Math.floorMod(Mob_Class.level().getGameTime() + Mob_Class.getId(), TARGET_REVALIDATE_INTERVAL_TICKS) != 0L) {
            MobMaxLife.checkAndDiscard(Attacker);
            return;
         }

         Entity Targeted_Entity = isHordeMob ? ChunkHeatManager.resolveHordeTargetPlayer(Mob_Class) : null;
         if (Targeted_Entity == null) {
            Targeted_Entity = GetNearestTarget.getNearest(Attacker);
         }
         if (Targeted_Entity instanceof LivingEntity livingTarget && (!isHordeMob || !ZAUtil.isVillagerEntity(livingTarget))) {
            if (withinRange(livingTarget, Attacker)) {
               if (isBloodmoon || isHordeMob || Mob_Class.hasLineOfSight(livingTarget) || Mob_Class.distanceToSqr(livingTarget) <= 100.0) {
                  if (Mob_Class.getTarget() != livingTarget) {
                     Mob_Class.setTarget(livingTarget);
                  }
               }
            }
         }

         MobMaxLife.checkAndDiscard(Attacker);
      }
   }

   private static boolean withinRange(Entity Targeted_Entity, Entity Entity_Class) {
      if (!MobInfo.isSameDimension(Targeted_Entity, Entity_Class)) {
         return false;
      }
      float Target_Dist_AboveGround = ((Integer)ESMConfig.MonsterAttackDistanceAboveGround.get()).intValue();
      float Target_Dist_UnderGround = ((Integer)ESMConfig.MonsterAttackDistanceUnderground.get()).intValue();
      double X_To = Math.abs(Targeted_Entity.getX() - Entity_Class.getX());
      double Z_To = Math.abs(Targeted_Entity.getZ() - Entity_Class.getZ());
      float Y_Pos = Entity_Class.blockPosition().getY();
      return Y_Pos < 50.0F
         ? Targeted_Entity.distanceToSqr(Entity_Class) < (double)(Target_Dist_UnderGround * Target_Dist_UnderGround)
         : X_To < Target_Dist_AboveGround && Z_To < Target_Dist_AboveGround;
   }
}
