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
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
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
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class ChunkHeatManager {

    public static final int MAX_CHUNK_HEAT = 150;
    public static final long POST_HORDE_BREATHER_TICKS = 1800L;
    public static final double GROUP_CLUSTER_RADIUS = 96.0;
    private static final Map<Long, HeatEntry> heatMap = new ConcurrentHashMap<>();
    private static final List<ActiveHorde> activeHordes = new CopyOnWriteArrayList<>();
    private static final Map<UUID, Long> playerBreatherUntilTick = new ConcurrentHashMap<>();

    public enum HordePhase {
        PHASE_1A_FAST_ZOMBIES,
        PHASE_1B_VENOM_SPIDERS,
        PHASE_2_BREACHERS_SKELETONS,
        PHASE_3_JUGGERNAUTS_WITCHES,
        PHASE_4_CLIMAX_SAPPERS
    }

    public static class ActiveHorde {
        public BlockPos center;
        public final Set<UUID> targetGroupPlayerIds;
        public final int totalDurationTicks;
        public final int subWaveCount;
        public final int worldStageNum;
        public final double approachAngleRad;
        public int elapsedTicks;
        public int noTargetTicks = 0;
        public boolean climaxRoarPlayed = false;

        public ActiveHorde(BlockPos center, Set<UUID> targetGroupPlayerIds, int totalDurationTicks, int subWaveCount, int worldStageNum, double approachAngleRad) {
            this.center = center;
            this.targetGroupPlayerIds = (targetGroupPlayerIds != null)
                    ? Collections.unmodifiableSet(new HashSet<>(targetGroupPlayerIds))
                    : Collections.emptySet();
            this.totalDurationTicks = totalDurationTicks;
            this.subWaveCount = subWaveCount;
            this.worldStageNum = worldStageNum;
            this.approachAngleRad = approachAngleRad;
            this.elapsedTicks = 0;
        }

        public ActiveHorde(BlockPos center, Set<UUID> targetGroupPlayerIds, int totalDurationTicks, int subWaveCount, int worldStageNum) {
            this(center, targetGroupPlayerIds, totalDurationTicks, subWaveCount, worldStageNum, new Random().nextDouble() * 2.0 * Math.PI);
        }

        public ActiveHorde(BlockPos center, int totalDurationTicks, int subWaveCount, int worldStageNum) {
            this(center, Collections.emptySet(), totalDurationTicks, subWaveCount, worldStageNum);
        }

        public BlockPos getStagingOrigin() {
            return BlockPos.containing(
                    center.getX() + Math.cos(approachAngleRad) * 45.0,
                    center.getY(),
                    center.getZ() + Math.sin(approachAngleRad) * 45.0
            );
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

    public static boolean canTriggerHordeForGroup(BlockPos triggerPos, Collection<UUID> groupPlayerIds) {
        activeHordes.removeIf(h -> h.elapsedTicks >= h.totalDurationTicks);
        for (ActiveHorde horde : activeHordes) {
            if (horde.elapsedTicks >= horde.totalDurationTicks) {
                continue;
            }
            if (groupPlayerIds != null && !groupPlayerIds.isEmpty() && horde.targetGroupPlayerIds != null && !horde.targetGroupPlayerIds.isEmpty()) {
                if (!Collections.disjoint(horde.targetGroupPlayerIds, groupPlayerIds)) {
                    return false;
                }
            }
            if (triggerPos != null && horde.center != null) {
                if (triggerPos.distSqr(horde.center) <= GROUP_CLUSTER_RADIUS * GROUP_CLUSTER_RADIUS) {
                    return false;
                }
            }
        }
        return true;
    }

    public static boolean tryRegisterHordeForGroup(BlockPos center, Collection<UUID> groupPlayerIds, int nearbyPlayers, int subWaveCount, int worldStageNum, double approachAngleRad) {
        if (!canTriggerHordeForGroup(center, groupPlayerIds)) {
            return false;
        }
        int durationTicks = computeHordeDurationTicks(Math.max(1, nearbyPlayers));
        Set<UUID> ids = (groupPlayerIds == null) ? Collections.emptySet() : Collections.unmodifiableSet(new HashSet<>(groupPlayerIds));
        activeHordes.add(new ActiveHorde(center, ids, durationTicks, subWaveCount, worldStageNum, approachAngleRad));
        return true;
    }

    public static boolean tryRegisterHordeForGroup(BlockPos center, Collection<UUID> groupPlayerIds, int nearbyPlayers, int subWaveCount, int worldStageNum) {
        return tryRegisterHordeForGroup(center, groupPlayerIds, nearbyPlayers, subWaveCount, worldStageNum, new Random().nextDouble() * 2.0 * Math.PI);
    }

    public static List<ActiveHorde> getActiveHordes() {
        return Collections.unmodifiableList(activeHordes);
    }

    public static boolean isPlayerLockedByHordeOrBloodmoon(UUID playerUuid, BlockPos playerPos, boolean isBloodmoonActive) {
        if (isBloodmoonActive) {
            return true;
        }
        Collection<UUID> ids = (playerUuid != null) ? Collections.singleton(playerUuid) : Collections.emptySet();
        return !canTriggerHordeForGroup(playerPos, ids);
    }

    public static float computeRoarVolumeMultiplier(double distanceBlocks, boolean obstructedByWall) {
        double distRatio = Math.min(1.0, Math.max(0.0, distanceBlocks) / 128.0);
        double distanceFactor = 1.0 - (0.10 * distRatio);
        double obstructionFactor = obstructedByWall ? 0.80 : 1.0;
        return (float) (distanceFactor * obstructionFactor);
    }

    public static double[] computeDirectionalSoundPosition(double playerX, double playerY, double playerZ, double roarOriginX, double roarOriginY, double roarOriginZ) {
        double dx = roarOriginX - playerX;
        double dy = roarOriginY - playerY;
        double dz = roarOriginZ - playerZ;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist < 0.001) {
            return new double[]{ playerX, playerY, playerZ };
        }
        double ux = dx / dist;
        double uy = dy / dist;
        double uz = dz / dist;
        return new double[]{
            playerX + ux * 4.0,
            playerY + uy * 4.0,
            playerZ + uz * 4.0
        };
    }

    public static double[] computeDirectionalSpawnOffset(double approachAngleRad, double normalizedAngleMinusOneToOne, double normalizedDistZeroToOne) {
        double clampedAngle = Math.max(-1.0, Math.min(1.0, normalizedAngleMinusOneToOne));
        double clampedDist = Math.max(0.0, Math.min(1.0, normalizedDistZeroToOne));
        double angle = approachAngleRad + clampedAngle * Math.toRadians(40.0);
        double dist = 38.0 + clampedDist * 14.0;
        return new double[]{ Math.cos(angle) * dist, Math.sin(angle) * dist };
    }

    public static int getActiveHordeCount() {
        activeHordes.removeIf(h -> h.elapsedTicks >= h.totalDurationTicks);
        return activeHordes.size();
    }

    public static void clearActiveHordes() {
        activeHordes.clear();
    }

    public static void markGroupPostHordeBreather(Collection<UUID> groupPlayerIds, long currentTick) {
        if (groupPlayerIds == null || groupPlayerIds.isEmpty()) return;
        long until = currentTick + POST_HORDE_BREATHER_TICKS;
        for (UUID id : groupPlayerIds) {
            if (id != null) {
                playerBreatherUntilTick.put(id, until);
            }
        }
    }

    public static boolean isGroupInPostHordeBreather(Collection<UUID> groupPlayerIds, long currentTick) {
        if (groupPlayerIds == null || groupPlayerIds.isEmpty()) return false;
        for (UUID id : groupPlayerIds) {
            if (id != null) {
                Long until = playerBreatherUntilTick.get(id);
                if (until != null && until > currentTick) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void resetHeatAround(int blockX, int blockZ, long gameTick) {
        int centerChunkX = blockX >> 4;
        int centerChunkZ = blockZ >> 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                long key = ChunkPos.pack(centerChunkX + dx, centerChunkZ + dz);
                HeatEntry entry = heatMap.computeIfAbsent(key, k -> new HeatEntry(0, gameTick));
                synchronized (entry) {
                    entry.heat = 0;
                    entry.lastTick = gameTick;
                    entry.lastWaveTriggerTick = gameTick;
                }
            }
        }
    }

    public static void clampHeatAround(int blockX, int blockZ, int maxHeat) {
        int centerChunkX = blockX >> 4;
        int centerChunkZ = blockZ >> 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                long key = ChunkPos.pack(centerChunkX + dx, centerChunkZ + dz);
                HeatEntry entry = heatMap.get(key);
                if (entry != null) {
                    synchronized (entry) {
                        if (entry.heat > maxHeat) {
                            entry.heat = maxHeat;
                        }
                    }
                }
            }
        }
    }

    public static boolean isQuietFirearmHandlingSound(String namespace, String path) {
        if (path == null) return false;
        String p = path.toLowerCase(Locale.ROOT);
        String ns = namespace != null ? namespace.toLowerCase(Locale.ROOT) : "";

        if ("tacz".equals(ns)) {
            if (p.contains("reload") || p.contains("inspect") || p.contains("draw")
                    || p.contains("holster") || p.contains("handling") || p.contains("cock")
                    || p.contains("bolt") || p.contains("unload") || p.contains("dry_fire")) {
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

        if ("minecraft".equals(ns)) {
            return false;
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
                    entry.heat = Math.min(MAX_CHUNK_HEAT, entry.heat + amount);
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
        playerBreatherUntilTick.clear();
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

    private static void sendDirectRoarSound(ServerLevel level, ServerPlayer player, SoundEvent soundEvent, double x, double y, double z, float volume, float pitch) {
        if (soundEvent == null || player == null) return;
        try {
            long seed = level != null ? level.getRandom().nextLong() : 0L;
            Holder<SoundEvent> soundHolder = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(soundEvent);
            ClientboundSoundPacket packet = new ClientboundSoundPacket(soundHolder, SoundSource.HOSTILE, x, y, z, volume, pitch, seed);
            player.connection.send(packet);
        } catch (Throwable t) {
            try {
                if (level != null) {
                    level.playSound((Entity) null, x, y, z, soundEvent, SoundSource.HOSTILE, volume, pitch);
                }
            } catch (Throwable ignored) {}
        }
    }

    public static void playHordeRoar(ServerLevel level, double originX, double originY, double originZ) {
        if (level == null) return;
        boolean playedDirectly = false;
        if (level.players() != null && !level.players().isEmpty()) {
            SoundEvent customRoar = null;
            try {
                customRoar = SoundRegistry.get("horde_roar");
            } catch (Throwable ignored) {}

            for (ServerPlayer player : level.players()) {
                if (player == null) continue;
                double distSq = player.distanceToSqr(originX, originY, originZ);
                if (distSq > 160.0 * 160.0) continue;
                double dist = Math.sqrt(distSq);

                boolean obstructed = false;
                try {
                    HitResult hit = level.clip(new ClipContext(
                            player.getEyePosition(),
                            new Vec3(originX, originY + 1.0, originZ),
                            ClipContext.Block.COLLIDER,
                            ClipContext.Fluid.NONE,
                            player
                    ));
                    obstructed = hit != null && hit.getType() != HitResult.Type.MISS;
                } catch (Throwable ignored) {}

                float volMult = computeRoarVolumeMultiplier(dist, obstructed);
                double[] soundPos = computeDirectionalSoundPosition(player.getX(), player.getEyeY(), player.getZ(), originX, originY, originZ);
                float pitch = obstructed ? 0.80f : 0.85f;

                if (customRoar != null) {
                    sendDirectRoarSound(level, player, customRoar, soundPos[0], soundPos[1], soundPos[2], 1.0f * volMult, pitch);
                }
                sendDirectRoarSound(level, player, SoundEvents.WARDEN_ROAR, soundPos[0], soundPos[1], soundPos[2], 1.0f * volMult, pitch * 0.7f);
                sendDirectRoarSound(level, player, SoundEvents.RAVAGER_ROAR, soundPos[0], soundPos[1], soundPos[2], 1.0f * volMult, pitch * 0.8f);
                playedDirectly = true;
            }
        }

        if (!playedDirectly) {
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
    }

    private static Mob createMobById(ServerLevel level, String vanillaId) {
        EntityType<?> type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(
                net.minecraft.resources.Identifier.withDefaultNamespace(vanillaId)
        );
        if (type == null) return null;
        Entity created = type.create(level, EntitySpawnReason.EVENT);
        return (created instanceof Mob m) ? m : null;
    }

    public static int findSafeSpawnY(ServerLevel level, int x, int preferredY, int z) {
        if (level == null) return preferredY;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = preferredY + 6; y >= preferredY - 8; y--) {
            pos.set(x, y, z);
            if (!level.getBlockState(pos.below()).isAir()
                    && level.getBlockState(pos).isAir()
                    && level.getBlockState(pos.above()).isAir()) {
                return y;
            }
        }
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
    }

    public static void spawnPhasedSubWave(ServerLevel level, BlockPos center, HordePhase phase, int count, int worldStageNum) {
        spawnPhasedSubWave(level, center, new Random().nextDouble() * 2.0 * Math.PI, phase, count, worldStageNum);
    }

    public static void spawnPhasedSubWave(ServerLevel level, BlockPos center, double approachAngleRad, HordePhase phase, int count, int worldStageNum) {
        if (level == null || center == null || count <= 0) return;
        Random rand = new Random();
        double coneHalfWidthRad = Math.toRadians(40.0);

        for (int i = 0; i < count; i++) {
            double angleOffset = (rand.nextDouble() * 2.0 - 1.0) * coneHalfWidthRad;
            double spawnAngle = approachAngleRad + angleOffset;
            double spawnDist = 38.0 + rand.nextDouble() * 14.0;
            double targetX = center.getX() + Math.cos(spawnAngle) * spawnDist;
            double targetZ = center.getZ() + Math.sin(spawnAngle) * spawnDist;
            int spawnY = findSafeSpawnY(level, (int) targetX, center.getY(), (int) targetZ);

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
                    mob.addTag("za_horde_mob");
                    if (mob instanceof Zombie || mob instanceof AbstractSkeleton) {
                        mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 7200, 0, false, false));
                    }
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
        triggerSwarmWave(level, originX, originY, originZ, waveSize, nearbyPlayers, stage, Collections.emptySet());
    }

    public static void triggerSwarmWave(ServerLevel level, double originX, double originY, double originZ, int waveSize, int nearbyPlayers, int stage, Collection<UUID> groupPlayerIds) {
        triggerSwarmWave(level, originX, originY, originZ, waveSize, nearbyPlayers, stage, groupPlayerIds, new Random().nextDouble() * 2.0 * Math.PI);
    }

    public static void triggerSwarmWave(ServerLevel level, double originX, double originY, double originZ, int waveSize, int nearbyPlayers, int stage, Collection<UUID> groupPlayerIds, double approachAngleRad) {
        if (level == null || waveSize <= 0) return;
        BlockPos center = BlockPos.containing(originX, originY, originZ);
        if (!canTriggerHordeForGroup(center, groupPlayerIds)) {
            return;
        }

        try {
            EntityScent waypoint = new EntityScent(ZombieAwareness.SENSE, level);
            waypoint.setPos(originX, originY, originZ);
            waypoint.setStrengthPeak(150);
            waypoint.setSenseType(EnumSenseType.WAYPOINT.ordinal());
            level.addFreshEntity(waypoint);
        } catch (Throwable ignored) {}

        HordePhase initialPhase = resolveHordePhase(0.0, stage);
        int initialWaveCount = Math.max(8, waveSize);
        spawnPhasedSubWave(level, center, approachAngleRad, initialPhase, initialWaveCount, stage);

        int subWaveCount = Math.max(6, waveSize / 3);
        tryRegisterHordeForGroup(center, groupPlayerIds, nearbyPlayers, subWaveCount, stage, approachAngleRad);
    }

    public static void tickServer(ServerLevel overworld) {
        if (overworld == null || activeHordes.isEmpty()) return;
        List<ActiveHorde> toRemove = new ArrayList<>();
        long currentTick = overworld.getGameTime();

        for (ActiveHorde horde : activeHordes) {
            horde.elapsedTicks++;
            if (horde.elapsedTicks >= horde.totalDurationTicks) {
                toRemove.add(horde);
                markGroupPostHordeBreather(horde.targetGroupPlayerIds, currentTick);
                resetHeatAround(horde.center.getX(), horde.center.getZ(), currentTick);
                continue;
            }

            if (!horde.targetGroupPlayerIds.isEmpty() && overworld.players() != null) {
                ServerPlayer closestPlayer = null;
                double closestDistSq = Double.MAX_VALUE;
                double maxRangeSq = 128.0 * 128.0;

                for (ServerPlayer player : overworld.players()) {
                    if (player != null && player.isAlive() && horde.targetGroupPlayerIds.contains(player.getUUID())) {
                        double distSq = player.distanceToSqr(horde.center.getX() + 0.5, horde.center.getY() + 0.5, horde.center.getZ() + 0.5);
                        if (distSq <= maxRangeSq && distSq < closestDistSq) {
                            closestDistSq = distSq;
                            closestPlayer = player;
                        }
                    }
                }

                if (closestPlayer != null) {
                    horde.center = closestPlayer.blockPosition();
                    horde.noTargetTicks = 0;
                } else {
                    horde.noTargetTicks++;
                    if (horde.noTargetTicks >= 100) {
                        toRemove.add(horde);
                        markGroupPostHordeBreather(horde.targetGroupPlayerIds, currentTick);
                        resetHeatAround(horde.center.getX(), horde.center.getZ(), currentTick);
                        continue;
                    }
                }
            }

            if (overworld.players() == null || overworld.players().isEmpty()) {
                continue;
            }

            double progress = (double) horde.elapsedTicks / (double) horde.totalDurationTicks;
            HordePhase phase = resolveHordePhase(progress, horde.worldStageNum);

            if (phase == HordePhase.PHASE_4_CLIMAX_SAPPERS && !horde.climaxRoarPlayed) {
                horde.climaxRoarPlayed = true;
                BlockPos staging = horde.getStagingOrigin();
                playHordeRoar(overworld, staging.getX(), staging.getY(), staging.getZ());
            }

            if (horde.elapsedTicks % 300 == 0) {
                spawnPhasedSubWave(overworld, horde.center, horde.approachAngleRad, phase, horde.subWaveCount, horde.worldStageNum);
            }
        }
        if (!toRemove.isEmpty()) {
            activeHordes.removeAll(toRemove);
        }
    }

    public static boolean isSuppressedTaCZSound(String soundName) {
        if (soundName == null) return false;
        String lower = soundName.toLowerCase(Locale.ROOT);
        return lower.equals("silence") || lower.equals("silence_3p") || lower.contains("silence");
    }

    public static void onTaCZGunSound(ServerLevel level, double x, double y, double z, int taczDistance, String soundName) {
        if (soundName == null) return;
        if (level != null && level.dimension() != Level.OVERWORLD) return;
        if (level == null) {
            // Unit test fallback path when Minecraft ServerLevel is not bootstrapped
            String lower = soundName.toLowerCase(Locale.ROOT);
            if (isSuppressedTaCZSound(lower)) {
                if (ZDayProgressionManager.isStageUnlocked(2)) {
                    int threshold = ZAConfigZDay.chunkHeatThreshold;
                    int currentHeat = getHeatAtBlockPos((int) x, (int) z, 0L);
                    int heatToAdd = Math.min(4, Math.max(0, (threshold - 1) - currentHeat));
                    if (heatToAdd > 0) {
                        addHeatAtBlockPos((int) x, (int) z, heatToAdd, 0L);
                        clampHeatAround((int) x, (int) z, threshold - 1);
                    }
                }
            } else if (lower.equals("shoot") || lower.equals("shoot_3p") || lower.contains("shoot")) {
                if (ZDayProgressionManager.isStageUnlocked(2)) {
                    addHeatAtBlockPos((int) x, (int) z, 25, 0L);
                }
            }
            return;
        }

        String lower = soundName.toLowerCase(Locale.ROOT);
        if (isSuppressedTaCZSound(lower)) {
            // Suppressed shot! Spawns a quiet Math.min(24, Math.max(16, taczDistance)) strength EntityScent sound node
            try {
                int soundStrength = Math.min(24, Math.max(16, taczDistance));
                ZAUtil.spawnOrBuffSenseAtPos(level, new Vector3d(x, y, z), EnumSenseType.SOUND, soundStrength, true);
            } catch (Throwable ignored) {}

            // If ZDayProgressionManager.isStageUnlocked(2), adds +4 heat capped at chunkHeatThreshold - 1, and does NOT call checkAndTriggerHorde!
            if (ZDayProgressionManager.isStageUnlocked(2)) {
                long gameTick = level.getGameTime();
                int threshold = ZAConfigZDay.chunkHeatThreshold;
                int currentHeat = getHeatAtBlockPos((int) x, (int) z, gameTick);
                int heatToAdd = Math.min(4, Math.max(0, (threshold - 1) - currentHeat));
                if (heatToAdd > 0) {
                    addHeatAtBlockPos((int) x, (int) z, heatToAdd, gameTick);
                    clampHeatAround((int) x, (int) z, threshold - 1);
                }
            }
        } else if (lower.equals("shoot") || lower.equals("shoot_3p") || lower.contains("shoot")) {
            // Unsuppressed shot! Spawns a loud Math.max(112, taczDistance * 2) strength EntityScent sound node and delegates to onSoundPlayed
            try {
                int soundStrength = Math.max(112, taczDistance * 2);
                ZAUtil.spawnOrBuffSenseAtPos(level, new Vector3d(x, y, z), EnumSenseType.SOUND, soundStrength, true);
            } catch (Throwable ignored) {}
            onSoundPlayed(level, x, y, z, "tacz", soundName, false);
        } else if (lower.contains("reload") || lower.contains("dry_fire") || lower.contains("bolt")) {
            // Close-range tactical handling noise! Spawns a 14-strength EntityScent sound node with 0 Chunk Heat
            try {
                ZAUtil.spawnOrBuffSenseAtPos(level, new Vector3d(x, y, z), EnumSenseType.SOUND, 14, true);
            } catch (Throwable ignored) {}
        }
    }

    public static void onSoundPlayed(ServerLevel level, double x, double y, double z, String namespace, String path) {
        onSoundPlayed(level, x, y, z, namespace, path, true);
    }

    public static void onSoundPlayed(ServerLevel level, double x, double y, double z, String namespace, String path, boolean spawnScent) {
        if (level == null) return;
        if (level.dimension() != Level.OVERWORLD) return;
        if (isQuietFirearmHandlingSound(namespace, path)) return;
        if (!isFirearmOrExplosionSound(namespace, path)) return;
        if (!ZDayProgressionManager.isStageUnlocked(2)) return;

        if (spawnScent) {
            try {
                ZAUtil.spawnOrBuffSenseAtPos(level, new Vector3d(x, y, z), EnumSenseType.SOUND, 112);
            } catch (Throwable ignored) {}
        }

        long gameTick = level.getGameTime();
        int currentHeat = addHeatAtBlockPos((int) x, (int) z, 25, gameTick);
        checkAndTriggerHorde(level, x, y, z, currentHeat, gameTick);
    }

    private static void checkAndTriggerHorde(ServerLevel level, double x, double y, double z, int currentHeat, long gameTick) {
        if (level != null && level.dimension() != Level.OVERWORLD) return;
        int threshold = ZAConfigZDay.chunkHeatThreshold;
        if (currentHeat >= threshold) {
            long centerKey = ChunkPos.pack(((int) x) >> 4, ((int) z) >> 4);
            HeatEntry entry = heatMap.get(centerKey);
            if (entry != null && gameTick - entry.lastWaveTriggerTick >= 1200L) {
                Set<UUID> groupPlayerIds = new HashSet<>();
                int nearbyPlayers = 0;
                if (level.players() != null) {
                    for (ServerPlayer player : level.players()) {
                        if (player == null) continue;
                        double distSq = player.distanceToSqr(x, y, z);
                        if (distSq <= GROUP_CLUSTER_RADIUS * GROUP_CLUSTER_RADIUS) {
                            groupPlayerIds.add(player.getUUID());
                        }
                        if (distSq <= 64.0 * 64.0) {
                            nearbyPlayers++;
                        }
                    }
                }
                if (nearbyPlayers == 0) nearbyPlayers = Math.max(1, groupPlayerIds.size());

                if (isGroupInPostHordeBreather(groupPlayerIds, gameTick)) {
                    return;
                }

                BlockPos triggerPos = BlockPos.containing(x, y, z);
                if (!canTriggerHordeForGroup(triggerPos, groupPlayerIds)) {
                    return;
                }

                double approachAngleRad = new Random().nextDouble() * 2.0 * Math.PI;
                double roarX = x + Math.cos(approachAngleRad) * 45.0;
                double roarZ = z + Math.sin(approachAngleRad) * 45.0;

                int stage = 1;
                for (int s = 10; s >= 1; s--) {
                    if (ZDayProgressionManager.isStageUnlocked(s)) {
                        stage = s;
                        break;
                    }
                }

                int durationTicks = computeHordeDurationTicks(nearbyPlayers);
                resetHeatAround((int) x, (int) z, gameTick);
                markGroupPostHordeBreather(groupPlayerIds, gameTick + durationTicks);

                playHordeRoar(level, roarX, y, roarZ);

                if (level.players() != null) {
                    for (ServerPlayer player : level.players()) {
                        if (player != null && (groupPlayerIds.contains(player.getUUID()) || player.distanceToSqr(x, y, z) <= 64.0 * 64.0)) {
                            player.sendSystemMessage(Component.literal("§4§l[!] A MASSIVE HORDE HAS HEARD YOUR GUNFIRE — RUN!"));
                        }
                    }
                }

                boolean bloodmoon = BloodmoonManager.isBloodmoonActive(level);
                int waveSize = computeSquadScaledWaveSize(ZAConfigZDay.baseSwarmWaveSize, stage, nearbyPlayers, bloodmoon);
                triggerSwarmWave(level, x, y, z, waveSize, nearbyPlayers, stage, groupPlayerIds, approachAngleRad);
            }
        }
    }
}
