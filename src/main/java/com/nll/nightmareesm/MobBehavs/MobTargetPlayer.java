package com.nll.nightmareesm.MobBehavs;

import com.nll.nightmareesm.ESMConfig;
import com.nll.nightmareesm.GetNearestTarget;
import com.nll.nightmareesm.helpers.ConfigHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MobTargetPlayer {
   private static final Logger LOGGER = LogManager.getLogger();

   public MobTargetPlayer(Entity Attacker) {
      this(Attacker, false);
   }

   public MobTargetPlayer(Entity Attacker, boolean isBloodmoon) {
      if (ConfigHelper.ConfigHasValue(ESMConfig.SiegeEntityWhitelist, Attacker, false)) {
         Mob Mob_Class = (Mob)Attacker;
         Entity Entity_Target = Mob_Class.getTarget();
         boolean isMonsterInfighting = (Boolean)ESMConfig.AllowMonsterInfighting.get();
         if (isMonsterInfighting && Entity_Target != null) {
            return;
         }

         Entity Targeted_Entity = new GetNearestTarget().Get(Attacker);
         if (Targeted_Entity != null && this.WithinRange(Targeted_Entity, Attacker)) {
            if (isBloodmoon || Mob_Class.hasLineOfSight(Targeted_Entity) || Mob_Class.distanceTo(Targeted_Entity) <= 10.0F) {
               Mob_Class.setTarget((LivingEntity)Targeted_Entity);
            }
         }

         new MobMaxLife(Attacker);
      }
   }

   boolean WithinRange(Entity Targeted_Entity, Entity Entity_Class) {
      float Target_Dist_AboveGround = ((Integer)ESMConfig.MonsterAttackDistanceAboveGround.get()).intValue();
      float Target_Dist_UnderGround = ((Integer)ESMConfig.MonsterAttackDistanceUnderground.get()).intValue();
      double X_To = Math.abs(Math.abs(Targeted_Entity.getX()) - Math.abs(Entity_Class.getX()));
      double Z_To = Math.abs(Math.abs(Targeted_Entity.getZ()) - Math.abs(Entity_Class.getZ()));
      float Y_Pos = Entity_Class.blockPosition().getY();
      return Y_Pos < 50.0F
         ? Targeted_Entity.distanceTo(Entity_Class) < Target_Dist_UnderGround
         : X_To < Target_Dist_AboveGround && Z_To < Target_Dist_AboveGround;
   }
}
