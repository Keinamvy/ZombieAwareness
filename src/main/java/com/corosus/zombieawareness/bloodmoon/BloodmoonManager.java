package com.corosus.zombieawareness.bloodmoon;

import com.corosus.zombieawareness.config.ZAConfigZDay;
import com.corosus.zombieawareness.heat.ChunkHeatManager;
import com.corosus.zombieawareness.zday.ZDayProgressionManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

public class BloodmoonManager {

    private static volatile Boolean forcedBloodmoon = null;
    private static volatile long lastDuskAlertDay = -1;

    public static void setForcedBloodmoon(Boolean forced) {
        forcedBloodmoon = forced;
    }

    public static Boolean getForcedBloodmoon() {
        return forcedBloodmoon;
    }

    public static boolean isBloodmoonDay(int worldDay) {
        int startDay = ZAConfigZDay.dayStage4Bloodmoon;
        int freq = Math.max(1, ZAConfigZDay.bloodmoonFrequencyDays);
        return worldDay >= startDay && ((worldDay - startDay) % freq == 0);
    }

    public static boolean isBloodmoonNightTime(long dayTime) {
        long tod = ((dayTime % 24000L) + 24000L) % 24000L;
        return tod >= 12000L && tod <= 23500L;
    }

    public static boolean isBloodmoonActive(Level level) {
        if (forcedBloodmoon != null) {
            return forcedBloodmoon;
        }
        if (level == null) {
            return false;
        }
        int day = ZDayProgressionManager.getActiveWorldDay();
        return isBloodmoonDay(day) && isBloodmoonNightTime(level.getDefaultClockTime());
    }

    public static void tickServer(ServerLevel overworld) {
        if (overworld == null || !overworld.tickRateManager().runsNormally()) return;
        if (!isBloodmoonActive(overworld)) {
            return;
        }
        int currentDay = ZDayProgressionManager.getActiveWorldDay();
        if (lastDuskAlertDay != currentDay) {
            lastDuskAlertDay = currentDay;
            Component text = Component.literal("§4§l[Bloodmoon] The Crimson Moon rises! The horde knows no rest tonight!");
            if (overworld.getServer() != null && overworld.getServer().getPlayerList() != null) {
                for (ServerPlayer player : overworld.getServer().getPlayerList().getPlayers()) {
                    player.sendSystemMessage(text);
                    ChunkHeatManager.playHordeRoar(overworld, player.getX(), player.getY(), player.getZ());
                }
            }
        }
    }

    public static void resetState() {
        forcedBloodmoon = null;
        lastDuskAlertDay = -1;
    }
}
