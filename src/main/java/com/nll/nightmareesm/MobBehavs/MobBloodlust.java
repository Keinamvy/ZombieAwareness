package com.nll.nightmareesm.MobBehavs;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;

public class MobBloodlust {
   Class<?>[] WhiteList = new Class<?>[]{Zombie.class, Drowned.class, Husk.class, ZombieVillager.class, ZombifiedPiglin.class, Hoglin.class, AbstractPiglin.class};

   public MobBloodlust(Entity Entity_Class) {
      if (this.isOnWhitelist(Entity_Class)) {
         LivingEntity Living_Entity = (LivingEntity)Entity_Class;
         Identifier Bloodlust_Identifier = Identifier.parse("bloodlust");
         var speedAttr = Living_Entity.getAttribute(Attributes.MOVEMENT_SPEED);
         if (speedAttr != null && !speedAttr.hasModifier(Bloodlust_Identifier)) {
            speedAttr.addPermanentModifier(new AttributeModifier(Bloodlust_Identifier, 0.25, Operation.ADD_MULTIPLIED_BASE));
         }
      }
   }

   boolean isOnWhitelist(Entity Current_Entity_Type) {
      for (int i = 0; i < this.WhiteList.length; i++) {
         Class<?> c = this.WhiteList[i];
         if (Current_Entity_Type.getClass() == c) {
            return true;
         }
      }

      return false;
   }
}
