package com.nll.nightmareesm;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class GetNearestTarget {
   private static final Logger LOGGER = LogManager.getLogger();

   public Entity Get(Entity Attacker) {
      MinecraftServer Curr_Server = Attacker != null && Attacker.level().getServer() != null ? Attacker.level().getServer() : nightmareesm.getCurrentServer();
      if (Curr_Server == null) {
         return null;
      }
      PlayerList Player_List = Curr_Server.getPlayerList();
      int PlayerCount = Player_List.getPlayerCount();
      return PlayerCount > 0 ? this.GetNearestEntity(Player_List, Attacker) : null;
   }

   Entity GetNearestEntity(PlayerList Player_List, Entity Attacker) {
      List<Entity> Nearby_Entities = this.GetNearbyEntities(Player_List, Attacker);
      int Entity_Count = Nearby_Entities.size();
      if (Entity_Count > 0) {
         float Winning_Distance = 999999.0F;
         Entity Winning_Target = Nearby_Entities.getFirst();

         for (int i = 0; i < Entity_Count; i++) {
            Entity PotentialTgt = Nearby_Entities.get(i);
            if (Winning_Target != PotentialTgt && this.isEntitySurvivalModeOrMob(PotentialTgt)) {
               float Dist = Attacker.distanceTo(PotentialTgt);
               if (Dist < Winning_Distance) {
                  Winning_Distance = Dist;
                  Winning_Target = PotentialTgt;
               }
            }
         }

         return Winning_Target;
      } else {
         return null;
      }
   }

   List<Entity> GetNearbyEntities(PlayerList Player_List, Entity Attacker) {
      List<Entity> Compiled_Entities = new ArrayList<>();
      List<ServerPlayer> All_Players = Player_List.getPlayers();
      int AllPlayerCount = Player_List.getPlayerCount();
      int AlivePlayerCount = 0;

      for (int i = 0; i < AllPlayerCount; i++) {
         ServerPlayer Plyr = All_Players.get(i);
         if (!Plyr.isCreative() && Plyr.isAlive()) {
            Compiled_Entities.add(Plyr);
            AlivePlayerCount++;
         }
      }

      if (AlivePlayerCount == 0 && (Boolean)ESMConfig.isMonstersTargetVillagers.get()) {
         double Attacker_Y = Attacker.getY();
         double AttackDist = ((Integer)ESMConfig.MonsterAttackDistanceAboveGround.get()).intValue();
         if (Attacker_Y < 50.0) {
            AttackDist = ((Integer)ESMConfig.MonsterAttackDistanceUnderground.get()).intValue();
         }

         TargetingConditions Tgt = TargetingConditions.forCombat().range(AttackDist);
         Mob Mob_Attacker = (Mob)Attacker;
         Level Lvl = MobInfo.GetMobLevel(Mob_Attacker);
         List<Villager> All_Nearby_Entities = Lvl.getEntitiesOfClass(Villager.class, Attacker.getBoundingBox().inflate(AttackDist, AttackDist, AttackDist));

         for (int i = 0; i < All_Nearby_Entities.size(); i++) {
            Villager Curr_Villager = All_Nearby_Entities.get(i);
            Entity Entity_Villager = Curr_Villager;
            Compiled_Entities.add(Entity_Villager);
         }
      }

      return Compiled_Entities;
   }

   boolean isEntitySurvivalModeOrMob(Entity Entity_Class) {
      return Entity_Class instanceof ServerPlayer Server_Player ? Server_Player.isCreative() : true;
   }
}
