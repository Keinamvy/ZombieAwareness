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

/**
 * Isolated Zombie Build Ability — Safe Block Placement Helper.
 *
 * <p>Shared placement primitive used by {@link MobBuildBridge} and {@link MobBuildUp} to place a
 * building block on the server only when the target position is loaded and does not overwrite
 * unbreakable blocks, obsidian, or block entities.
 *
 * <p><b>Current Status:</b> Kept intact alongside {@link MobBuildBridge}, {@link MobBuildUp}, and
 * {@link com.nll.nightmareesm.SnapToBlockCenter} for future re-enablement of the Zombie Build ability.
 */
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
