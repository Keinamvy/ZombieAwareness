package com.nll.nightmareesm;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayer.RespawnConfig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.LevelData.RespawnData;

public class SetPlayerSpawn {
   public SetPlayerSpawn(Player Player_Entity, BlockPos Pos) {
      if (Player_Entity instanceof ServerPlayer Server_Player) {
         ServerLevel Lvl = Server_Player.level();
         GlobalPos gPos = new GlobalPos(Lvl.dimension(), Pos);
         RespawnData Resp_Data = new RespawnData(gPos, 0.0F, 0.0F);
         RespawnConfig Resp_Config = new RespawnConfig(Resp_Data, true);
         Server_Player.setRespawnPosition(Resp_Config, Server_Player.connection != null);
      }
   }
}
