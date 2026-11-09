package com.nll.nightmareesm.MobBehavs;

import com.nll.nightmareesm.MobInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;

public class MobPlaceBlock {
   public MobPlaceBlock(Entity Entity_Class, Block Block_Type, BlockPos Position) {
      Mob Mob_Entity = (Mob)Entity_Class;
      Level Lvl = MobInfo.GetMobLevel(Mob_Entity);
      Lvl.setBlockAndUpdate(Position, Block_Type.defaultBlockState());
      LevelReader reader = Entity_Class.level();
      if (reader != null) {
         SoundType Snd = Block_Type.defaultBlockState().getSoundType();
         Lvl.playSound(null, Position, Snd.getBreakSound(), SoundSource.BLOCKS, 0.6F, 0.5F);
      }
   }
}
