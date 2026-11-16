package com.nll.nightmareesm;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class GiveRandomArmor {
   private static final float CHANCE_OF_ARMOR = 20.0F;
   private static final Item[] ARMOR_HEAD = new Item[]{Items.CHAINMAIL_HELMET, Items.GOLDEN_HELMET, Items.IRON_HELMET, Items.LEATHER_HELMET};
   private static final Item[] ARMOR_CHEST = new Item[]{Items.CHAINMAIL_CHESTPLATE, Items.GOLDEN_CHESTPLATE, Items.IRON_CHESTPLATE, Items.LEATHER_CHESTPLATE};
   private static final Item[] ARMOR_LEGGINGS = new Item[]{Items.CHAINMAIL_LEGGINGS, Items.GOLDEN_LEGGINGS, Items.IRON_LEGGINGS, Items.LEATHER_LEGGINGS};
   private static final Item[] ARMOR_BOOTS = new Item[]{Items.CHAINMAIL_BOOTS, Items.GOLDEN_BOOTS, Items.IRON_BOOTS, Items.LEATHER_BOOTS};

   public GiveRandomArmor(Monster M) {
      give(M, false);
   }

   public GiveRandomArmor(Monster M, boolean force) {
      give(M, force);
   }

   public static void give(Monster M, boolean force) {
      if (M == null) {
         return;
      }
      int RNG_Val = RNG.getInt(0, 100);
      if (force || RNG_Val < CHANCE_OF_ARMOR) {
         int ArmorType = RNG.getInt(0, ARMOR_HEAD.length);
         M.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ARMOR_HEAD[ArmorType]));
         M.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ARMOR_CHEST[ArmorType]));
         M.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ARMOR_LEGGINGS[ArmorType]));
         M.setItemSlot(EquipmentSlot.FEET, new ItemStack(ARMOR_BOOTS[ArmorType]));
      }
   }
}
