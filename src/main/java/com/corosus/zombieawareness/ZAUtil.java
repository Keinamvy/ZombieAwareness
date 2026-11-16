package com.corosus.zombieawareness;

import com.corosus.coroutil.util.*;
import com.corosus.zombieawareness.client.SoundProfileEntry;
import com.corosus.zombieawareness.client.SoundRegistry;
import com.corosus.zombieawareness.config.ZAConfigFeatures;
import com.corosus.zombieawareness.config.ZAConfigGeneral;
import com.corosus.zombieawareness.config.ZAConfigPlayerLists;
import com.corosus.zombieawareness.config.ZAConfigZDay;
import com.corosus.zombieawareness.heat.ChunkHeatManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.EndGatewayBlock;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

public class ZAUtil {
    
    public static Random rand = new Random();
    
    public static Map<UUID, Integer> lastHealths = new ConcurrentHashMap<>();
    public static Map<UUID, Long> lastBleedTimes = new ConcurrentHashMap<>();

    public static List<SoundProfileEntry> listSoundProfiles = new CopyOnWriteArrayList<>();

	//runtime cache lookups, populates as sounds are made, useful for partial matches that require iterating entire sound profile list each time
	public static Map<String, SoundProfileEntry> lookupSoundNameToProfileCache = new ConcurrentHashMap<>();
	private static final SoundProfileEntry NO_MATCH_SENTINEL = new SoundProfileEntry("__za_no_match__", 0.0D);
	public static Map<Integer, SoundProfileEntry> lookupSoundIntToProfile = new ConcurrentHashMap<>();

    public static Map<Entity, Long> lookupLastAlertTime = Collections.synchronizedMap(new WeakHashMap<>());
    public static long alertDelay = 60*20;
    
    public static Map<Entity, Long> lookupLastInvestigateTime = Collections.synchronizedMap(new WeakHashMap<>());
    public static long investigateDelay = 60*20;

	public static Map<UUID, Long> lookupLastWaypointTime = new ConcurrentHashMap<>();

	public static Map<EntityType, Boolean> lookupTickableEntitiesCache = new ConcurrentHashMap<>();

	public static String SPEED_BOOST_TAG = "za_speedbosted";
	public static String ZA_LAST_ACTION = "za_last_action";

    public static final float APEX_ARMOR_BYPASS_RATIO = 0.40f; // Bypasses 40% of player armor
    public static final float NECROTIC_REGEN_EFFICIENCY = 0.50f; // Reduces healing/regeneration efficiency by 50%
    public static final long NECROTIC_TRAUMA_DURATION_TICKS = 160L; // 8 seconds
    public static final float HORDE_MOB_LOOT_DROP_CHANCE = 0.20f; // Only 20% of horde mobs drop loot (80% drop reduction)
    public static final double DAMAGE_CLONE_LOOT_MULTIPLIER = 0.25D; // Clones drop at 25% of a normal mob's drop rate
    public static final double NORMAL_HORDE_GUNPOWDER_DROP_CHANCE = 0.50D; // 50% chance for normal horde mobs to drop 1-3 gunpowder
    public static final String DAMAGE_CLONE_TAG = "za_damage_clone";

    public static final long ONE_HEART_CLUTCH_COOLDOWN_TICKS = 6000L; // 5 minutes
    public static final double ONE_HEART_CLUTCH_CHANCE = 0.25D;
    public static final float ONE_HEART_CLUTCH_HEALTH = 2.0F; // 1 heart
    public static final long ONE_HEART_CLUTCH_INVUL_TICKS = 40L; // 2.0s post-clutch immunity
    public static final int ADRENALINE_SURGE_DURATION_TICKS = 240; // 12 seconds
    public static final float ADRENALINE_LIFE_ON_KILL_HEAL = 2.0F; // +1 heart
    public static final List<String> CLUTCH_EPILOGUE_LINES = List.of(
            "Not here... not like this.",
            "Just one more breath...",
            "I can't fall yet...",
            "Vision's fading... push through!"
    );

    private static long lastDistressScentTick = -9999L;

    private static final Map<UUID, Long> necroticTraumaUntilTick = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> lastDamageCloneTickMap = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> lastOneHeartClutchTickMap = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> adrenalineActiveUntilTickMap = new ConcurrentHashMap<>();

    public static final long ELITE_DIAMOND_WINDOW_TICKS = 6000L; // 5 minutes
    private static final Object ELITE_DIAMOND_LOCK = new Object();
    private static final ArrayDeque<Long> recentBonusDiamondTicks = new ArrayDeque<>();

    public static boolean debug = false;
    
    static {
		//do not initialize early, thanks to forge config not allowing early config loading
		//addSoundHooks();
    }

	public static synchronized void initSoundHookDataIfEmpty() {
		if (listSoundProfiles.isEmpty()) {
			addSoundHooks();
		}
	}

	public static synchronized void addSoundHooks() {
		int noisyInteractRange = 30;
		double noisyInteractBuff = 1.3D;

		listSoundProfiles.clear();
		lookupSoundIntToProfile.clear();
		lookupSoundNameToProfileCache.clear();

		//short dist ones
		listSoundProfiles.add(new SoundProfileEntry(SoundEvents.ARROW_HIT_PLAYER, 1.1D));
		listSoundProfiles.add(new SoundProfileEntry(SoundEvents.ARROW_HIT, 1.1D));
		listSoundProfiles.add(new SoundProfileEntry(SoundEvents.CROSSBOW_HIT, 1.1D));

		listSoundProfiles.add(new SoundProfileEntry(SoundEvents.CHEST_CLOSE, noisyInteractBuff).setMaxDistToSpawnFromPlayer(noisyInteractRange));

		List<Integer> listDoorSoundEvents = new ArrayList<>();
		//trap doors opening and close, metal and wood
		listDoorSoundEvents.add(1037);
		listDoorSoundEvents.add(1007);
		listDoorSoundEvents.add(1036);
		listDoorSoundEvents.add(1013);
		//doors, opening and close, metal and wood
		listDoorSoundEvents.add(1011);
		listDoorSoundEvents.add(1012);
		listDoorSoundEvents.add(1005);
		listDoorSoundEvents.add(1006);
		//fence gates
		listDoorSoundEvents.add(1008);
		listDoorSoundEvents.add(1014);
		addSoundIntegerEntry(new SoundProfileEntry("debug-doors", noisyInteractBuff).setListSoundEventTypes(listDoorSoundEvents));

		//records
		List<Integer> list = new ArrayList<>();
		list.add(1010);
		addSoundIntegerEntry(new SoundProfileEntry("debug-records", 300).setListSoundEventTypes(list));

		listSoundProfiles.add(new SoundProfileEntry(".place", noisyInteractBuff));
		//listSoundProfiles.add(new SoundProfileEntry("generic.eat", 0.3D));
		listSoundProfiles.add(new SoundProfileEntry("player.burp", 1.1D));

		//covers all note block sounds
		listSoundProfiles.add(new SoundProfileEntry("block.note", noisyInteractBuff).setMaxDistToSpawnFromPlayer(64));

		listSoundProfiles.add(new SoundProfileEntry("lever.click", noisyInteractBuff).setMaxDistToSpawnFromPlayer(noisyInteractRange));
		listSoundProfiles.add(new SoundProfileEntry("pressure_plate", noisyInteractBuff).setMaxDistToSpawnFromPlayer(noisyInteractRange));
		listSoundProfiles.add(new SoundProfileEntry("button.click", noisyInteractBuff).setMaxDistToSpawnFromPlayer(noisyInteractRange));

		listSoundProfiles.add(new SoundProfileEntry("tripwire", noisyInteractBuff).setMaxDistToSpawnFromPlayer(noisyInteractRange));
		listSoundProfiles.add(new SoundProfileEntry("block.barrel", noisyInteractBuff).setMaxDistToSpawnFromPlayer(noisyInteractRange));

		//long dist ones
		if (ZAConfigFeatures.noisyZombies) listSoundProfiles.add(new SoundProfileEntry(SoundEvents.ZOMBIE_AMBIENT, 0.8D, ZAConfigGeneral.noisyZombiesReinforceOddsTo1).setMaxDistToSpawnFromPlayer(18));
		if (ZAConfigFeatures.noisyPistons) listSoundProfiles.add(new SoundProfileEntry(SoundEvents.PISTON_EXTEND, 2D, 5).setMaxDistToSpawnFromPlayer(24));

		//not used traditionally, looked up directly
		listSoundProfiles.add(new SoundProfileEntry(SoundEvents.GENERIC_EXPLODE.value(), 10D).setMaxDistToSpawnFromPlayer(24));
	}

	public static void addSoundIntegerEntry(SoundProfileEntry entry) {
		for (int soundID : entry.getListSoundEventTypes()) {
			lookupSoundIntToProfile.put(soundID, entry);
		}
		//redundant now?
		listSoundProfiles.add(entry);
	}

	public static SoundProfileEntry getSoundIDEntry(int soundType) {
		return lookupSoundIntToProfile.get(soundType);
	}
    
    public static SoundProfileEntry getSoundIDEntry(String sound) {
		if (sound == null) {
			return null;
		}
		SoundProfileEntry cached = lookupSoundNameToProfileCache.get(sound);
		if (cached != null) {
			return cached == NO_MATCH_SENTINEL ? null : cached;
		}
    	for (SoundProfileEntry entry : listSoundProfiles) {

    		if (entry.getSoundName().equals(sound)) {
				lookupSoundNameToProfileCache.put(sound, entry);
    			return entry;
    		} else if (entry.isPartialMatchOnly() && sound.contains(entry.getSoundName())) {
				lookupSoundNameToProfileCache.put(sound, entry);
    			return entry;
    		}
    	}
		lookupSoundNameToProfileCache.put(sound, NO_MATCH_SENTINEL);
    	return null;
    }
	
