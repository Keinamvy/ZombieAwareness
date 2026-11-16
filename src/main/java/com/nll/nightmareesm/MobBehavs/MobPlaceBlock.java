package com.nll.nightmareesm.MobBehavs;

import com.nll.nightmareesm.MobInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

public class MobPlaceBlock {
   public MobPlaceBlock(Entity Entity_Class, Block Block_Type, BlockPos Position) {
      if (Entity_Class instanceof Mob Mob_Entity && Position != null && Block_Type != null) {
         Level Lvl = MobInfo.GetMobLevel(Mob_Entity);
         if (!MobInfo.isServerPosLoaded(Lvl, Position)) {
            return;
         }
         BlockState existing = Lvl.getBlockState(Position);
         if (existing.getDestroySpeed(Lvl, Position) < 0 || existing.is(Blocks.OBSIDIAN) || existing.is(Blocks.CRYING_OBSIDIAN) || Lvl.getBlockEntity(Position) != null) {
            return;
         }
         Lvl.setBlockAndUpdate(Position, Block_Type.defaultBlockState());
         SoundType Snd = Block_Type.defaultBlockState().getSoundType();
         Lvl.playSound(null, Position, Snd.getBreakSound(), SoundSource.BLOCKS, 0.6F, 0.5F);
      }
   }
}
