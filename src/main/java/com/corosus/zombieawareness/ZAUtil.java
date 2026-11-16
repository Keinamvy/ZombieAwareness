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
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.world.damagesource.DamageSource;
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
import net.minecraft.world.item.ItemStack;
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

public class ZAUtil {
    
    public static Random rand = new Random();
    
    public static Map<String, Integer> lastHealths = new ConcurrentHashMap<>();
    public static Map<String, Long> lastBleedTimes = new ConcurrentHashMap<>();

    public static List<SoundProfileEntry> listSoundProfiles = new CopyOnWriteArrayList<>();

	//runtime cache lookups, populates as sounds are made, useful for partial matches that require iterating entire sound profile list each time
	public static Map<String, SoundProfileEntry> lookupSoundNameToProfileCache = new ConcurrentHashMap<>();
	public static Map<Integer, SoundProfileEntry> lookupSoundIntToProfile = new ConcurrentHashMap<>();

    public static Map<Entity, Long> lookupLastAlertTime = Collections.synchronizedMap(new WeakHashMap<>());
    public static long alertDelay = 60*20;
    
    public static Map<Entity, Long> lookupLastInvestigateTime = Collections.synchronizedMap(new WeakHashMap<>());
    public static long investigateDelay = 60*20;

	public static Map<String, Long> lookupLastWaypointTime = new ConcurrentHashMap<>();

	public static Map<EntityType, Boolean> lookupTickableEntitiesCache = new ConcurrentHashMap<>();

	public static String SPEED_BOOST_TAG = "za_speedbosted";
	public static String ZA_LAST_ACTION = "za_last_action";

    public static final float APEX_ARMOR_BYPASS_RATIO = 0.40f; // Bypasses 40% of player armor
    public static final float NECROTIC_REGEN_EFFICIENCY = 0.50f; // Reduces healing/regeneration efficiency by 50%
    public static final long NECROTIC_TRAUMA_DURATION_TICKS = 160L; // 8 seconds
    public static final float HORDE_MOB_LOOT_DROP_CHANCE = 0.20f; // Only 20% of horde mobs drop loot (80% drop reduction)
    public static final String DAMAGE_CLONE_TAG = "za_damage_clone";

    private static long lastDistressScentTick = -9999L;

