package com.nll.nightmareesm.MobBehavs;

import com.corosus.zombieawareness.ZAUtil;
import com.corosus.zombieawareness.heat.ChunkHeatManager;
import com.nll.nightmareesm.ESMConfig;
import com.nll.nightmareesm.GetNearestTarget;
import com.nll.nightmareesm.MobInfo;
import com.nll.nightmareesm.helpers.ConfigHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MobTargetPlayer {
   private static final Logger LOGGER = LogManager.getLogger();

   public MobTargetPlayer(Entity Attacker) {
      this(Attacker, false);
   }

   public MobTargetPlayer(Entity Attacker, boolean isBloodmoon) {
      boolean isHordeMob = ZAUtil.isHordeMob(Attacker);
      if (Attacker instanceof Mob Mob_Class && (isHordeMob || ConfigHelper.ConfigHasValue(ESMConfig.SiegeEntityWhitelist, Attacker, false))) {
         LivingEntity Entity_Target = Mob_Class.getTarget();
         if (isHordeMob && ZAUtil.isVillagerEntity(Entity_Target)) {
            Mob_Class.setTarget(null);
            Entity_Target = null;
         }
         boolean isMonsterInfighting = (Boolean)ESMConfig.AllowMonsterInfighting.get();
         if (isMonsterInfighting && Entity_Target != null && (!isHordeMob || Entity_Target instanceof Player)) {
            return;
         }

         Entity Targeted_Entity = isHordeMob ? ChunkHeatManager.resolveHordeTargetPlayer(Mob_Class) : null;
         if (Targeted_Entity == null) {
            Targeted_Entity = new GetNearestTarget().Get(Attacker);
         }
         if (Targeted_Entity instanceof LivingEntity livingTarget && (!isHordeMob || !ZAUtil.isVillagerEntity(livingTarget))) {
            if (this.WithinRange(livingTarget, Attacker)) {
               if (isBloodmoon || isHordeMob || Mob_Class.hasLineOfSight(livingTarget) || Mob_Class.distanceTo(livingTarget) <= 10.0F) {
                  Mob_Class.setTarget(livingTarget);
               }
            }
         }

         new MobMaxLife(Attacker);
      }
   }

   boolean WithinRange(Entity Targeted_Entity, Entity Entity_Class) {
      if (!MobInfo.isSameDimension(Targeted_Entity, Entity_Class)) {
         return false;
      }
      float Target_Dist_AboveGround = ((Integer)ESMConfig.MonsterAttackDistanceAboveGround.get()).intValue();
      float Target_Dist_UnderGround = ((Integer)ESMConfig.MonsterAttackDistanceUnderground.get()).intValue();
      double X_To = Math.abs(Targeted_Entity.getX() - Entity_Class.getX());
      double Z_To = Math.abs(Targeted_Entity.getZ() - Entity_Class.getZ());
      float Y_Pos = Entity_Class.blockPosition().getY();
      return Y_Pos < 50.0F
         ? Targeted_Entity.distanceTo(Entity_Class) < Target_Dist_UnderGround
         : X_To < Target_Dist_AboveGround && Z_To < Target_Dist_AboveGround;
   }
}
