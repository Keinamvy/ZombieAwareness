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
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.entity.monster.spider.Spider;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

public class ChunkHeatManager {

    public static final int MAX_CHUNK_HEAT = 150;
    public static final long POST_HORDE_BREATHER_TICKS = 4200L;
    public static final long CORPSE_RUN_GRACE_TICKS = 3600L;
    public static final double GROUP_CLUSTER_RADIUS = 96.0;
    public static final double VICTORY_RETAIN_RADIUS_BLOCKS = 24.0;
    public static final int DEFAULT_BASE_SWARM_WAVE_SIZE = 48;
    public static final int DEFAULT_HORDE_MAX_ALIVE_CAP = 180;
    public static final int DEFAULT_HORDE_BASE_DURATION_TICKS = 6000;
    public static final int DEFAULT_HORDE_RAMPAGE_DURATION_TICKS = 2400;
    public static final int DEFAULT_RAMPAGE_SKELETON_ATTACK_INTERVAL_TICKS = 8;
    public static final float DEFAULT_RAMPAGE_ARROW_VELOCITY_MULTIPLIER = 1.70F;
    public static final double DEFAULT_RAMPAGE_SPIDER_STEP_HEIGHT = 1.05D;
    public static final int RAMPAGE_SPEED_AMPLIFIER = 0; // amplifier 0 = Speed I
    public static final int RAMPAGE_SPEED_DURATION_TICKS = 600;
    public static final String RAMPAGE_TAG = ZAUtil.RAMPAGE_TAG;
    public static final int DEFAULT_HORDE_RAPID_SPAWN_INTERVAL_TICKS = 15;
    public static final int DEFAULT_HORDE_RAPID_SPAWN_BATCH_SIZE = 3;
    public static final int MAX_RAPID_SPAWN_BATCH_SIZE = 6;
    public static final double PHASE_1A_END = 0.10;
    public static final double PHASE_1B_END = 0.20;
    public static final double PHASE_2_END = 0.32;
    public static final double PHASE_3_END = 0.45;
    public static final double PHASE_4_END = 0.72;
    public static final double HORDE_MOB_HEALTH_MULTIPLIER = 0.75;
    public static final double HORDE_JUGGERNAUT_MAX_HEALTH_CAP = 20.0;
    public static final int NO_DRY_SPAWN_Y = Integer.MIN_VALUE;
    private static final Map<Long, HeatEntry> heatMap = new ConcurrentHashMap<>();
    private static final List<ActiveHorde> activeHordes = new CopyOnWriteArrayList<>();
    private static final Map<UUID, Long> playerBreatherUntilTick = new ConcurrentHashMap<>();
    private static volatile boolean hasRecentlySeenSpecialUnitsOrTurrets = false;

    public enum HordePhase {
        PHASE_1A_FAST_ZOMBIES,
        PHASE_1B_VENOM_SPIDERS,
        PHASE_2_BREACHERS_SKELETONS,
        PHASE_3_JUGGERNAUTS_WITCHES,
        PHASE_4_CLIMAX_SAPPERS,
        PHASE_5_APEX_CORROSION
    }

    public static class ActiveHorde {
        public volatile BlockPos center;
        public final Set<UUID> targetGroupPlayerIds;
        public final Set<UUID> originalGroupPlayerIds;
        public final int totalDurationTicks;
        public volatile int subWaveCount;
        public final int worldStageNum;
        public final double approachAngleRad;
        public volatile int elapsedTicks;
        public volatile int noTargetTicks = 0;
        public volatile boolean climaxRoarPlayed = false;
        public volatile boolean apexPhaseTriggered = false;
        public volatile boolean bloodmoonWardensSpawned = false;
        public volatile boolean surroundMode = false;
        public volatile int phase4InitialMobCount = -1;
        public volatile boolean wasNight = false;
        public volatile boolean rampagePhaseActive = false;
        public volatile int rampageElapsedTicks = 0;
        public volatile int rampageDurationTicks = DEFAULT_HORDE_RAMPAGE_DURATION_TICKS;
        public volatile int rampageInitialMobCount = 0;
        public volatile long lastFortifiedCheckTick = -1L;
        public volatile BlockPos lastFortifiedCheckPos = null;

        public boolean isFullyExpired() {
            return rampagePhaseActive
                    ? rampageElapsedTicks >= rampageDurationTicks
                    : elapsedTicks >= totalDurationTicks;
        }

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
            this(center, targetGroupPlayerIds, totalDurationTicks, subWaveCount, worldStageNum, ThreadLocalRandom.current().nextDouble() * 2.0 * Math.PI);
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
        public volatile int heat;
        public volatile long lastTick;
        public volatile long lastWaveTriggerTick;

        public HeatEntry(int heat, long lastTick) {
            this.heat = heat;
            this.lastTick = lastTick;
            this.lastWaveTriggerTick = -999999L;
        }
    }

    public static int computeHordeDurationTicks(int nearbyPlayers) {
        int baseTicks = ZAConfigZDay.hordeBaseDurationTicks > 0
                ? ZAConfigZDay.hordeBaseDurationTicks
                : DEFAULT_HORDE_BASE_DURATION_TICKS;
        return baseTicks + Math.max(0, nearbyPlayers - 1) * 600;
    }

