package com.corosus.zombieawareness.zday;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ZDayPlayerLedger {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public long activePlayTicks = 0L;
    public int forcedWorldDay = -1;
    public int lastBroadcastedStage = 1;
    public Map<String, Integer> playerLastSeenStage = new HashMap<>();
    public Map<String, Integer> playerLastSeenDay = new HashMap<>();

    public ZDayPlayerLedger() {
    }

    public static ZDayPlayerLedger load(Path file) {
        if (file != null && Files.exists(file)) {
            try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                ZDayPlayerLedger ledger = GSON.fromJson(reader, ZDayPlayerLedger.class);
                if (ledger != null) {
                    if (ledger.playerLastSeenStage == null) {
                        ledger.playerLastSeenStage = new HashMap<>();
                    }
                    if (ledger.playerLastSeenDay == null) {
                        ledger.playerLastSeenDay = new HashMap<>();
                    }
                    return ledger;
                }
            } catch (Exception e) {
                System.err.println("[ZombieAwareness] Failed to load ZDayPlayerLedger from " + file + ": " + e.getMessage());
            }
        }
        return new ZDayPlayerLedger();
    }

    public synchronized void save(Path file) {
        if (file == null) {
            return;
        }
        try {
            if (file.getParent() != null && !Files.exists(file.getParent())) {
                Files.createDirectories(file.getParent());
            }
            try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
        } catch (Exception e) {
            System.err.println("[ZombieAwareness] Failed to save ZDayPlayerLedger to " + file + ": " + e.getMessage());
        }
    }

    public synchronized List<ZDayStage> getCatchUpStagesAndMarkSeen(UUID playerUuid, int currentWorldDay, int currentStageNum) {
        if (playerUuid == null) {
            return Collections.emptyList();
        }
        String uuidStr = playerUuid.toString();
        int lastStage = playerLastSeenStage.getOrDefault(uuidStr, 1);
        List<ZDayStage> missed = ZDayStage.getUnlockedStagesBetween(lastStage, currentStageNum);
        playerLastSeenStage.put(uuidStr, currentStageNum);
        playerLastSeenDay.put(uuidStr, currentWorldDay);
        return missed;
    }

    public synchronized void recordPlayerSeen(UUID playerUuid, int day, int stage) {
        if (playerUuid == null) {
            return;
        }
        playerLastSeenStage.put(playerUuid.toString(), stage);
        playerLastSeenDay.put(playerUuid.toString(), day);
    }

    public synchronized int getPlayerLastSeenDay(UUID playerUuid, int defaultDay) {
        if (playerUuid == null) {
            return defaultDay;
        }
        return playerLastSeenDay.getOrDefault(playerUuid.toString(), defaultDay);
    }

    public synchronized int getPlayerLastSeenStage(UUID playerUuid, int defaultStage) {
        if (playerUuid == null) {
            return defaultStage;
        }
        return playerLastSeenStage.getOrDefault(playerUuid.toString(), defaultStage);
    }

    public synchronized List<ZDayStage> getCatchUpStages(UUID playerUuid, int currentStage) {
        if (playerUuid == null) {
            return Collections.emptyList();
        }
        int lastStage = playerLastSeenStage.getOrDefault(playerUuid.toString(), 1);
        return ZDayStage.getUnlockedStagesBetween(lastStage, currentStage);
    }
}
