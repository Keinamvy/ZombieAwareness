package com.nll.nightmareesm;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class GiveRandomPickaxe {
   private static final Item[] PICKAXES = new Item[]{
      Items.STONE_PICKAXE,
      Items.IRON_PICKAXE,
      Items.WOODEN_PICKAXE,
      Items.IRON_SWORD,
      Items.STONE_SWORD,
      Items.WOODEN_SWORD,
      Items.IRON_AXE,
      Items.WOODEN_AXE,
      Items.STONE_AXE
   };

   public GiveRandomPickaxe(Monster M) {
      give(M);
   }

   public static void give(Monster M) {
      if (M == null) {
         return;
      }
      ItemStack Pickaxe_Item_Stack = M.getItemBySlot(EquipmentSlot.MAINHAND);
      if (Pickaxe_Item_Stack.isEmpty()) {
         Item Pickaxe_Item = PICKAXES[RNG.getInt(0, PICKAXES.length)];
         M.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Pickaxe_Item));
      }
   }
}