    public static HordePhase resolveHordePhase(double progressZeroToOne, int worldStageNum) {
        HordePhase raw;
        if (progressZeroToOne < PHASE_1A_END) {
            raw = HordePhase.PHASE_1A_FAST_ZOMBIES;
        } else if (progressZeroToOne < PHASE_1B_END) {
            raw = HordePhase.PHASE_1B_VENOM_SPIDERS;
        } else if (progressZeroToOne < PHASE_2_END) {
            raw = HordePhase.PHASE_2_BREACHERS_SKELETONS;
        } else if (progressZeroToOne < PHASE_3_END) {
            raw = HordePhase.PHASE_3_JUGGERNAUTS_WITCHES;
        } else if (progressZeroToOne < PHASE_4_END) {
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

    /**
     * Scales special/elite horde mob spawn rates cleanly across early-to-late Z-Day stages
     * so Days 1-40 focus on frontline fodder and special units ramp smoothly afterward.
     */
    public static double computeStageEliteSpawnMultiplier(int worldStageNum) {
        if (worldStageNum <= 1) return 0.0D;
        if (worldStageNum == 2) return 0.15D;
        if (worldStageNum == 3) return 0.30D;
        if (worldStageNum == 4) return 0.40D;
        if (worldStageNum == 5) return 0.55D;
        if (worldStageNum == 6) return 0.70D;
        if (worldStageNum == 7) return 0.80D;
        if (worldStageNum == 8) return 0.90D;
        return 1.0D;
    }

    public static boolean canSpawnEliteInStage(String eliteTag, int worldStageNum) {
        if (eliteTag == null || worldStageNum <= 1) {
            return false;
        }
        return switch (eliteTag) {
            case HordeSpecialUnitHelper.TANKER_TAG, HordeSpecialUnitHelper.CRAWLER_TAG -> worldStageNum >= 2;
            case HordeSpecialUnitHelper.ENGINEER_TAG -> worldStageNum >= 3;
            case HordeSpecialUnitHelper.KNIGHT_TAG -> worldStageNum >= 5;
            case "za_screamer" -> worldStageNum >= 6;
            case HordeSpecialUnitHelper.JUGGERNAUT_TAG -> worldStageNum >= 7;
            case HordeSpecialUnitHelper.SAPPER_TAG -> worldStageNum >= 8;
            default -> false;
        };
    }

    public static double computeScaledEliteChance(double baseChance, String eliteTag, int worldStageNum) {
        if (baseChance <= 0.0D || !canSpawnEliteInStage(eliteTag, worldStageNum)) {
            return 0.0D;
        }
        return Math.min(1.0D, baseChance * computeStageEliteSpawnMultiplier(worldStageNum));
    }

    /**
     * Stages 1-2 keep early horde zombies near player walking speed (~4.3 blocks/s),
     * transitioning smoothly via short Speed I in Stages 3-4 before full Speed II in Stages 5+.
     */
    public static boolean canGrantSprinterSpeedEffect(int worldStageNum, double roll) {
        if (worldStageNum <= 2) {
            return false;
        }
        if (worldStageNum == 3) {
            return roll >= 0.0D && roll < 0.25D;
        }
        if (worldStageNum == 4) {
            return roll >= 0.0D && roll < 0.40D;
        }
        if (worldStageNum <= 6) {
            return roll >= 0.0D && roll < 0.75D;
        }
        return true;
    }

    public static int computeSprinterSpeedAmplifier(int worldStageNum) {
        return worldStageNum >= 5 ? 1 : 0;
    }

    public static int computeSprinterSpeedDurationTicks(int worldStageNum) {
        return worldStageNum >= 5 ? 7200 : 1200;
    }

    private static void applyStageSprinterSpeed(Mob mob, int worldStageNum, double roll) {
        if (mob != null && canGrantSprinterSpeedEffect(worldStageNum, roll)) {
            mob.addEffect(new MobEffectInstance(
                    MobEffects.SPEED,
                    computeSprinterSpeedDurationTicks(worldStageNum),
                    computeSprinterSpeedAmplifier(worldStageNum)
            ));
        }
    }

    public static void announceHordeNotice(ServerLevel overworld, ActiveHorde horde, String message, SoundEvent cueSound) {
        if (overworld == null || horde == null) return;
        Component text = Component.literal(message);
        if (overworld.getServer() != null && overworld.getServer().getPlayerList() != null) {
            for (ServerPlayer player : overworld.getServer().getPlayerList().getPlayers()) {
                if (player != null) {
                    boolean isTarget = horde.originalGroupPlayerIds.contains(player.getUUID()) || horde.targetGroupPlayerIds.contains(player.getUUID());
                    boolean isNear = player.level() == overworld && player.distanceToSqr(horde.center.getX() + 0.5, horde.center.getY() + 0.5, horde.center.getZ() + 0.5) <= GROUP_CLUSTER_RADIUS * GROUP_CLUSTER_RADIUS * 2.25;
                    if (isTarget || isNear) {
                        player.sendSystemMessage(text);
                        player.sendSystemMessage(text, true);
                        if (cueSound != null) {
                            try {
                                overworld.playSound(null, player.getX(), player.getY(), player.getZ(), cueSound, SoundSource.PLAYERS, 1.2F, 1.0F);
                            } catch (Throwable ignored) {
                                try {
                                    overworld.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.2F, 1.0F);
                                } catch (Throwable ignored2) {}
                            }
                        }
                    }
                }
            }
        }
    }

    public static void announceHordeVictory(ServerLevel overworld, ActiveHorde horde, String message) {
        announceHordeNotice(overworld, horde, message, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE);
    }

    public static boolean isDaytime(Level level) {
        if (level == null) return false;
        long tod = ((level.getDefaultClockTime() % 24000L) + 24000L) % 24000L;
        return tod < 12000L || tod > 23500L;
    }

    public static boolean isDawnArrival(boolean wasNight, long dayTime, boolean bloodmoonActive) {
        if (bloodmoonActive || !wasNight) return false;
        long timeOfDay = ((dayTime % 24000L) + 24000L) % 24000L;
        return timeOfDay < 12000L || timeOfDay > 23500L;
    }

    public static int computeGunfireHeatAmount(boolean isDay, boolean suppressed) {
        if (suppressed) {
            return isDay ? 2 : 4;
        } else {
            return isDay ? 10 : 25;
        }
    }

    public static boolean checkAndDropPhantomPassenger(Phantom phantom, LivingEntity target) {
        if (phantom == null || !phantom.isAlive() || phantom.getPassengers().isEmpty()) {
            return false;
        }
        if (target == null || !target.isAlive()) {
            return false;
        }
        double dx = phantom.getX() - target.getX();
        double dz = phantom.getZ() - target.getZ();
        double horizontalDist = Math.hypot(dx, dz);
        double dy = phantom.getY() - target.getY();

        if (horizontalDist <= 4.5 && dy >= 2.0 && dy <= 28.0) {
            for (Entity passenger : new ArrayList<>(phantom.getPassengers())) {
                passenger.stopRiding();
                if (passenger instanceof LivingEntity living) {
                    living.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 80, 0, false, false));
                    living.setDeltaMovement(0.0, -0.4, 0.0);
                    if (living instanceof Mob m) {
                        m.setTarget(target);
                    }
                }
            }
            if (phantom.level() instanceof ServerLevel serverLevel) {
                serverLevel.playSound(null, phantom.getX(), phantom.getY(), phantom.getZ(),
                        SoundEvents.PHANTOM_BITE, SoundSource.HOSTILE, 2.0F, 0.8F);
            }
            return true;
        }
        return false;
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
            if (mob.entityTags().contains(HordeSpecialUnitHelper.TANKER_TAG)) {
                double tankerMax = Math.max(HordeSpecialUnitHelper.TANKER_MAX_HEALTH, attr.getBaseValue());
                attr.setBaseValue(tankerMax);
                mob.setHealth((float) tankerMax);
                return;
            }
            if (mob.entityTags().contains(HordeSpecialUnitHelper.KNIGHT_TAG)) {
                double knightMax = Math.max(HordeSpecialUnitHelper.KNIGHT_MAX_HEALTH, attr.getBaseValue());
                attr.setBaseValue(knightMax);
                mob.setHealth((float) knightMax);
                return;
            }
            double targetMax = computeHordeMobMaxHealth(attr.getBaseValue(), mob.entityTags().contains("za_juggernaut"));
            attr.setBaseValue(targetMax);
            mob.setHealth((float) targetMax);
        }
    }

    private static boolean hasConflictingActiveHorde(BlockPos triggerPos, Collection<UUID> groupPlayerIds) {
        for (ActiveHorde horde : activeHordes) {
            if (horde == null || horde.isFullyExpired()) {
                continue;
            }
            if (groupPlayerIds != null && !groupPlayerIds.isEmpty() && horde.targetGroupPlayerIds != null && !horde.targetGroupPlayerIds.isEmpty()) {
                if (!Collections.disjoint(horde.targetGroupPlayerIds, groupPlayerIds)) {
                    return true;
                }
            }
            if (triggerPos != null && horde.center != null) {
                if (triggerPos.distSqr(horde.center) <= GROUP_CLUSTER_RADIUS * GROUP_CLUSTER_RADIUS) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean canTriggerHordeForGroup(BlockPos triggerPos, Collection<UUID> groupPlayerIds) {
        activeHordes.removeIf(ActiveHorde::isFullyExpired);
        return !hasConflictingActiveHorde(triggerPos, groupPlayerIds);
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
        return tryRegisterHordeForGroup(center, groupPlayerIds, nearbyPlayers, subWaveCount, worldStageNum, ThreadLocalRandom.current().nextDouble() * 2.0 * Math.PI);
    }

    public static List<ActiveHorde> getActiveHordes() {
        return Collections.unmodifiableList(activeHordes);
    }

    public static boolean isPlayerLockedByHordeOrBloodmoon(UUID playerUuid, BlockPos playerPos, boolean isBloodmoonActive) {
        if (isBloodmoonActive) {
            return true;
        }
        Collection<UUID> ids = (playerUuid != null) ? Collections.singleton(playerUuid) : Collections.emptySet();
        return hasConflictingActiveHorde(playerPos, ids);
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

    public static boolean isFlankSpawnIndex(int index) {
        return Math.floorMod(index, 10) >= 7;
    }

    public static double computeFlankApproachAngle(double frontAngleRad, double randomOffsetMinusOneToOne) {
        double clamped = Math.max(-1.0, Math.min(1.0, randomOffsetMinusOneToOne));
        return frontAngleRad + Math.PI + clamped * (Math.PI / 3.0);
    }

    public static boolean shouldDisperseOnVictory(double nearestPlayerDistSq) {
        return nearestPlayerDistSq > (VICTORY_RETAIN_RADIUS_BLOCKS * VICTORY_RETAIN_RADIUS_BLOCKS);
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

    private static AABB hordeSearchBox(BlockPos center, double radius) {
        double cx = center.getX() + 0.5;
        double cy = center.getY() + 0.5;
        double cz = center.getZ() + 0.5;
        return new AABB(cx - radius, cy - radius, cz - radius, cx + radius, cy + radius, cz + radius);
    }

    public static boolean isChunkLoadedAt(ServerLevel level, int blockX, int blockZ) {
        return level != null && level.hasChunk(blockX >> 4, blockZ >> 4);
    }

    private static boolean areRayChunksLoaded(ServerLevel level, Vec3 start, Vec3 end) {
        if (level == null || start == null || end == null) {
            return false;
        }
        double dx = end.x - start.x;
        double dz = end.z - start.z;
        double horizDist = Math.hypot(dx, dz);
        int steps = Math.max(1, (int) Math.ceil(horizDist / 8.0));
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / (double) steps;
            int bx = (int) Math.floor(start.x + dx * t);
            int bz = (int) Math.floor(start.z + dz * t);
            if (!level.hasChunk(bx >> 4, bz >> 4)) {
                return false;
            }
        }
        return true;
    }

    public static boolean hasNearbyBaseBlockEntity(ServerLevel level, BlockPos center, int radius) {
        if (level == null || center == null || !level.isLoaded(center)) return false;
        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            int bx = center.getX() + dx;
            for (int dz = -radius; dz <= radius; dz++) {
                int bz = center.getZ() + dz;
                if (!isChunkLoadedAt(level, bx, bz)) {
                    continue;
                }
                for (int dy = -2; dy <= 4; dy++) {
                    mpos.set(bx, center.getY() + dy, bz);
                    if (!level.isLoaded(mpos)) {
                        continue;
                    }
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
        if (level == null || center == null || !level.isLoaded(center)) return false;
        int blockedDirections = 0;
        BlockPos scanCenter = center.above();
        for (int[] dir : COMPASS_DIRECTIONS) {
            boolean blocked = false;
            for (int dist = 1; dist <= 10; dist++) {
                BlockPos checkPos = scanCenter.offset(dir[0] * dist, 0, dir[1] * dist);
                if (!level.isLoaded(checkPos)) {
                    break;
                }
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
        if (blockedDirections >= 5) {
            return true;
        }
        if (blockedDirections < 3) {
            return false;
        }
        boolean hasRoofOrBase = (level.isLoaded(scanCenter) && !level.canSeeSkyFromBelowWater(scanCenter))
                || hasNearbyBaseBlockEntity(level, center, 8);
        return isFortifiedFromWallCount(blockedDirections, hasRoofOrBase);
    }

    public static int getActiveHordeCount() {
        activeHordes.removeIf(ActiveHorde::isFullyExpired);
        return activeHordes.size();
    }

    public static void clearActiveHordes() {
        activeHordes.clear();
    }

    public static boolean isRampageMob(Entity entity) {
        return entity != null && entity.entityTags().contains(RAMPAGE_TAG);
    }

    public static float getRampageArrowVelocityMultiplier() {
        double mult = ZAConfigZDay.hordeRampageArrowSpeedMultiplier;
        return mult > 1.0D ? (float) mult : DEFAULT_RAMPAGE_ARROW_VELOCITY_MULTIPLIER;
    }

    public static int computeRampageBowInterval(int normalInterval) {
        int configured = ZAConfigZDay.hordeRampageSkeletonAttackIntervalTicks > 0
                ? ZAConfigZDay.hordeRampageSkeletonAttackIntervalTicks
                : DEFAULT_RAMPAGE_SKELETON_ATTACK_INTERVAL_TICKS;
        int baseline = normalInterval > 0 ? normalInterval : 20;
        return Math.max(4, Math.min(baseline, configured));
    }

    public static double getRampageSpiderStepHeight() {
        double configured = ZAConfigZDay.hordeRampageSpiderStepHeight;
        return configured >= 1.0D ? configured : DEFAULT_RAMPAGE_SPIDER_STEP_HEIGHT;
    }

    /**
     * Enrages a surviving horde mob during Rampage overtime:
     * grants Speed I (amplifier 0) and upgrades Spider step height >= 1.05 blocks.
     */
    public static void applyRampageBuffs(Mob mob) {
        if (mob == null || !mob.isAlive() || mob.isRemoved()) {
            return;
        }
        mob.addTag(RAMPAGE_TAG);
        MobEffectInstance existingSpeed = mob.getEffect(MobEffects.SPEED);
        if (existingSpeed == null || existingSpeed.getDuration() < 200) {
            mob.addEffect(new MobEffectInstance(MobEffects.SPEED, RAMPAGE_SPEED_DURATION_TICKS, RAMPAGE_SPEED_AMPLIFIER, false, true));
        }
        if (mob instanceof Spider) {
            var stepAttr = mob.getAttribute(Attributes.STEP_HEIGHT);
            double desiredStep = getRampageSpiderStepHeight();
            if (stepAttr != null && stepAttr.getBaseValue() < desiredStep) {
                stepAttr.setBaseValue(desiredStep);
            }
        }
    }

    public static int activateRampageOnNearbyHordeMobs(ServerLevel level, ActiveHorde horde) {
        if (level == null || horde == null || horde.center == null) {
            return 0;
        }
        double radius = GROUP_CLUSTER_RADIUS * 1.5D;
        double radiusSq = radius * radius;
        double cx = horde.center.getX() + 0.5;
        double cy = horde.center.getY() + 0.5;
        double cz = horde.center.getZ() + 0.5;
        List<Mob> nearby = level.getEntitiesOfClass(
                Mob.class,
                hordeSearchBox(horde.center, radius),
                m -> m != null && m.isAlive() && ZAUtil.isHordeMob(m) && m.distanceToSqr(cx, cy, cz) <= radiusSq
        );
        for (Mob m : nearby) {
            applyRampageBuffs(m);
        }
        return nearby.size();
    }

    public static boolean shouldReinforceHorde(ActiveHorde horde) {
        return horde != null && !horde.rampagePhaseActive && horde.elapsedTicks < horde.totalDurationTicks;
    }

    public static boolean evaluatePrimaryTimerEnd(ActiveHorde horde, int currentAliveCount, int configuredRampageDurationTicks) {
        if (horde == null || currentAliveCount <= 0) {
            return false;
        }
        horde.rampagePhaseActive = true;
        horde.rampageElapsedTicks = 0;
        horde.rampageDurationTicks = configuredRampageDurationTicks > 0
                ? configuredRampageDurationTicks
                : DEFAULT_HORDE_RAMPAGE_DURATION_TICKS;
        horde.rampageInitialMobCount = currentAliveCount;
        return true;
    }

    private static void collapseEngineerBlocks(ServerLevel level) {
        if (level == null) {
            return;
        }
        try {
            com.nll.nightmareesm.MobBehavs.MobPlaceBlock.collapseAllBlocks(level);
        } catch (Throwable ignored) {
        }
    }

    private static void tickEngineerBlockDecay(ServerLevel level, long currentTick) {
        if (level == null) {
            return;
        }
        try {
            com.nll.nightmareesm.MobBehavs.MobPlaceBlock.tickDecay(level, currentTick);
        } catch (Throwable ignored) {
        }
    }

    private static void completeHordeVictory(ServerLevel overworld, ActiveHorde horde, long currentTick, String message, List<ActiveHorde> toRemove) {
        toRemove.add(horde);
        markGroupPostHordeBreather(horde.targetGroupPlayerIds, currentTick);
        resetHeatAround(horde.center.getX(), horde.center.getZ(), currentTick);
        disperseDistantHordeMobsOnVictory(overworld, horde);
        collapseEngineerBlocks(overworld);
        announceHordeVictory(overworld, horde, message);
    }

    public static int disperseDistantHordeMobsOnVictory(ServerLevel level, ActiveHorde horde) {
        if (level == null || horde == null || horde.center == null) {
            return 0;
        }
        double searchRadius = GROUP_CLUSTER_RADIUS * 1.5;
        double searchRadiusSq = searchRadius * searchRadius;
        double cx = horde.center.getX() + 0.5;
        double cy = horde.center.getY() + 0.5;
        double cz = horde.center.getZ() + 0.5;

        List<ServerPlayer> alivePlayers = new ArrayList<>();
        if (level.players() != null) {
            for (ServerPlayer player : level.players()) {
                if (player != null && player.isAlive() && !player.isDeadOrDying() && !player.isSpectator()) {
                    alivePlayers.add(player);
                }
            }
        }

        List<Mob> mobs = level.getEntitiesOfClass(
                Mob.class,
                hordeSearchBox(horde.center, searchRadius),
                m -> m != null && m.isAlive() && ZAUtil.isHordeMob(m) && m.distanceToSqr(cx, cy, cz) <= searchRadiusSq
        );
        if (mobs.isEmpty()) {
            return 0;
        }

        int dispersed = 0;
        for (Mob mob : mobs) {
            double minDistSq;
            if (alivePlayers.isEmpty()) {
                minDistSq = mob.distanceToSqr(cx, cy, cz);
            } else {
                minDistSq = Double.MAX_VALUE;
                for (ServerPlayer player : alivePlayers) {
                    double dSq = mob.distanceToSqr(player);
                    if (dSq < minDistSq) {
                        minDistSq = dSq;
                    }
                }
            }
            if (shouldDisperseOnVictory(minDistSq)) {
                try {
                    level.sendParticles(ParticleTypes.SMOKE, mob.getX(), mob.getY() + 0.8, mob.getZ(), 5, 0.25, 0.4, 0.25, 0.02);
                } catch (Throwable ignored) {
                }
                mob.discard();
                dispersed++;
            }
        }
        return dispersed;
    }

    public static int disperseHordeMobsAround(ServerLevel level, BlockPos center, double radius, double discardFraction) {
        if (level == null || center == null || radius <= 0) return 0;
        double radiusSq = radius * radius;
        double cx = center.getX() + 0.5;
        double cy = center.getY() + 0.5;
        double cz = center.getZ() + 0.5;
        List<Mob> mobs = level.getEntitiesOfClass(
                Mob.class,
                hordeSearchBox(center, radius),
                m -> m != null && m.isAlive() && m.entityTags().contains("za_horde_mob") && m.distanceToSqr(cx, cy, cz) <= radiusSq
        );
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
                    collapseEngineerBlocks(level);
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
        hasRecentlySeenSpecialUnitsOrTurrets = false;
        HordeSpecialUnitHelper.clearAllState();
    }

    private static void pruneExpiredMaps(long currentTick) {
        heatMap.entrySet().removeIf(mapEntry -> {
            HeatEntry entry = mapEntry.getValue();
            if (entry == null) {
                return true;
            }
            synchronized (entry) {
                long elapsed = Math.max(0L, currentTick - entry.lastTick);
                int decay = (int) (elapsed / 4L);
                int effectiveHeat = Math.max(0, entry.heat - decay);
                return effectiveHeat == 0 && (currentTick - entry.lastWaveTriggerTick) >= 1200L;
            }
        });
        playerBreatherUntilTick.entrySet().removeIf(e -> e.getValue() == null || e.getValue() <= currentTick);
    }

    public static int computeSquadScaledWaveSize(int baseSize, int stageNum, int nearbyPlayers, boolean bloodmoon) {
        int base = baseSize > 0 ? baseSize : DEFAULT_BASE_SWARM_WAVE_SIZE;
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

    public static int computeRapidSpawnIntervalTicks() {
        return ZAConfigZDay.hordeRapidSpawnIntervalTicks > 0
                ? ZAConfigZDay.hordeRapidSpawnIntervalTicks
                : DEFAULT_HORDE_RAPID_SPAWN_INTERVAL_TICKS;
    }

    public static int computeRapidSpawnBatchSize(int subWaveCount, boolean fortified, boolean bloodmoon) {
        int base = ZAConfigZDay.hordeRapidSpawnBatchSize > 0
                ? ZAConfigZDay.hordeRapidSpawnBatchSize
                : DEFAULT_HORDE_RAPID_SPAWN_BATCH_SIZE;
        int batch = base;
        if (bloodmoon) {
            batch++;
        }
        if (fortified) {
            batch++;
        }
        if (subWaveCount >= 30) {
            batch++;
        }
        int cap = Math.max(base, MAX_RAPID_SPAWN_BATCH_SIZE);
        return Math.max(1, Math.min(cap, batch));
    }

    public static int computeEffectiveHordeMobCap(int baseCap, int nearbyPlayers, boolean bloodmoon) {
        int resolvedBase = baseCap > 0 ? baseCap : DEFAULT_HORDE_MAX_ALIVE_CAP;
        int squad = Math.max(1, nearbyPlayers);
        int cap = resolvedBase + (squad - 1) * 40;
        return bloodmoon ? cap * 2 : cap;
    }

    public static int computeAllowedSubWaveSpawnCount(int requestedCount, int currentAliveInHorde, int effectiveMaxCap) {
        if (requestedCount <= 0 || effectiveMaxCap <= 0 || currentAliveInHorde >= effectiveMaxCap) {
            return 0;
        }
        return Math.min(requestedCount, effectiveMaxCap - currentAliveInHorde);
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
                    Vec3 eyePos = player.getEyePosition();
                    Vec3 targetVec = new Vec3(originX, originY + 1.0, originZ);
                    if (areRayChunksLoaded(level, eyePos, targetVec)) {
                        HitResult hit = level.clip(new ClipContext(
                                eyePos,
                                targetVec,
                                ClipContext.Block.COLLIDER,
                                ClipContext.Fluid.NONE,
                                player
                        ));
                        obstructed = hit != null && hit.getType() != HitResult.Type.MISS;
                    }
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
        if (!isChunkLoadedAt(level, x, z)) return NO_DRY_SPAWN_Y;
        BlockPos.MutableBlockPos belowPos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos feetPos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos abovePos = new BlockPos.MutableBlockPos();

        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        boolean surfaceValid = false;
        feetPos.set(x, surfaceY, z);
        belowPos.set(x, surfaceY - 1, z);
        abovePos.set(x, surfaceY + 1, z);
        if (level.isLoaded(belowPos) && level.isLoaded(feetPos) && level.isLoaded(abovePos)) {
            BlockState belowState = level.getBlockState(belowPos);
            BlockState feetState = level.getBlockState(feetPos);
            BlockState headState = level.getBlockState(abovePos);
            if (isDrySolidSpawnColumn(belowState, feetState, headState)
                    && !belowState.getCollisionShape(level, belowPos).isEmpty()) {
                surfaceValid = true;
                if (surfaceY >= preferredY - 12 && surfaceY <= preferredY + 8) {
                    return surfaceY;
                }
            }
        }

        for (int y = preferredY + 8; y >= preferredY - 12; y--) {
            if (y == surfaceY) {
                continue;
            }
            feetPos.set(x, y, z);
            belowPos.set(x, y - 1, z);
            abovePos.set(x, y + 1, z);
            if (!level.isLoaded(belowPos) || !level.isLoaded(feetPos) || !level.isLoaded(abovePos)) {
                continue;
            }
            BlockState belowState = level.getBlockState(belowPos);
            BlockState feetState = level.getBlockState(feetPos);
            BlockState headState = level.getBlockState(abovePos);
            if (isDrySolidSpawnColumn(belowState, feetState, headState)
                    && !belowState.getCollisionShape(level, belowPos).isEmpty()) {
                return y;
            }
        }
        return surfaceValid ? surfaceY : NO_DRY_SPAWN_Y;
    }

    public static double selectDryApproachAngle(ServerLevel level, BlockPos center, double preferredAngleRad) {
        if (level == null || center == null) return preferredAngleRad;
        for (int k = 0; k < 12; k++) {
            double angle = preferredAngleRad + k * (2.0 * Math.PI / 12.0);
            int testX = (int) Math.round(center.getX() + Math.cos(angle) * 45.0);
            int testZ = (int) Math.round(center.getZ() + Math.sin(angle) * 45.0);
            if (!isChunkLoadedAt(level, testX, testZ)) {
                continue;
            }
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
        if (level == null || !isChunkLoadedAt(level, x, z)) return NO_DRY_SPAWN_Y;
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        BlockPos feetPos = new BlockPos(x, surfaceY - 1, z);
        BlockPos headPos = new BlockPos(x, surfaceY, z);
        if (!level.isLoaded(feetPos) || !level.isLoaded(headPos)) {
            return NO_DRY_SPAWN_Y;
        }
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
        boolean isTanker = zombie.entityTags().contains(HordeSpecialUnitHelper.TANKER_TAG);
        boolean isKnight = zombie.entityTags().contains(HordeSpecialUnitHelper.KNIGHT_TAG);
        boolean isEngineer = zombie.entityTags().contains(HordeSpecialUnitHelper.ENGINEER_TAG);
        boolean isRampage = zombie.entityTags().contains(RAMPAGE_TAG);

        Drowned drowned = zombie.convertTo(
            EntityTypes.DROWNED,
            ConversionParams.single(zombie, true, false),
            EntitySpawnReason.CONVERSION,
            (d) -> {
                d.addTag("za_horde_mob");
                if (isApex) d.addTag("za_apex_corrosive");
                if (isJuggernaut) d.addTag("za_juggernaut");
                if (isSapper) d.addTag("za_tnt_sapper");
                if (isTanker) d.addTag(HordeSpecialUnitHelper.TANKER_TAG);
                if (isKnight) d.addTag(HordeSpecialUnitHelper.KNIGHT_TAG);
                if (isEngineer) d.addTag(HordeSpecialUnitHelper.ENGINEER_TAG);
                applyHordeMobHealthScaling(d);
                if (isRampage) applyRampageBuffs(d);
                HordeSpecialUnitHelper.applySpecialMobIndicator(d);
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
        return level.getEntitiesOfClass(
                Mob.class,
                hordeSearchBox(center, radius),
                mob -> mob != null && mob.isAlive() && mob.entityTags().contains("za_horde_mob") && mob.distanceToSqr(cx, cy, cz) <= radiusSq
        ).size();
    }

    public static boolean isEligibleHordeTargetPlayer(boolean alive, boolean deadOrDying, boolean spectator, boolean creative) {
        return alive && !deadOrDying && !spectator && !creative;
    }

    public static boolean isEligibleHordeTargetPlayer(ServerPlayer player) {
        return player != null && isEligibleHordeTargetPlayer(
                player.isAlive(),
                player.isDeadOrDying(),
                player.isSpectator(),
                player.isCreative()
        );
    }

    public static ActiveHorde findNearestActiveHorde(double x, double y, double z) {
        ActiveHorde nearest = null;
        double bestDistSq = (GROUP_CLUSTER_RADIUS * 1.5) * (GROUP_CLUSTER_RADIUS * 1.5);
        for (ActiveHorde horde : activeHordes) {
            if (horde == null || horde.center == null || horde.isFullyExpired()) {
                continue;
            }
            double dx = x - (horde.center.getX() + 0.5);
            double dy = y - (horde.center.getY() + 0.5);
            double dz = z - (horde.center.getZ() + 0.5);
            double dSq = dx * dx + dy * dy + dz * dz;
            if (dSq <= bestDistSq) {
                bestDistSq = dSq;
                nearest = horde;
            }
        }
        return nearest;
    }

    public static ServerPlayer resolveHordeTargetPlayerForHorde(ServerLevel level, ActiveHorde horde) {
        if (level == null || horde == null || level.players() == null || level.players().isEmpty()) {
            return null;
        }
        double cx = horde.center != null ? horde.center.getX() + 0.5 : 0.0;
        double cy = horde.center != null ? horde.center.getY() + 0.5 : 64.0;
        double cz = horde.center != null ? horde.center.getZ() + 0.5 : 0.0;
        double maxDistSq = 128.0 * 128.0;

        ServerPlayer bestGroupPlayer = null;
        double bestGroupDistSq = Double.MAX_VALUE;
        ServerPlayer bestAnyPlayer = null;
        double bestAnyDistSq = Double.MAX_VALUE;

        for (ServerPlayer player : level.players()) {
            if (!isEligibleHordeTargetPlayer(player)) {
                continue;
            }
            double dSq = horde.center != null ? player.distanceToSqr(cx, cy, cz) : 0.0;
            if (horde.center != null && dSq > maxDistSq) {
                continue;
            }
            if (horde.targetGroupPlayerIds != null && horde.targetGroupPlayerIds.contains(player.getUUID())) {
                if (dSq < bestGroupDistSq) {
                    bestGroupDistSq = dSq;
                    bestGroupPlayer = player;
                }
            }
            if (dSq < bestAnyDistSq) {
                bestAnyDistSq = dSq;
                bestAnyPlayer = player;
            }
        }
        return bestGroupPlayer != null ? bestGroupPlayer : bestAnyPlayer;
    }

    public static ServerPlayer resolveHordeTargetPlayer(ServerLevel level, double x, double y, double z) {
        if (level == null || level.players() == null || level.players().isEmpty()) {
            return null;
        }
        ActiveHorde horde = findNearestActiveHorde(x, y, z);
        if (horde != null) {
            ServerPlayer hordePlayer = resolveHordeTargetPlayerForHorde(level, horde);
            if (hordePlayer != null) {
                return hordePlayer;
            }
        }
        ServerPlayer closest = null;
        double closestDistSq = 128.0 * 128.0;
        for (ServerPlayer player : level.players()) {
            if (!isEligibleHordeTargetPlayer(player)) {
                continue;
            }
            double dSq = player.distanceToSqr(x, y, z);
            if (dSq <= closestDistSq) {
                closestDistSq = dSq;
                closest = player;
            }
        }
        return closest;
    }

    public static ServerPlayer resolveHordeTargetPlayer(Mob mob) {
        if (mob == null || !(mob.level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        return resolveHordeTargetPlayer(serverLevel, mob.getX(), mob.getY(), mob.getZ());
    }

    public static int recruitNearbyWildMobsToHorde(ServerLevel level, BlockPos center, double radius) {
        if (level == null || center == null || radius <= 0) return 0;
        double radiusSq = radius * radius;
        double centerX = center.getX() + 0.5;
        double centerY = center.getY() + 0.5;
        double centerZ = center.getZ() + 0.5;

        ServerPlayer batchTarget = resolveHordeTargetPlayer(level, centerX, centerY, centerZ);
        List<Mob> nearbyHostiles = level.getEntitiesOfClass(
                Mob.class,
                hordeSearchBox(center, radius),
                mob -> mob != null && mob.isAlive() && mob.distanceToSqr(centerX, centerY, centerZ) <= radiusSq && ZAUtil.isHostileMob(mob)
        );
        int recruited = 0;
        for (Mob mob : nearbyHostiles) {
            if (!mob.entityTags().contains("za_horde_mob")) {
                mob.addTag("za_horde_mob");
            }
            LivingEntity currentTarget = mob.getTarget();
            if (currentTarget != null && ZAUtil.isVillagerEntity(currentTarget)) {
                mob.setTarget(null);
                currentTarget = null;
            }
            if (currentTarget == null || !currentTarget.isAlive()) {
                if (batchTarget != null) {
                    mob.setTarget(batchTarget);
                }
            }
            if (!mob.getNavigation().isInProgress()) {
                try {
                    if (batchTarget != null) {
                        CoroUtilPath.tryMoveToEntityLivingLongDist(mob, batchTarget, 1.0);
                    } else {
                        CoroUtilPath.tryMoveToXYZLongDist(mob, center.getX(), center.getY(), center.getZ(), 1.0);
                    }
                } catch (Throwable ignored) {}
            }
            recruited++;
        }
        return recruited;
    }

    private static Mob spawnStageScaledFrontZombie(
            ServerLevel level,
            HordePhase phase,
            int worldStageNum,
            double x,
            double y,
            double z,
            boolean isApexPhase,
            ThreadLocalRandom rand,
            ServerPlayer targetPlayer) {
        double r = rand.nextDouble();
        double knightP = computeScaledEliteChance(0.16D, HordeSpecialUnitHelper.KNIGHT_TAG, worldStageNum);
        double cursor = knightP;
        if (r < cursor) {
            HordeSpecialUnitHelper.spawnKnightCavalry(level, x, y, z, isApexPhase, targetPlayer);
            return null;
        }

        Zombie zombie = new Zombie(level);
        double tankerP = computeScaledEliteChance(0.20D, HordeSpecialUnitHelper.TANKER_TAG, worldStageNum);
        cursor += tankerP;
        if (r < cursor) {
            HordeSpecialUnitHelper.applyTankerLoadout(zombie);
            return zombie;
        }

        double engineerP = computeScaledEliteChance(0.16D, HordeSpecialUnitHelper.ENGINEER_TAG, worldStageNum);
        cursor += engineerP;
        if (r < cursor) {
            HordeSpecialUnitHelper.applyEngineerLoadout(zombie);
            return zombie;
        }

        double screamerP = computeScaledEliteChance(0.06D, "za_screamer", worldStageNum);
        cursor += screamerP;
        if (r < cursor) {
            com.nll.nightmareesm.nightmareesm.makeScreamer(zombie);
            return zombie;
        }

        double juggernautP = phase.ordinal() >= HordePhase.PHASE_3_JUGGERNAUTS_WITCHES.ordinal()
                ? computeScaledEliteChance(0.14D, HordeSpecialUnitHelper.JUGGERNAUT_TAG, worldStageNum)
                : 0.0D;
        cursor += juggernautP;
        if (r < cursor) {
            com.nll.nightmareesm.nightmareesm.applyJuggernautLoadout(zombie);
            if (isApexPhase) {
                zombie.addEffect(new MobEffectInstance(MobEffects.SPEED, 7200, 1));
            }
            return zombie;
        }

        double crawlerBase = (phase == HordePhase.PHASE_3_JUGGERNAUTS_WITCHES) ? 0.12D : 0.05D;
        double crawlerP = computeScaledEliteChance(crawlerBase, HordeSpecialUnitHelper.CRAWLER_TAG, worldStageNum);
        cursor += crawlerP;
        if (r < cursor) {
            zombie.addTag(HordeSpecialUnitHelper.CRAWLER_TAG);
            zombie.addEffect(new MobEffectInstance(MobEffects.SPEED, 7200, 1));
            return zombie;
        }

        double sapperP = phase.ordinal() >= HordePhase.PHASE_4_CLIMAX_SAPPERS.ordinal()
                ? computeScaledEliteChance(0.14D, HordeSpecialUnitHelper.SAPPER_TAG, worldStageNum)
                : 0.0D;
        cursor += sapperP;
        if (r < cursor) {
            zombie.addTag(HordeSpecialUnitHelper.SAPPER_TAG);
            return zombie;
        }

        if (worldStageNum >= 5) {
            new com.nll.nightmareesm.GiveRandomPickaxe(zombie);
        } else {
            applyStageSprinterSpeed(zombie, worldStageNum, rand.nextDouble());
        }
        return zombie;
    }

    public static void spawnPhasedSubWave(ServerLevel level, BlockPos center, HordePhase phase, int count, int worldStageNum) {
        spawnPhasedSubWave(level, center, ThreadLocalRandom.current().nextDouble() * 2.0 * Math.PI, phase, count, worldStageNum, false);
    }

    public static void spawnPhasedSubWave(ServerLevel level, BlockPos center, double approachAngleRad, HordePhase phase, int count, int worldStageNum) {
        spawnPhasedSubWave(level, center, approachAngleRad, phase, count, worldStageNum, false);
    }

    public static void spawnPhasedSubWave(ServerLevel level, BlockPos center, double approachAngleRad, HordePhase phase, int count, int worldStageNum, boolean surroundMode) {
        if (level == null || center == null || count <= 0) return;
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        ServerPlayer batchTargetPlayer = resolveHordeTargetPlayer(level, center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5);

        for (int i = 0; i < count; i++) {
            float roll = rand.nextFloat();
            boolean isFlank = isFlankSpawnIndex(i);
            double spawnApproachAngle = isFlank
                    ? computeFlankApproachAngle(approachAngleRad, rand.nextDouble() * 2.0 - 1.0)
                    : approachAngleRad;
            boolean willBePhantom = !isFlank && canHordeSpawnPhantoms(phase, worldStageNum) && (roll >= 0.88f);

            double targetX = center.getX();
            double targetZ = center.getZ();
            int spawnY = NO_DRY_SPAWN_Y;

            for (int attempt = 0; attempt < 16; attempt++) {
                double candX, candZ;
                if (attempt < 8) {
                    double normAngle = rand.nextDouble() * 2.0 - 1.0;
                    double normDist = rand.nextDouble();
                    double[] offset = computeSpawnOffset(spawnApproachAngle, normAngle, normDist, surroundMode);
                    candX = center.getX() + offset[0];
                    candZ = center.getZ() + offset[1];
                } else {
                    double sweepAngle = rand.nextDouble() * 2.0 * Math.PI;
                    double sweepDist = 24.0 + rand.nextDouble() * 28.0;
                    candX = center.getX() + Math.cos(sweepAngle) * sweepDist;
                    candZ = center.getZ() + Math.sin(sweepAngle) * sweepDist;
                }
                int bx = (int) Math.floor(candX);
                int bz = (int) Math.floor(candZ);
                if (!isChunkLoadedAt(level, bx, bz)) {
                    continue;
                }
                int foundY = findSafeDrySpawnY(level, bx, center.getY(), bz);
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
                    int bx = (int) Math.floor(candX);
                    int bz = (int) Math.floor(candZ);
                    if (!isChunkLoadedAt(level, bx, bz)) {
                        continue;
                    }
                    int waterY = findSafeWaterSpawnY(level, bx, center.getY(), bz);
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
                double[] offset = computeSpawnOffset(spawnApproachAngle, normAngle, normDist, surroundMode);
                targetX = center.getX() + offset[0];
                targetZ = center.getZ() + offset[1];
                if (!isChunkLoadedAt(level, (int) Math.floor(targetX), (int) Math.floor(targetZ))) {
                    continue;
                }
                spawnY = center.getY();
            }

            try {
                Mob mob = null;

                if (isWaterSpawn) {
                    Drowned drowned = new Drowned(EntityTypes.DROWNED, level);
                    applyStageSprinterSpeed(drowned, worldStageNum, rand.nextDouble());
                    mob = drowned;
                } else if (isFlank) {
                    double flankRoll = rand.nextDouble();
                    double knightChance = phase.ordinal() >= HordePhase.PHASE_2_BREACHERS_SKELETONS.ordinal()
                            ? computeScaledEliteChance(0.35D, HordeSpecialUnitHelper.KNIGHT_TAG, worldStageNum)
                            : 0.0D;
                    double crawlerChance = computeScaledEliteChance(0.33D, HordeSpecialUnitHelper.CRAWLER_TAG, worldStageNum);
                    double sapperChance = phase.ordinal() >= HordePhase.PHASE_3_JUGGERNAUTS_WITCHES.ordinal()
                            ? computeScaledEliteChance(0.22D, HordeSpecialUnitHelper.SAPPER_TAG, worldStageNum)
                            : 0.0D;
                    if (flankRoll < knightChance) {
                        HordeSpecialUnitHelper.spawnKnightCavalry(
                                level, targetX, spawnY, targetZ, phase == HordePhase.PHASE_5_APEX_CORROSION, batchTargetPlayer);
                        continue;
                    } else if (flankRoll < knightChance + crawlerChance) {
                        Zombie z = new Zombie(level);
                        z.addTag(HordeSpecialUnitHelper.CRAWLER_TAG);
                        z.addEffect(new MobEffectInstance(MobEffects.SPEED, 7200, 1));
                        mob = z;
                    } else if (flankRoll < knightChance + crawlerChance + sapperChance) {
                        Zombie z = new Zombie(level);
                        z.addTag(HordeSpecialUnitHelper.SAPPER_TAG);
                        mob = z;
                    } else {
                        Zombie z = new Zombie(level);
                        applyStageSprinterSpeed(z, worldStageNum, rand.nextDouble());
                        mob = z;
                    }
                } else {
                    switch (phase) {
                        case PHASE_1A_FAST_ZOMBIES -> {
                            Zombie z = new Zombie(level);
                            double tankerChance = computeScaledEliteChance(0.15D, HordeSpecialUnitHelper.TANKER_TAG, worldStageNum);
                            double crawlerChance = computeScaledEliteChance(0.08D, HordeSpecialUnitHelper.CRAWLER_TAG, worldStageNum);
                            if (roll < tankerChance) {
                                HordeSpecialUnitHelper.applyTankerLoadout(z);
                            } else if (roll < tankerChance + crawlerChance) {
                                z.addTag(HordeSpecialUnitHelper.CRAWLER_TAG);
                                z.addEffect(new MobEffectInstance(MobEffects.SPEED, 7200, 1));
                            } else {
                                applyStageSprinterSpeed(z, worldStageNum, rand.nextDouble());
                            }
                            mob = z;
                        }
                        case PHASE_1B_VENOM_SPIDERS -> {
                            double tankerChance = computeScaledEliteChance(0.18D, HordeSpecialUnitHelper.TANKER_TAG, worldStageNum);
                            double crawlerChance = computeScaledEliteChance(0.08D, HordeSpecialUnitHelper.CRAWLER_TAG, worldStageNum);
                            if (roll < tankerChance) {
                                Zombie z = new Zombie(level);
                                HordeSpecialUnitHelper.applyTankerLoadout(z);
                                mob = z;
                            } else if (roll < tankerChance + crawlerChance) {
                                Zombie z = new Zombie(level);
                                z.addTag(HordeSpecialUnitHelper.CRAWLER_TAG);
                                z.addEffect(new MobEffectInstance(MobEffects.SPEED, 7200, 1));
                                mob = z;
                            } else if (roll < 0.65f) {
                                Zombie z = new Zombie(level);
                                applyStageSprinterSpeed(z, worldStageNum, rand.nextDouble());
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
                                Mob spawned = spawnStageScaledFrontZombie(
                                        level, phase, worldStageNum, targetX, spawnY, targetZ, false, rand, batchTargetPlayer);
                                if (spawned == null) {
                                    continue;
                                }
                                mob = spawned;
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
                                Mob spawned = spawnStageScaledFrontZombie(
                                        level, phase, worldStageNum, targetX, spawnY, targetZ, false, rand, batchTargetPlayer);
                                if (spawned == null) {
                                    continue;
                                }
                                mob = spawned;
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
                                Mob spawned = spawnStageScaledFrontZombie(
                                        level, phase, worldStageNum, targetX, spawnY, targetZ, false, rand, batchTargetPlayer);
                                if (spawned == null) {
                                    continue;
                                }
                                mob = spawned;
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
                            } else if (roll < 0.45f) {
                                Mob spawned = spawnStageScaledFrontZombie(
                                        level, phase, worldStageNum, targetX, spawnY, targetZ, true, rand, batchTargetPlayer);
                                if (spawned == null) {
                                    continue;
                                }
                                mob = spawned;
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
                    if (!level.isLoaded(mob.blockPosition())) {
                        continue;
                    }
                    try {
                        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), EntitySpawnReason.EVENT, null);
                    } catch (Throwable ignored) {}
                    applyHordeMobHealthScaling(mob);
                    if (mob instanceof Zombie z && HordeSpecialUnitHelper.isTankerZombie(z)) {
                        HordeSpecialUnitHelper.enforceTankerSpeed(z);
                        HordeSpecialUnitHelper.ensureTankerResistanceEffect(z);
                    }
                    HordeSpecialUnitHelper.applySpecialMobIndicator(mob);
                    level.addFreshEntity(mob);
                    if (batchTargetPlayer != null) {
                        mob.setTarget(batchTargetPlayer);
                    }
                    if (isPhantom) {
                        Mob passenger = level.getRandom().nextBoolean() ? (Mob) createMobById(level, "creeper") : new Zombie(level);
                        if (passenger != null) {
                            passenger.addTag("za_horde_mob");
                            passenger.addTag("za_airdrop_passenger");
                            passenger.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 7200, 0, false, false));
                            passenger.setPos(targetX, actualY, targetZ);
                            if (level.isLoaded(passenger.blockPosition())) {
                                try {
                                    passenger.finalizeSpawn(level, level.getCurrentDifficultyAt(passenger.blockPosition()), EntitySpawnReason.EVENT, null);
                                } catch (Throwable ignored) {}
                                applyHordeMobHealthScaling(passenger);
                                level.addFreshEntity(passenger);
                                passenger.startRiding(mob);
                                if (batchTargetPlayer != null) {
                                    passenger.setTarget(batchTargetPlayer);
                                }
                            }
                        }
                    }
                    try {
                        if (batchTargetPlayer != null) {
                            CoroUtilPath.tryMoveToEntityLivingLongDist(mob, batchTargetPlayer, 1.0);
                        } else {
                            CoroUtilPath.tryMoveToXYZLongDist(mob, center.getX(), center.getY(), center.getZ(), 1.0);
                        }
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
        triggerSwarmWave(level, originX, originY, originZ, waveSize, nearbyPlayers, stage, groupPlayerIds, ThreadLocalRandom.current().nextDouble() * 2.0 * Math.PI);
    }

    public static void triggerSwarmWave(ServerLevel level, double originX, double originY, double originZ, int waveSize, int nearbyPlayers, int stage, Collection<UUID> groupPlayerIds, double approachAngleRad) {
        if (level == null || waveSize <= 0) return;
        BlockPos center = BlockPos.containing(originX, originY, originZ);
        if (!canTriggerHordeForGroup(center, groupPlayerIds)) {
            return;
        }

        boolean fortified = isPositionFortifiedByWallsOrBase(level, center);
        int finalWaveSize = computeFortifiedWaveCount(waveSize, fortified);
        boolean bloodmoon = BloodmoonManager.isBloodmoonActive(level);
        int effectiveCap = computeEffectiveHordeMobCap(ZAConfigZDay.hordeMaxAliveNearPlayer, nearbyPlayers, bloodmoon);
        int currentAlive = countAliveHordeMobsAround(level, center, GROUP_CLUSTER_RADIUS);

        int subWaveCount = computeSubWaveCount(finalWaveSize);
        if (!tryRegisterHordeForGroup(center, groupPlayerIds, nearbyPlayers, subWaveCount, stage, approachAngleRad, fortified)) {
            return;
        }
        ActiveHorde registered = activeHordes.isEmpty() ? null : activeHordes.get(activeHordes.size() - 1);
        if (registered != null && center.equals(registered.center)) {
            registered.lastFortifiedCheckTick = level.getGameTime();
            registered.lastFortifiedCheckPos = center;
        }

        try {
            if (level.isLoaded(center)) {
                EntityScent waypoint = new EntityScent(ZombieAwareness.SENSE, level);
                waypoint.setPos(originX, originY, originZ);
                waypoint.setStrengthPeak(150);
                waypoint.setSenseType(EnumSenseType.WAYPOINT.ordinal());
                level.addFreshEntity(waypoint);
            }
        } catch (Throwable ignored) {}

        HordePhase initialPhase = resolveHordePhase(0.0, stage);
        int initialBatchTarget = computeRapidSpawnBatchSize(subWaveCount, fortified, bloodmoon);
        int initialWaveCount = computeAllowedSubWaveSpawnCount(initialBatchTarget, currentAlive, effectiveCap);
        if (initialWaveCount > 0) {
            spawnPhasedSubWave(level, center, approachAngleRad, initialPhase, initialWaveCount, stage, fortified);
        }

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
        if (overworld == null || !overworld.tickRateManager().runsNormally()) return;
        long currentTick = overworld.getGameTime();

        if (currentTick % 1200L == 0) {
            pruneExpiredMaps(currentTick);
        }
        if (currentTick % 20L == 0) {
            tickEngineerBlockDecay(overworld, currentTick);
        }

        // 1. Ambient Late-Game Night Horde Check (runs every 1200 ticks = 60s)
        if (currentTick % 1200L == 0 && overworld.players() != null && !overworld.players().isEmpty()) {
            int stage = ZDayProgressionManager.getCurrentStage().getStageNumber();
            boolean bloodmoon = BloodmoonManager.isBloodmoonActive(overworld);
            ThreadLocalRandom ambientRand = ThreadLocalRandom.current();

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

        if (activeHordes.isEmpty()) {
            tickSpecialUnitsAndTurretFocus(overworld, currentTick);
            return;
        }
        List<ActiveHorde> toRemove = new ArrayList<>();

        for (ActiveHorde horde : activeHordes) {
            if (!horde.rampagePhaseActive) {
                horde.elapsedTicks++;
                if (horde.elapsedTicks >= horde.totalDurationTicks) {
                    int aliveAtEnd = countAliveHordeMobsAround(overworld, horde.center, GROUP_CLUSTER_RADIUS);
                    if (!evaluatePrimaryTimerEnd(horde, aliveAtEnd, ZAConfigZDay.hordeRampageDurationTicks)) {
                        completeHordeVictory(overworld, horde, currentTick, "\u00a7a\u00a7l[!] YOU HAVE SURVIVED THE HORDE! THE UNDEAD RETREAT!", toRemove);
                        continue;
                    }
                    activateRampageOnNearbyHordeMobs(overworld, horde);
                    announceHordeNotice(
                            overworld,
                            horde,
                            "\u00a74\u00a7l[!] MAIN HORDE TIMER ENDED — RAMPAGE OVERTIME! DEFEAT THE REMAINING "
                                    + aliveAtEnd + " ENRAGED MOBS WITHIN 2 MINUTES!",
                            SoundEvents.WITHER_SPAWN
                    );
                }
            } else {
                horde.rampageElapsedTicks++;
                if (horde.rampageElapsedTicks >= horde.rampageDurationTicks) {
                    completeHordeVictory(overworld, horde, currentTick, "\u00a7a\u00a7l[!] YOU HAVE SURVIVED THE HORDE! THE UNDEAD RETREAT!", toRemove);
                    continue;
                }
                if (horde.rampageElapsedTicks % 20 == 0) {
                    int currentAlive = countAliveHordeMobsAround(overworld, horde.center, GROUP_CLUSTER_RADIUS);
                    if (currentAlive <= 0) {
                        completeHordeVictory(overworld, horde, currentTick, "\u00a7a\u00a7l[!] ALL REMAINING RAMPAGING HORDE MOBS CLEARED — VICTORY IS YOURS!", toRemove);
                        continue;
                    }
                    activateRampageOnNearbyHordeMobs(overworld, horde);
                }
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
                        collapseEngineerBlocks(overworld);
                        continue;
                    }
                }
            } else {
                ServerPlayer nearestAny = resolveHordeTargetPlayerForHorde(overworld, horde);
                if (nearestAny != null) {
                    horde.center = nearestAny.blockPosition();
                    horde.noTargetTicks = 0;
                } else {
                    horde.noTargetTicks++;
                    if (horde.noTargetTicks >= 100) {
                        toRemove.add(horde);
                        resetHeatAround(horde.center.getX(), horde.center.getZ(), currentTick);
                        collapseEngineerBlocks(overworld);
                        continue;
                    }
                }
            }

            if (overworld.players() == null || overworld.players().isEmpty()) {
                continue;
            }

            double progress = (double) horde.elapsedTicks / (double) horde.totalDurationTicks;
            HordePhase phase = resolveHordePhase(progress, horde.worldStageNum);

            if (horde.lastFortifiedCheckTick < 0L
                    || (currentTick - horde.lastFortifiedCheckTick) >= 100L
                    || horde.lastFortifiedCheckPos == null
                    || horde.center.distSqr(horde.lastFortifiedCheckPos) > 64.0) {
                horde.surroundMode = isPositionFortifiedByWallsOrBase(overworld, horde.center);
                horde.lastFortifiedCheckTick = currentTick;
                horde.lastFortifiedCheckPos = horde.center;
            }
            boolean fortified = horde.surroundMode;
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

            long dayTime = overworld.getDefaultClockTime();
            long timeOfDay = ((dayTime % 24000L) + 24000L) % 24000L;
            boolean isNightNow = (timeOfDay >= 12000L && timeOfDay <= 23500L);
            boolean isBloodmoon = BloodmoonManager.isBloodmoonActive(overworld);
            int effectiveCap = computeEffectiveHordeMobCap(
                    ZAConfigZDay.hordeMaxAliveNearPlayer,
                    Math.max(1, horde.targetGroupPlayerIds.size()),
                    isBloodmoon
            );

            if (shouldReinforceHorde(horde) && phase == HordePhase.PHASE_5_APEX_CORROSION && !horde.apexPhaseTriggered) {
                horde.apexPhaseTriggered = true;
                BlockPos staging = horde.getStagingOrigin();
                playHordeRoar(overworld, staging.getX(), staging.getY(), staging.getZ());
                int batchTarget = computeRapidSpawnBatchSize(currentSubWaveCount, horde.surroundMode, isBloodmoon);
                int currentAlive = countAliveHordeMobsAround(overworld, horde.center, GROUP_CLUSTER_RADIUS);
                int spawnCount = computeAllowedSubWaveSpawnCount(batchTarget, currentAlive, effectiveCap);
                if (spawnCount > 0) {
                    spawnPhasedSubWave(overworld, horde.center, horde.approachAngleRad, phase, spawnCount, horde.worldStageNum, horde.surroundMode);
                }
            }
            if (HordeSpecialUnitHelper.shouldSpawnBloodmoonPhase5Wardens(phase, isBloodmoon, horde.bloodmoonWardensSpawned, shouldReinforceHorde(horde))) {
                horde.bloodmoonWardensSpawned = true;
                HordeSpecialUnitHelper.spawnBloodmoonPhase5Wardens(
                        overworld, horde.center, horde.approachAngleRad, horde.surroundMode, horde.originalGroupPlayerIds
                );
            }
            if (isNightNow) {
                horde.wasNight = true;
            }
            if (isDawnArrival(horde.wasNight, dayTime, isBloodmoon)) {
                boolean anyPlayerAlive = false;
                if (overworld.players() != null) {
                    for (ServerPlayer player : overworld.players()) {
                        if (player != null && horde.targetGroupPlayerIds.contains(player.getUUID()) && player.isAlive() && !player.isDeadOrDying()) {
                            anyPlayerAlive = true;
                            break;
                        }
                    }
                }
                if (anyPlayerAlive) {
                    completeHordeVictory(overworld, horde, currentTick, "\u00a7a\u00a7l[!] DAWN BREAKS \u2014 YOU HAVE SURVIVED THE NIGHT! THE HORDE RETREATS!", toRemove);
                    continue;
                }
            }

            if (!horde.rampagePhaseActive && phase.ordinal() >= HordePhase.PHASE_4_CLIMAX_SAPPERS.ordinal()) {
                if (horde.phase4InitialMobCount < 0 || horde.elapsedTicks % 20 == 0) {
                    int currentAlive = countAliveHordeMobsAround(overworld, horde.center, GROUP_CLUSTER_RADIUS);
                    if (horde.phase4InitialMobCount < 0) {
                        horde.phase4InitialMobCount = Math.max(1, currentAlive);
                    }
                    if (isHordeDecimated(currentAlive, horde.phase4InitialMobCount) || currentAlive == 0) {
                        completeHordeVictory(overworld, horde, currentTick, "\u00a7a\u00a7l[!] THE HORDE HAS BEEN OVERCOME (UNDER 2% REMAINING) \u2014 VICTORY IS YOURS!", toRemove);
                        continue;
                    }
                }
            }

            int phaseTick = horde.rampagePhaseActive ? horde.rampageElapsedTicks : horde.elapsedTicks;
            if (phaseTick % 10 == 0) {
                double radiusSq = GROUP_CLUSTER_RADIUS * GROUP_CLUSTER_RADIUS;
                double cx = horde.center.getX() + 0.5;
                double cy = horde.center.getY() + 0.5;
                double cz = horde.center.getZ() + 0.5;
                List<Phantom> carrierPhantoms = overworld.getEntitiesOfClass(
                        Phantom.class,
                        hordeSearchBox(horde.center, GROUP_CLUSTER_RADIUS),
                        p -> p != null && p.isAlive() && !p.getPassengers().isEmpty()
                                && p.entityTags().contains("za_horde_mob")
                                && p.distanceToSqr(cx, cy, cz) <= radiusSq
                );
                for (Phantom phantom : carrierPhantoms) {
                    LivingEntity target = phantom.getTarget();
                    if (target == null) {
                        target = overworld.getNearestPlayer(phantom, 96.0);
                    }
                    if (target != null) {
                        checkAndDropPhantomPassenger(phantom, target);
                    }
                }
            }

            if (phaseTick % 20 == 0) {
                double radiusSq = GROUP_CLUSTER_RADIUS * GROUP_CLUSTER_RADIUS;
                double steerRadius = GROUP_CLUSTER_RADIUS * 1.5;
                double steerRadiusSq = steerRadius * steerRadius;
                double cx = horde.center.getX() + 0.5;
                double cy = horde.center.getY() + 0.5;
                double cz = horde.center.getZ() + 0.5;
                ServerPlayer activeTargetPlayer = resolveHordeTargetPlayerForHorde(overworld, horde);
                List<Mob> nearbyHordeMobs = overworld.getEntitiesOfClass(
                        Mob.class,
                        hordeSearchBox(horde.center, steerRadius),
                        m -> m != null && m.isAlive() && ZAUtil.isHordeMob(m) && m.distanceToSqr(cx, cy, cz) <= steerRadiusSq
                );
                List<Zombie> toConvertToDrowned = null;
                for (Mob m : nearbyHordeMobs) {
                    if (HordeSpecialUnitHelper.isBloodmoonWarden(m)) {
                        continue;
                    }
                    LivingEntity currentTarget = m.getTarget();
                    if (currentTarget != null && ZAUtil.isVillagerEntity(currentTarget)) {
                        m.setTarget(null);
                        currentTarget = null;
                    }
                    if (activeTargetPlayer != null) {
                        if (currentTarget == null || !currentTarget.isAlive()) {
                            m.setTarget(activeTargetPlayer);
                        }
                        if (!m.getNavigation().isInProgress() && m.getNavigation().isDone() && m.distanceToSqr(activeTargetPlayer) > 16.0) {
                            try {
                                CoroUtilPath.tryMoveToEntityLivingLongDist(m, activeTargetPlayer, 1.0);
                            } catch (Throwable ignored) {}
                        }
                    }
                    if (m instanceof Zombie z && !(z instanceof Drowned) && z.entityTags().contains("za_horde_mob")) {
                        if (z.distanceToSqr(cx, cy, cz) <= radiusSq) {
                            if (z.isInWater() || z.isInShallowWater() || z.isUnderWater()
                                    || (overworld.isLoaded(z.blockPosition()) && overworld.getFluidState(z.blockPosition()).is(FluidTags.WATER))) {
                                if (toConvertToDrowned == null) {
                                    toConvertToDrowned = new ArrayList<>();
                                }
                                toConvertToDrowned.add(z);
                            }
                        }
                    }
                }
                if (toConvertToDrowned != null) {
                    for (Zombie z : toConvertToDrowned) {
                        if (z.isAlive() && !z.isRemoved()) {
                            convertHordeZombieToDrowned(z);
                        }
                    }
                }
            }

            if (shouldReinforceHorde(horde) && horde.elapsedTicks % 100 == 0) {
                recruitNearbyWildMobsToHorde(overworld, horde.center, GROUP_CLUSTER_RADIUS);
            }

            int spawnInterval = computeRapidSpawnIntervalTicks();
            if (shouldReinforceHorde(horde) && horde.elapsedTicks % spawnInterval == 0) {
                int batchTarget = computeRapidSpawnBatchSize(currentSubWaveCount, horde.surroundMode, isBloodmoon);
                int currentAlive = countAliveHordeMobsAround(overworld, horde.center, GROUP_CLUSTER_RADIUS);
                int spawnCount = computeAllowedSubWaveSpawnCount(batchTarget, currentAlive, effectiveCap);
                if (spawnCount > 0) {
                    spawnPhasedSubWave(overworld, horde.center, horde.approachAngleRad, phase, spawnCount, horde.worldStageNum, horde.surroundMode);
                }
            }
        }
        if (!toRemove.isEmpty()) {
            activeHordes.removeAll(toRemove);
        }
        tickSpecialUnitsAndTurretFocus(overworld, currentTick);
    }

    private static void tickSpecialUnitsAndTurretFocus(ServerLevel overworld, long currentTick) {
        if (overworld == null) return;
        if (activeHordes.isEmpty()
                && !hasRecentlySeenSpecialUnitsOrTurrets
                && !HordeSpecialUnitHelper.hasActiveWardenStates()
                && !HordeSpecialUnitHelper.hasArmedThrownSapperStates()
                && currentTick % 40L != 0L) {
            return;
        }
        List<Mob> turrets = null;
        List<Mob> tankers = null;
        List<Mob> wardens = null;
        List<Zombie> juggernauts = null;
        List<Mob> thrownSappers = null;
        boolean foundAnySpecialOrTurret = false;

        for (Entity e : overworld.getAllEntities()) {
            if (!(e instanceof Mob m) || !m.isAlive() || m.isRemoved()) {
                continue;
            }
            if (m.entityTags().contains(HordeSpecialUnitHelper.SAPPER_FUSE_TAG)
                    || HordeSpecialUnitHelper.isArmedThrownSapper(m.getUUID())) {
                foundAnySpecialOrTurret = true;
                if (thrownSappers == null) thrownSappers = new ArrayList<>();
                thrownSappers.add(m);
            }
            if (HordeSpecialUnitHelper.isTaczTurretEntity(m)) {
                foundAnySpecialOrTurret = true;
                if (turrets == null) turrets = new ArrayList<>();
                turrets.add(m);
            } else if (m instanceof Zombie z && HordeSpecialUnitHelper.isTankerZombie(z)) {
                foundAnySpecialOrTurret = true;
                if (tankers == null) tankers = new ArrayList<>();
                tankers.add(z);
                if (currentTick % 20L == 0) {
                    HordeSpecialUnitHelper.enforceTankerSpeed(z);
                    HordeSpecialUnitHelper.ensureTankerResistanceEffect(z);
                }
            } else if (m instanceof Zombie z && HordeSpecialUnitHelper.isKnightZombie(z)) {
                foundAnySpecialOrTurret = true;
                if (currentTick % 20L == 0) {
                    HordeSpecialUnitHelper.ensureKnightResistanceEffect(z);
                }
            } else if (m instanceof Zombie z && HordeSpecialUnitHelper.isJuggernautZombie(z)) {
                foundAnySpecialOrTurret = true;
                if (juggernauts == null) juggernauts = new ArrayList<>();
                juggernauts.add(z);
            } else if (HordeSpecialUnitHelper.isKnightMount(m)) {
                foundAnySpecialOrTurret = true;
                HordeSpecialUnitHelper.steerKnightMountToRiderTarget(m);
            } else if (HordeSpecialUnitHelper.isBloodmoonWarden(m)) {
                foundAnySpecialOrTurret = true;
                if (wardens == null) wardens = new ArrayList<>();
                wardens.add(m);
            }
        }
        hasRecentlySeenSpecialUnitsOrTurrets = foundAnySpecialOrTurret;

        List<Mob> turretList = turrets != null ? turrets : Collections.emptyList();
        if (currentTick % 5L == 0 && turrets != null && tankers != null) {
            HordeSpecialUnitHelper.tickTurretFocus(turrets, tankers);
        }
        if (juggernauts != null) {
            for (Zombie juggernaut : juggernauts) {
                HordeSpecialUnitHelper.tickJuggernautThrower(overworld, juggernaut, turretList, currentTick);
            }
        }
        if (thrownSappers != null) {
            HordeSpecialUnitHelper.tickThrownSappers(overworld, thrownSappers);
        }
        if (wardens != null) {
            for (Mob warden : wardens) {
                ServerPlayer fallbackPlayer = resolveHordeTargetPlayer(warden);
                HordeSpecialUnitHelper.tickBloodmoonWarden(overworld, warden, turretList, fallbackPlayer);
            }
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
        if (level != null && level.getServer() != null && !level.getServer().isSameThread()) {
            level.getServer().execute(() -> onTaCZGunSound(level, x, y, z, taczDistance, soundName));
            return;
        }
        if (level == null) {
            // Unit test fallback path when Minecraft ServerLevel is not bootstrapped
            String lower = soundName.toLowerCase(Locale.ROOT);
            if (isSuppressedTaCZSound(lower)) {
                if (ZDayProgressionManager.isStageUnlocked(2)) {
                    int threshold = ZAConfigZDay.chunkHeatThreshold;
                    int currentHeat = getHeatAtBlockPos((int) x, (int) z, 0L);
                int heatUnit = computeGunfireHeatAmount(isDaytime(level), true);
                int heatToAdd = Math.min(heatUnit, Math.max(0, (threshold - 1) - currentHeat));
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
                int heatUnit = computeGunfireHeatAmount(isDaytime(level), true);
                int heatToAdd = Math.min(heatUnit, Math.max(0, (threshold - 1) - currentHeat));
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
        if (level.getServer() != null && !level.getServer().isSameThread()) {
            level.getServer().execute(() -> onSoundPlayed(level, x, y, z, namespace, path, spawnScent));
            return;
        }
        if (isQuietFirearmHandlingSound(namespace, path)) return;
        if (!isFirearmOrExplosionSound(namespace, path)) return;
        if (!ZDayProgressionManager.isStageUnlocked(2)) return;

        if (spawnScent) {
            try {
                ZAUtil.spawnOrBuffSenseAtPos(level, new Vector3d(x, y, z), EnumSenseType.SOUND, 112);
            } catch (Throwable ignored) {}
        }

        long gameTick = level.getGameTime();
        int heatToAdd = computeGunfireHeatAmount(isDaytime(level), false);
        int currentHeat = addHeatAtBlockPos((int) x, (int) z, heatToAdd, gameTick);
        checkAndTriggerHorde(level, x, y, z, currentHeat, gameTick);
    }

    private static void checkAndTriggerHorde(ServerLevel level, double x, double y, double z, int currentHeat, long gameTick) {
        if (level != null && level.dimension() != Level.OVERWORLD) return;
        int threshold = ZAConfigZDay.chunkHeatThreshold;
        if (currentHeat >= threshold) {
            long centerKey = ChunkPos.pack(((int) x) >> 4, ((int) z) >> 4);
            HeatEntry entry = heatMap.get(centerKey);
            if (entry != null) {
                boolean initialCooldownReady;
                synchronized (entry) {
                    initialCooldownReady = (gameTick - entry.lastWaveTriggerTick >= 1200L);
                }
                if (!initialCooldownReady) {
                    return;
                }

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

                synchronized (entry) {
                    if (gameTick - entry.lastWaveTriggerTick < 1200L) {
                        return;
                    }
                    entry.lastWaveTriggerTick = gameTick;
                }

                double preferredAngleRad = ThreadLocalRandom.current().nextDouble() * 2.0 * Math.PI;
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