    public static final Map<UUID, Long> lastPortalLockdownMessageTick = new ConcurrentHashMap<>();

    public static boolean isPortalBlock(BlockState state) {
        if (state == null) return false;
        return state.getBlock() instanceof NetherPortalBlock
            || state.getBlock() instanceof EndPortalBlock
            || state.getBlock() instanceof EndGatewayBlock;
    }

    public static boolean isStandingInOrNearPortal(Player player) {
        if (player == null) return false;
        if (player.portalProcess != null && player.portalProcess.isInsidePortalThisTick()) {
            return true;
        }
        Level level = player.level();
        BlockPos pPos = player.blockPosition();
        for (BlockPos checkPos : BlockPos.betweenClosed(pPos.offset(-1, -1, -1), pPos.offset(1, 1, 1))) {
            if (isPortalBlock(level.getBlockState(checkPos))) {
                return true;
            }
        }
        return false;
    }

    public static boolean checkPortalLockdown(ServerPlayer player) {
        if (player == null || player.isCreative() || player.isSpectator()) {
            return false;
        }
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        if (!isStandingInOrNearPortal(player)) {
            return false;
        }

        boolean isBloodmoon = com.corosus.zombieawareness.bloodmoon.BloodmoonManager.isBloodmoonActive(serverLevel);
        boolean isLocked = com.corosus.zombieawareness.heat.ChunkHeatManager.isPlayerLockedByHordeOrBloodmoon(
            player.getUUID(),
            player.blockPosition(),
            isBloodmoon
        );

        if (!isLocked) {
            return false;
        }

        if (player.portalProcess != null) {
            player.portalProcess = null;
        }
        player.setPortalCooldown(100);

        net.minecraft.world.phys.Vec3 look = player.getLookAngle();
        double pushX = -look.x * 0.5;
        double pushZ = -look.z * 0.5;
        if (Math.abs(pushX) < 0.05 && Math.abs(pushZ) < 0.05) {
            pushZ = -0.3;
        }
        player.setDeltaMovement(pushX, 0.25, pushZ);

        serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.PLAYERS, 1.5F, 0.8F);
        serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.5F, 0.8F);

        long gameTime = serverLevel.getGameTime();
        Long lastTick = lastPortalLockdownMessageTick.get(player.getUUID());
        if (lastTick == null || gameTime - lastTick >= 60L) {
            lastPortalLockdownMessageTick.put(player.getUUID(), gameTime);
            player.sendSystemMessage(Component.literal("§4§l[!] THE HORDE HAS YOUR SCENT — DIMENSIONAL RIFTS ARE SEALED!"));
        }

        return true;
    }

	public static void removePlayerTracking(UUID playerUuid) {
		if (playerUuid == null) return;
		lastHealths.remove(playerUuid);
		lastBleedTimes.remove(playerUuid);
		lookupLastWaypointTime.remove(playerUuid);
		lastPortalLockdownMessageTick.remove(playerUuid);
		necroticTraumaUntilTick.remove(playerUuid);
		lastOneHeartClutchTickMap.remove(playerUuid);
		adrenalineActiveUntilTickMap.remove(playerUuid);
	}

	public static void clearPlayerTracking() {
		lastHealths.clear();
		lastBleedTimes.clear();
		lookupLastWaypointTime.clear();
		lastPortalLockdownMessageTick.clear();
		clearNecroticTrauma();
		resetAdrenalineState();
		resetEliteLootWindowState();
	}

	public static void tickPlayer(Player player) {
		if (player instanceof ServerPlayer serverPlayer) {
			checkPortalLockdown(serverPlayer);
		}

		UUID playerId = player.getUUID();
		if (ZAConfigFeatures.wanderingHordes) {

			Long lastWaypointVal = lookupLastWaypointTime.get(playerId);
			long lastWaypoint = lastWaypointVal != null ? lastWaypointVal : 0L;
			if (lastWaypoint < System.currentTimeMillis()) {
				lastWaypoint = System.currentTimeMillis() + ZAConfigFeatures.frequencyOfWanderingHordesPerPlayer * 1000L;
				lookupLastWaypointTime.put(playerId, lastWaypoint);
				spawnWaypoint(player);
			}
		}

        if (ZAConfigFeatures.awareness_Scent && !player.isCreative()) {
        	Integer lastHealthVal = lastHealths.get(playerId);
        	int lastHealth = lastHealthVal != null ? lastHealthVal : 0;
        	Long lastBleedVal = lastBleedTimes.get(playerId);
    		long lastBleedTime = lastBleedVal != null ? lastBleedVal : 0L;
    		
    		Vector3d pos = new Vector3d(player.getX(), player.getY(), player.getZ());
    		
            if((int)player.getHealth() != lastHealth) {
                if(player.getHealth() < lastHealth) {
                	EntityScent scent = spawnOrBuffSenseAtPos(player.level(), pos, EnumSenseType.SCENT_BLOOD, ZAConfigGeneral.scentStrength);
                	if (scent != null) {
                		ZombieAwareness.dbg("spawned or buffed scent sense from damage: " + scent.getStrengthPeak());
                	}
                }

                lastHealth = (int) player.getHealth();
            }
            
            lastHealths.put(playerId, lastHealth);

            if(player.getHealth() / player.getMaxHealth() < 0.6F && lastBleedTime < System.currentTimeMillis()) {
                lastBleedTime = System.currentTimeMillis() + 30000L;
                lastBleedTimes.put(playerId, lastBleedTime);
                EntityScent scent = spawnOrBuffSenseAtPos(player.level(), pos, EnumSenseType.SCENT_BLOOD, ZAConfigGeneral.scentStrength);
                if (scent != null) {
                	ZombieAwareness.dbg("spawned or buffed scent sense from bleeding: " + scent.getStrengthPeak());
                }
            }
        }
    }

	public static void processMobSpawn(LivingEntity ent) {
		if (ZAConfigGeneral.zombieRandSpeedBoost > 0) {

			if (ent instanceof Zombie) {
				if (!isMobSpeedBosted((Mob) ent)) {
					giveRandomSpeedBoost((Mob) ent);
				}
			}
		}
	}

	public static boolean isMobSpeedBosted(Mob ent) {
		return ZombieAwareness.getPersistentDataSafe(ent).getBooleanOr(SPEED_BOOST_TAG, false);
	}
	
	public static void giveRandomSpeedBoost(Mob ent) {
		if (ent == null || ent.entityTags().contains(TANKER_TAG)) {
			return;
		}
		if (ZAConfigGeneral.zombieRandSpeedBoost > 0) {
			double randBoost = CU.rand().nextDouble() * ZAConfigGeneral.zombieRandSpeedBoost;
			if (ent.isBaby()) {
				randBoost *= -1;
			}
			AttributeModifier speedBoostModifier = new AttributeModifier(CoroUtilAttributes.SPEED_BOOST_ID, randBoost, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
            if (!ent.getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(CoroUtilAttributes.SPEED_BOOST_ID)) {
				ZombieAwareness.dbg("boosting zombie speed to " + randBoost);
                ent.getAttribute(Attributes.MOVEMENT_SPEED).addPermanentModifier(speedBoostModifier);
				ZombieAwareness.getPersistentDataSafe(ent).putBoolean(SPEED_BOOST_TAG, true);
            }
		}
	}
    
    public static void huntTarget(Mob ent, LivingEntity targ, int pri) {
    	CoroUtilPath.tryMoveToEntityLivingLongDist(ent, targ, 1);
		if (ent instanceof Mob) (ent).setTarget(targ);
	}
	
	public static void huntTarget(Mob ent, LivingEntity targ) {
		huntTarget(ent, targ, 0);
	}
    
	public static boolean isEnemy(Entity ent, Entity targ) {
		return isEnemy(ent, targ, false);
	}

	public static boolean isHostileMob(Entity entity) {
		return entity instanceof Enemy || entity instanceof Monster;
	}

	public static final String TANKER_TAG = "za_tanker";
	public static final String BLOODMOON_WARDEN_TAG = "za_bloodmoon_warden";
	public static final String KNIGHT_TAG = "za_knight";
	public static final String KNIGHT_MOUNT_TAG = "za_knight_mount";
	public static final String RAMPAGE_TAG = "za_rampage";
	public static final String LABELED_MOB_TAG = "za_labeled_mob";
	public static final String ENGINEER_TAG = "za_engineer";
	public static final String THROWN_PROJECTILE_TAG = "za_thrown_projectile";
	public static final String THROWN_LANDED_TAG = "za_thrown_landed";

	private static final Set<String> HORDE_MOB_TAGS = Set.of(
			"za_horde_mob",
			"za_tnt_sapper",
			"za_juggernaut",
			"za_crawler",
			"za_apex_corrosive",
			"za_screamer",
			"za_airdrop_passenger",
			ENGINEER_TAG,
			TANKER_TAG,
			BLOODMOON_WARDEN_TAG,
			KNIGHT_TAG,
			KNIGHT_MOUNT_TAG,
			RAMPAGE_TAG
	);

	private static final Set<String> ELITE_HORDE_TAGS = Set.of(
			TANKER_TAG,
			KNIGHT_TAG,
			"za_juggernaut",
			ENGINEER_TAG,
			"za_tnt_sapper",
			"za_screamer",
			BLOODMOON_WARDEN_TAG
	);

	private static boolean hasAnyTag(Collection<String> tags, Set<String> targetSet) {
		if (tags == null || tags.isEmpty()) {
			return false;
		}
		if (tags.size() < targetSet.size()) {
			for (String tag : tags) {
				if (targetSet.contains(tag)) {
					return true;
				}
			}
			return false;
		}
		for (String tag : targetSet) {
			if (tags.contains(tag)) {
				return true;
			}
		}
		return false;
	}

	public static boolean hasHordeMobTag(Collection<String> tags) {
		if (tags == null || tags.isEmpty()) {
			return false;
		}
		if (tags.contains("za_horde_mob")) {
			return true;
		}
		return hasAnyTag(tags, HORDE_MOB_TAGS);
	}

	public static boolean isHordeMob(Entity entity) {
		return entity != null && hasHordeMobTag(entity.entityTags());
	}

	public static boolean hasEliteHordeTag(Collection<String> tags) {
		return hasAnyTag(tags, ELITE_HORDE_TAGS);
	}

	public static boolean isEliteHordeMob(Entity entity) {
		return entity != null && hasEliteHordeTag(entity.entityTags());
	}

	public static boolean isVillagerEntity(Entity entity) {
		return entity instanceof AbstractVillager || entity instanceof Npc;
	}

	/**
	 * Pure policy predicate used to keep horde mobs focused on players instead of infighting or targeting villagers.
	 */
	public static boolean shouldBlockHordeMobTarget(boolean attackerIsHordeMob, boolean targetIsVillager, boolean targetIsHostile) {
		if (targetIsHostile) {
			return true;
		}
		return attackerIsHordeMob && targetIsVillager;
	}

	public static boolean shouldBlockHordeTarget(Entity attacker, Entity target) {
		if (shouldBlockHostileFriendlyFireOrAggro(attacker, target)) {
			return true;
		}
		return isHordeMob(attacker) && isVillagerEntity(target);
	}

	public static boolean shouldBlockWardenFriendlyAction(boolean wardenIsHorde, boolean targetIsHorde, boolean targetIsHostile, boolean targetIsVillager) {
		return wardenIsHorde && (targetIsHorde || targetIsHostile || targetIsVillager);
	}

	public static boolean shouldBlockHostileFriendlyFireOrAggro(Entity sourceOrAttacker, Entity victim) {
		if (sourceOrAttacker == null || victim == null) {
			return false;
		}
		if (isHostileMob(victim) || isHordeMob(victim)) {
			if (isHostileMob(sourceOrAttacker) || isHordeMob(sourceOrAttacker)) {
				return true;
			}
			if (sourceOrAttacker instanceof PrimedTnt tnt && (tnt.entityTags().contains("za_sapper_tnt")
					|| isHostileMob(tnt.getOwner())
					|| isHordeMob(tnt.getOwner()))) {
				return true;
			}
		}
		return false;
	}
	
    public static boolean isEnemy(Entity ent, Entity targ, boolean omniTarget) {
    	if (targ instanceof LivingEntity) {
			if (targ instanceof Player) {
				if (!((Player) targ).isCreative() && ((Player) targ).getEffect(MobEffects.INVISIBILITY) == null) {
					if (!omniTarget) {
						return true;
					} else if (ZAConfigPlayerLists.whiteListUsedOmniscient) {
						if (ZAConfigPlayerLists.whitelistOmniscientTargettedPlayers.contains(CoroUtilEntity.getName(((Player) targ)))) {
							if (ZAConfigGeneral.debugConsoleOmniscient) ZombieAwareness.dbg(CoroUtilEntity.getName((Player) targ) + " targetting omnisciently by " + ent);
							return true;
						}
					} else {
						return true;
					}
				}
			}
			return false;
    	}
    	return false;
	}
    
    public static boolean sanityCheck(Entity ent, Entity entity1) {
		return true;
	}

	public static void markPerformedPathing(Mob ent) {
		//setting to 100+ prevents random pathing from cancelling our path, not entirely though, a mixin does this instead now
		ent.setNoActionTime(0);
		ZombieAwareness.getPersistentDataSafe(ent).putLong(ZA_LAST_ACTION, ent.level().getGameTime());
	}
    
    public static void tickAI(Mob ent) {
		//if (true) return;
		//ZAConfig.debugConsoleSuperDetailed = false;
		if (!ZAUtil.isZombieAwarenessActive(ent.level())) return;

		boolean hordeMob = isHordeMob(ent);
		if (hordeMob && ent.getTarget() != null && isVillagerEntity(ent.getTarget())) {
			ent.setTarget(null);
		}

    	if (ZAConfigGeneral.debugConsoleSuperDetailed) ZombieAwareness.dbg("ZA DBG: Ticking: " + ent);

		long lastActionTime = ZombieAwareness.getLastActionTick(ent);
		if (lastActionTime > 0 && ent.level().getGameTime() - ZAConfigGeneral.tickCooldownBetweenPathfinds < lastActionTime) return;

		//if (ent.getNoActionTime() <= 40) return;
    	
    	//A more performance friendly omniscient, only runs it when no target, but still allows for smaller ranged retargetting
		//adding entity ID onto world time to stagger processing per entity more, should improve TPS
    	if ((ent.level().getGameTime() + ent.getId()) % 40 == 0) {
			if (hordeMob && ent.getTarget() == null) {
				ServerPlayer hordeTarget = ChunkHeatManager.resolveHordeTargetPlayer(ent);
				if (hordeTarget != null) {
					huntTarget(ent, hordeTarget);
					markPerformedPathing(ent);
				} else {
					ai_FindTarget(ent, true);
				}
			} else if (ZAConfigGeneral.omniscient && ent.getTarget() == null) {
	    		ai_FindTarget(ent, true);
	    	} else {
	    		ai_FindTarget(ent, false);
	    	}
    	}
		
		EntityScent senseTracked = null;
		
		if (ent.getTarget() == null && (ent.getNavigation().isDone())) {
			//Find player made senses
			if (!ZAConfigGeneral.awareness_Light_OnlyZombies || (ent instanceof Zombie)) {
				if (!ZAConfigFeatures.awareness_Light || !ai_FindLightSource(ent)) {
					if (CU.rand().nextInt(3) == 0) {
						senseTracked = ai_FindSense(ent, true);
					}
	    			
    			}
    		} else {
    			senseTracked = ai_FindSense(ent);
    		}
    	}
		
		if (senseTracked != null && ent.getNavigation().getPath() != null) {
			Node pathTo = ent.getNavigation().getPath().getEndNode();
			if (pathTo != null) {
				Player player = getClosestPlayer(ent.level(), pathTo.x, pathTo.y, pathTo.z, 6D);
				if (player != null) {
					//tryPlayInvestigateSound(ent, new Vec3d(ent.getX(), ent.getY(), ent.getZ()));
					tryPlayInvestigateSound(ent, new Vector3d(pathTo.x, pathTo.y, pathTo.z));
				}
				
			}
			
		}
	    	
	    tickCustomMob(ent);
    }
    
    public static void tickCustomMob(Mob ent) {
    	if (ZAConfigFeatures.wanderingHordes) {
	    	if (ent instanceof Spider) {
	    		if (ent.getPassengers().size() > 0 && ent.getPassengers().get(0) instanceof Skeleton) {
	    			if (CU.rand().nextInt(100) == 0) {
	    				spawnWaypoint(ent);
	    			}
	    		}
	    	}
    	}
    }
    
    public static boolean ai_FindLightSource(Mob ent) {
    	if (ent == null || ent.level().isBrightOutside()) return false;

		boolean forceOn = false;
    	if (forceOn || CU.rand().nextInt(3) == 0) {
    		Player entP = getClosestPlayerToEntity(ent.level(), ent, 999);
    		if (entP == null || (!forceOn && ent.distanceToSqr(entP) <= 256.0D)) {
    			return false;
    		}

    		Random rand = CU.rand();
    		BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
    		for (int i = 0; i < 4; i++) {
    			int size = 32 * (i + 1);
	    		int rX = Mth.floor(entP.getX() + (rand.nextInt(size) - (size / 2)));
	    		int rY = Mth.floor(entP.getY() + (rand.nextInt(Math.max(1, size / 2)) - (size / 4)));
	    		int rZ = Mth.floor(entP.getZ() + (rand.nextInt(size) - (size / 2)));

	    		mutablePos.set(rX, rY, rZ);
				if (!ent.level().isLoaded(mutablePos)) continue;

	    		float lightValue = entP.level().getBrightness(LightLayer.BLOCK, mutablePos);
	    		if (forceOn || lightValue > 0.2F) {
	    			if (forceOn || CU.rand().nextInt(5) == 0) {
						boolean canSeePos = CoroUtilEntity.canSee(ent, mutablePos);
						if (canSeePos) {
							ZombieAwareness.dbg("try path to light source - " + rX + ", " + rY + ", " + rZ);
							Profiler.get().push("zombieawareness_pathfind");
							boolean pathFound = CoroUtilPath.tryMoveToXYZLongDist(ent, rX, rY, rZ, 1);
							Profiler.get().pop();
							if (pathFound) {
								ZombieAwareness.dbg("node count: " + ent.getNavigation().getPath().getNodeCount());
								ZombieAwareness.dbg("pathing to lightsource at " + rX + ", " + rY + ", " + rZ + " - " + ent);
								markPerformedPathing(ent);
							}
							return true;
						}
    				}
	    		}
    		}
    	}
    	return false;
    }
    
    public static EntityScent ai_FindSense(Mob ent) {
    	return ai_FindSense(ent, true);
    }
    
    public static EntityScent ai_FindSense(Mob ent, boolean includeWaypoints) {
    	
    	EntityScent var3 = getSenseNearEntity(ent);

        if(var3 != null) {
        	if (includeWaypoints || var3.getSenseType() != 2) {
				//if (ent.getNavigation().moveTo(var3, 1)) {
				Profiler.get().push("zombieawareness_pathfind");
				boolean pathFound = CoroUtilPath.tryMoveToEntityLivingLongDist(ent, var3, 1);
				//for (int i = 0; i < 1000; i++) {
				//pathFound = ent.getNavigation().moveTo(var3, 1);
				//}
				Profiler.get().pop();
        		if (pathFound) {
					markPerformedPathing(ent);
        			ZombieAwareness.dbg("ai_FindSense call, type: " + ((EntityScent)var3).getSenseType() + " - " + ent.getName() + " -> " + var3.position());
        			return var3;
        		}
        	}
        }
        
        return null;
    }
    
    public static boolean ai_FindTarget(Mob ent, boolean omniscient) {
    	if (ent == null || ent.level() == null) return false;
    	if (ent.getClass().getSimpleName().equals("EntityZombiePlayer")) {
    		return false;
    	}
    	long huntRange = omniscient ? 512L : ZAConfigGeneral.sightRange;
    	double maxRangeSq = (double) huntRange * (double) huntRange;
    	double halfVertRange = huntRange * 0.5D;

    	if ((ent.getTarget() == null || CU.rand().nextInt(100) == 0)) {
			Player clEnt = null;
			double closestSq = Double.MAX_VALUE;
			for (Player player : ent.level().players()) {
				if (player == null || Math.abs(player.getY() - ent.getY()) > halfVertRange) {
					continue;
				}
				double distSq = ent.distanceToSqr(player);
				if (distSq > maxRangeSq || distSq >= closestSq) {
					continue;
				}
				if (isEnemy(ent, player, omniscient)
						&& (omniscient || ZAConfigGeneral.seeThroughWalls || player.hasLineOfSight(ent))
						&& sanityCheck(ent, player)) {
					closestSq = distSq;
					clEnt = player;
				}
			}
	        if (clEnt != null) {
	        	huntTarget(ent, clEnt);
				markPerformedPathing(ent);
				ZombieAwareness.dbg(" hunting target " + ent + " " + clEnt);
	        	return true;
	        }
		}
    	return false;
    }
    
    /**
     * Gets sense within range provided sense is strong enough, has random chance and doesnt always return closest sense
     * 
     * @param entSource
     * @return
     */
    public static EntityScent getSenseNearEntity(Entity entSource) {
        List<Entity> listEnts = entSource.level().getEntities(entSource, entSource.getBoundingBox().inflate(ZAConfigGeneral.maxPFRangeSense, ZAConfigGeneral.maxPFRangeSense, ZAConfigGeneral.maxPFRangeSense));
        
        EntityScent bestEnt = null;
		float bestRangeAkaStrength = 0;
		double bestDist = 9999;

		float rangeTooClose = 5F;
		int randChance = 10;
		float percentChance = (float) ZAConfigGeneral.findSense_PercentChance / 100;
		//randChance = 0;
		/*if (entityScent.getSenseType() == 2) {
			rangeTooClose = 10F;
			randChance = 2;
		}*/

		if (CU.rand().nextFloat() <= percentChance) {
			for(int i = 0; i < listEnts.size(); ++i) {
				Entity entCheck = listEnts.get(i);

				if (entCheck instanceof EntityScent entityScent) {

					double dist = entSource.distanceTo(entCheck);

					if (entityScent.getRange() > bestRangeAkaStrength && dist < entityScent.getRange() && (entityScent.getSenseType() != 2 || CU.rand().nextInt(2) == 0)) {
						bestEnt = (EntityScent) entCheck;
						bestRangeAkaStrength = entityScent.getRange();
						bestDist = dist;
						//return entBest;
					}
				}
			}

			//after we found strongest sound source, if were too close, still do nothing, prevents then wandering to quieter source if theyre next to loud one
			if (bestDist <= rangeTooClose) {
				return null;
			}
		}

        return bestEnt;
    }

	public static boolean isSafeServerLevelDispatch(boolean isServerLevel, boolean hasServer, boolean isSameThread) {
		return isServerLevel && hasServer && isSameThread;
	}

	public static boolean isServerMainThread(Level world) {
		return world instanceof ServerLevel sl && isSafeServerLevelDispatch(true, sl.getServer() != null, sl.getServer() != null && sl.getServer().isSameThread());
	}

	public static void hookPlayEvent(int type, Level world, double pX, double pY, double pZ, int data) {

		//TODO: TEMP DEBUG!!!
		//listSoundProfiles.clear();

		initSoundHookDataIfEmpty();

		//if event type is for playing a record
		if (world == null || world.isClientSide() || !isServerMainThread(world) || !canSpawnTraceQuickCheck(world)) return;

		SoundProfileEntry entry = getSoundIDEntry(type);
		if (entry != null) {
			Player closestPlayer = getClosestPlayer(world, pX, pY, pZ, entry.getMaxDistToSpawnFromPlayer());
			if (closestPlayer != null) {

				Vector3d pos = new Vector3d(pX, pY, pZ);
				if (!canSpawnTrace(world, pX, pY, pZ)) {
					return;
				}

				handleSoundProfileEvent(world, entry, pos, closestPlayer);
			}
		}
	}

	public static void hookSoundEventClient(SoundEvent sound, Level world, double x, double y, double z, float volume, float pitch) {
		// Debug client sound logging disabled for production performance
	}

    public static void hookSoundEvent(SoundEvent sound, Level world, double x, double y, double z, float volume, float pitch) {

		//TODO: TEMP DEBUG!!!
		//listSoundProfiles.clear();

		initSoundHookDataIfEmpty();
    	
    	if (world == null || world.isClientSide() || sound == null || !isServerMainThread(world) || !canSpawnTraceQuickCheck(world)) return;

		Identifier soundId = sound.location();
		if (world instanceof ServerLevel serverLevel && soundId != null) {
			com.corosus.zombieawareness.heat.ChunkHeatManager.onSoundPlayed(serverLevel, x, y, z, soundId.getNamespace(), soundId.getPath());
		}

		String soundName = SoundProfileEntry.getSoundEventName(sound);
		SoundProfileEntry entry = getSoundIDEntry(soundName);

		if (entry != null) {
			Player closestPlayer = getClosestPlayer(world, x, y, z, entry.getMaxDistToSpawnFromPlayer());
			if (closestPlayer != null) {
				Vector3d pos = new Vector3d(x, y, z);
				if (!canSpawnTrace(world, x, y, z)) {
					//spawn 1 higher up, added for tripwire detection, cant spawn sense on tripwire
					y += 1;
					if (!canSpawnTrace(world, x, y, z)) {
						return;
					}
				}
				handleSoundProfileEvent(world, entry, pos, closestPlayer);
			}
		}
    }

	public static void handleSoundProfileEvent(Level world, SoundProfileEntry entry, Vector3d pos, Player closestPlayer) {
		double distToPlayer = Math.sqrt(closestPlayer.distanceToSqr(pos.x, pos.y, pos.z));
		double strength = ZAConfigGeneral.soundStrength;
		if (distToPlayer <= entry.getMaxDistToSpawnFromPlayer()) {
			if (entry.getOddsTo1ToUse() <= 0 || rand.nextInt(entry.getOddsTo1ToUse()) == 0) {
				strength *= entry.getMultiplier();

				EntityScent scent = spawnOrBuffSenseAtPos(world, pos, EnumSenseType.SOUND, (int)strength);
				if (scent != null) {
					ZombieAwareness.dbg("spawned or buffed sound sense from soundEvent, sound: " + entry.getSoundName() + ", str: " + scent.getStrengthPeak()/* + ", vol: " + volume*/);
				}
			}
		}
	}
    
    public static void hookBlockEvent(Player player, int chance) {
		if (player != null && !canSpawnTraceQuickCheck(player.level())) return;
    	
    	if (player == null || (ZAConfigPlayerLists.whiteListUsedSenses && !ZAConfigPlayerLists.whitelistSenses.contains(CoroUtilEntity.getName(player)))) return;
    	
		if (!player.level().isClientSide() && isServerMainThread(player.level()) && CU.rand().nextInt(chance) == 0) {

			int strength = ZAConfigGeneral.soundStrength;
			Vector3d pos = new Vector3d(player.getX(), player.getY(), player.getZ());

			EntityScent scent = spawnOrBuffSenseAtPos(player.level(), pos, EnumSenseType.SOUND, strength);
			if (scent != null) {
				ZombieAwareness.dbg("spawned or buffed sound sense from PlayerEvent: " + scent.getStrengthPeak());
			}
		}
    }

	/**
	 * Player can be null
	 *
	 * @param player
	 * @param chance
	 */
	public static void handleBlockBasedEvent(Player player, Level world, BlockPos pos, int chance) {
		if (player == null && ZAConfigGeneral.blockBreakEvent_PlayersOnly) {
			return;
		}

		if (!canSpawnTraceQuickCheck(world)) return;

		if (player != null && ZAConfigPlayerLists.whiteListUsedSenses) {
			if (ZAConfigPlayerLists.whitelistSenses.contains(CoroUtilEntity.getName(player))) return;
		}

		if (!world.isClientSide() && isServerMainThread(world) && CU.rand().nextInt(chance) == 0) {

			int strength = ZAConfigGeneral.soundStrength;
			Vector3d posVec = new Vector3d(pos.getX(), pos.getY(), pos.getZ());

			EntityScent scent = spawnOrBuffSenseAtPos(world, posVec, EnumSenseType.SOUND, strength);
			if (scent != null) {
				ZombieAwareness.dbg("spawned or buffed sound sense from BlockBasedEvent: " + scent.getStrengthPeak());
			}
		}
	}

    public static void hookSetAttackTarget(LivingEntity entity, LivingEntity target) {
    	//ZombieAwareness.dbg(event.getEntityLiving().getEntityId() + " targetting " + event.getTarget());
    	
    	if (entity instanceof Mob) {
    		if (target instanceof Player) {
	    		//tryPlayAlertSound((EntityLiving)event.getEntityLiving(), new Vec3d(event.getTarget().getX(), event.getTarget().getY(), event.getTarget().getZ()));
	    		tryPlayTargetSound((Mob)entity, target, new Vector3d(entity.getX(), entity.getY(), entity.getZ()));
    		} else if (target == null) {
    			//dont use, AI stupidly detargets when resetting tasks despite still chasing player, causing double alert noise if this code is used
    			/*if (lookupLastAlertTime.containsKey(event.getEntityLiving())) {
    				lookupLastAlertTime.remove(event.getEntityLiving());
        			System.out.println("detarget");
    			}*/
    		}
    	}
    	
    }
    
    public static void spawnWaypoint(Entity entSource) {
        if (entSource == null || entSource.level() == null || !isServerMainThread(entSource.level())) {
            return;
        }
        int range = 128;
        
        double tryX = (int)entSource.getX() - (range/2) + (rand.nextInt(range));
        double tryZ = (int)entSource.getZ() - (range/2) + (rand.nextInt(range));
        if (!entSource.level().hasChunk(Mth.floor(tryX) >> 4, Mth.floor(tryZ) >> 4)) {
            return;
        }
        double tryY = entSource.level().getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(tryX), Mth.floor(tryZ));

        if (!canSpawnTrace(entSource.level(), tryX, tryY, tryZ)) {
            return;
        }

        double height = entSource.getY();
        
        EntityScent var1 = getSenseNodeAtPos(entSource.level(), new Vector3d(tryX, tryY, tryZ), EnumSenseType.WAYPOINT);
        
        boolean newNode = false;
    	
    	if (var1 == null) {
    		var1 = new EntityScent(ZombieAwareness.SENSE, entSource.level());
    		newNode = true;
    	}

        var1.setStrengthPeak(150);
        
        if (newNode) {
	        var1.setPos(tryX, tryY, tryZ);
	        var1.setSenseType(2);
	        
	        entSource.level().addFreshEntity(var1);
        }

		//System.out.println("spawning new waypoint for ");
        if (debug) System.out.println("WP: " + entSource + " - range: " + var1.getRange());
    }

	public static boolean canSpawnTraceQuickCheck(Level world) {
		if (!ZAConfigFeatures.awareness_Sound) {
			return false;
		}
		if (ZAConfigFeatures.awareness_Sound_OverworldOnly) {
			if (world.dimension() != Level.OVERWORLD) return false;
		}
		return true;
	}

    public static boolean canSpawnTrace(Level world, double x, double y, double z) {
    	BlockPos pos = CoroUtilBlock.blockPos(x,y,z);
    	if (!world.isLoaded(pos)) return false;
    	BlockState state = world.getBlockState(pos);
    	//iirc circuits check was to prevent senses spawning on pressure plates and triggering them, but there should be better ways to stop that...
		//might be redundant since AABB and canTriggerWalking and canBeCollidedWith fix
        if (state.getPistonPushReaction() == PushReaction.POPPED && (!(state.getBlock() instanceof ButtonBlock) && !(state.getBlock() instanceof LeverBlock))) {
            return false;
        }
        return true;
    }
    
    public static Player getClosestPlayerToEntity(Level world, Entity par1Entity, double par2)
    {
        return getClosestPlayer(world, par1Entity.getX(), par1Entity.getY(), par1Entity.getZ(), par2);
    }

    /**
     * Gets the closest player to the point within the specified distance (distance can be set to less than 0 to not
     * limit the distance). Args: x, y, z, dist
     */
    public static Player getClosestPlayer(Level world, double x, double y, double z, double maxDistance)
    {
        double closestDist = -1.0D;
        Player closestPlayer = null;

        for (int i = 0; i < world.players().size(); ++i)
        {
            Player entityplayer1 = world.players().get(i);
            if (!ZAConfigPlayerLists.whiteListUsedSenses || ZAConfigPlayerLists.whitelistSenses.contains(CoroUtilEntity.getName(entityplayer1))) {
            	double d5 = entityplayer1.distanceToSqr(x, y, z);

                if ((maxDistance < 0.0D || d5 < maxDistance * maxDistance) && (closestDist == -1.0D || d5 < closestDist))
                {
                    closestDist = d5;
                    closestPlayer = entityplayer1;
                }
            }
        }

        return closestPlayer;
    }

    /**
     * Checks if a scent of the same type is already at this location
     * 
     * @param parWorld
     * @param parPos
     * @param type
     * @return
     */
    public static EntityScent getSenseNodeAtPos(Level parWorld, Vector3d parPos, EnumSenseType type) {
    	
    	if (ZAConfigGeneral.extraScentCutoffRange == -1) return null;
    	
    	AABB aabb = new AABB(parPos.x, parPos.y, parPos.z, parPos.x + 1, parPos.y + 1, parPos.z + 1);
    	aabb = aabb.inflate(ZAConfigGeneral.extraScentCutoffRange, ZAConfigGeneral.extraScentCutoffRange, ZAConfigGeneral.extraScentCutoffRange);
    	
    	List list = parWorld.getEntitiesOfClass(EntityScent.class, aabb);
    	
    	if (list.size() > 0) {
    		for(int j = 0; j < list.size(); j++)
            {
    			EntityScent node = (EntityScent)list.get(j);
    			if (node.getSenseType() == type.ordinal()) {
    				return node;
    			}
            }
    	}
    	
    	return null;
    }
    
    public static EntityScent spawnOrBuffSenseAtPos(Level world, Vector3d parPos, EnumSenseType type, int strength) {
    	return spawnOrBuffSenseAtPos(world, parPos, type, strength, true);
    }
    
    /**
     * Tries to spawn a new sense, if one is close enough, it will multiply that senses current strength by lastMultiply
     * 
     * @param world
     * @param parPos
     * @param type
     * @param strength
     * @return
     */
    public static EntityScent spawnOrBuffSenseAtPos(Level world, Vector3d parPos, EnumSenseType type, int strength, boolean frequentSoundMultiply) {
		if (world == null || parPos == null || !isServerMainThread(world)) {
			return null;
		}
		if (!world.isLoaded(CoroUtilBlock.blockPos(parPos.x, parPos.y, parPos.z))) {
			return null;
		}
		
    	EntityScent sense = getSenseNodeAtPos(world, parPos, type);
    	
    	if (sense == null) {
    		sense = new EntityScent(ZombieAwareness.SENSE, world);
    		sense.setSenseType(type.ordinal());
	        sense.setPos(parPos.x, parPos.y, parPos.z);
    		sense.setStrengthPeak(strength);
	        world.addFreshEntity(sense);
    	} else if (frequentSoundMultiply) {
    		//instead of amplifying current strength, amp the base value, but only if current strength is weaker than param
    		float str = sense.getStrengthPeak();
    		if (str < strength) {
    			str = strength;
    		}
    		
	        if(sense.lastBuffTime + (long) ZAConfigGeneral.frequentSoundThreshold > System.currentTimeMillis()) {
	        	sense.lastMultiply += 0.1F;
	        	str *= sense.lastMultiply;
	        } else {
	        	sense.lastMultiply = 1.0F;
	        }
	        
	        sense.lastBuffTime = System.currentTimeMillis();
	        sense.setStrengthPeak((int)str);
    	}
    	
        return sense;
    }
    
    public static void tryPlayTargetSound(Mob entAlerted, LivingEntity entTargetted, Vector3d pos) {

		if (!ZAConfigFeatures.soundAlerts) return;

		if (!ZAConfigFeatures.soundAlertsForAllAttackingMobs && !ZombieAwareness.canProcessEntity(entAlerted)) return;

		//prevent target spam sound from omniscient zombies from HW-inv
		/*if (entAlerted.getEntityData().hasKey(UtilEntityBuffs.dataEntityBuffed_AI_Omniscience)) {
			return;
		}*/

		//added max dist and blocks loaded check due to https://github.com/Corosauce/ZombieAwareness/issues/11
		double distMaxCancel = 75;
    	if (!lookupLastAlertTime.containsKey(entAlerted) || lookupLastAlertTime.get(entAlerted) + alertDelay < entAlerted.level().getGameTime()) {
			if (entAlerted.distanceTo(entTargetted) < distMaxCancel
                    && entAlerted.level().dimension() == entTargetted.level().dimension()
					&& entAlerted.level().isLoaded(entAlerted.blockPosition())
					&& entTargetted.level().isLoaded(entTargetted.blockPosition())) {
				if (entAlerted.hasLineOfSight(entTargetted)) {
					//entAlerted.level().playSound(null, pos.x, pos.y, pos.z, SoundRegistry.get("target"), SoundCategory.HOSTILE, 3F, 0.8F + (entAlerted.level().random.nextFloat() * 0.2F));
					entAlerted.level().playSound(null, entTargetted.getX(), entTargetted.getY(), entTargetted.getZ(), ZAConfigFeatures.soundUseAlternateAlertNoise ? SoundRegistry.get("alert") : SoundRegistry.get("target"), SoundSource.HOSTILE, (float) ZAConfigFeatures.soundVolumeAlertTarget, ZAConfigFeatures.soundUseAlternateAlertNoise ? 1F : 0.8F + (CU.rand().nextFloat() * 0.2F));
					lookupLastAlertTime.put(entAlerted, entAlerted.level().getGameTime());
					//ZombieAwareness.dbg("!!! alert play for ent: " + entAlerted.getEntityId() + ", lookupSize: " + lookupLastAlertTime.size());
				} else {
					//likely due to new call for help routine in vanilla, so treat it like investigating until line of sight is made
					tryPlayInvestigateSound(entAlerted, pos);
					//ZombieAwareness.dbg("??? tried play alert for no LOS entity: " + entAlerted.getEntityId() + ", lookupSize: " + lookupLastAlertTime.size());
				}
			}
		} else {
			//ZombieAwareness.dbg("already played alert for ent: " + entAlerted.getEntityId() + ", lookupSize: " + lookupLastAlertTime.size());
		}
    }
    
    public static void tryPlayInvestigateSound(Mob entAlerted, Vector3d pos) {

		if (!ZAConfigFeatures.soundInvestigates) return;

		if (!ZombieAwareness.canProcessEntity(entAlerted)) return;

    	if (!lookupLastInvestigateTime.containsKey(entAlerted) || lookupLastInvestigateTime.get(entAlerted) + investigateDelay < entAlerted.level().getGameTime()) {
			entAlerted.level().playSound(null, pos.x, pos.y, pos.z, SoundRegistry.get("investigate"), SoundSource.HOSTILE, (float)ZAConfigFeatures.soundVolumeInvestigate, 0.7F + (CU.rand().nextFloat() * 0.3F));
			lookupLastInvestigateTime.put(entAlerted, entAlerted.level().getGameTime());
			ZombieAwareness.dbg("!!! investigate play for ent: " + entAlerted.getId() + ", lookupSize: " + lookupLastInvestigateTime.size());
		}
    }

	public static boolean isZombieAwarenessActive(Level world) {
		if (world == null) return false;
		if (ZAConfigGeneral.daysBeforeFeaturesActivate <= 0) return true;
		double day = ((double)world.getDefaultClockTime() / CoroUtilWorldTime.getDayLength());
		if (day >= ZAConfigGeneral.daysBeforeFeaturesActivate) {
			return true;
		} else {
			return false;
		}
	}

    public static float[] splitApexArmorBypassDamage(float totalDamage) {
        return new float[]{ totalDamage * (1.0f - APEX_ARMOR_BYPASS_RATIO), totalDamage * APEX_ARMOR_BYPASS_RATIO };
    }

    public static void applyNecroticTrauma(UUID playerUuid, long currentTick) {
        if (playerUuid != null) {
            necroticTraumaUntilTick.put(playerUuid, currentTick + NECROTIC_TRAUMA_DURATION_TICKS);
        }
    }

    public static boolean hasNecroticTrauma(UUID playerUuid, long currentTick) {
        return playerUuid != null && necroticTraumaUntilTick.getOrDefault(playerUuid, -1L) > currentTick;
    }

    public static void clearNecroticTrauma() {
        necroticTraumaUntilTick.clear();
    }

    public static float computeModifiedHealAmount(UUID playerUuid, long currentTick, float baseHealAmount) {
        if (baseHealAmount <= 0f) {
            return baseHealAmount;
        }
        if (hasNecroticTrauma(playerUuid, currentTick)) {
            return baseHealAmount * NECROTIC_REGEN_EFFICIENCY;
        }
        return baseHealAmount;
    }

    public static int computeArmorDurabilityShred(int maxDurability) {
        return Math.max(12, (int) Math.ceil(maxDurability * 0.05));
    }

    public static int shredPlayerArmorDurability(Player player, ServerLevel level) {
        if (player == null) return 0;
        EquipmentSlot[] armorSlots = new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
        };
        int totalShredded = 0;
        for (EquipmentSlot slot : armorSlots) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack != null && !stack.isEmpty() && stack.isDamageableItem()) {
                int shred = computeArmorDurabilityShred(stack.getMaxDamage());
                stack.setDamageValue(Math.min(stack.getMaxDamage() - 1, stack.getDamageValue() + shred));
                totalShredded += shred;
            }
        }
        return totalShredded;
    }

    public static void resetDistressScentThrottle() {
        lastDistressScentTick = -9999L;
    }

    public static boolean canSpawnMonsterDistressScent(int worldStageNum) {
        return worldStageNum >= 5;
    }

    public static int computeMonsterDistressScentStrength(int worldStageNum) {
        return worldStageNum < 5 ? 0 : Math.min(140, 80 + (worldStageNum - 5) * 12);
    }

    public static EntityScent trySpawnMonsterDistressScent(ServerLevel level, LivingEntity deadMob, int worldStageNum) {
        if (level == null || deadMob == null || !isHostileMob(deadMob) || !canSpawnMonsterDistressScent(worldStageNum)) {
            return null;
        }
        long gameTime = level.getGameTime();
        if (gameTime >= lastDistressScentTick && gameTime - lastDistressScentTick < 40L) {
            return null;
        }
        lastDistressScentTick = gameTime;
        int strength = computeMonsterDistressScentStrength(worldStageNum);
        Vector3d pos = new Vector3d(deadMob.getX(), deadMob.getY(), deadMob.getZ());
        EntityScent scent = spawnOrBuffSenseAtPos(level, pos, EnumSenseType.SCENT_BLOOD, strength, true);
        spawnOrBuffSenseAtPos(level, pos, EnumSenseType.SOUND, strength, true);
        return scent;
    }

    public static double computeLootDropRate(boolean isHorde, boolean isElite, boolean isDamageClone) {
        if (isDamageClone) {
            return DAMAGE_CLONE_LOOT_MULTIPLIER;
        }
        if (isElite) {
            return 1.0D;
        }
        return isHorde ? HORDE_MOB_LOOT_DROP_CHANCE : 1.0D;
    }

    public static boolean shouldHordeMobDropLoot(boolean isHordeMob, float randomZeroToOne) {
        return shouldHordeMobDropLoot(isHordeMob, false, false, randomZeroToOne);
    }

    public static boolean shouldHordeMobDropLoot(boolean isHordeMob, boolean isEliteHordeMob, float randomZeroToOne) {
        return shouldHordeMobDropLoot(isHordeMob, isEliteHordeMob, false, randomZeroToOne);
    }

    public static boolean shouldHordeMobDropLoot(
            boolean isHordeMob,
            boolean isEliteHordeMob,
            boolean isDamageClone,
            float randomZeroToOne) {
        double rate = computeLootDropRate(isHordeMob, isEliteHordeMob, isDamageClone);
        if (rate >= 1.0D) {
            return true;
        }
        return randomZeroToOne < rate;
    }

    public static void resetAdrenalineState() {
        lastOneHeartClutchTickMap.clear();
        adrenalineActiveUntilTickMap.clear();
    }

    public static boolean isClutchBypassDamage(DamageSource source) {
        if (source == null) {
            return false;
        }
        return source.is(DamageTypes.GENERIC_KILL)
                || source.is(DamageTypes.FELL_OUT_OF_WORLD)
                || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }

    /**
     * Pure eligibility check for the 1-Heart Clutch protagonist save (<= 1 heart remaining, 5-min cooldown, 25% chance).
     */
    public static boolean canTriggerOneHeartClutch(
            boolean isAlive,
            boolean isCreativeOrSpectator,
            boolean bypassesClutch,
            float currentHealth,
            float damageAmount,
            long lastClutchTick,
            long gameTime,
            double randomZeroToOne) {
        if (!isAlive || isCreativeOrSpectator || bypassesClutch || damageAmount <= 0.0F) {
            return false;
        }
        if ((currentHealth - damageAmount) > ONE_HEART_CLUTCH_HEALTH) {
            return false;
        }
        if (lastClutchTick >= 0L && gameTime >= lastClutchTick && (gameTime - lastClutchTick) < ONE_HEART_CLUTCH_COOLDOWN_TICKS) {
            return false;
        }
        return randomZeroToOne < ONE_HEART_CLUTCH_CHANCE;
    }

    public static boolean hasActiveAdrenaline(UUID playerUuid, long gameTime) {
        return playerUuid != null && adrenalineActiveUntilTickMap.getOrDefault(playerUuid, Long.MIN_VALUE) > gameTime;
    }

    public static long getLastClutchTick(UUID playerUuid) {
        return playerUuid != null ? lastOneHeartClutchTickMap.getOrDefault(playerUuid, Long.MIN_VALUE) : Long.MIN_VALUE;
    }

    public static void recordClutchAndAdrenaline(UUID playerUuid, long gameTime) {
        if (playerUuid == null) {
            return;
        }
        if (lastOneHeartClutchTickMap.size() > 4096) {
            lastOneHeartClutchTickMap.entrySet().removeIf(e -> (gameTime - e.getValue()) > ONE_HEART_CLUTCH_COOLDOWN_TICKS);
        }
        if (adrenalineActiveUntilTickMap.size() > 4096) {
            adrenalineActiveUntilTickMap.entrySet().removeIf(e -> e.getValue() <= gameTime);
        }
        lastOneHeartClutchTickMap.put(playerUuid, gameTime);
        adrenalineActiveUntilTickMap.put(playerUuid, gameTime + ADRENALINE_SURGE_DURATION_TICKS);
    }

    public static float computeAdrenalineKillHeal(boolean isHostileOrHordeMob, boolean killerHasActiveAdrenaline) {
        return (isHostileOrHordeMob && killerHasActiveAdrenaline) ? ADRENALINE_LIFE_ON_KILL_HEAL : 0.0F;
    }

    /**
     * Triggers the 1-Heart Clutch epilogue and 12s Adrenaline Surge for a ServerPlayer about to drop to <= 1 heart.
     */
    public static boolean tryTriggerOneHeartClutch(ServerPlayer player, float damageAmount, DamageSource source, long gameTime) {
        if (player == null || !(player.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        if (player.isInvulnerableTo(serverLevel, source)) {
            return false;
        }
        long lastClutchTick = getLastClutchTick(player.getUUID());
        boolean triggered = canTriggerOneHeartClutch(
                player.isAlive() && !player.isDeadOrDying(),
                player.isCreative() || player.isSpectator(),
                isClutchBypassDamage(source),
                player.getHealth(),
                damageAmount,
                lastClutchTick,
                gameTime,
                ThreadLocalRandom.current().nextDouble()
        );
        if (!triggered) {
            return false;
        }

        recordClutchAndAdrenaline(player.getUUID(), gameTime);
        player.setHealth(ONE_HEART_CLUTCH_HEALTH);

        player.addEffect(new MobEffectInstance(MobEffects.SPEED, ADRENALINE_SURGE_DURATION_TICKS, 1));
        player.addEffect(new MobEffectInstance(MobEffects.HASTE, ADRENALINE_SURGE_DURATION_TICKS, 1));
        player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, ADRENALINE_SURGE_DURATION_TICKS, 0));

        serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.5F, 0.9F);
        serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.9F, 1.15F);

        String line = CLUTCH_EPILOGUE_LINES.get(ThreadLocalRandom.current().nextInt(CLUTCH_EPILOGUE_LINES.size()));
        Component epilogue = Component.literal("§c§o" + line);
        player.sendSystemMessage(epilogue, true);
        if (player.connection != null) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 50, 15));
            player.connection.send(new ClientboundSetTitleTextPacket(Component.empty()));
            player.connection.send(new ClientboundSetSubtitleTextPacket(epilogue));
        }
        return true;
    }

    public static float tryApplyAdrenalineLifeOnKill(ServerLevel level, LivingEntity deadEntity, DamageSource source) {
        if (level == null || deadEntity == null || source == null) {
            return 0.0F;
        }
        if (!(source.getEntity() instanceof ServerPlayer killer) || !killer.isAlive()) {
            return 0.0F;
        }
        boolean targetValid = isHostileMob(deadEntity) || isHordeMob(deadEntity);
        float heal = computeAdrenalineKillHeal(targetValid, hasActiveAdrenaline(killer.getUUID(), level.getGameTime()));
        if (heal <= 0.0F) {
            return 0.0F;
        }
        killer.heal(heal);
        level.playSound(null, killer.getX(), killer.getY(), killer.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.7F, 1.35F);
        level.playSound(null, killer.getX(), killer.getY(), killer.getZ(),
                SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.45F, 1.25F);
        return heal;
    }

    public record EliteDropSpec(String itemId, int minCount, int maxCount, double chance) {}

    public record EliteDropRoll(String itemId, int count) {}

    public enum EliteLootTier {
        SPECIALIST,
        HEAVY,
        JUGGERNAUT,
        WARDEN
    }

    private static final List<EliteDropSpec> SPECIALIST_ELITE_DROPS = List.of(
            new EliteDropSpec("minecraft:iron_ingot", 2, 4, 1.0D),
            new EliteDropSpec("minecraft:copper_ingot", 2, 4, 1.0D),
            new EliteDropSpec("minecraft:gunpowder", 2, 4, 1.0D),
            new EliteDropSpec("minecraft:coal", 2, 5, 1.0D),
            new EliteDropSpec("minecraft:redstone", 2, 4, 1.0D)
    );

    private static final List<EliteDropSpec> HEAVY_ELITE_DROPS = List.of(
            new EliteDropSpec("minecraft:iron_ingot", 4, 7, 1.0D),
            new EliteDropSpec("minecraft:copper_ingot", 3, 6, 1.0D),
            new EliteDropSpec("minecraft:gold_ingot", 2, 4, 1.0D),
            new EliteDropSpec("minecraft:gunpowder", 2, 5, 1.0D),
            new EliteDropSpec("minecraft:emerald", 1, 2, 0.60D),
            new EliteDropSpec("minecraft:diamond", 1, 1, 0.12D)
    );

    private static final List<EliteDropSpec> JUGGERNAUT_ELITE_DROPS = List.of(
            new EliteDropSpec("minecraft:iron_ingot", 6, 10, 1.0D),
            new EliteDropSpec("minecraft:gold_ingot", 3, 5, 1.0D),
            new EliteDropSpec("minecraft:redstone", 4, 8, 1.0D),
            new EliteDropSpec("minecraft:emerald", 1, 3, 0.75D),
            new EliteDropSpec("minecraft:diamond", 1, 1, 0.25D)
    );

    private static final List<EliteDropSpec> WARDEN_ELITE_DROPS = List.of(
            new EliteDropSpec("minecraft:netherite_scrap", 1, 2, 1.0D),
            new EliteDropSpec("minecraft:diamond", 2, 4, 1.0D),
            new EliteDropSpec("minecraft:echo_shard", 2, 4, 1.0D),
            new EliteDropSpec("minecraft:emerald", 4, 6, 1.0D),
            new EliteDropSpec("minecraft:gunpowder", 6, 10, 1.0D)
    );

    private static final List<String> DIAMOND_CAP_FALLBACK_ITEMS = List.of(
            "minecraft:gold_ingot",
            "minecraft:iron_ingot",
            "minecraft:emerald"
    );

    public static void resetEliteLootWindowState() {
        synchronized (ELITE_DIAMOND_LOCK) {
            recentBonusDiamondTicks.clear();
        }
    }

    public static int getBonusDiamondsInCurrentWindow(long gameTime) {
        synchronized (ELITE_DIAMOND_LOCK) {
            pruneExpiredBonusDiamondTicks(gameTime);
            return recentBonusDiamondTicks.size();
        }
    }

    private static void pruneExpiredBonusDiamondTicks(long gameTime) {
        while (!recentBonusDiamondTicks.isEmpty()) {
            long oldest = recentBonusDiamondTicks.peekFirst();
            if (gameTime < oldest || (gameTime - oldest) >= ELITE_DIAMOND_WINDOW_TICKS) {
                recentBonusDiamondTicks.pollFirst();
            } else {
                break;
            }
        }
    }

    private static boolean tryConsumeBonusDiamondSlot(long gameTime) {
        int maxCap = Math.max(0, ZAConfigZDay.maxBonusDiamondsPerHordeWindow);
        if (maxCap <= 0) {
            return false;
        }
        synchronized (ELITE_DIAMOND_LOCK) {
            pruneExpiredBonusDiamondTicks(gameTime);
            if (recentBonusDiamondTicks.size() >= maxCap) {
                return false;
            }
            recentBonusDiamondTicks.addLast(gameTime);
            return true;
        }
    }

    public static boolean isEligibleForEliteMineralLoot(boolean isEliteHordeMob, boolean isDamageClone, double randomZeroToOne) {
        if (!isEliteHordeMob) {
            return false;
        }
        return !isDamageClone || randomZeroToOne < DAMAGE_CLONE_LOOT_MULTIPLIER;
    }

    public static EliteLootTier classifyEliteTier(Collection<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        if (tags.contains(BLOODMOON_WARDEN_TAG)) {
            return EliteLootTier.WARDEN;
        }
        if (tags.contains("za_juggernaut")) {
            return EliteLootTier.JUGGERNAUT;
        }
        if (tags.contains(TANKER_TAG) || tags.contains(KNIGHT_TAG)) {
            return EliteLootTier.HEAVY;
        }
        if (tags.contains("za_screamer") || tags.contains("za_tnt_sapper") || tags.contains(ENGINEER_TAG)) {
            return EliteLootTier.SPECIALIST;
        }
        return null;
    }

    public static List<EliteDropSpec> getEliteMineralDropTable(Collection<String> tags) {
        EliteLootTier tier = classifyEliteTier(tags);
        if (tier == null) {
            return List.of();
        }
        return switch (tier) {
            case SPECIALIST -> SPECIALIST_ELITE_DROPS;
            case HEAVY -> HEAVY_ELITE_DROPS;
            case JUGGERNAUT -> JUGGERNAUT_ELITE_DROPS;
            case WARDEN -> WARDEN_ELITE_DROPS;
        };
    }

    private static int rollCountInRange(EliteDropSpec spec, java.util.Random rng) {
        int span = Math.max(0, spec.maxCount() - spec.minCount());
        return spec.minCount() + (span > 0 ? rng.nextInt(span + 1) : 0);
    }

    public static List<EliteDropRoll> rollEliteMineralDrops(Collection<String> tags, java.util.Random random) {
        return rollEliteMineralDrops(tags, random, 0L, false);
    }

    public static List<EliteDropRoll> rollEliteMineralDrops(Collection<String> tags, java.util.Random random, long gameTime) {
        return rollEliteMineralDrops(tags, random, gameTime, true);
    }

    public static List<EliteDropRoll> rollEliteMineralDrops(
            Collection<String> tags,
            java.util.Random random,
            long gameTime,
            boolean enforceDiamondWindowCap) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        EliteLootTier tier = classifyEliteTier(tags);
        if (tier == null) {
            return List.of();
        }
        java.util.Random rng = random != null ? random : ThreadLocalRandom.current();
        if (!isEligibleForEliteMineralLoot(true, tags.contains(DAMAGE_CLONE_TAG), rng.nextDouble())) {
            return List.of();
        }
        List<EliteDropSpec> table = getEliteMineralDropTable(tags);
        List<EliteDropRoll> rolls = new ArrayList<>(table.size());
        for (EliteDropSpec spec : table) {
            if (spec.chance() >= 1.0D || rng.nextDouble() < spec.chance()) {
                String itemId = spec.itemId();
                int count = rollCountInRange(spec, rng);
                if (tier != EliteLootTier.WARDEN
                        && "minecraft:diamond".equals(itemId)
                        && enforceDiamondWindowCap
                        && !tryConsumeBonusDiamondSlot(gameTime)) {
                    itemId = DIAMOND_CAP_FALLBACK_ITEMS.get(rng.nextInt(DIAMOND_CAP_FALLBACK_ITEMS.size()));
                    count = 1 + rng.nextInt(2);
                }
                if (count > 0) {
                    rolls.add(new EliteDropRoll(itemId, count));
                }
            }
        }
        return rolls;
    }

    private static Item resolveEliteDropItem(String itemId) {
        return switch (itemId) {
            case "minecraft:iron_ingot" -> Items.IRON_INGOT;
            case "minecraft:gunpowder" -> Items.GUNPOWDER;
            case "minecraft:coal" -> Items.COAL;
            case "minecraft:redstone" -> Items.REDSTONE;
            case "minecraft:copper_ingot" -> Items.COPPER_INGOT;
            case "minecraft:gold_ingot" -> Items.GOLD_INGOT;
            case "minecraft:emerald" -> Items.EMERALD;
            case "minecraft:diamond" -> Items.DIAMOND;
            case "minecraft:netherite_scrap" -> Items.NETHERITE_SCRAP;
            case "minecraft:echo_shard" -> Items.ECHO_SHARD;
            default -> null;
        };
    }

    public static int dropEliteMineralLoot(ServerLevel level, LivingEntity entity, DamageSource source) {
        if (level == null || entity == null || !isEliteHordeMob(entity)) {
            return 0;
        }
        if (level.getServer() == null || !level.getServer().isSameThread() || !level.isLoaded(entity.blockPosition())) {
            return 0;
        }
        List<EliteDropRoll> rolls = rollEliteMineralDrops(
                entity.entityTags(),
                ThreadLocalRandom.current(),
                level.getGameTime(),
                true
        );
        int totalDropped = 0;
        for (EliteDropRoll roll : rolls) {
            Item item = resolveEliteDropItem(roll.itemId());
            if (item != null && roll.count() > 0) {
                entity.spawnAtLocation(level, new ItemStack(item, roll.count()));
                totalDropped += roll.count();
            }
        }
        return totalDropped;
    }

    public static int rollNormalHordeGunpowderDrop(
            boolean isHordeMob,
            boolean isEliteHordeMob,
            boolean isDamageClone,
            double chanceRoll,
            int countRoll) {
        if (!isHordeMob || isEliteHordeMob) {
            return 0;
        }
        double effectiveChance = isDamageClone
                ? NORMAL_HORDE_GUNPOWDER_DROP_CHANCE * DAMAGE_CLONE_LOOT_MULTIPLIER
                : NORMAL_HORDE_GUNPOWDER_DROP_CHANCE;
        if (chanceRoll < 0.0D || chanceRoll >= effectiveChance) {
            return 0;
        }
        return 1 + Math.floorMod(countRoll, 3);
    }

    public static int dropNormalHordeGunpowder(ServerLevel level, LivingEntity entity) {
        if (level == null || entity == null) {
            return 0;
        }
        boolean isHorde = entity.entityTags().contains("za_horde_mob") || isHordeMob(entity);
        boolean isElite = isEliteHordeMob(entity);
        boolean isClone = entity.entityTags().contains(DAMAGE_CLONE_TAG);
        if (!isHorde || isElite || level.getServer() == null || !level.getServer().isSameThread() || !level.isLoaded(entity.blockPosition())) {
            return 0;
        }
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int count = rollNormalHordeGunpowderDrop(true, false, isClone, rng.nextDouble(), rng.nextInt(3));
        if (count > 0) {
            entity.spawnAtLocation(level, new ItemStack(Items.GUNPOWDER, count));
        }
        return count;
    }

    public static void resetDamageCloneState() {
        lastDamageCloneTickMap.clear();
    }

    public static boolean canTriggerDamageClone(
            boolean isHordeMob,
            boolean isHostileMob,
            boolean isAlive,
            boolean hasCloneTag,
            boolean hasAttackerEntity,
            float damageAmount,
            long lastCloneTick,
            long currentTick,
            long cooldownTicks,
            int nearbyMobCount,
            int maxNearbyMobs,
            double cloneChance,
            double randomZeroToOne) {
        if (!isHordeMob || !isHostileMob || !isAlive || hasCloneTag || !hasAttackerEntity || damageAmount <= 0.0f) {
            return false;
        }
        if (cloneChance <= 0.0) {
            return false;
        }
        if (lastCloneTick >= 0L && (currentTick - lastCloneTick) < Math.max(0L, cooldownTicks)) {
            return false;
        }
        if (maxNearbyMobs > 0 && nearbyMobCount >= maxNearbyMobs) {
            return false;
        }
        return randomZeroToOne < cloneChance;
    }

    public static int trySpawnDamageClones(ServerLevel level, Mob mob, DamageSource source, float amount) {
        if (level == null || mob == null || source == null || ZAConfigZDay.mobDamageCloneCount <= 0) {
            return 0;
        }
        boolean parentIsHorde = isHordeMob(mob);
        if (!parentIsHorde) {
            return 0;
        }
        if (level.getServer() == null || !level.getServer().isSameThread() || !level.isLoaded(mob.blockPosition())) {
            return 0;
        }
        long gameTime = level.getGameTime();
        long lastCloneTick = lastDamageCloneTickMap.getOrDefault(mob.getUUID(), -1L);
        int nearbyHostiles = level.getEntitiesOfClass(
                Mob.class,
                mob.getBoundingBox().inflate(24.0, 12.0, 24.0),
                m -> m != null && m.isAlive() && isHostileMob(m)
        ).size();

        boolean allowed = canTriggerDamageClone(
                parentIsHorde,
                isHostileMob(mob),
                mob.isAlive() && !mob.isRemoved(),
                mob.entityTags().contains(DAMAGE_CLONE_TAG),
                source.getEntity() != null,
                amount,
                lastCloneTick,
                gameTime,
                ZAConfigZDay.mobDamageCloneCooldownTicks,
                nearbyHostiles,
                ZAConfigZDay.mobDamageCloneMaxNearby,
                ZAConfigZDay.mobDamageCloneChance,
                level.getRandom().nextDouble()
        );
        if (!allowed) {
            return 0;
        }

        if (lastDamageCloneTickMap.size() > 4096) {
            long expiry = Math.max(200L, ZAConfigZDay.mobDamageCloneCooldownTicks);
            lastDamageCloneTickMap.entrySet().removeIf(e -> (gameTime - e.getValue()) > expiry);
        }
        lastDamageCloneTickMap.put(mob.getUUID(), gameTime);

        boolean parentIsApex = mob.entityTags().contains("za_apex_corrosive");
        LivingEntity preferredTarget = mob.getTarget();
        if (preferredTarget != null && (!preferredTarget.isAlive() || shouldBlockHordeTarget(mob, preferredTarget))) {
            preferredTarget = null;
        }
        if (preferredTarget == null && source.getEntity() instanceof LivingEntity attacker && attacker.isAlive()
                && !shouldBlockHordeTarget(mob, attacker)) {
            preferredTarget = attacker;
        }
        if (preferredTarget == null && parentIsHorde) {
            preferredTarget = ChunkHeatManager.resolveHordeTargetPlayer(level, mob.getX(), mob.getY(), mob.getZ());
        }

        int spawned = 0;
        int targetClones = ZAConfigZDay.mobDamageCloneCount;
        for (int i = 0; i < targetClones; i++) {
            try {
                Entity created = mob.getType().create(level, EntitySpawnReason.MOB_SUMMONED);
                if (!(created instanceof Mob clone)) {
                    continue;
                }
                double spawnX = mob.getX();
                double spawnY = mob.getY();
                double spawnZ = mob.getZ();
                BlockPos safePos = findSafeNearbyClonePos(level, mob.blockPosition(), level.getRandom());
                if (safePos != null) {
                    spawnX = safePos.getX() + 0.5;
                    spawnY = safePos.getY();
                    spawnZ = safePos.getZ() + 0.5;
                }

                clone.setPos(spawnX, spawnY, spawnZ);
                if (!level.isLoaded(clone.blockPosition())) {
                    continue;
                }
                clone.addTag(DAMAGE_CLONE_TAG);
                if (parentIsHorde) {
                    clone.addTag("za_horde_mob");
                    if (parentIsApex) {
                        clone.addTag("za_apex_corrosive");
                    }
                }
                try {
                    clone.finalizeSpawn(level, level.getCurrentDifficultyAt(clone.blockPosition()), EntitySpawnReason.MOB_SUMMONED, null);
                } catch (Throwable ignored) {}
                if (parentIsHorde) {
                    ChunkHeatManager.applyHordeMobHealthScaling(clone);
                }
                if (preferredTarget != null && preferredTarget.isAlive() && !shouldBlockHordeTarget(clone, preferredTarget)) {
                    clone.setTarget(preferredTarget);
                }
                if (level.addFreshEntity(clone)) {
                    spawned++;
                }
            } catch (Throwable ignored) {}
        }
        return spawned;
    }

    private static BlockPos findSafeNearbyClonePos(ServerLevel level, BlockPos origin, net.minecraft.util.RandomSource rand) {
        if (level == null || origin == null || rand == null) {
            return null;
        }
        for (int attempt = 0; attempt < 8; attempt++) {
            int dx = rand.nextInt(5) - 2;
            int dz = rand.nextInt(5) - 2;
            if (dx == 0 && dz == 0) {
                continue;
            }
            for (int dy = -1; dy <= 2; dy++) {
                BlockPos feet = origin.offset(dx, dy, dz);
                BlockPos head = feet.above();
                BlockPos below = feet.below();
                if (!level.isLoaded(feet)) {
                    continue;
                }
                if (level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                        && level.getBlockState(head).getCollisionShape(level, head).isEmpty()
                        && !level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
                    return feet;
                }
            }
        }
        return null;
    }
}
