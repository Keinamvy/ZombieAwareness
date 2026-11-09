package com.nll.nightmareesm.MobBehavs;

import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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
         var healthAttr = Living_Entity.getAttribute(Attributes.MAX_HEALTH);
         if (healthAttr != null && !healthAttr.hasModifier(Bloodlust_Identifier)) {
            AttributeModifier Attr_Mod = new AttributeModifier(Bloodlust_Identifier, 0.5, Operation.ADD_MULTIPLIED_BASE);
            healthAttr.addPermanentModifier(Attr_Mod);
            Living_Entity.setHealth(Living_Entity.getHealth() + Living_Entity.getHealth() * 0.5F);
         }
         var speedAttr = Living_Entity.getAttribute(Attributes.MOVEMENT_SPEED);
         if (speedAttr != null && !speedAttr.hasModifier(Bloodlust_Identifier)) {
            speedAttr.addPermanentModifier(new AttributeModifier(Bloodlust_Identifier, 0.25, Operation.ADD_MULTIPLIED_BASE));
         }
         Living_Entity.addEffect(new MobEffectInstance(MobEffects.HEALTH_BOOST, 1000, 1));
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
