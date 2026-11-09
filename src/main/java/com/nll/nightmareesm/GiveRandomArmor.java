package com.nll.nightmareesm;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class GiveRandomArmor {
   float Chance_Of_Armor = 20.0F;
   Item[] Armor_Head = new Item[]{Items.CHAINMAIL_HELMET, Items.GOLDEN_HELMET, Items.IRON_HELMET, Items.LEATHER_HELMET};
   Item[] Armor_Chest = new Item[]{Items.CHAINMAIL_CHESTPLATE, Items.GOLDEN_CHESTPLATE, Items.IRON_CHESTPLATE, Items.LEATHER_CHESTPLATE};
   Item[] Armor_Leggins = new Item[]{Items.CHAINMAIL_LEGGINGS, Items.GOLDEN_LEGGINGS, Items.IRON_LEGGINGS, Items.LEATHER_LEGGINGS};
   Item[] Armor_Boots = new Item[]{Items.CHAINMAIL_BOOTS, Items.GOLDEN_BOOTS, Items.IRON_BOOTS, Items.LEATHER_BOOTS};

   public GiveRandomArmor(Monster M) {
      this(M, false);
   }

   public GiveRandomArmor(Monster M, boolean force) {
      int RNG_Val = new RNG().GetInt(0, 100);
      if (force || RNG_Val < this.Chance_Of_Armor) {
         int ArmorType = new RNG().GetInt(0, this.Armor_Head.length);
         Item Head_Armor = this.Armor_Head[ArmorType];
         Item Chest_Armor = this.Armor_Chest[ArmorType];
         Item Leggings_Armor = this.Armor_Leggins[ArmorType];
         Item Boots_Armor = this.Armor_Boots[ArmorType];
         M.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Head_Armor));
         M.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Chest_Armor));
         M.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Leggings_Armor));
         M.setItemSlot(EquipmentSlot.FEET, new ItemStack(Boots_Armor));
      }
   }
}
