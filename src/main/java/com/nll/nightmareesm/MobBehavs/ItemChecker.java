package com.nll.nightmareesm.MobBehavs;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;

public class ItemChecker {
   public static boolean EntityHasPickaxe(Entity Entity_Class) {
      Mob Mob_Class = (Mob)Entity_Class;
      ItemStack Item_In_Entity_Hand = Mob_Class.getMainHandItem();
      String Item_Name = Item_In_Entity_Hand.getItem().getDescriptionId();
      return Item_Name.toLowerCase().contains("pick");
   }
}
