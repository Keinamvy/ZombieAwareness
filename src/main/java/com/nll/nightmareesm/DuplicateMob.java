package com.nll.nightmareesm;

import com.nll.nightmareesm.helpers.ConfigHelper;
import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DuplicateMob {
   private static final Logger LOGGER = LoggerFactory.getLogger("DuplicateMob");
   int Swarm_Placement_Proximity = 2;
   ArrayList<BlockPos> Used_Points = new ArrayList<>();

   public DuplicateMob(Mob M, int Count) {
      if (ConfigHelper.ConfigHasValue(ESMConfig.DuplicatedMobs, M, false)) {
         for (int i = 0; i < Count; i++) {
            this.Clone(M);
         }
      }
   }

   void Clone(Mob M) {
      ServerLevel Lvl = MobInfo.GetMobServerLevel(M);
      if (Lvl != null) {
         BlockPos pos = M.blockPosition();
         BlockPos Offset_Pos = this.GetOffsetPosition(pos);
         if (this.isPositionGood(Lvl, Offset_Pos)) {
            M.getType().spawn(Lvl, new ItemStack(Items.DIRT), null, Offset_Pos, EntitySpawnReason.MOB_SUMMONED, true, false);
         }
      }
   }

   BlockPos GetOffsetPosition(BlockPos Original_Pos) {
      int Rand_X_Off = new RNG().GetInt(-this.Swarm_Placement_Proximity, this.Swarm_Placement_Proximity + 1);
      int Rand_Z_Off = new RNG().GetInt(-this.Swarm_Placement_Proximity, this.Swarm_Placement_Proximity + 1);
      return PositionInfo.OffsetPos(Original_Pos, Rand_X_Off, 0.0, Rand_Z_Off);
   }

   boolean isPositionGood(ServerLevel Lvl, BlockPos Position) {
      boolean isBottomOK = Lvl.getBlockState(Position).isAir();
      boolean isTopOK = Lvl.getBlockState(PositionInfo.OffsetPos(Position, 0.0, 1.0, 0.0)).isAir();
      return isBottomOK && isTopOK;
   }
}
