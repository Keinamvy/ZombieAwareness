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
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
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
    public static final long CORPSE_RUN_GRACE_TICKS = 3600L;
    public static final double GROUP_CLUSTER_RADIUS = 96.0;
    public static final double HORDE_MOB_HEALTH_MULTIPLIER = 0.75;
    public static final double HORDE_JUGGERNAUT_MAX_HEALTH_CAP = 20.0;
    public static final int NO_DRY_SPAWN_Y = Integer.MIN_VALUE;
    private static final Map<Long, HeatEntry> heatMap = new ConcurrentHashMap<>();
    private static final List<ActiveHorde> activeHordes = new CopyOnWriteArrayList<>();
    private static final Map<UUID, Long> playerBreatherUntilTick = new ConcurrentHashMap<>();

    public enum HordePhase {
        PHASE_1A_FAST_ZOMBIES,
        PHASE_1B_VENOM_SPIDERS,
        PHASE_2_BREACHERS_SKELETONS,
        PHASE_3_JUGGERNAUTS_WITCHES,
        PHASE_4_CLIMAX_SAPPERS,
        PHASE_5_APEX_CORROSION
    }

    public static class ActiveHorde {
        public BlockPos center;
        public final Set<UUID> targetGroupPlayerIds;
        public final Set<UUID> originalGroupPlayerIds;
        public final int totalDurationTicks;
        public int subWaveCount;
        public final int worldStageNum;
        public final double approachAngleRad;
        public int elapsedTicks;
        public int noTargetTicks = 0;
        public boolean climaxRoarPlayed = false;
        public boolean apexPhaseTriggered = false;
        public boolean surroundMode = false;
        public int phase4InitialMobCount = -1;

        public ActiveHorde(BlockPos center, Set<UUID> targetGroupPlayerIds, int totalDurationTicks, int subWaveCount, int worldStageNum, double approachAngleRad) {
            this.center = center;
            Set<UUID> targetSet = ConcurrentHashMap.newKeySet();
            if (targetGroupPlayerIds != null) {
                for (UUID id : targetGroupPlayerIds) {
                    if (id != null) {
                        targetSet.add(id);
                    }
                }
            }
            this.targetGroupPlayerIds = targetSet;
            this.originalGroupPlayerIds = Collections.unmodifiableSet(new HashSet<>(targetSet));
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
        } else if (progressZeroToOne < 0.93) {
            raw = HordePhase.PHASE_4_CLIMAX_SAPPERS;
        } else {
            raw = HordePhase.PHASE_5_APEX_CORROSION;
        }

        HordePhase maxAllowed;
        if (worldStageNum <= 4) {
            maxAllowed = HordePhase.PHASE_1B_VENOM_SPIDERS;
        } else if (worldStageNum <= 7) {
            maxAllowed = HordePhase.PHASE_2_BREACHERS_SKELETONS;
        } else if (worldStageNum <= 9) {
            maxAllowed = HordePhase.PHASE_3_JUGGERNAUTS_WITCHES;
        } else {
            maxAllowed = HordePhase.PHASE_5_APEX_CORROSION;
        }

        return raw.ordinal() > maxAllowed.ordinal() ? maxAllowed : raw;
    }

    public static boolean canHordeSpawnPhantoms(HordePhase phase, int worldStageNum) {
        return worldStageNum >= 5 && (phase == HordePhase.PHASE_2_BREACHERS_SKELETONS
                || phase == HordePhase.PHASE_3_JUGGERNAUTS_WITCHES
                || phase == HordePhase.PHASE_4_CLIMAX_SAPPERS
                || phase == HordePhase.PHASE_5_APEX_CORROSION);
    }

    public static int computePhantomSpawnY(int groundSpawnY) {
        return groundSpawnY + 16;
    }

    public static double computeHordeMobMaxHealth(double baseMaxHealth, boolean isJuggernaut) {
        if (baseMaxHealth <= 0.0) return 15.0;
        if (isJuggernaut) {
            return Math.min(HORDE_JUGGERNAUT_MAX_HEALTH_CAP, baseMaxHealth);
        }
        return Math.max(8.0, Math.round(baseMaxHealth * HORDE_MOB_HEALTH_MULTIPLIER));
    }

    public static void applyHordeMobHealthScaling(Mob mob) {
        if (mob == null) return;
        var attr = mob.getAttribute(Attributes.MAX_HEALTH);
        if (attr != null) {
            mob.removeEffect(MobEffects.HEALTH_BOOST);
            double targetMax = computeHordeMobMaxHealth(attr.getBaseValue(), mob.entityTags().contains("za_juggernaut"));
            attr.setBaseValue(targetMax);
            mob.setHealth((float) targetMax);
        }
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

    public static boolean tryRegisterHordeForGroup(BlockPos center, Collection<UUID> groupPlayerIds, int nearbyPlayers, int subWaveCount, int worldStageNum, double approachAngleRad, boolean surroundMode) {
        if (!canTriggerHordeForGroup(center, groupPlayerIds)) {
            return false;
        }
        int durationTicks = computeHordeDurationTicks(Math.max(1, nearbyPlayers));
        Set<UUID> ids = (groupPlayerIds == null) ? Collections.emptySet() : Collections.unmodifiableSet(new HashSet<>(groupPlayerIds));
        ActiveHorde horde = new ActiveHorde(center, ids, durationTicks, subWaveCount, worldStageNum, approachAngleRad);
        horde.surroundMode = surroundMode;
        activeHordes.add(horde);
        return true;
    }

    public static boolean tryRegisterHordeForGroup(BlockPos center, Collection<UUID> groupPlayerIds, int nearbyPlayers, int subWaveCount, int worldStageNum, double approachAngleRad) {
        return tryRegisterHordeForGroup(center, groupPlayerIds, nearbyPlayers, subWaveCount, worldStageNum, approachAngleRad, false);
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

    public static double[] computeSpawnOffset(double approachAngleRad, double normalizedAngleMinusOneToOne, double normalizedDistZeroToOne, boolean surroundMode) {
        double clampedAngle = Math.max(-1.0, Math.min(1.0, normalizedAngleMinusOneToOne));
        double clampedDist = Math.max(0.0, Math.min(1.0, normalizedDistZeroToOne));
        double halfArcRad = surroundMode ? Math.PI : Math.toRadians(40.0);
        double angle = approachAngleRad + clampedAngle * halfArcRad;
        double dist = 38.0 + clampedDist * 14.0;
        return new double[]{ Math.cos(angle) * dist, Math.sin(angle) * dist };
    }

    public static double[] computeDirectionalSpawnOffset(double approachAngleRad, double normalizedAngleMinusOneToOne, double normalizedDistZeroToOne) {
        return computeSpawnOffset(approachAngleRad, normalizedAngleMinusOneToOne, normalizedDistZeroToOne, false);
    }

    public static String getCompassDirectionFromAngle(double angleRad) {
        double dx = Math.cos(angleRad);
        double dz = Math.sin(angleRad);
        double compassDegrees = Math.toDegrees(Math.atan2(dx, -dz));
        if (compassDegrees < 0) {
            compassDegrees += 360.0;
        }
        int index = (int) Math.floor((compassDegrees + 22.5) / 45.0) & 7;
        String[] directions = { "North", "North-East", "East", "South-East", "South", "South-West", "West", "North-West" };
        return directions[index];
    }

    public static boolean isHordeDecimated(int currentAlive, int phase4StartingCount) {
        if (phase4StartingCount <= 0) return false;
        if (currentAlive <= 0) return true;
        double ratio = (double) currentAlive / (double) phase4StartingCount;
        return ratio <= 0.02 || currentAlive <= Math.max(1, (int) (phase4StartingCount * 0.02));
    }

    public static boolean canAwakenLateGameAmbientHorde(int worldStageNum, long dayTime, boolean isBloodmoon, boolean canTriggerForGroup, boolean inBreather) {
        if (!canTriggerForGroup || inBreather) return false;
        long timeOfDay = ((dayTime % 24000L) + 24000L) % 24000L;
        boolean isNightWindow = timeOfDay >= 13000L && timeOfDay <= 22500L;
        if (!isNightWindow) return false;
        return isBloodmoon || worldStageNum >= 7;
    }

    public static boolean isFortifiedFromWallCount(int blockedDirectionsOutOf8, boolean hasRoofOrBaseBlock) {
        return blockedDirectionsOutOf8 >= 5 || (blockedDirectionsOutOf8 >= 3 && hasRoofOrBaseBlock);
    }

    public static int computeFortifiedWaveCount(int baseWaveCount, boolean isFortified) {
        return isFortified ? (int) Math.ceil(baseWaveCount * 1.40) : baseWaveCount;
    }

    private static final int[][] COMPASS_DIRECTIONS = {
        {1, 0}, {1, 1}, {0, 1}, {-1, 1},
        {-1, 0}, {-1, -1}, {0, -1}, {1, -1}
    };

    public static boolean hasNearbyBaseBlockEntity(ServerLevel level, BlockPos center, int radius) {
        if (level == null || center == null) return false;
        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -2; dy <= 4; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    mpos.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (level.getBlockEntity(mpos) != null) {
                        return true;
                    }
                    BlockState bs = level.getBlockState(mpos);
                    if (bs.getBlock() instanceof BedBlock) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean isPositionFortifiedByWallsOrBase(ServerLevel level, BlockPos center) {
        if (level == null || center == null) return false;
        int blockedDirections = 0;
        BlockPos scanCenter = center.above();
        for (int[] dir : COMPASS_DIRECTIONS) {
            boolean blocked = false;
            for (int dist = 1; dist <= 10; dist++) {
                BlockPos checkPos = scanCenter.offset(dir[0] * dist, 0, dir[1] * dist);
                BlockState state = level.getBlockState(checkPos);
                if ((!state.isAir() && (state.isSolid() || !state.getCollisionShape(level, checkPos).isEmpty()))
                        || state.getBlock() instanceof DoorBlock
                        || state.getBlock() instanceof FenceGateBlock
                        || state.getBlock() instanceof TrapDoorBlock) {
                    blocked = true;
                    break;
                }
            }
            if (blocked) {
                blockedDirections++;
            }
        }
        boolean hasRoofOrBase = !level.canSeeSkyFromBelowWater(scanCenter) || hasNearbyBaseBlockEntity(level, center, 8);
        return isFortifiedFromWallCount(blockedDirections, hasRoofOrBase);
    }

    public static int getActiveHordeCount() {
        activeHordes.removeIf(h -> h.elapsedTicks >= h.totalDurationTicks);
        return activeHordes.size();
    }

    public static void clearActiveHordes() {
        activeHordes.clear();
    }

    public static int disperseHordeMobsAround(ServerLevel level, BlockPos center, double radius, double discardFraction) {
        if (level == null || center == null) return 0;
        double radiusSq = radius * radius;
        List<Mob> mobs = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof Mob m && m.isAlive() && m.entityTags().contains("za_horde_mob")) {
                if (m.distanceToSqr(center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5) <= radiusSq) {
                    mobs.add(m);
                }
            }
        }
        if (mobs.isEmpty()) return 0;
        int keepCount = (int) Math.floor(mobs.size() * (1.0 - discardFraction));
        int discardTarget = mobs.size() - keepCount;
        int discarded = 0;
        for (int i = 0; i < mobs.size() && discarded < discardTarget; i++) {
            Mob mob = mobs.get(i);
            if (mob != null && mob.isAlive()) {
                mob.discard();
                discarded++;
            }
        }
        return discarded;
    }

    public static boolean onPlayerDeath(ServerLevel level, UUID deadPlayerUuid, BlockPos deathPos, long currentTick) {
        for (ActiveHorde horde : activeHordes) {
            boolean matched = (deadPlayerUuid != null && horde.targetGroupPlayerIds.contains(deadPlayerUuid))
                    || (horde.targetGroupPlayerIds.isEmpty() && deathPos != null && horde.center != null
                        && deathPos.distSqr(horde.center) <= GROUP_CLUSTER_RADIUS * GROUP_CLUSTER_RADIUS);
            if (!matched) {
                continue;
            }

            int prevCount = Math.max(1, horde.targetGroupPlayerIds.size());
            if (deadPlayerUuid != null) {
                horde.targetGroupPlayerIds.remove(deadPlayerUuid);
                playerBreatherUntilTick.put(deadPlayerUuid, currentTick + CORPSE_RUN_GRACE_TICKS);
            }

            if (horde.targetGroupPlayerIds.isEmpty()) {
                activeHordes.remove(horde);
                for (UUID id : horde.originalGroupPlayerIds) {
                    if (id != null) {
                        playerBreatherUntilTick.put(id, currentTick + CORPSE_RUN_GRACE_TICKS);
                    }
                }
                BlockPos center = horde.center != null ? horde.center : deathPos;
                if (center != null) {
                    resetHeatAround(center.getX(), center.getZ(), currentTick);
                }
                if (level != null) {
                    if (center != null) {
                        disperseHordeMobsAround(level, center, GROUP_CLUSTER_RADIUS, 0.80);
                    }
                    if (level.players() != null) {
                        for (ServerPlayer player : level.players()) {
                            if (player != null && horde.originalGroupPlayerIds.contains(player.getUUID())) {
                                player.sendSystemMessage(Component.literal("§7§o[!] Having overrun your position, the horde disperses into the shadows..."));
                            }
                        }
                    }
                }
                return true;
            } else {
                horde.subWaveCount = Math.max(4, (int) Math.ceil(horde.subWaveCount * ((double) horde.targetGroupPlayerIds.size() / (double) prevCount)));
                return false;
            }
        }
        return false;
    }

    public static void markGroupPostHordeBreather(Collection<UUID> groupPlayerIds, long currentTick) {
        if (groupPlayerIds == null || groupPlayerIds.isEmpty()) return;
        long until = currentTick + POST_HORDE_BREATHER_TICKS;
        for (UUID id : groupPlayerIds) {
            if (id != null) {
                playerBreatherUntilTick.compute(id, (k, v) -> v == null ? until : Math.max(v, until));
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
        int base = baseSize > 0 ? baseSize : 28;
        int squad = Math.max(1, nearbyPlayers);
        int stage = Math.max(1, stageNum);

        int wave = base + (squad - 1) * 6 + (stage - 1) * 3;
        if (bloodmoon) {
            wave *= 2;
        }
        return wave;
    }

    public static int computeSubWaveCount(int finalWaveSize) {
        return Math.max(12, (int) Math.ceil(finalWaveSize * 0.55));
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

    public static boolean isDrySolidSpawnColumn(BlockState belowState, BlockState feetState, BlockState headState) {
        if (belowState == null || feetState == null || headState == null) {
            return false;
        }
        if (!belowState.getFluidState().isEmpty() || !feetState.getFluidState().isEmpty() || !headState.getFluidState().isEmpty()) {
            return false;
        }
        if (belowState.isAir() || !belowState.isSolid()) {
            return false;
        }
        return feetState.isAir() && headState.isAir();
    }

    public static int findSafeDrySpawnY(ServerLevel level, int x, int preferredY, int z) {
        if (level == null) return preferredY;
        BlockPos.MutableBlockPos belowPos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos feetPos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos abovePos = new BlockPos.MutableBlockPos();

        for (int y = preferredY + 8; y >= preferredY - 12; y--) {
            feetPos.set(x, y, z);
            belowPos.set(x, y - 1, z);
            abovePos.set(x, y + 1, z);
            BlockState belowState = level.getBlockState(belowPos);
            BlockState feetState = level.getBlockState(feetPos);
            BlockState headState = level.getBlockState(abovePos);
            if (isDrySolidSpawnColumn(belowState, feetState, headState)
                    && !belowState.getCollisionShape(level, belowPos).isEmpty()) {
                return y;
            }
        }
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        feetPos.set(x, surfaceY, z);
        belowPos.set(x, surfaceY - 1, z);
        abovePos.set(x, surfaceY + 1, z);
        BlockState belowState = level.getBlockState(belowPos);
        BlockState feetState = level.getBlockState(feetPos);
        BlockState headState = level.getBlockState(abovePos);
        if (isDrySolidSpawnColumn(belowState, feetState, headState)
                && !belowState.getCollisionShape(level, belowPos).isEmpty()) {
            return surfaceY;
        }
        return NO_DRY_SPAWN_Y;
    }

    public static int findSafeSpawnY(ServerLevel level, int x, int preferredY, int z) {
        int dryY = findSafeDrySpawnY(level, x, preferredY, z);
        return dryY != NO_DRY_SPAWN_Y ? dryY : preferredY;
    }

    public static double selectDryApproachAngle(ServerLevel level, BlockPos center, double preferredAngleRad) {
        if (level == null || center == null) return preferredAngleRad;
        for (int k = 0; k < 12; k++) {
            double angle = preferredAngleRad + k * (2.0 * Math.PI / 12.0);
            int testX = (int) Math.round(center.getX() + Math.cos(angle) * 45.0);
            int testZ = (int) Math.round(center.getZ() + Math.sin(angle) * 45.0);
            int dryY = findSafeDrySpawnY(level, testX, center.getY(), testZ);
            if (dryY != NO_DRY_SPAWN_Y) {
                return angle;
            }
        }
        return preferredAngleRad;
    }

    public static boolean isWaterSpawnColumn(BlockState feetState, BlockState headState) {
        if (feetState == null || headState == null) return false;
        return feetState.getFluidState().is(FluidTags.WATER) && (headState.isAir() || headState.getFluidState().is(FluidTags.WATER));
    }

    public static int findSafeWaterSpawnY(ServerLevel level, int x, int preferredY, int z) {
        if (level == null) return NO_DRY_SPAWN_Y;
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        BlockPos feetPos = new BlockPos(x, surfaceY - 1, z);
        BlockPos headPos = new BlockPos(x, surfaceY, z);
        if (isWaterSpawnColumn(level.getBlockState(feetPos), level.getBlockState(headPos))) {
            return surfaceY - 1;
        }
        return NO_DRY_SPAWN_Y;
    }

    public static Drowned convertHordeZombieToDrowned(Zombie zombie) {
        if (zombie == null || zombie.isRemoved() || !(zombie.level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        if (zombie instanceof Drowned) {
            return (Drowned) zombie;
        }
        LivingEntity target = zombie.getTarget();
        boolean isApex = zombie.entityTags().contains("za_apex_corrosive");
        boolean isJuggernaut = zombie.entityTags().contains("za_juggernaut");
        boolean isSapper = zombie.entityTags().contains("za_tnt_sapper");

        Drowned drowned = zombie.convertTo(
            EntityTypes.DROWNED,
            ConversionParams.single(zombie, true, false),
            EntitySpawnReason.CONVERSION,
            (d) -> {
                d.addTag("za_horde_mob");
                if (isApex) d.addTag("za_apex_corrosive");
                if (isJuggernaut) d.addTag("za_juggernaut");
                if (isSapper) d.addTag("za_tnt_sapper");
                applyHordeMobHealthScaling(d);
                if (target != null && target.isAlive()) {
                    d.setTarget(target);
                }
            }
        );
        if (drowned != null) {
            serverLevel.playSound(null, drowned.getX(), drowned.getY(), drowned.getZ(),
                    SoundEvents.ZOMBIE_CONVERTED_TO_DROWNED, SoundSource.HOSTILE, 2.0F, 1.0F);
        }
        return drowned;
    }

    public static int countAliveHordeMobsAround(ServerLevel level, BlockPos center, double radius) {
        if (level == null || center == null || radius <= 0) return 0;
        double radiusSq = radius * radius;
        double cx = center.getX() + 0.5;
        double cy = center.getY() + 0.5;
        double cz = center.getZ() + 0.5;
        int count = 0;
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof Mob mob && mob.isAlive()) {
                if (mob.distanceToSqr(cx, cy, cz) <= radiusSq && mob.entityTags().contains("za_horde_mob")) {
                    count++;
                }
            }
        }
        return count;
    }

    public static int recruitNearbyWildMobsToHorde(ServerLevel level, BlockPos center, double radius) {
        if (level == null || center == null || radius <= 0) return 0;
        double radiusSq = radius * radius;
        double centerX = center.getX() + 0.5;
        double centerY = center.getY() + 0.5;
        double centerZ = center.getZ() + 0.5;

        int recruited = 0;
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof Mob mob && mob.isAlive()) {
                if (mob.distanceToSqr(centerX, centerY, centerZ) <= radiusSq && ZAUtil.isHostileMob(mob)) {
                    if (!mob.entityTags().contains("za_horde_mob")) {
                        mob.addTag("za_horde_mob");
                    }
                    if (mob.getTarget() == null) {
                        ServerPlayer nearestSurvival = null;
                        double nearestPlayerDistSq = 96.0 * 96.0;
                        if (level.players() != null) {
                            for (ServerPlayer player : level.players()) {
                                if (player != null && player.isAlive() && !player.isDeadOrDying() && !player.isSpectator() && !player.isCreative()) {
                                    double dSq = player.distanceToSqr(mob);
                                    if (dSq <= nearestPlayerDistSq) {
                                        nearestPlayerDistSq = dSq;
                                        nearestSurvival = player;
                                    }
                                }
                            }
                        }
                        if (nearestSurvival != null) {
                            mob.setTarget(nearestSurvival);
                        }
                    }
                    try {
                        CoroUtilPath.tryMoveToXYZLongDist(mob, center.getX(), center.getY(), center.getZ(), 1.0);
                    } catch (Throwable ignored) {}
                    recruited++;
                }
            }
        }
        return recruited;
    }

    public static void spawnPhasedSubWave(ServerLevel level, BlockPos center, HordePhase phase, int count, int worldStageNum) {
        spawnPhasedSubWave(level, center, new Random().nextDouble() * 2.0 * Math.PI, phase, count, worldStageNum, false);
    }

    public static void spawnPhasedSubWave(ServerLevel level, BlockPos center, double approachAngleRad, HordePhase phase, int count, int worldStageNum) {
        spawnPhasedSubWave(level, center, approachAngleRad, phase, count, worldStageNum, false);
    }

    public static void spawnPhasedSubWave(ServerLevel level, BlockPos center, double approachAngleRad, HordePhase phase, int count, int worldStageNum, boolean surroundMode) {
        if (level == null || center == null || count <= 0) return;
        Random rand = new Random();

        for (int i = 0; i < count; i++) {
            float roll = rand.nextFloat();
            boolean willBePhantom = canHordeSpawnPhantoms(phase, worldStageNum) && (roll >= 0.88f);

            double targetX = center.getX();
            double targetZ = center.getZ();
            int spawnY = NO_DRY_SPAWN_Y;

            for (int attempt = 0; attempt < 16; attempt++) {
                double candX, candZ;
                if (attempt < 8) {
                    double normAngle = rand.nextDouble() * 2.0 - 1.0;
                    double normDist = rand.nextDouble();
                    double[] offset = computeSpawnOffset(approachAngleRad, normAngle, normDist, surroundMode);
                    candX = center.getX() + offset[0];
                    candZ = center.getZ() + offset[1];
                } else {
                    double sweepAngle = rand.nextDouble() * 2.0 * Math.PI;
                    double sweepDist = 24.0 + rand.nextDouble() * 28.0;
                    candX = center.getX() + Math.cos(sweepAngle) * sweepDist;
                    candZ = center.getZ() + Math.sin(sweepAngle) * sweepDist;
                }
                int foundY = findSafeDrySpawnY(level, (int) candX, center.getY(), (int) candZ);
                if (foundY != NO_DRY_SPAWN_Y) {
                    targetX = candX;
                    targetZ = candZ;
                    spawnY = foundY;
                    break;
                }
            }

            boolean isWaterSpawn = false;
            if (spawnY == NO_DRY_SPAWN_Y && surroundMode) {
                for (int attempt = 0; attempt < 12; attempt++) {
                    double sweepAngle = rand.nextDouble() * 2.0 * Math.PI;
                    double sweepDist = 28.0 + rand.nextDouble() * 24.0;
                    double candX = center.getX() + Math.cos(sweepAngle) * sweepDist;
                    double candZ = center.getZ() + Math.sin(sweepAngle) * sweepDist;
                    int waterY = findSafeWaterSpawnY(level, (int) candX, center.getY(), (int) candZ);
                    if (waterY != NO_DRY_SPAWN_Y) {
                        targetX = candX;
                        targetZ = candZ;
                        spawnY = waterY;
                        isWaterSpawn = true;
                        break;
                    }
                }
            }

            if (spawnY == NO_DRY_SPAWN_Y) {
                if (!willBePhantom) {
                    continue;
                }
                double normAngle = rand.nextDouble() * 2.0 - 1.0;
                double normDist = rand.nextDouble();
                double[] offset = computeSpawnOffset(approachAngleRad, normAngle, normDist, surroundMode);
                targetX = center.getX() + offset[0];
                targetZ = center.getZ() + offset[1];
                spawnY = center.getY();
            }

            try {
                Mob mob = null;

                if (isWaterSpawn) {
                    Drowned drowned = new Drowned(EntityTypes.DROWNED, level);
                    drowned.addEffect(new MobEffectInstance(MobEffects.SPEED, 7200, 1));
                    mob = drowned;
                } else {
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
                        if (canHordeSpawnPhantoms(phase, worldStageNum) && roll >= 0.88f) {
                            mob = createMobById(level, "phantom");
                        } else if (roll < 0.60f) {
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
                        if (canHordeSpawnPhantoms(phase, worldStageNum) && roll >= 0.88f) {
                            mob = createMobById(level, "phantom");
                        } else if (roll < 0.55f) {
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
                        if (canHordeSpawnPhantoms(phase, worldStageNum) && roll >= 0.88f) {
                            mob = createMobById(level, "phantom");
                        } else if (roll < 0.50f) {
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
                    case PHASE_5_APEX_CORROSION -> {
                        if (canHordeSpawnPhantoms(phase, worldStageNum) && roll >= 0.88f) {
                            mob = createMobById(level, "phantom");
                        } else if (roll < 0.40f) {
                            Zombie z = new Zombie(level);
                            if (rand.nextBoolean()) {
                                com.nll.nightmareesm.nightmareesm.applyJuggernautLoadout(z);
                                z.addEffect(new MobEffectInstance(MobEffects.SPEED, 7200, 1));
                            } else {
                                if (rand.nextBoolean()) {
                                    z.addTag("za_tnt_sapper");
                                } else {
                                    new com.nll.nightmareesm.GiveRandomPickaxe(z);
                                }
                            }
                            mob = z;
                        } else if (roll < 0.60f) {
                            mob = createMobById(level, "cave_spider");
                        } else if (roll < 0.80f) {
                            mob = createMobById(level, "wither_skeleton");
                            if (mob == null) {
                                mob = createMobById(level, "skeleton");
                            }
                        } else {
                            mob = createMobById(level, "creeper");
                        }
                    }
                }
            }

                if (mob != null) {
                    mob.addTag("za_horde_mob");
                    if (phase == HordePhase.PHASE_5_APEX_CORROSION) {
                        mob.addTag("za_apex_corrosive");
                    }
                    boolean isPhantom = (mob instanceof Phantom);
                    if (mob instanceof Zombie || mob instanceof AbstractSkeleton || isPhantom) {
                        mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 7200, 0, false, false));
                    }
                    double actualY = isPhantom ? computePhantomSpawnY(spawnY) : spawnY;
                    mob.setPos(targetX, actualY, targetZ);
                    try {
                        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), EntitySpawnReason.EVENT, null);
                    } catch (Throwable ignored) {}
                    applyHordeMobHealthScaling(mob);
                    level.addFreshEntity(mob);
                    if (isPhantom) {
                        ServerPlayer nearestSurvival = null;
                        double nearestDistSq = 96.0 * 96.0;
                        if (level.players() != null) {
                            for (ServerPlayer player : level.players()) {
                                if (player != null && player.isAlive() && !player.isDeadOrDying() && !player.isSpectator() && !player.isCreative()) {
                                    double dSq = player.distanceToSqr(mob);
                                    if (dSq <= nearestDistSq) {
                                        nearestDistSq = dSq;
                                        nearestSurvival = player;
                                    }
                                }
                            }
                        }
                        if (nearestSurvival != null) {
                            mob.setTarget(nearestSurvival);
                        }
                    }
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

        boolean fortified = isPositionFortifiedByWallsOrBase(level, center);
        int finalWaveSize = computeFortifiedWaveCount(waveSize, fortified);

        HordePhase initialPhase = resolveHordePhase(0.0, stage);
        int initialWaveCount = Math.max(12, finalWaveSize);
        spawnPhasedSubWave(level, center, approachAngleRad, initialPhase, initialWaveCount, stage, fortified);

        int subWaveCount = computeSubWaveCount(finalWaveSize);
        tryRegisterHordeForGroup(center, groupPlayerIds, nearbyPlayers, subWaveCount, stage, approachAngleRad, fortified);

        recruitNearbyWildMobsToHorde(level, center, GROUP_CLUSTER_RADIUS);

        if (fortified && level.players() != null) {
            for (ServerPlayer player : level.players()) {
                if (player != null && (groupPlayerIds.contains(player.getUUID()) || player.distanceToSqr(originX, originY, originZ) <= 64.0 * 64.0)) {
                    player.sendSystemMessage(Component.literal("§4§l[!] THE HORDE HAS SURROUNDED YOUR FORTIFICATION FROM ALL SIDES (NORTH, SOUTH, EAST, WEST) — HOLD THE LINE!"));
                }
            }
        } else if (level.players() != null) {
            String dir = getCompassDirectionFromAngle(approachAngleRad);
            for (ServerPlayer player : level.players()) {
                if (player != null && (groupPlayerIds.contains(player.getUUID()) || player.distanceToSqr(originX, originY, originZ) <= 64.0 * 64.0)) {
                    player.sendSystemMessage(Component.literal("§4§l[!] HORDE ADVANCING FROM THE " + dir.toUpperCase(Locale.ROOT) + "!"));
                }
            }
        }
    }

    public static void tickServer(ServerLevel overworld) {
        if (overworld == null) return;
        long currentTick = overworld.getGameTime();

        // 1. Ambient Late-Game Night Horde Check (runs every 1200 ticks = 60s)
        if (currentTick % 1200L == 0 && overworld.players() != null && !overworld.players().isEmpty()) {
            int stage = 1;
            for (int s = 10; s >= 1; s--) {
                if (ZDayProgressionManager.isStageUnlocked(s)) {
                    stage = s;
                    break;
                }
            }
            boolean bloodmoon = BloodmoonManager.isBloodmoonActive(overworld);
            Random ambientRand = new Random();

            Set<UUID> evaluatedPlayers = new HashSet<>();
            for (ServerPlayer player : overworld.players()) {
                if (player == null || evaluatedPlayers.contains(player.getUUID())) continue;
                if (!player.isAlive() || player.isDeadOrDying() || player.isSpectator() || player.isCreative()) {
                    evaluatedPlayers.add(player.getUUID());
                    continue;
                }

                Set<UUID> groupPlayerIds = new HashSet<>();
                int nearbyPlayers = 0;
                for (ServerPlayer other : overworld.players()) {
                    if (other == null) continue;
                    double distSq = other.distanceToSqr(player.position());
                    if (distSq <= GROUP_CLUSTER_RADIUS * GROUP_CLUSTER_RADIUS) {
                        groupPlayerIds.add(other.getUUID());
                        evaluatedPlayers.add(other.getUUID());
                    }
                    if (distSq <= 64.0 * 64.0) {
                        nearbyPlayers++;
                    }
                }
                if (nearbyPlayers == 0) nearbyPlayers = Math.max(1, groupPlayerIds.size());

                BlockPos triggerPos = player.blockPosition();
                boolean canTrigger = canTriggerHordeForGroup(triggerPos, groupPlayerIds);
                boolean inBreather = isGroupInPostHordeBreather(groupPlayerIds, currentTick);

                if (canAwakenLateGameAmbientHorde(stage, overworld.getDefaultClockTime(), bloodmoon, canTrigger, inBreather)) {
                    float chance = bloodmoon ? 1.0f : 0.35f;
                    if (ambientRand.nextFloat() < chance) {
                        double preferredAngleRad = ambientRand.nextDouble() * 2.0 * Math.PI;
                        double approachAngleRad = selectDryApproachAngle(overworld, triggerPos, preferredAngleRad);
                        double roarX = player.getX() + Math.cos(approachAngleRad) * 45.0;
                        double roarZ = player.getZ() + Math.sin(approachAngleRad) * 45.0;

                        int durationTicks = computeHordeDurationTicks(nearbyPlayers);
                        resetHeatAround((int) player.getX(), (int) player.getZ(), currentTick);
                        markGroupPostHordeBreather(groupPlayerIds, currentTick + durationTicks);

                        playHordeRoar(overworld, roarX, player.getY(), roarZ);

                        String dir = getCompassDirectionFromAngle(approachAngleRad);
                        for (ServerPlayer p : overworld.players()) {
                            if (p != null && (groupPlayerIds.contains(p.getUUID()) || p.distanceToSqr(player.position()) <= 64.0 * 64.0)) {
                                p.sendSystemMessage(Component.literal("§4§l[!] THE NIGHT CHILL CONDENSES — AN APEX SIEGE HORDE IS APPROACHING FROM THE " + dir.toUpperCase(Locale.ROOT) + "!"));
                            }
                        }

                        int waveSize = computeSquadScaledWaveSize(ZAConfigZDay.baseSwarmWaveSize, stage, nearbyPlayers, bloodmoon);
                        triggerSwarmWave(overworld, player.getX(), player.getY(), player.getZ(), waveSize, nearbyPlayers, stage, groupPlayerIds, approachAngleRad);
                    }
                }
            }
        }

        if (activeHordes.isEmpty()) return;
        List<ActiveHorde> toRemove = new ArrayList<>();

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
                    if (player != null && horde.targetGroupPlayerIds.contains(player.getUUID())) {
                        if (!player.isAlive() || player.isDeadOrDying()) {
                            boolean wiped = onPlayerDeath(overworld, player.getUUID(), player.blockPosition(), currentTick);
                            if (wiped) {
                                break;
                            }
                            continue;
                        }
                        double distSq = player.distanceToSqr(horde.center.getX() + 0.5, horde.center.getY() + 0.5, horde.center.getZ() + 0.5);
                        if (distSq <= maxRangeSq && distSq < closestDistSq) {
                            closestDistSq = distSq;
                            closestPlayer = player;
                        }
                    }
                }

                if (!activeHordes.contains(horde)) {
                    continue;
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

            boolean fortified = isPositionFortifiedByWallsOrBase(overworld, horde.center);
            horde.surroundMode = fortified;
            int currentSubWaveCount = computeFortifiedWaveCount(horde.subWaveCount, fortified);

            if (phase == HordePhase.PHASE_4_CLIMAX_SAPPERS && !horde.climaxRoarPlayed) {
                horde.climaxRoarPlayed = true;
                BlockPos staging = horde.getStagingOrigin();
                playHordeRoar(overworld, staging.getX(), staging.getY(), staging.getZ());
                String dir = getCompassDirectionFromAngle(horde.approachAngleRad);
                if (overworld.players() != null) {
                    for (ServerPlayer player : overworld.players()) {
                        if (player != null && (horde.originalGroupPlayerIds.contains(player.getUUID())
                                || player.distanceToSqr(horde.center.getX() + 0.5, horde.center.getY() + 0.5, horde.center.getZ() + 0.5) <= GROUP_CLUSTER_RADIUS * GROUP_CLUSTER_RADIUS)) {
                            player.sendSystemMessage(Component.literal("§4§l[!] DEMOLITION SAPPERS ADVANCING FROM THE " + dir.toUpperCase(Locale.ROOT) + "!"));
                        }
                    }
                }
            }

            if (phase == HordePhase.PHASE_5_APEX_CORROSION && !horde.apexPhaseTriggered) {
                horde.apexPhaseTriggered = true;
                BlockPos staging = horde.getStagingOrigin();
                playHordeRoar(overworld, staging.getX(), staging.getY(), staging.getZ());
                spawnPhasedSubWave(overworld, horde.center, horde.approachAngleRad, phase, currentSubWaveCount, horde.worldStageNum, horde.surroundMode);
            }

            if (phase.ordinal() >= HordePhase.PHASE_4_CLIMAX_SAPPERS.ordinal()) {
                if (horde.phase4InitialMobCount < 0) {
                    horde.phase4InitialMobCount = Math.max(1, countAliveHordeMobsAround(overworld, horde.center, GROUP_CLUSTER_RADIUS));
                }
                int currentAlive = countAliveHordeMobsAround(overworld, horde.center, GROUP_CLUSTER_RADIUS);
                if (isHordeDecimated(currentAlive, horde.phase4InitialMobCount)) {
                    toRemove.add(horde);
                    markGroupPostHordeBreather(horde.targetGroupPlayerIds, currentTick);
                    resetHeatAround(horde.center.getX(), horde.center.getZ(), currentTick);
                    disperseHordeMobsAround(overworld, horde.center, GROUP_CLUSTER_RADIUS, 1.0);
                    if (overworld.players() != null) {
                        for (ServerPlayer player : overworld.players()) {
                            if (player != null && (horde.originalGroupPlayerIds.contains(player.getUUID())
                                    || player.distanceToSqr(horde.center.getX() + 0.5, horde.center.getY() + 0.5, horde.center.getZ() + 0.5) <= GROUP_CLUSTER_RADIUS * GROUP_CLUSTER_RADIUS)) {
                                player.sendSystemMessage(Component.literal("§a§l[!] THE HORDE HAS BEEN OVERCOME (UNDER 2% REMAINING) — VICTORY IS YOURS!"));
                            }
                        }
                    }
                    continue;
                }
            }

            if (horde.elapsedTicks % 20 == 0) {
                double radiusSq = GROUP_CLUSTER_RADIUS * GROUP_CLUSTER_RADIUS;
                double cx = horde.center.getX() + 0.5;
                double cy = horde.center.getY() + 0.5;
                double cz = horde.center.getZ() + 0.5;
                for (Entity e : overworld.getAllEntities()) {
                    if (e instanceof Zombie z && !(z instanceof Drowned) && z.isAlive() && z.entityTags().contains("za_horde_mob")) {
                        if (z.distanceToSqr(cx, cy, cz) <= radiusSq) {
                            if (z.isInWater() || z.isInShallowWater() || z.isUnderWater() || overworld.getFluidState(z.blockPosition()).is(FluidTags.WATER)) {
                                convertHordeZombieToDrowned(z);
                            }
                        }
                    }
                }
            }

            if (horde.elapsedTicks % 100 == 0) {
                recruitNearbyWildMobsToHorde(overworld, horde.center, GROUP_CLUSTER_RADIUS);
            }

            if (horde.elapsedTicks % 200 == 0) {
                spawnPhasedSubWave(overworld, horde.center, horde.approachAngleRad, phase, currentSubWaveCount, horde.worldStageNum, horde.surroundMode);
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

                double preferredAngleRad = new Random().nextDouble() * 2.0 * Math.PI;
                double approachAngleRad = selectDryApproachAngle(level, triggerPos, preferredAngleRad);
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
                    String dir = getCompassDirectionFromAngle(approachAngleRad);
                    for (ServerPlayer player : level.players()) {
                        if (player != null && (groupPlayerIds.contains(player.getUUID()) || player.distanceToSqr(x, y, z) <= 64.0 * 64.0)) {
                            player.sendSystemMessage(Component.literal("§4§l[!] A MASSIVE HORDE IS APPROACHING FROM THE " + dir.toUpperCase(Locale.ROOT) + " — RUN!"));
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