    private static final Map<UUID, Long> necroticTraumaUntilTick = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> lastDamageCloneTickMap = new ConcurrentHashMap<>();

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
		if (lookupSoundNameToProfileCache.containsKey(sound)) {
			return lookupSoundNameToProfileCache.get(sound);
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

	public static void tickPlayer(Player player) {
		if (player instanceof ServerPlayer serverPlayer) {
			checkPortalLockdown(serverPlayer);
		}
    	
		if (ZAConfigFeatures.wanderingHordes) {

			long lastWaypoint = lookupLastWaypointTime.containsKey(CoroUtilEntity.getName(player)) ? lookupLastWaypointTime.get(CoroUtilEntity.getName(player)) : 0L;
			if (lastWaypoint < System.currentTimeMillis()) {
				//System.out.println("lastWaypoint: " + lastWaypoint);
				lastWaypoint = System.currentTimeMillis() + ZAConfigFeatures.frequencyOfWanderingHordesPerPlayer * 1000L;
				lookupLastWaypointTime.put(CoroUtilEntity.getName(player), lastWaypoint);
				//if (rand.nextInt(25) == 0) {
				spawnWaypoint(player);
				//}
			}
		}

        if (ZAConfigFeatures.awareness_Scent && !player.isCreative()) {
        	int lastHealth = lastHealths.containsKey(CoroUtilEntity.getName(player)) ? lastHealths.get(CoroUtilEntity.getName(player)) : 0;
    		Long lastBleedTime = lastBleedTimes.containsKey(CoroUtilEntity.getName(player)) ? lastBleedTimes.get(CoroUtilEntity.getName(player)) : 0L;
    		
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
            
            lastHealths.put(CoroUtilEntity.getName(player), lastHealth);

            if(player.getHealth() / player.getMaxHealth() < 0.6F && lastBleedTime < System.currentTimeMillis()) {
                lastBleedTime = System.currentTimeMillis() + 30000L;
                lastBleedTimes.put(CoroUtilEntity.getName(player), lastBleedTime);
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

	private static final Set<String> HORDE_MOB_TAGS = Set.of(
			"za_horde_mob",
			"za_tnt_sapper",
			"za_juggernaut",
			"za_crawler",
			"za_apex_corrosive",
			"za_screamer",
			"za_airdrop_passenger",
			TANKER_TAG,
			BLOODMOON_WARDEN_TAG,
			KNIGHT_TAG,
			KNIGHT_MOUNT_TAG,
			RAMPAGE_TAG
	);

	public static boolean hasHordeMobTag(Collection<String> tags) {
		if (tags == null || tags.isEmpty()) {
			return false;
		}
		for (String tag : HORDE_MOB_TAGS) {
			if (tags.contains(tag)) {
				return true;
			}
		}
		return false;
	}

	public static boolean isHordeMob(Entity entity) {
		return entity != null && hasHordeMobTag(entity.entityTags());
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

		long lastActionTime = ZombieAwareness.getPersistentDataSafe(ent).getLongOr(ZA_LAST_ACTION, 0L);
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
    	
    	if (ent.level().isBrightOutside()) return false;

		boolean forceOn = false;
    	
    	if (forceOn || CU.rand().nextInt(3) == 0) {

			float lightValueAtEntity = ent.level().getBrightness(LightLayer.BLOCK, ent.blockPosition());

    		Random rand = new Random();
    		
    		int size;
    		
    		for (int i = 0; i < 4; i++) {
    			Player entP = getClosestPlayerToEntity(ent.level(), ent, 999);
        		if (entP != null) {

					//adjusted from 32 for 1.16 to get better results, vanilla might have changed something
        			size = 32 * (i+1);
        			//size = 12 * (i+1);
        			//size = 4;

		    		int rX = (int) Math.floor(entP.getX() + (rand.nextInt(size) - (size/2)));
		    		int rY = (int) Math.floor(entP.getY() + (rand.nextInt(size/2) - (size/4)));
		    		int rZ = (int) Math.floor(entP.getZ() + (rand.nextInt(size) - (size/2)));

		    		BlockPos pos = new BlockPos(rX, rY, rZ);

					if (!ent.level().isLoaded(pos)) continue;

		    		float lightValue = entP.level().getBrightness(LightLayer.BLOCK, ent.blockPosition());

					//as of 1.16 its float values for brightness, 0.2 seems like a good minimum brightness
					//moon phases doesnt seem to affect this value

		    		//if bright enough and also as bright or brighter than where they are currently
		    		if (forceOn || lightValue > 0.2F/* && lightValue >= lightValueAtEntity*/) {
						//adjusted to 32 from 64
		    			if ((forceOn || CU.rand().nextInt(5) == 0) && ent.distanceTo(entP) > 16) {
							boolean canSeePos = CoroUtilEntity.canSee(ent, new BlockPos(rX, rY, rZ));
							if (canSeePos) {
								ZombieAwareness.dbg("try path to light source - " + rX + ", " + rY + ", " + rZ);
								//if (ent.getNavigation().moveTo(rX, rY, rZ, 1)) {
								Profiler.get().push("zombieawareness_pathfind");
								boolean pathFound = CoroUtilPath.tryMoveToXYZLongDist(ent, rX, rY, rZ, 1);
								//pathFound = ent.getNavigation().moveTo(rX, rY, rZ, 1);
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
    	long huntRange = ZAConfigGeneral.sightRange;
    	
    	if (omniscient) huntRange = 512;
    	
    	if ((ent.getTarget() == null || CU.rand().nextInt(100) == 0)) {
			boolean found = false;
			Entity clEnt = null;
			float closest = 9999F;
	    	List list = ent.level().getEntities(ent, ent.getBoundingBox().inflate(huntRange, huntRange/2, huntRange));
	        for(int j = 0; j < list.size(); j++)
	        {
	            Entity entity1 = (Entity)list.get(j);

	            //for new calmed zombies, keep them from targetting/pathing towards player
				//just blacklisting all of them from this feature, not checking if calm currently
	            if (ent.getClass().getSimpleName().equals("EntityZombiePlayer")) {
	            	continue;
				}

	            if(isEnemy(ent, entity1, omniscient))
	            {
	            	if (omniscient || (ZAConfigGeneral.seeThroughWalls || ((LivingEntity) entity1).hasLineOfSight(ent))) {
	            		if (sanityCheck(ent, entity1)) {
	            			float dist = (float) ent.distanceToSqr(entity1);
	            			if (dist < closest) {
	            				closest = dist;
	            				clEnt = entity1;
	            			}
	            		}
	            	}
	            }
	        }
	        if (clEnt != null) {
	        	huntTarget(ent, (LivingEntity)clEnt);
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
		Player closestPlayer = getClosestPlayer(world, x, y, z, 5);
		if (closestPlayer != null) {
			System.out.println("client sound heard: " + SoundProfileEntry.getSoundEventName(sound));
		}
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

	public static void test(Object... args) {
		//System.out.println("can attack? " );
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

    public static boolean shouldHordeMobDropLoot(boolean isHordeMob, float randomZeroToOne) {
        if (!isHordeMob) {
            return true;
        }
        return randomZeroToOne < HORDE_MOB_LOOT_DROP_CHANCE;
    }

    public static void resetDamageCloneState() {
        lastDamageCloneTickMap.clear();
    }

    public static boolean canTriggerDamageClone(
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
        if (!isHostileMob || !isAlive || hasCloneTag || !hasAttackerEntity || damageAmount <= 0.0f) {
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

        boolean parentIsHorde = isHordeMob(mob);
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
