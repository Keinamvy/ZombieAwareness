package com.corosus.zahordeoptimizer.config;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;

/**
 * Runtime configuration for the ZA Horde Optimizer addon.
 * Stored in ./config/zombieawareness/HordeOptimizer.properties when running on Fabric.
 */
public final class HordeOptimizerConfig {

    public static final File DEFAULT_CONFIG_FILE = new File("./config/zombieawareness/HordeOptimizer.properties");

    public static volatile boolean enabled = true;

    // Feature 1: Horde collision suppression
    public static volatile boolean disableHordeCollision = true;
    public static volatile boolean hordeToHordeCollisionOnly = true;
    public static volatile boolean preventHordeEntityCramming = true;
    public static volatile boolean makeHordeMobsUnpushable = false;
    public static volatile boolean includeNearbyHostilesInActiveHorde = false;

    // Feature 2: Spatial cluster optimization
    public static volatile boolean enableClusterOptimization = true;
    public static volatile int clusterCellSizeXZ = 6;
    public static volatile int clusterCellSizeY = 8;
    public static volatile int minClusterSizeToOptimize = 2;
    public static volatile int pathCacheTtlTicks = 20;
    public static volatile double pathCacheTargetTolerance = 4.0;
    public static volatile double maxFollowerPathReuseDistance = 14.0;
    public static volatile double closeRangeDirectPathDist = 6.0;
    public static volatile int maxThrottleInterval = 8;
    public static volatile long staleMemberTimeoutTicks = 100L;
    public static volatile boolean debugLogging = false;

    private HordeOptimizerConfig() {
    }

    public static void resetDefaults() {
        enabled = true;
        disableHordeCollision = true;
        hordeToHordeCollisionOnly = true;
        preventHordeEntityCramming = true;
        makeHordeMobsUnpushable = false;
        includeNearbyHostilesInActiveHorde = false;
        enableClusterOptimization = true;
        clusterCellSizeXZ = 6;
        clusterCellSizeY = 8;
        minClusterSizeToOptimize = 2;
        pathCacheTtlTicks = 20;
        pathCacheTargetTolerance = 4.0;
        maxFollowerPathReuseDistance = 14.0;
        closeRangeDirectPathDist = 6.0;
        maxThrottleInterval = 8;
        staleMemberTimeoutTicks = 100L;
        debugLogging = false;
    }

    public static void loadOrCreate(File configFile) {
        if (configFile == null) {
            return;
        }
        File parent = configFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        if (configFile.exists()) {
            Properties props = new Properties();
            try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
                props.load(reader);
                enabled = parseBoolean(props, "enabled", enabled);
                disableHordeCollision = parseBoolean(props, "disableHordeCollision", disableHordeCollision);
                hordeToHordeCollisionOnly = parseBoolean(props, "hordeToHordeCollisionOnly", hordeToHordeCollisionOnly);
                preventHordeEntityCramming = parseBoolean(props, "preventHordeEntityCramming", preventHordeEntityCramming);
                makeHordeMobsUnpushable = parseBoolean(props, "makeHordeMobsUnpushable", makeHordeMobsUnpushable);
                includeNearbyHostilesInActiveHorde = parseBoolean(props, "includeNearbyHostilesInActiveHorde", includeNearbyHostilesInActiveHorde);
                enableClusterOptimization = parseBoolean(props, "enableClusterOptimization", enableClusterOptimization);
                clusterCellSizeXZ = Math.max(2, parseInt(props, "clusterCellSizeXZ", clusterCellSizeXZ));
                clusterCellSizeY = Math.max(2, parseInt(props, "clusterCellSizeY", clusterCellSizeY));
                minClusterSizeToOptimize = Math.max(2, parseInt(props, "minClusterSizeToOptimize", minClusterSizeToOptimize));
                pathCacheTtlTicks = Math.max(1, parseInt(props, "pathCacheTtlTicks", pathCacheTtlTicks));
                pathCacheTargetTolerance = Math.max(0.5, parseDouble(props, "pathCacheTargetTolerance", pathCacheTargetTolerance));
                maxFollowerPathReuseDistance = Math.max(2.0, parseDouble(props, "maxFollowerPathReuseDistance", maxFollowerPathReuseDistance));
                closeRangeDirectPathDist = Math.max(0.0, parseDouble(props, "closeRangeDirectPathDist", closeRangeDirectPathDist));
                maxThrottleInterval = Math.max(1, parseInt(props, "maxThrottleInterval", maxThrottleInterval));
                staleMemberTimeoutTicks = Math.max(20L, parseLong(props, "staleMemberTimeoutTicks", staleMemberTimeoutTicks));
                debugLogging = parseBoolean(props, "debugLogging", debugLogging);
            } catch (IOException ignored) {
            }
        }
        save(configFile);
    }

    public static void save(File configFile) {
        if (configFile == null) {
            return;
        }
        File parent = configFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(configFile))) {
            writer.write("# ZA Horde Optimizer configuration\n");
            writer.write("enabled=" + enabled + "\n");
            writer.write("disableHordeCollision=" + disableHordeCollision + "\n");
            writer.write("hordeToHordeCollisionOnly=" + hordeToHordeCollisionOnly + "\n");
            writer.write("preventHordeEntityCramming=" + preventHordeEntityCramming + "\n");
            writer.write("makeHordeMobsUnpushable=" + makeHordeMobsUnpushable + "\n");
            writer.write("includeNearbyHostilesInActiveHorde=" + includeNearbyHostilesInActiveHorde + "\n");
            writer.write("enableClusterOptimization=" + enableClusterOptimization + "\n");
            writer.write("clusterCellSizeXZ=" + clusterCellSizeXZ + "\n");
            writer.write("clusterCellSizeY=" + clusterCellSizeY + "\n");
            writer.write("minClusterSizeToOptimize=" + minClusterSizeToOptimize + "\n");
            writer.write("pathCacheTtlTicks=" + pathCacheTtlTicks + "\n");
            writer.write("pathCacheTargetTolerance=" + pathCacheTargetTolerance + "\n");
            writer.write("maxFollowerPathReuseDistance=" + maxFollowerPathReuseDistance + "\n");
            writer.write("closeRangeDirectPathDist=" + closeRangeDirectPathDist + "\n");
            writer.write("maxThrottleInterval=" + maxThrottleInterval + "\n");
            writer.write("staleMemberTimeoutTicks=" + staleMemberTimeoutTicks + "\n");
            writer.write("debugLogging=" + debugLogging + "\n");
        } catch (IOException ignored) {
        }
    }

    private static boolean parseBoolean(Properties props, String key, boolean fallback) {
        String raw = props.getProperty(key);
        return raw != null ? Boolean.parseBoolean(raw.trim()) : fallback;
    }

    private static int parseInt(Properties props, String key, int fallback) {
        String raw = props.getProperty(key);
        if (raw == null) return fallback;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static long parseLong(Properties props, String key, long fallback) {
        String raw = props.getProperty(key);
        if (raw == null) return fallback;
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static double parseDouble(Properties props, String key, double fallback) {
        String raw = props.getProperty(key);
        if (raw == null) return fallback;
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
