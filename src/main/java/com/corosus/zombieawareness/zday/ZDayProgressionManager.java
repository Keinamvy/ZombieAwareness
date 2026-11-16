package com.corosus.zombieawareness.zday;

import com.corosus.zombieawareness.config.ZAConfigZDay;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ZDayProgressionManager {

    private record StageCache(int worldDay, int forcedDay, int configSignature, ZDayStage stage) {}

    private static final ExecutorService LEDGER_IO_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ZA-ZDay-Ledger-IO");
        t.setDaemon(true);
        return t;
    });

    private static volatile ZDayPlayerLedger ledger = new ZDayPlayerLedger();
    private static volatile Path ledgerPath = null;
    private static volatile long prevVanillaDayTime = -1L;
    private static volatile int forcedWorldDay = -1;
    private static volatile MinecraftServer currentServer = null;
    private static volatile StageCache cachedStage = null;

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
                cachedStage = null;
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
        ZDayPlayerLedger currentLedger = ledger;
        int onlineCount = server.getPlayerCount();
        long currentVanillaDayTime = server.overworld() != null ? server.overworld().getDefaultClockTime() : 0L;
        if (prevVanillaDayTime < 0L) {
            prevVanillaDayTime = currentVanillaDayTime;
        }

        synchronized (currentLedger) {
            currentLedger.activePlayTicks = advanceTickLogic(currentLedger.activePlayTicks, prevVanillaDayTime, currentVanillaDayTime, onlineCount);
        }
        prevVanillaDayTime = currentVanillaDayTime;

        ZDayStage currentStage = getCurrentStage();
        int currentStageNum = currentStage.getStageNumber();

        if (currentStageNum > currentLedger.lastBroadcastedStage) {
            int worldDay = getActiveWorldDay();
            String title = currentStage.getTitle();
            String desc = currentStage.getDescription();
            Component announceHeader = Component.literal("§4§l[Z-Day Escalation] Dawn of Day " + worldDay + " — Stage " + currentStageNum + ": " + title);
            Component announceDesc = Component.literal("§c" + desc);
            server.getPlayerList().broadcastSystemMessage(announceHeader, false);
            server.getPlayerList().broadcastSystemMessage(announceDesc, false);

            synchronized (currentLedger) {
                currentLedger.lastBroadcastedStage = currentStageNum;
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    currentLedger.recordPlayerSeen(player.getUUID(), worldDay, currentStageNum);
                }
            }
            saveLedger();
        } else if (server.getTickCount() % 600 == 0) {
            saveLedgerAsync();
        }
    }

    public static void saveLedger() {
        ZDayPlayerLedger currentLedger = ledger;
        Path path = ledgerPath;
        if (currentLedger != null && path != null) {
            synchronized (currentLedger) {
                currentLedger.forcedWorldDay = forcedWorldDay;
                currentLedger.save(path);
            }
        }
    }

    public static void saveLedgerAsync() {
        ZDayPlayerLedger currentLedger = ledger;
        Path path = ledgerPath;
        if (currentLedger != null && path != null) {
            String snapshot;
            synchronized (currentLedger) {
                currentLedger.forcedWorldDay = forcedWorldDay;
                snapshot = currentLedger.toJsonSnapshot();
            }
            LEDGER_IO_EXECUTOR.execute(() -> ZDayPlayerLedger.writeSnapshot(path, snapshot));
        }
    }

    public static int getActiveWorldDay() {
        int forced = forcedWorldDay;
        if (forced > 0) {
            return forced;
        }
        ZDayPlayerLedger currentLedger = ledger;
        if (currentLedger == null) {
            return 1;
        }
        return (int) (currentLedger.activePlayTicks / 24000L) + 1;
    }

    public static long getActivePlayTicks() {
        ZDayPlayerLedger currentLedger = ledger;
        return currentLedger != null ? currentLedger.activePlayTicks : 0L;
    }

    private static int computeStageConfigSignature() {
        int h = ZAConfigZDay.dayStage2TaCZ;
        h = 31 * h + ZAConfigZDay.dayStage3Blackout;
        h = 31 * h + ZAConfigZDay.dayStage4Bloodmoon;
        h = 31 * h + ZAConfigZDay.dayStage5Breachers;
        h = 31 * h + ZAConfigZDay.dayStage6WebsAndSprinters;
        h = 31 * h + ZAConfigZDay.dayStage7Screamers;
        h = 31 * h + ZAConfigZDay.dayStage8Engineers;
        h = 31 * h + ZAConfigZDay.dayStage9Juggernauts;
        h = 31 * h + ZAConfigZDay.dayStage10Collapse;
        return h;
    }

    public static ZDayStage getCurrentStage() {
        int forced = forcedWorldDay;
        int worldDay = getActiveWorldDay();
        int sig = computeStageConfigSignature();
        StageCache cache = cachedStage;
        if (cache != null && cache.worldDay() == worldDay && cache.forcedDay() == forced && cache.configSignature() == sig) {
            return cache.stage();
        }
        ZDayStage resolved = ZDayStage.getStageForDay(worldDay);
        cachedStage = new StageCache(worldDay, forced, sig, resolved);
        return resolved;
    }

    public static boolean isStageUnlocked(int stageNumber) {
        return getCurrentStage().getStageNumber() >= stageNumber;
    }

    public static void setForcedWorldDay(int day) {
        forcedWorldDay = day;
        cachedStage = null;
        ZDayPlayerLedger currentLedger = ledger;
        if (currentLedger != null) {
            synchronized (currentLedger) {
                currentLedger.forcedWorldDay = day;
            }
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
        cachedStage = null;
    }

    public static ZDayPlayerLedger getLedger() {
        return ledger;
    }

    public static void setLedgerForTests(ZDayPlayerLedger testLedger) {
        ledger = testLedger;
    }
}
