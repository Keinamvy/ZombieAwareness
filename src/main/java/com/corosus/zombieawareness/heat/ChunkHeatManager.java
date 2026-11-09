package com.corosus.zombieawareness.heat;

import com.corosus.coroutil.util.CoroUtilPath;
import com.corosus.zombieawareness.EntityScent;
import com.corosus.zombieawareness.EnumSenseType;
import com.corosus.zombieawareness.ZAUtil;
import com.corosus.zombieawareness.ZombieAwareness;
import com.corosus.zombieawareness.bloodmoon.BloodmoonManager;
import com.corosus.zombieawareness.client.SoundRegistry;
import com.corosus.zombieawareness.config.ZAConfigZDay;
import com.corosus.zombieawareness.zday.ZDayProgressionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class ChunkHeatManager {

    private static final Map<Long, HeatEntry> heatMap = new ConcurrentHashMap<>();
    private static final List<ActiveHorde> activeHordes = new CopyOnWriteArrayList<>();

    public enum HordePhase {
        PHASE_1A_FAST_ZOMBIES,
        PHASE_1B_VENOM_SPIDERS,
        PHASE_2_BREACHERS_SKELETONS,
        PHASE_3_JUGGERNAUTS_WITCHES,
        PHASE_4_CLIMAX_SAPPERS
    }

    public static class ActiveHorde {
        public final BlockPos center;
        public final int totalDurationTicks;
        public final int subWaveCount;
        public final int worldStageNum;
        public int elapsedTicks;

        public ActiveHorde(BlockPos center, int totalDurationTicks, int subWaveCount, int worldStageNum) {
            this.center = center;
            this.totalDurationTicks = totalDurationTicks;
            this.subWaveCount = subWaveCount;
            this.worldStageNum = worldStageNum;
            this.elapsedTicks = 0;
        }
    }

    public static class HeatEntry {
        public int heat;
        public long lastTick;
        public long lastWaveTriggerTick;

        public HeatEntry(int heat, long lastTick) {
            this.heat = heat;
            this.lastTick = lastTick;
            this.lastWaveTriggerTick = -999999L;
        }
    }

    public static int computeHordeDurationTicks(int nearbyPlayers) {
        return 3600 + Math.max(0, nearbyPlayers - 1) * 600;
    }

    public static HordePhase resolveHordePhase(double progressZeroToOne, int worldStageNum) {
        HordePhase raw;
        if (progressZeroToOne < 0.15) {
            raw = HordePhase.PHASE_1A_FAST_ZOMBIES;
        } else if (progressZeroToOne < 0.35) {
            raw = HordePhase.PHASE_1B_VENOM_SPIDERS;
        } else if (progressZeroToOne < 0.60) {
            raw = HordePhase.PHASE_2_BREACHERS_SKELETONS;
        } else if (progressZeroToOne < 0.85) {
            raw = HordePhase.PHASE_3_JUGGERNAUTS_WITCHES;
        } else {
            raw = HordePhase.PHASE_4_CLIMAX_SAPPERS;
        }

        HordePhase maxAllowed;
        if (worldStageNum <= 4) {
            maxAllowed = HordePhase.PHASE_1B_VENOM_SPIDERS;
        } else if (worldStageNum <= 7) {
            maxAllowed = HordePhase.PHASE_2_BREACHERS_SKELETONS;
        } else if (worldStageNum <= 9) {
            maxAllowed = HordePhase.PHASE_3_JUGGERNAUTS_WITCHES;
        } else {
            maxAllowed = HordePhase.PHASE_4_CLIMAX_SAPPERS;
        }

        return raw.ordinal() > maxAllowed.ordinal() ? maxAllowed : raw;
    }

    public static int getActiveHordeCount() {
        return activeHordes.size();
    }

    public static void clearActiveHordes() {
        activeHordes.clear();
    }

    public static boolean isQuietFirearmHandlingSound(String namespace, String path) {
        if (path == null) return false;
        String p = path.toLowerCase(Locale.ROOT);
        String ns = namespace != null ? namespace.toLowerCase(Locale.ROOT) : "";

        if ("tacz".equals(ns)) {
            if (p.contains("reload") || p.contains("inspect") || p.contains("draw")
                    || p.contains("holster") || p.contains("handling") || p.contains("cock")
                    || p.contains("bolt") || p.contains("unload")) {
                return true;
            }
        }
        return p.contains("reload") || p.contains("inspect");
    }

    public static boolean isFirearmOrExplosionSound(String namespace, String path) {
        if (path == null) return false;
        String p = path.toLowerCase(Locale.ROOT);
        String ns = namespace != null ? namespace.toLowerCase(Locale.ROOT) : "";

        if (isQuietFirearmHandlingSound(namespace, path)) {
            return false;
        }

        if ("tacz".equals(ns)) {
            if (!p.contains("ui") && !p.contains("click")) {
                return true;
            }
        }

        return p.contains("gun") || p.contains("shoot") || p.contains("rifle")
                || p.contains("pistol") || p.contains("shotgun") || p.contains("sniper")
                || p.contains("smg") || p.contains("explode") || p.contains("explosion")
                || p.contains("grenade") || p.contains("firearm");
    }

    public static int addHeatAtBlockPos(int blockX, int blockZ, int amount, long gameTick) {
        int centerChunkX = blockX >> 4;
        int centerChunkZ = blockZ >> 4;
        int centerResult = 0;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int cx = centerChunkX + dx;
                int cz = centerChunkZ + dz;
                long key = ChunkPos.pack(cx, cz);
                HeatEntry entry = heatMap.computeIfAbsent(key, k -> new HeatEntry(0, gameTick));
                synchronized (entry) {
                    long elapsed = Math.max(0, gameTick - entry.lastTick);
                    int decay = (int) (elapsed / 4L);
                    entry.heat = Math.max(0, entry.heat - decay);
                    entry.heat += amount;
                    entry.lastTick = gameTick;
                    if (dx == 0 && dz == 0) {
                        centerResult = entry.heat;
                    }
                }
            }
        }
        return centerResult;
    }

    public static int getHeatAtBlockPos(int blockX, int blockZ, long gameTick) {
        int chunkX = blockX >> 4;
        int chunkZ = blockZ >> 4;
        long key = ChunkPos.pack(chunkX, chunkZ);
        HeatEntry entry = heatMap.get(key);
        if (entry == null) {
            return 0;
        }
        synchronized (entry) {
            long elapsed = Math.max(0, gameTick - entry.lastTick);
            int decay = (int) (elapsed / 4L);
            return Math.max(0, entry.heat - decay);
        }
    }

    public static void clearAllHeat() {
        heatMap.clear();
        clearActiveHordes();
    }

    public static int computeSquadScaledWaveSize(int baseSize, int stageNum, int nearbyPlayers, boolean bloodmoon) {
        int base = baseSize > 0 ? baseSize : 16;
        int squad = Math.max(1, nearbyPlayers);
        int stage = Math.max(1, stageNum);

        int wave = base + (squad - 1) * 4 + (stage - 1) * 2;
        if (bloodmoon) {
            wave *= 2;
        }
        return wave;
    }

    public static void playHordeRoar(ServerLevel level, double originX, double originY, double originZ) {
        if (level == null) return;
        try {
            SoundEvent customRoar = SoundRegistry.get("horde_roar");
            if (customRoar != null) {
                level.playSound((Entity) null, originX, originY, originZ, customRoar, SoundSource.HOSTILE, 4.0f, 0.85f);
            }
        } catch (Throwable ignored) {}
        level.playSound((Entity) null, originX, originY, originZ, SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 5.0f, 0.55f);
        level.playSound((Entity) null, originX, originY, originZ, SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 5.0f, 0.65f);
        level.playSound((Entity) null, originX, originY, originZ, SoundEvents.ZOMBIE_VILLAGER_CONVERTED, SoundSource.HOSTILE, 5.0f, 0.5f);
    }

    private static Mob createMobById(ServerLevel level, String vanillaId) {
        EntityType<?> type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(
                net.minecraft.resources.Identifier.withDefaultNamespace(vanillaId)
        );
        if (type == null) return null;
        Entity created = type.create(level, EntitySpawnReason.EVENT);
        return (created instanceof Mob m) ? m : null;
    }

    public static void spawnPhasedSubWave(ServerLevel level, BlockPos center, HordePhase phase, int count, int worldStageNum) {
        if (level == null || center == null || count <= 0) return;
        Random rand = new Random();

        for (int i = 0; i < count; i++) {
            double baseAngle = rand.nextDouble() * 2.0 * Math.PI;
            double spawnDist = 24.0 + rand.nextDouble() * 16.0;
            double targetX = center.getX() + Math.cos(baseAngle) * spawnDist;
            double targetZ = center.getZ() + Math.sin(baseAngle) * spawnDist;
            int spawnY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) targetX, (int) targetZ);

            try {
                Mob mob = null;
                float roll = rand.nextFloat();

                switch (phase) {
                    case PHASE_1A_FAST_ZOMBIES -> {
                        Zombie z = new Zombie(level);
                        z.addEffect(new MobEffectInstance(MobEffects.SPEED, 7200, 1));
                        mob = z;
                    }
                    case PHASE_1B_VENOM_SPIDERS -> {
                        if (roll < 0.65f) {
                            Zombie z = new Zombie(level);
                            z.addEffect(new MobEffectInstance(MobEffects.SPEED, 7200, 1));
                            mob = z;
                        } else if (roll < 0.85f) {
                            mob = createMobById(level, "cave_spider");
                        } else {
                            mob = createMobById(level, "spider");
                        }
                    }
                    case PHASE_2_BREACHERS_SKELETONS -> {
                        if (roll < 0.60f) {
                            Zombie z = new Zombie(level);
                            new com.nll.nightmareesm.GiveRandomPickaxe(z);
                            mob = z;
                        } else if (roll < 0.80f) {
                            mob = createMobById(level, rand.nextBoolean() ? "cave_spider" : "spider");
                        } else {
                            mob = createMobById(level, (worldStageNum >= 8 && rand.nextBoolean()) ? "stray" : "skeleton");
                        }
                    }
                    case PHASE_3_JUGGERNAUTS_WITCHES -> {
                        if (roll < 0.55f) {
                            Zombie z = new Zombie(level);
                            if (rand.nextBoolean()) {
                                com.nll.nightmareesm.nightmareesm.applyJuggernautLoadout(z);
                            } else {
                                z.addTag("za_crawler");
                                z.addEffect(new MobEffectInstance(MobEffects.SPEED, 7200, 1));
                            }
                            mob = z;
                        } else if (roll < 0.70f) {
                            mob = createMobById(level, "cave_spider");
                        } else if (roll < 0.90f) {
                            mob = createMobById(level, "skeleton");
                        } else {
                            mob = createMobById(level, "witch");
                        }
                    }
                    case PHASE_4_CLIMAX_SAPPERS -> {
                        if (roll < 0.50f) {
                            Zombie z = new Zombie(level);
                            if (rand.nextBoolean()) {
                                com.nll.nightmareesm.nightmareesm.applyJuggernautLoadout(z);
                            } else {
                                z.addTag("za_tnt_sapper");
                            }
                            mob = z;
                        } else if (roll < 0.65f) {
                            mob = createMobById(level, "cave_spider");
                        } else if (roll < 0.80f) {
                            mob = createMobById(level, "skeleton");
                        } else {
                            mob = createMobById(level, "creeper");
                        }
                    }
                }

                if (mob != null) {
                    mob.setPos(targetX, spawnY, targetZ);
                    try {
                        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), EntitySpawnReason.EVENT, null);
                    } catch (Throwable ignored) {}
                    level.addFreshEntity(mob);
                    try {
                        CoroUtilPath.tryMoveToXYZLongDist(mob, center.getX(), center.getY(), center.getZ(), 1.0);
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}
        }
    }

    public static void triggerSwarmWave(ServerLevel level, double originX, double originY, double originZ, int waveSize) {
        triggerSwarmWave(level, originX, originY, originZ, waveSize, 1, 1);
    }

    public static void triggerSwarmWave(ServerLevel level, double originX, double originY, double originZ, int waveSize, int nearbyPlayers, int stage) {
        if (level == null || waveSize <= 0) return;

        try {
            EntityScent waypoint = new EntityScent(ZombieAwareness.SENSE, level);
            waypoint.setPos(originX, originY, originZ);
            waypoint.setStrengthPeak(150);
            waypoint.setSenseType(EnumSenseType.WAYPOINT.ordinal());
            level.addFreshEntity(waypoint);
        } catch (Throwable ignored) {}

        BlockPos center = BlockPos.containing(originX, originY, originZ);
        HordePhase initialPhase = resolveHordePhase(0.0, stage);
        int initialWaveCount = Math.max(8, waveSize);
        spawnPhasedSubWave(level, center, initialPhase, initialWaveCount, stage);

        int durationTicks = computeHordeDurationTicks(nearbyPlayers);
        int subWaveCount = Math.max(6, waveSize / 3);
        activeHordes.add(new ActiveHorde(center, durationTicks, subWaveCount, stage));
    }

    public static void tickServer(ServerLevel overworld) {
        if (overworld == null || activeHordes.isEmpty()) return;
        List<ActiveHorde> toRemove = new ArrayList<>();
        for (ActiveHorde horde : activeHordes) {
            horde.elapsedTicks++;
            if (horde.elapsedTicks >= horde.totalDurationTicks) {
                toRemove.add(horde);
                continue;
            }
            if (horde.elapsedTicks % 300 == 0) {
                double progress = (double) horde.elapsedTicks / (double) horde.totalDurationTicks;
                HordePhase phase = resolveHordePhase(progress, horde.worldStageNum);
                spawnPhasedSubWave(overworld, horde.center, phase, horde.subWaveCount, horde.worldStageNum);
            }
        }
        if (!toRemove.isEmpty()) {
            activeHordes.removeAll(toRemove);
        }
    }

    public static void onSoundPlayed(ServerLevel level, double x, double y, double z, String namespace, String path) {
        if (level == null) return;
        if (isQuietFirearmHandlingSound(namespace, path)) return;
        if (!isFirearmOrExplosionSound(namespace, path)) return;
        if (!ZDayProgressionManager.isStageUnlocked(2)) return;

        try {
            ZAUtil.spawnOrBuffSenseAtPos(level, new Vector3d(x, y, z), EnumSenseType.SOUND, 112);
        } catch (Throwable ignored) {}

        long gameTick = level.getGameTime();
        int currentHeat = addHeatAtBlockPos((int) x, (int) z, 25, gameTick);

        int threshold = ZAConfigZDay.chunkHeatThreshold;
        if (currentHeat >= threshold) {
            long centerKey = ChunkPos.pack(((int) x) >> 4, ((int) z) >> 4);
            HeatEntry entry = heatMap.get(centerKey);
            if (entry != null && gameTick - entry.lastWaveTriggerTick >= 1200L) {
                entry.lastWaveTriggerTick = gameTick;
                int cx = ((int) x) >> 4;
                int cz = ((int) z) >> 4;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        HeatEntry e = heatMap.get(ChunkPos.pack(cx + dx, cz + dz));
                        if (e != null) {
                            e.lastWaveTriggerTick = gameTick;
                        }
                    }
                }

                playHordeRoar(level, x, y, z);

                int nearbyPlayers = 0;
                for (ServerPlayer player : level.players()) {
                    if (player.distanceToSqr(x, y, z) <= 64.0 * 64.0) {
                        nearbyPlayers++;
                        player.sendSystemMessage(Component.literal("§4§l[!] A MASSIVE HORDE HAS HEARD YOUR GUNFIRE — RUN!"));
                    }
                }
                if (nearbyPlayers == 0) nearbyPlayers = 1;

                int stage = 1;
                for (int s = 10; s >= 1; s--) {
                    if (ZDayProgressionManager.isStageUnlocked(s)) {
                        stage = s;
                        break;
                    }
                }

                boolean bloodmoon = BloodmoonManager.isBloodmoonActive(level);
                int waveSize = computeSquadScaledWaveSize(ZAConfigZDay.baseSwarmWaveSize, stage, nearbyPlayers, bloodmoon);
                triggerSwarmWave(level, x, y, z, waveSize, nearbyPlayers, stage);
            }
        }
    }
}
