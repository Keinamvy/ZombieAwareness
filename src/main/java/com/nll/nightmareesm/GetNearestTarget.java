package com.nll.nightmareesm;

import com.corosus.zombieawareness.ZAUtil;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.Level;

public class GetNearestTarget {
   public Entity Get(Entity Attacker) {
      return getNearest(Attacker);
   }

   public static Entity getNearest(Entity Attacker) {
      if (Attacker == null) {
         return null;
      }
      MinecraftServer Curr_Server = Attacker.level().getServer() != null ? Attacker.level().getServer() : nightmareesm.getCurrentServer();
      if (Curr_Server == null) {
         return null;
      }
      PlayerList Player_List = Curr_Server.getPlayerList();
      if (Player_List.getPlayerCount() <= 0) {
         return null;
      }

      List<ServerPlayer> allPlayers = Player_List.getPlayers();
      ServerPlayer bestPlayer = null;
      double bestDistSq = Double.MAX_VALUE;
      int alivePlayerCount = 0;

      for (int i = 0; i < allPlayers.size(); i++) {
         ServerPlayer plyr = allPlayers.get(i);
         if (MobInfo.isSameDimension(plyr, Attacker)
               && isValidSurvivalPlayerTarget(plyr.isCreative(), plyr.isSpectator())
               && plyr.isAlive()) {
            alivePlayerCount++;
            double distSq = Attacker.distanceToSqr(plyr);
            if (distSq < bestDistSq) {
               bestDistSq = distSq;
               bestPlayer = plyr;
            }
         }
      }

      if (bestPlayer != null) {
         return bestPlayer;
      }

      if (alivePlayerCount == 0 && (Boolean)ESMConfig.isMonstersTargetVillagers.get() && !ZAUtil.isHordeMob(Attacker) && Attacker instanceof Mob mobAttacker) {
         double attackDist = Attacker.getY() < 50.0
            ? ((Integer)ESMConfig.MonsterAttackDistanceUnderground.get()).intValue()
            : ((Integer)ESMConfig.MonsterAttackDistanceAboveGround.get()).intValue();
         Level lvl = MobInfo.GetMobLevel(mobAttacker);
         if (lvl != null && !lvl.isClientSide()) {
            List<Villager> villagers = lvl.getEntitiesOfClass(Villager.class, Attacker.getBoundingBox().inflate(attackDist, attackDist, attackDist));
            Villager bestVillager = null;
            double bestVillagerDistSq = Double.MAX_VALUE;
            for (int i = 0; i < villagers.size(); i++) {
               Villager v = villagers.get(i);
               double dSq = Attacker.distanceToSqr(v);
               if (dSq < bestVillagerDistSq) {
                  bestVillagerDistSq = dSq;
                  bestVillager = v;
               }
            }
            return bestVillager;
         }
      }

      return null;
   }

   public static boolean isValidSurvivalPlayerTarget(boolean isCreative, boolean isSpectator) {
      return !isCreative && !isSpectator;
   }
}
