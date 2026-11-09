package com.corosus.zombieawareness.zday;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ZDayProgressionManager {

    private static ZDayPlayerLedger ledger = new ZDayPlayerLedger();
    private static Path ledgerPath = null;
    private static long prevVanillaDayTime = -1L;
    private static int forcedWorldDay = -1;
    private static MinecraftServer currentServer = null;

    public static long advanceTickLogic(long currentActiveTicks, long prevVanillaDayTime, long currentVanillaDayTime, int onlinePlayerCount) {
        if (onlinePlayerCount <= 0) {
            return currentActiveTicks;
        }
        if (currentVanillaDayTime > prevVanillaDayTime) {
            long delta = currentVanillaDayTime - prevVanillaDayTime;
            if (delta > 1L && delta <= 24000L) {
                return currentActiveTicks + delta;
            }
        }
        return currentActiveTicks + 1L;
    }

    public static void initServer(MinecraftServer server) {
        currentServer = server;
        if (server != null) {
            try {
                Path dataDir = server.getWorldPath(LevelResource.ROOT).resolve("data");
                ledgerPath = dataDir.resolve("zombieawareness_zday_ledger.json");
                ledger = ZDayPlayerLedger.load(ledgerPath);
                forcedWorldDay = ledger.forcedWorldDay;
                if (server.overworld() != null) {
                    prevVanillaDayTime = server.overworld().getDefaultClockTime();
                }
            } catch (Exception e) {
                System.err.println("[ZombieAwareness] Error initializing ZDayProgressionManager: " + e.getMessage());
                if (ledger == null) {
                    ledger = new ZDayPlayerLedger();
                }
            }
        } else {
            if (ledger == null) {
                ledger = new ZDayPlayerLedger();
            }
        }
    }

    public static void tickServer(MinecraftServer server) {
        currentServer = server;
        if (ledger == null || ledgerPath == null) {
            initServer(server);
        }
        int onlineCount = server.getPlayerCount();
        long currentVanillaDayTime = server.overworld() != null ? server.overworld().getDefaultClockTime() : 0L;
        if (prevVanillaDayTime < 0L) {
            prevVanillaDayTime = currentVanillaDayTime;
        }

        ledger.activePlayTicks = advanceTickLogic(ledger.activePlayTicks, prevVanillaDayTime, currentVanillaDayTime, onlineCount);
        prevVanillaDayTime = currentVanillaDayTime;

        ZDayStage currentStage = getCurrentStage();
        int currentStageNum = currentStage.getStageNumber();

        if (currentStageNum > ledger.lastBroadcastedStage) {
            int worldDay = getActiveWorldDay();
            String title = currentStage.getTitle();
            String desc = currentStage.getDescription();
            Component announceHeader = Component.literal("§4§l[Z-Day Escalation] Dawn of Day " + worldDay + " — Stage " + currentStageNum + ": " + title);
            Component announceDesc = Component.literal("§c" + desc);
            server.getPlayerList().broadcastSystemMessage(announceHeader, false);
            server.getPlayerList().broadcastSystemMessage(announceDesc, false);

            ledger.lastBroadcastedStage = currentStageNum;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                ledger.recordPlayerSeen(player.getUUID(), worldDay, currentStageNum);
            }
            saveLedger();
        } else if (server.getTickCount() % 600 == 0) {
            saveLedger();
        }
    }

    public static void saveLedger() {
        if (ledger != null && ledgerPath != null) {
            ledger.forcedWorldDay = forcedWorldDay;
            ledger.save(ledgerPath);
        }
    }

    public static int getActiveWorldDay() {
        if (forcedWorldDay > 0) {
            return forcedWorldDay;
        }
        if (ledger == null) {
            return 1;
        }
        return (int) (ledger.activePlayTicks / 24000L) + 1;
    }

    public static long getActivePlayTicks() {
        return ledger != null ? ledger.activePlayTicks : 0L;
    }

    public static ZDayStage getCurrentStage() {
        return ZDayStage.getStageForDay(getActiveWorldDay());
    }

    public static boolean isStageUnlocked(int stageNumber) {
        return getCurrentStage().getStageNumber() >= stageNumber;
    }

    public static void setForcedWorldDay(int day) {
        forcedWorldDay = day;
        if (ledger != null) {
            ledger.forcedWorldDay = day;
            saveLedger();
        }
    }

    public static int getForcedWorldDay() {
        return forcedWorldDay;
    }

    public static int getNextUnlockDay(int currentStageNum) {
        for (ZDayStage stage : ZDayStage.values()) {
            if (stage.getStageNumber() > currentStageNum) {
                return stage.getUnlockDay();
            }
        }
        return -1;
    }

    public static List<String> buildCatchUpReportLines(UUID playerUuid) {
        if (ledger == null) {
            ledger = new ZDayPlayerLedger();
        }
        int currentWorldDay = getActiveWorldDay();
        int currentStageNum = getCurrentStage().getStageNumber();
        List<ZDayStage> missed = ledger.getCatchUpStagesAndMarkSeen(playerUuid, currentWorldDay, currentStageNum);
        saveLedger();
        if (missed.isEmpty()) {
            int nextDay = getNextUnlockDay(currentStageNum);
            String nextStr = nextDay > 0 ? "Day " + nextDay : "MAX REACHED";
            return List.of("§7[Z-Day] Day " + currentWorldDay + " (Stage " + currentStageNum + "/10) — Next escalation on " + nextStr + ". Type /zday for full dossier.");
        }
        List<String> lines = new ArrayList<>();
        lines.add("§4§l=====================================================");
        lines.add("§c§l[Z-Day] While You Were Away — Apocalypse Intelligence Briefing");
        lines.add("§7World Day: §e" + currentWorldDay + " §7| Threat Level: §cStage " + currentStageNum + "/10 (" + getCurrentStage().getTitle() + ")");
        for (ZDayStage stage : missed) {
            lines.add("§6[Day " + stage.getUnlockDay() + "] Stage " + stage.getStageNumber() + ": " + stage.getTitle());
            lines.add("  §f" + stage.getDescription());
        }
        lines.add("§7Type §e/zday§7 or §e/zday log§7 to review complete threat dossiers.");
        lines.add("§4§l=====================================================");
        return lines;
    }

    public static void onPlayerJoin(ServerPlayer player) {
        if (player == null) {
            return;
        }
        List<String> report = buildCatchUpReportLines(player.getUUID());
        for (String line : report) {
            player.sendSystemMessage(Component.literal(line));
        }
    }

    public static void resetForTests() {
        ledger = new ZDayPlayerLedger();
        ledgerPath = null;
        prevVanillaDayTime = -1L;
        forcedWorldDay = -1;
        currentServer = null;
    }

    public static ZDayPlayerLedger getLedger() {
        return ledger;
    }

    public static void setLedgerForTests(ZDayPlayerLedger testLedger) {
        ledger = testLedger;
    }
}
