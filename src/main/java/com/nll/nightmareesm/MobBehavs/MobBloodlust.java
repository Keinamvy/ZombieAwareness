package com.nll.nightmareesm.MobBehavs;

import java.util.Set;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;

public class MobBloodlust {
   private static final Set<Class<?>> WHITELIST = Set.of(
      Zombie.class, Drowned.class, Husk.class, ZombieVillager.class, ZombifiedPiglin.class, Hoglin.class, AbstractPiglin.class
   );
   private static final Identifier BLOODLUST_IDENTIFIER = Identifier.parse("bloodlust");

   public MobBloodlust(Entity Entity_Class) {
      apply(Entity_Class);
   }

   public static void apply(Entity Entity_Class) {
      if (Entity_Class instanceof LivingEntity Living_Entity && WHITELIST.contains(Entity_Class.getClass())) {
         var speedAttr = Living_Entity.getAttribute(Attributes.MOVEMENT_SPEED);
         if (speedAttr != null && !speedAttr.hasModifier(BLOODLUST_IDENTIFIER)) {
            speedAttr.addPermanentModifier(new AttributeModifier(BLOODLUST_IDENTIFIER, 0.25, Operation.ADD_MULTIPLIED_BASE));
         }
      }
   }
}
