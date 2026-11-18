package com.corosus.zombieawareness.heat;

import com.corosus.coroutil.util.CoroUtilAttributes;
import com.corosus.coroutil.util.CoroUtilPath;
import com.corosus.zombieawareness.ZAUtil;
import com.corosus.zombieawareness.ZombieAwareness;
import com.corosus.zombieawareness.config.ZAConfigZDay;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.behavior.warden.SonicBoom;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.animal.equine.ZombieHorse;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.monster.warden.WardenAi;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

/**
 * Coordinates special horde units and optional TACZ-Turrets runtime integration:
 * 1) Bloodmoon Phase 5 Wardens (3 per horde) prioritizing TACZ turrets from distance via Sonic Boom.
 * 2) Tanker Zombies (za_tanker) with Resistance III that pull turret focus.
 */
public final class HordeSpecialUnitHelper {

    public static final String TANKER_TAG = ZAUtil.TANKER_TAG;
    public static final String BLOODMOON_WARDEN_TAG = ZAUtil.BLOODMOON_WARDEN_TAG;
    public static final String KNIGHT_TAG = ZAUtil.KNIGHT_TAG;
    public static final String KNIGHT_MOUNT_TAG = ZAUtil.KNIGHT_MOUNT_TAG;
    public static final String ENGINEER_TAG = "za_engineer";
    public static final String JUGGERNAUT_TAG = "za_juggernaut";
    public static final String CRAWLER_TAG = "za_crawler";
    public static final String SAPPER_TAG = "za_tnt_sapper";
    public static final String SCREAMER_TAG = "za_screamer";
    public static final String THROWN_PROJECTILE_TAG = "za_thrown_projectile";
    public static final String THROWN_LANDED_TAG = "za_thrown_landed";
    public static final String SAPPER_FUSE_TAG = "za_sapper_fuse";
    public static final String LABELED_MOB_TAG = ZAUtil.LABELED_MOB_TAG;
    public static final String TACZ_TURRETS_NAMESPACE = "tacz_turrets";

    public static final int TANKER_RESISTANCE_AMPLIFIER = 2; // amplifier 2 = Resistance III
    public static final int TANKER_EFFECT_DURATION_TICKS = 7200;
    public static final double TANKER_MAX_HEALTH = 40.0D;
    public static final double TANKER_KNOCKBACK_RESISTANCE = 0.90D;
    public static final double TANKER_MOVEMENT_SPEED = 0.16D;
    public static final double TANKER_SCALE = 1.35D;

    public static final int KNIGHT_RESISTANCE_AMPLIFIER = 0; // amplifier 0 = Resistance I
    public static final double KNIGHT_MAX_HEALTH = 30.0D;
    public static final double KNIGHT_KNOCKBACK_RESISTANCE = 0.50D;
    public static final double KNIGHT_SCALE = 1.10D;
    public static final double KNIGHT_MOUNT_MAX_HEALTH = 30.0D;
    public static final double KNIGHT_MOUNT_SPEED = 0.30D;

    public static final double ENGINEER_SCALE = 1.05D;
    public static final double JUGGERNAUT_SCALE = 1.20D;
    public static final double CRAWLER_SCALE = 0.75D;
    public static final double SCREAMER_SCALE = 1.05D;
    public static final double WARDEN_SCALE = 1.15D;

    public static final int DEFAULT_JUGGERNAUT_THROW_INTERVAL_TICKS = 200;
    public static final double DEFAULT_JUGGERNAUT_THROW_MIN_RANGE = 8.0D;
    public static final double DEFAULT_JUGGERNAUT_THROW_MAX_RANGE = 28.0D;
    public static final double JUGGERNAUT_AMMO_GRAB_RADIUS = 5.0D;
    public static final int DEFAULT_THROWN_SAPPER_FUSE_TICKS = 50;
    public static final int SCREAMER_GLOW_DURATION_TICKS = 7200;

    public static final String TANKER_LABEL = "§b§l[TANKER]";
    public static final String KNIGHT_LABEL = "§6§l[KNIGHT]";
    public static final String ENGINEER_LABEL = "§3§l[ENGINEER]";
    public static final String JUGGERNAUT_LABEL = "§4§l[JUGGERNAUT]";
    public static final String CRAWLER_LABEL = "§e§l[CRAWLER]";
    public static final String SCREAMER_LABEL = "§d§l[SCREAMER]";
    public static final String SAPPER_LABEL = "§c§l[SAPPER]";
    public static final String APEX_CORROSIVE_LABEL = "§2§l[CORROSIVE]";
    public static final String WARDEN_LABEL = "§5§l[SIEGE WARDEN]";

    public static final int BLOODMOON_WARDEN_COUNT = 3;
    public static final double WARDEN_TURRET_SCAN_RADIUS = 64.0D;
    public static final double WARDEN_SONIC_BOOM_MAX_RANGE = 48.0D;
    public static final double WARDEN_STANDOFF_MIN_RANGE = 12.0D;
    public static final double WARDEN_STANDOFF_DESIRED_RANGE = 16.0D;
    public static final double WARDEN_STANDOFF_MAX_RANGE = 24.0D;
    public static final int WARDEN_SONIC_CHARGE_TICKS = 34;
    public static final int WARDEN_SONIC_COOLDOWN_TICKS = 60;
    public static final float WARDEN_SONIC_DAMAGE = 14.0F;
    public static final double TURRET_FOCUS_RADIUS = 48.0D;

    public enum WardenMovementAction {
        ADVANCE_TO_STANDOFF,
        HOLD_STANDOFF,
        RETREAT_TO_STANDOFF
    }

    private static final class WardenSonicState {
        int chargeTicks = -1;
        int cooldownTicks = 20;
        UUID activeTargetId = null;
    }

    public static final class ThrownSapperFuseState {
        public volatile boolean landed = false;
        public volatile int remainingFuseTicks;
        public volatile int airborneTicks = 0;

        public ThrownSapperFuseState(int remainingFuseTicks) {
            this.remainingFuseTicks = Math.max(1, remainingFuseTicks);
        }
    }

    private static final class TurretMethodHandles {
        final Method alertToMethod;
        final Method isEnabledMethod;
        final Method getRangeMethod;
        final Method isValidTargetMethod;

        TurretMethodHandles(Class<?> cls) {
            alertToMethod = findMethod(cls, "alertTo", LivingEntity.class);
            isEnabledMethod = findMethod(cls, "isEnabled");
            getRangeMethod = findMethod(cls, "getRange");
            isValidTargetMethod = findMethod(cls, "isValidTarget", LivingEntity.class);
        }

        private static Method findMethod(Class<?> cls, String name, Class<?>... paramTypes) {
            try {
                Method m = cls.getMethod(name, paramTypes);
                m.setAccessible(true);
                return m;
            } catch (Throwable ignored) {
                return null;
            }
        }
    }

    private static final Map<UUID, WardenSonicState> WARDEN_SONIC_STATES = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> JUGGERNAUT_LAST_THROW_TICK = new ConcurrentHashMap<>();
    private static final Map<UUID, ThrownSapperFuseState> ARMED_THROWN_SAPPERS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, TurretMethodHandles> TURRET_METHOD_CACHE = new ConcurrentHashMap<>();
    private static final Map<EntityType<?>, Boolean> TURRET_TYPE_CACHE = new ConcurrentHashMap<>();
    private static final boolean TACZ_TURRETS_LOADED = detectTaczTurretsLoaded();

    private HordeSpecialUnitHelper() {
    }

    private static boolean detectTaczTurretsLoaded() {
        try {
            return FabricLoader.getInstance().isModLoaded(TACZ_TURRETS_NAMESPACE);
        } catch (Throwable ignored) {
            // Plain JUnit test environment without FabricLoader bootstrap
            return true;
        }
    }

    public static boolean isTaczTurretsLoaded() {
        return TACZ_TURRETS_LOADED;
    }

    public static boolean hasActiveWardenStates() {
        return !WARDEN_SONIC_STATES.isEmpty();
    }

    public static boolean hasArmedThrownSapperStates() {
        return !ARMED_THROWN_SAPPERS.isEmpty();
    }

    public static boolean isArmedThrownSapper(UUID entityId) {
        return entityId != null && ARMED_THROWN_SAPPERS.containsKey(entityId);
    }

    public static void clearAllState() {
        WARDEN_SONIC_STATES.clear();
        JUGGERNAUT_LAST_THROW_TICK.clear();
        ARMED_THROWN_SAPPERS.clear();
    }

    public static void removeWardenState(UUID entityId) {
        if (entityId != null) {
            WARDEN_SONIC_STATES.remove(entityId);
            JUGGERNAUT_LAST_THROW_TICK.remove(entityId);
            ARMED_THROWN_SAPPERS.remove(entityId);
        }
    }

    public static boolean isTankerZombie(Entity entity) {
        return entity != null && entity.entityTags().contains(TANKER_TAG);
    }

    public static boolean isBloodmoonWarden(Entity entity) {
        return entity != null && entity.entityTags().contains(BLOODMOON_WARDEN_TAG);
    }

    public static boolean isKnightZombie(Entity entity) {
        return entity != null && entity.entityTags().contains(KNIGHT_TAG);
    }

    public static boolean isKnightMount(Entity entity) {
        return entity != null && entity.entityTags().contains(KNIGHT_MOUNT_TAG);
    }

    public static boolean isEngineerZombie(Entity entity) {
        return entity != null && entity.entityTags().contains(ENGINEER_TAG);
    }

    public static boolean isJuggernautZombie(Entity entity) {
        return entity != null && entity.entityTags().contains(JUGGERNAUT_TAG);
    }

    public static boolean isZASpecialLabeledMob(Entity entity) {
        return entity != null && (entity.entityTags().contains(LABELED_MOB_TAG) || ZAUtil.isHordeMob(entity));
    }

    public static boolean shouldBlockWardenTargeting(Entity warden, Entity target) {
        if (warden == null || target == null) {
            return false;
        }
        boolean wardenIsHorde = isBloodmoonWarden(warden) || ZAUtil.isHordeMob(warden);
        return ZAUtil.shouldBlockWardenFriendlyAction(
                wardenIsHorde,
                ZAUtil.isHordeMob(target),
                ZAUtil.isHostileMob(target),
                ZAUtil.isVillagerEntity(target)
        );
    }

    public static String resolveSpecialIndicatorLabel(Collection<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        if (tags.contains(BLOODMOON_WARDEN_TAG)) {
            return WARDEN_LABEL;
        }
        if (tags.contains(TANKER_TAG)) {
            return TANKER_LABEL;
        }
        if (tags.contains(KNIGHT_TAG)) {
            return KNIGHT_LABEL;
        }
        if (tags.contains(ENGINEER_TAG)) {
            return ENGINEER_LABEL;
        }
        if (tags.contains(JUGGERNAUT_TAG)) {
            return JUGGERNAUT_LABEL;
        }
        if (tags.contains("za_tnt_sapper")) {
            return SAPPER_LABEL;
        }
        if (tags.contains("za_screamer")) {
            return SCREAMER_LABEL;
        }
        if (tags.contains("za_crawler")) {
            return CRAWLER_LABEL;
        }
        if (tags.contains("za_apex_corrosive") && !tags.contains(KNIGHT_MOUNT_TAG)) {
            return APEX_CORROSIVE_LABEL;
        }
        return null;
    }

    public static double resolveSpecialIndicatorScale(Collection<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return 1.0D;
        }
        if (tags.contains(TANKER_TAG)) {
            return TANKER_SCALE;
        }
        if (tags.contains("za_juggernaut")) {
            return JUGGERNAUT_SCALE;
        }
        if (tags.contains(BLOODMOON_WARDEN_TAG)) {
            return WARDEN_SCALE;
        }
        if (tags.contains(KNIGHT_TAG)) {
            return KNIGHT_SCALE;
        }
        if (tags.contains(ENGINEER_TAG)) {
            return ENGINEER_SCALE;
        }
        if (tags.contains("za_screamer")) {
            return SCREAMER_SCALE;
        }
        if (tags.contains("za_crawler")) {
            return CRAWLER_SCALE;
        }
        return 1.0D;
    }

    /**
     * Returns true only when the entity's tag set represents a [SCREAMER] unit actively belonging to a horde.
     * Non-horde Screamers and all other horde mobs, elites, Apex variants, or special units must not glow.
     */
    public static boolean shouldHordeScreamerGlow(Collection<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return false;
        }
        return tags.contains("za_horde_mob")
                && tags.contains(SCREAMER_TAG)
                && SCREAMER_LABEL.equals(resolveSpecialIndicatorLabel(tags));
    }

    /**
     * Applies visible colored nameplates, distinct scale, and unmistakable gear indicators for special ZA mobs.
     */
    public static void applySpecialMobIndicator(Mob mob) {
        if (mob == null) {
            return;
        }
        Collection<String> tags = mob.entityTags();
        String label = resolveSpecialIndicatorLabel(tags);
        if (label != null) {
            mob.addTag(LABELED_MOB_TAG);
            mob.setCustomName(Component.literal(label));
            mob.setCustomNameVisible(true);
        }

        double targetScale = resolveSpecialIndicatorScale(tags);
        if (Math.abs(targetScale - 1.0D) > 0.001D) {
            var scaleAttr = mob.getAttribute(Attributes.SCALE);
            if (scaleAttr != null && Math.abs(scaleAttr.getBaseValue() - targetScale) > 0.001D) {
                scaleAttr.setBaseValue(targetScale);
            }
        }

        if (tags.contains(TANKER_TAG)) {
            mob.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
            if (mob.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
                mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
            }
        } else if (tags.contains(KNIGHT_TAG)) {
            if (mob.getMainHandItem().isEmpty()) {
                mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SPEAR));
            }
        } else if (tags.contains(ENGINEER_TAG)) {
            if (mob.getMainHandItem().isEmpty()) {
                mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
            }
            if (mob.getOffhandItem().isEmpty()) {
                mob.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.COARSE_DIRT));
            }
            if (mob.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
                mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CHAINMAIL_HELMET));
            }
            if (mob.getItemBySlot(EquipmentSlot.CHEST).isEmpty()) {
                mob.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.CHAINMAIL_CHESTPLATE));
            }
        } else if (tags.contains(JUGGERNAUT_TAG)) {
            if (mob.getItemBySlot(EquipmentSlot.CHEST).isEmpty()) {
                mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
                mob.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
                mob.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
                mob.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
            }
        } else if (tags.contains(SAPPER_TAG)) {
            if (!mob.getMainHandItem().is(Items.TNT)) {
                mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TNT));
            }
            if (mob.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
                mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
            }
        } else if (tags.contains(SCREAMER_TAG)) {
            if (mob.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
                mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
            }
        }

        if (shouldHordeScreamerGlow(tags) && !mob.hasEffect(MobEffects.GLOWING)) {
            mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, SCREAMER_GLOW_DURATION_TICKS, 0, false, false));
        }
    }

    /**
     * Equips and tags a Siege Engineer zombie (za_engineer) for temporary bridge/pillar building.
     */
    public static void applyEngineerLoadout(Zombie zombie) {
        if (zombie == null) {
            return;
        }
        zombie.addTag("za_horde_mob");
        zombie.addTag(ENGINEER_TAG);
        zombie.addTag(LABELED_MOB_TAG);
        zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
        zombie.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.COARSE_DIRT));
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CHAINMAIL_HELMET));
        zombie.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.CHAINMAIL_CHESTPLATE));
        applySpecialMobIndicator(zombie);
    }

    public static boolean isTaczTurretEntityTypeId(String namespace, String path) {
        return TACZ_TURRETS_NAMESPACE.equals(namespace)
                && ("turret".equals(path) || "sentry_turret".equals(path));
    }

    public static boolean isTaczTurretEntity(Entity entity) {
        if (!TACZ_TURRETS_LOADED || entity == null || entity.getType() == null) {
            return false;
        }
        return TURRET_TYPE_CACHE.computeIfAbsent(entity.getType(), type -> {
            Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            return key != null && isTaczTurretEntityTypeId(key.getNamespace(), key.getPath());
        });
    }

    private static TurretMethodHandles getHandles(Mob turret) {
        return TURRET_METHOD_CACHE.computeIfAbsent(turret.getClass(), TurretMethodHandles::new);
    }

    public static boolean isTurretEnabled(Mob turret) {
        if (turret == null || !turret.isAlive() || turret.isRemoved()) {
            return false;
        }
        Method m = getHandles(turret).isEnabledMethod;
        if (m != null) {
            try {
                Object res = m.invoke(turret);
                if (res instanceof Boolean b) {
                    return b;
                }
            } catch (Throwable ignored) {
            }
        }
        return true;
    }

    public static double getTurretEffectiveRange(Mob turret) {
        if (turret == null) {
            return TURRET_FOCUS_RADIUS;
        }
        Method m = getHandles(turret).getRangeMethod;
        if (m != null) {
            try {
                Object res = m.invoke(turret);
                if (res instanceof Number n && n.doubleValue() > 0.0) {
                    return n.doubleValue();
                }
            } catch (Throwable ignored) {
            }
        }
        return TURRET_FOCUS_RADIUS;
    }

    public static boolean isTurretValidTarget(Mob turret, LivingEntity candidate) {
        if (turret == null || candidate == null || !candidate.isAlive() || candidate.isRemoved()) {
            return false;
        }
        Method m = getHandles(turret).isValidTargetMethod;
        if (m != null) {
            try {
                Object res = m.invoke(turret, candidate);
                if (res instanceof Boolean b) {
                    return b;
                }
            } catch (Throwable ignored) {
            }
        }
        return true;
    }

    /**
     * Applies frontline Tanker tags, Resistance III, slower speed, bigger scale, heavy armor + shield, and nameplate.
     */
    public static void applyTankerLoadout(Zombie zombie) {
        if (zombie == null) {
            return;
        }
        zombie.addTag("za_horde_mob");
        zombie.addTag(TANKER_TAG);
        zombie.addTag(LABELED_MOB_TAG);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
        zombie.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        zombie.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
        zombie.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.NETHERITE_BOOTS));
        zombie.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));

        var healthAttr = zombie.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttr != null) {
            double targetMax = Math.max(TANKER_MAX_HEALTH, healthAttr.getBaseValue());
            healthAttr.setBaseValue(targetMax);
            zombie.setHealth((float) targetMax);
        }
        var kbAttr = zombie.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (kbAttr != null) {
            kbAttr.setBaseValue(Math.max(TANKER_KNOCKBACK_RESISTANCE, kbAttr.getBaseValue()));
        }
        enforceTankerSpeed(zombie);
        applySpecialMobIndicator(zombie);
        ensureTankerResistanceEffect(zombie);
    }

    /**
     * Ensures Tanker zombies stay slower than regular zombies and strip ZA's random speed boost modifier.
     */
    public static void enforceTankerSpeed(Zombie zombie) {
        if (zombie == null) {
            return;
        }
        var speedAttr = zombie.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.removeModifier(CoroUtilAttributes.SPEED_BOOST_ID);
            if (speedAttr.getBaseValue() > TANKER_MOVEMENT_SPEED) {
                speedAttr.setBaseValue(TANKER_MOVEMENT_SPEED);
            }
        }
    }

    /**
     * Applies Knight tags, spear, iron knight armor, scale, nameplate, and Resistance I (amplifier 0).
     */
    public static void applyKnightLoadout(Zombie knight, boolean upgradedSpear) {
        if (knight == null) {
            return;
        }
        knight.addTag("za_horde_mob");
        knight.addTag(KNIGHT_TAG);
        knight.addTag(LABELED_MOB_TAG);
        knight.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        knight.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        knight.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
        knight.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
        knight.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(upgradedSpear ? Items.DIAMOND_SPEAR : Items.IRON_SPEAR));

        var healthAttr = knight.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttr != null) {
            double targetMax = Math.max(KNIGHT_MAX_HEALTH, healthAttr.getBaseValue());
            healthAttr.setBaseValue(targetMax);
            knight.setHealth((float) targetMax);
        }
        var kbAttr = knight.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (kbAttr != null) {
            kbAttr.setBaseValue(Math.max(KNIGHT_KNOCKBACK_RESISTANCE, kbAttr.getBaseValue()));
        }
        applySpecialMobIndicator(knight);
        ensureKnightResistanceEffect(knight);
    }

    /**
     * Keeps Resistance I (amplifier 0) active on Knight zombies.
     */
    public static void ensureKnightResistanceEffect(Zombie knight) {
        if (knight == null || !knight.isAlive()) {
            return;
        }
        MobEffectInstance existing = knight.getEffect(MobEffects.RESISTANCE);
        if (existing == null || existing.getAmplifier() < KNIGHT_RESISTANCE_AMPLIFIER || existing.getDuration() < 600) {
            knight.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, TANKER_EFFECT_DURATION_TICKS, KNIGHT_RESISTANCE_AMPLIFIER, false, true));
        }
    }

    /**
     * Spawns vanilla MC 26.3's native Knight Zombie (ZombieHorse + spear-wielding Zombie rider),
     * tagged as a horde Knight with Resistance I, Knight armor, and clear visual indicator.
     */
    public static Zombie spawnKnightCavalry(
            ServerLevel level,
            double x,
            double y,
            double z,
            boolean apexCorrosive,
            LivingEntity initialTarget
    ) {
        if (level == null || !level.isLoaded(BlockPos.containing(x, y, z))) {
            return null;
        }
        try {
            ZombieHorse horse = new ZombieHorse(EntityTypes.ZOMBIE_HORSE, level);
            horse.addTag("za_horde_mob");
            horse.addTag(KNIGHT_MOUNT_TAG);
            horse.addTag(LABELED_MOB_TAG);
            if (apexCorrosive) {
                horse.addTag("za_apex_corrosive");
            }
            horse.setPersistenceRequired();
            horse.setPos(x, y, z);
            // Trigger vanilla MC 26.3 native spear-wielding ZombieHorseman spawn via NATURAL finalizeSpawn
            try {
                horse.finalizeSpawn(level, level.getCurrentDifficultyAt(horse.blockPosition()), EntitySpawnReason.NATURAL, null);
            } catch (Throwable ignored) {
            }
            horse.setTamed(true);
            horse.setItemSlot(EquipmentSlot.SADDLE, new ItemStack(Items.SADDLE));
            var horseHp = horse.getAttribute(Attributes.MAX_HEALTH);
            if (horseHp != null) {
                double targetHp = Math.max(KNIGHT_MOUNT_MAX_HEALTH, horseHp.getBaseValue());
                horseHp.setBaseValue(targetHp);
                horse.setHealth((float) targetHp);
            }
            var horseSpeed = horse.getAttribute(Attributes.MOVEMENT_SPEED);
            if (horseSpeed != null && horseSpeed.getBaseValue() < KNIGHT_MOUNT_SPEED) {
                horseSpeed.setBaseValue(KNIGHT_MOUNT_SPEED);
            }
            horse.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 7200, 0, false, false));

            Zombie knight = null;
            for (Entity passenger : horse.getPassengers()) {
                if (passenger instanceof Zombie passengerZombie) {
                    knight = passengerZombie;
                    break;
                }
            }
            if (knight == null) {
                knight = new Zombie(level);
                knight.setPos(x, y, z);
                try {
                    knight.finalizeSpawn(level, level.getCurrentDifficultyAt(knight.blockPosition()), EntitySpawnReason.JOCKEY, null);
                } catch (Throwable ignored) {
                }
                knight.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SPEAR));
                knight.startRiding(horse, true, false);
            }

            applyKnightLoadout(knight, apexCorrosive);
            if (apexCorrosive) {
                knight.addTag("za_apex_corrosive");
            }
            ChunkHeatManager.applyHordeMobHealthScaling(knight);
            knight.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 7200, 0, false, false));
            ensureKnightResistanceEffect(knight);
            applySpecialMobIndicator(knight);

            level.addFreshEntityWithPassengers(horse);
            if (initialTarget != null && initialTarget.isAlive()) {
                knight.setTarget(initialTarget);
                try {
                    CoroUtilPath.tryMoveToEntityLivingLongDist(horse, initialTarget, 1.25D);
                } catch (Throwable ignored) {
                }
            }
            return knight;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Keeps ZombieHorse cavalry moving steadily toward the mounted Knight's active target.
     */
    public static void steerKnightMountToRiderTarget(Mob mount) {
        if (mount == null || !mount.isAlive() || (mount.tickCount % 10 != 0 && !mount.getNavigation().isDone()) || mount.getPassengers().isEmpty()) {
            return;
        }
        for (Entity passenger : mount.getPassengers()) {
            if (passenger instanceof Mob rider && rider.isAlive() && isKnightZombie(rider)) {
                LivingEntity target = rider.getTarget();
                if (target != null && target.isAlive() && mount.distanceToSqr(target) > 6.25D) {
                    try {
                        CoroUtilPath.tryMoveToEntityLivingLongDist(mount, target, 1.25D);
                    } catch (Throwable ignored) {
                    }
                }
                break;
            }
        }
    }

    /**
     * Keeps Resistance III (amplifier 2) active on Tanker zombies.
     */
    public static void ensureTankerResistanceEffect(Zombie zombie) {
        if (zombie == null || !zombie.isAlive()) {
            return;
        }
        MobEffectInstance existing = zombie.getEffect(MobEffects.RESISTANCE);
        if (existing == null || existing.getAmplifier() < TANKER_RESISTANCE_AMPLIFIER || existing.getDuration() < 600) {
            zombie.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, TANKER_EFFECT_DURATION_TICKS, TANKER_RESISTANCE_AMPLIFIER, false, true));
        }
    }

    /**
     * Pure gate determining whether Bloodmoon Phase 5 Wardens should be spawned for an active horde.
     */
    public static boolean shouldSpawnBloodmoonPhase5Wardens(
            ChunkHeatManager.HordePhase phase,
            boolean isBloodmoon,
            boolean alreadySpawned,
            boolean shouldReinforce
    ) {
        return shouldReinforce
                && isBloodmoon
                && phase == ChunkHeatManager.HordePhase.PHASE_5_APEX_CORROSION
                && !alreadySpawned;
    }

    /**
     * Spawns up to BLOODMOON_WARDEN_COUNT (3) Bloodmoon Wardens on loaded dry ground near the horde approach cone.
     */
    public static int spawnBloodmoonPhase5Wardens(
            ServerLevel level,
            BlockPos center,
            double approachAngleRad,
            boolean surroundMode,
            Collection<UUID> originalGroupPlayerIds
    ) {
        if (level == null || center == null) {
            return 0;
        }
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        int spawned = 0;

        for (int i = 0; i < BLOODMOON_WARDEN_COUNT; i++) {
            double targetX = center.getX();
            double targetZ = center.getZ();
            int spawnY = ChunkHeatManager.NO_DRY_SPAWN_Y;

            for (int attempt = 0; attempt < 20; attempt++) {
                double normAngle = rand.nextDouble() * 2.0 - 1.0;
                double normDist = rand.nextDouble();
                double[] offset = ChunkHeatManager.computeSpawnOffset(approachAngleRad, normAngle, normDist, surroundMode);
                double candX = center.getX() + offset[0];
                double candZ = center.getZ() + offset[1];
                if (attempt >= 10) {
                    double sweepAngle = rand.nextDouble() * 2.0 * Math.PI;
                    double sweepDist = 28.0 + rand.nextDouble() * 24.0;
                    candX = center.getX() + Math.cos(sweepAngle) * sweepDist;
                    candZ = center.getZ() + Math.sin(sweepAngle) * sweepDist;
                }
                int bx = (int) Math.floor(candX);
                int bz = (int) Math.floor(candZ);
                if (!ChunkHeatManager.isChunkLoadedAt(level, bx, bz)) {
                    continue;
                }
                int foundY = ChunkHeatManager.findSafeDrySpawnY(level, bx, center.getY(), bz);
                if (foundY != ChunkHeatManager.NO_DRY_SPAWN_Y) {
                    targetX = candX;
                    targetZ = candZ;
                    spawnY = foundY;
                    break;
                }
            }

            if (spawnY == ChunkHeatManager.NO_DRY_SPAWN_Y) {
                continue;
            }

            try {
                Warden warden = new Warden(EntityTypes.WARDEN, level);
                warden.addTag("za_horde_mob");
                warden.addTag("za_apex_corrosive");
                warden.addTag(BLOODMOON_WARDEN_TAG);
                warden.setPersistenceRequired();
                warden.setPos(targetX, spawnY, targetZ);
                if (!level.isLoaded(warden.blockPosition())) {
                    continue;
                }
                try {
                    warden.finalizeSpawn(level, level.getCurrentDifficultyAt(warden.blockPosition()), EntitySpawnReason.EVENT, null);
                } catch (Throwable ignored) {
                }
                ChunkHeatManager.applyHordeMobHealthScaling(warden);
                warden.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 7200, 0, false, false));
                WardenAi.setDigCooldown(warden);
                applySpecialMobIndicator(warden);
                level.addFreshEntity(warden);
                spawned++;
            } catch (Throwable ignored) {
            }
        }

        if (spawned > 0 && level.players() != null) {
            String dir = ChunkHeatManager.getCompassDirectionFromAngle(approachAngleRad).toUpperCase(Locale.ROOT);
            for (ServerPlayer player : level.players()) {
                if (player != null && ((originalGroupPlayerIds != null && originalGroupPlayerIds.contains(player.getUUID()))
                        || player.distanceToSqr(center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5) <= ChunkHeatManager.GROUP_CLUSTER_RADIUS * ChunkHeatManager.GROUP_CLUSTER_RADIUS)) {
                    player.sendSystemMessage(Component.literal("§5§l[!] " + spawned + " BLOODMOON WARDENS EMERGE FROM THE " + dir + " TO SHATTER YOUR DEFENSES!"));
                }
            }
        }
        return spawned;
    }

    /**
     * Pure coordinate selector returning the index of the closest enabled turret within radius, or -1 (player fallback).
     */
    public static int selectClosestTurretIndex(
            double wardenX,
            double wardenY,
            double wardenZ,
            double[] turretX,
            double[] turretY,
            double[] turretZ,
            boolean[] turretAliveAndEnabled,
            double scanRadius
    ) {
        if (turretX == null || turretY == null || turretZ == null || turretAliveAndEnabled == null || scanRadius <= 0.0D) {
            return -1;
        }
        int n = Math.min(Math.min(turretX.length, turretY.length), Math.min(turretZ.length, turretAliveAndEnabled.length));
        int bestIdx = -1;
        double bestDistSq = scanRadius * scanRadius;
        for (int i = 0; i < n; i++) {
            if (!turretAliveAndEnabled[i]) {
                continue;
            }
            double dx = turretX[i] - wardenX;
            double dy = turretY[i] - wardenY;
            double dz = turretZ[i] - wardenZ;
            double dSq = dx * dx + dy * dy + dz * dz;
            if (dSq <= bestDistSq) {
                bestDistSq = dSq;
                bestIdx = i;
            }
        }
        return bestIdx;
    }

    /**
     * Pure coordinate selector returning the index of the closest alive visible Tanker within turret range, or -1.
     */
    public static int selectPriorityTankerIndex(
            double turretX,
            double turretY,
            double turretZ,
            double[] tankerX,
            double[] tankerY,
            double[] tankerZ,
            boolean[] tankerAliveAndVisible,
            double maxRange
    ) {
        return selectClosestTurretIndex(turretX, turretY, turretZ, tankerX, tankerY, tankerZ, tankerAliveAndVisible, maxRange);
    }

    /**
     * Picks the nearest alive enabled turret within radius, falling back to fallbackPlayer if none exist.
     */
    public static LivingEntity selectBloodmoonWardenTarget(
            Mob warden,
            List<? extends Mob> turrets,
            LivingEntity fallbackPlayer,
            double turretScanRadius
    ) {
        if (warden == null) {
            return fallbackPlayer;
        }
        Mob bestTurret = null;
        double bestDistSq = turretScanRadius * turretScanRadius;
        if (turrets != null) {
            for (Mob turret : turrets) {
                if (turret == null || !turret.isAlive() || turret.isRemoved() || !isTurretEnabled(turret)) {
                    continue;
                }
                double dSq = warden.distanceToSqr(turret);
                if (dSq <= bestDistSq) {
                    bestDistSq = dSq;
                    bestTurret = turret;
                }
            }
        }
        if (bestTurret != null) {
            return bestTurret;
        }
        return (fallbackPlayer != null && fallbackPlayer.isAlive() && !fallbackPlayer.isRemoved())
                ? fallbackPlayer
                : null;
    }

    public static WardenMovementAction computeWardenTurretMovementAction(double distanceBlocks) {
        if (distanceBlocks < WARDEN_STANDOFF_MIN_RANGE) {
            return WardenMovementAction.RETREAT_TO_STANDOFF;
        }
        if (distanceBlocks > WARDEN_STANDOFF_MAX_RANGE) {
            return WardenMovementAction.ADVANCE_TO_STANDOFF;
        }
        return WardenMovementAction.HOLD_STANDOFF;
    }

    public static boolean canFireSonicBoomAtTurret(double distanceBlocks) {
        return distanceBlocks >= 0.0D && distanceBlocks <= WARDEN_SONIC_BOOM_MAX_RANGE;
    }

    /**
     * Computes a standoff coordinate along the turret -> warden axis at desiredRange distance.
     */
    public static double[] computeWardenStandoffPos(double wardenX, double wardenZ, double turretX, double turretZ, double desiredRange) {
        double dx = wardenX - turretX;
        double dz = wardenZ - turretZ;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0e-4D) {
            return new double[] { turretX + desiredRange, turretZ };
        }
        double scale = desiredRange / len;
        return new double[] { turretX + dx * scale, turretZ + dz * scale };
    }

    /**
     * Drives anti-turret Warden prioritization, dig prevention, standoff spacing, and long-range Sonic Boom attacks.
     */
    public static void tickBloodmoonWarden(
            ServerLevel level,
            Mob warden,
            List<? extends Mob> turrets,
            LivingEntity fallbackPlayer
    ) {
        if (level == null || warden == null || !warden.isAlive() || warden.isRemoved()) {
            return;
        }

        boolean periodicCheck = (warden.tickCount % 5 == 0);

        // Prevent horde Wardens from burrowing underground and despawning mid-siege
        if (periodicCheck) {
            try {
                WardenAi.setDigCooldown(warden);
            } catch (Throwable ignored) {
            }

            // Scrub any accidental anger/target pointing at horde or hostile mobs
            try {
                if (shouldBlockWardenTargeting(warden, warden.getTarget())) {
                    warden.setTarget(null);
                }
                Brain<?> b = warden.getBrain();
                b.getMemory(MemoryModuleType.ATTACK_TARGET).ifPresent(memTarget -> {
                    if (shouldBlockWardenTargeting(warden, memTarget)) {
                        b.eraseMemory(MemoryModuleType.ATTACK_TARGET);
                    }
                });
                b.getMemory(MemoryModuleType.ROAR_TARGET).ifPresent(roarTarget -> {
                    if (shouldBlockWardenTargeting(warden, roarTarget)) {
                        b.eraseMemory(MemoryModuleType.ROAR_TARGET);
                    }
                });
                if (warden instanceof Warden w) {
                    w.getEntityAngryAt().ifPresent(angryAt -> {
                        if (shouldBlockWardenTargeting(w, angryAt)) {
                            w.clearAnger(angryAt);
                        }
                    });
                }
            } catch (Throwable ignored) {
            }
        }

        LivingEntity chosenTarget = selectBloodmoonWardenTarget(warden, turrets, fallbackPlayer, WARDEN_TURRET_SCAN_RADIUS);
        if (chosenTarget == null) {
            return;
        }

        boolean targetIsTurret = isTaczTurretEntity(chosenTarget) || (turrets != null && turrets.contains(chosenTarget));
        if (!targetIsTurret) {
            WARDEN_SONIC_STATES.remove(warden.getUUID());
            if (warden.getTarget() != chosenTarget) {
                warden.setTarget(chosenTarget);
                if (warden instanceof Warden w) {
                    w.increaseAngerAt(chosenTarget, 100, false);
                }
            }
            return;
        }

        // Lock Warden AI onto the priority turret without resetting Sonic Boom cooldown on every tick
        WardenSonicState state = WARDEN_SONIC_STATES.computeIfAbsent(warden.getUUID(), id -> new WardenSonicState());
        boolean targetChanged = state.activeTargetId == null || !state.activeTargetId.equals(chosenTarget.getUUID());
        state.activeTargetId = chosenTarget.getUUID();

        if (targetChanged || warden.getTarget() != chosenTarget) {
            try {
                warden.setTarget(chosenTarget);
                Brain<?> brain = warden.getBrain();
                brain.setMemory(MemoryModuleType.ATTACK_TARGET, chosenTarget);
                if (warden instanceof Warden w) {
                    w.increaseAngerAt(chosenTarget, 150, false);
                }
            } catch (Throwable ignored) {
            }
        }

        // Suppress vanilla close-range SonicBoom while our long-range turret driver is in control
        if (targetChanged || periodicCheck) {
            try {
                SonicBoom.setCooldown(warden, 80);
            } catch (Throwable ignored) {
            }
        }

        double distSq = warden.distanceToSqr(chosenTarget);
        double dist = Math.sqrt(distSq);

        // Standoff positioning & facing
        try {
            warden.getLookControl().setLookAt(chosenTarget, 30.0F, 30.0F);
            @SuppressWarnings("unchecked")
            Brain<LivingEntity> brain = (Brain<LivingEntity>) warden.getBrain();
            brain.setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(chosenTarget, true));

            WardenMovementAction action = computeWardenTurretMovementAction(dist);
            if (action == WardenMovementAction.HOLD_STANDOFF) {
                brain.eraseMemory(MemoryModuleType.WALK_TARGET);
                warden.getNavigation().stop();
            } else {
                double[] standoff = computeWardenStandoffPos(
                        warden.getX(), warden.getZ(),
                        chosenTarget.getX(), chosenTarget.getZ(),
                        WARDEN_STANDOFF_DESIRED_RANGE
                );
                Vec3 moveTarget = new Vec3(standoff[0], warden.getY(), standoff[1]);
                brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(moveTarget, 1.15F, 2));
                if (warden.tickCount % 10 == 0 || warden.getNavigation().isDone()) {
                    warden.getNavigation().moveTo(standoff[0], chosenTarget.getY(), standoff[1], 1.15D);
                }
            }
        } catch (Throwable ignored) {
        }

        // Distance Sonic Boom windup + execution
        if (!canFireSonicBoomAtTurret(dist)) {
            if (state.chargeTicks >= 0) {
                state.chargeTicks = -1;
            }
            return;
        }

        if (state.chargeTicks < 0) {
            if (state.cooldownTicks > 0) {
                state.cooldownTicks--;
                return;
            }
            state.chargeTicks = WARDEN_SONIC_CHARGE_TICKS;
            try {
                level.broadcastEntityEvent(warden, (byte) 62);
                warden.playSound(SoundEvents.WARDEN_SONIC_CHARGE, 3.0F, 1.0F);
            } catch (Throwable ignored) {
            }
            return;
        }

        if (state.chargeTicks > 0) {
            state.chargeTicks--;
            return;
        }

        // Fire Sonic Boom beam from Warden chest toward turret eye position
        state.chargeTicks = -1;
        state.cooldownTicks = WARDEN_SONIC_COOLDOWN_TICKS;
        executeSonicBoomStrike(level, warden, chosenTarget);
    }

    public static void executeSonicBoomStrike(ServerLevel level, Mob warden, LivingEntity target) {
        if (level == null || warden == null || target == null || !target.isAlive() || shouldBlockWardenTargeting(warden, target)) {
            return;
        }
        try {
            Vec3 chestOffset = warden.getAttachments().get(EntityAttachment.WARDEN_CHEST, 0, warden.getYRot());
            Vec3 startPos = warden.position().add(chestOffset);
            Vec3 delta = target.getEyePosition().subtract(startPos);
            Vec3 dir = delta.normalize();
            int steps = Mth.floor(delta.length()) + 7;
            for (int i = 1; i < steps; i++) {
                Vec3 p = startPos.add(dir.scale(i));
                level.sendParticles(ParticleTypes.SONIC_BOOM, p.x, p.y, p.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
            warden.playSound(SoundEvents.WARDEN_SONIC_BOOM, 3.0F, 1.0F);
            target.hurtServer(level, level.damageSources().sonicBoom(warden), WARDEN_SONIC_DAMAGE);
        } catch (Throwable t) {
            ZombieAwareness.dbg("Bloodmoon Warden sonic boom error: " + t.getMessage());
        }
    }

    /**
     * Selects the nearest alive visible Tanker zombie within maxRange that the turret can validly target.
     */
    public static <T extends Mob> T selectPriorityTankerForTurret(
            Mob turret,
            List<T> tankers,
            double maxRange,
            Predicate<T> losPredicate
    ) {
        if (turret == null || tankers == null || tankers.isEmpty() || maxRange <= 0.0D) {
            return null;
        }
        T best = null;
        double bestDistSq = maxRange * maxRange;
        for (T tanker : tankers) {
            if (tanker == null || !tanker.isAlive() || tanker.isRemoved()) {
                continue;
            }
            double distSq = turret.distanceToSqr(tanker);
            if (distSq > bestDistSq) {
                continue;
            }
            if (!isTurretValidTarget(turret, tanker)) {
                continue;
            }
            if (losPredicate != null && !losPredicate.test(tanker)) {
                continue;
            }
            bestDistSq = distSq;
            best = tanker;
        }
        return best;
    }

    /**
     * Forces a turret entity to focus on the chosen Tanker via TurretEntity#alertTo + vanilla Mob/Brain target memories.
     */
    public static void directTurretToTanker(Mob turret, Mob tanker) {
        if (turret == null || tanker == null || !tanker.isAlive()) {
            return;
        }
        Method alertTo = getHandles(turret).alertToMethod;
        if (alertTo != null) {
            try {
                alertTo.invoke(turret, tanker);
            } catch (Throwable ignored) {
            }
        }
        if (turret.getTarget() != tanker) {
            turret.setTarget(tanker);
        }
        try {
            Brain<?> brain = turret.getBrain();
            if (brain != null) {
                LivingEntity currentMem = brain.getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
                if (currentMem != tanker) {
                    brain.setMemory(MemoryModuleType.ATTACK_TARGET, tanker);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Redirects enabled TACZ turrets to focus alive visible za_tanker zombies ahead of squishier horde mobs.
     */
    public static int tickTurretFocus(List<? extends Mob> turrets, List<? extends Mob> tankers) {
        if (turrets == null || turrets.isEmpty() || tankers == null || tankers.isEmpty()) {
            return 0;
        }
        int redirected = 0;
        for (Mob turret : turrets) {
            if (!isTurretEnabled(turret)) {
                continue;
            }
            double range = Math.max(16.0D, getTurretEffectiveRange(turret));
            Mob priorityTanker = selectPriorityTankerForTurret(
                    turret,
                    tankers,
                    range,
                    t -> turret.hasLineOfSight(t) || turret.getSensing().hasLineOfSight(t)
            );
            if (priorityTanker == null) {
                continue;
            }
            LivingEntity currentTarget = turret.getTarget();
            boolean alreadyTanker = currentTarget != null && currentTarget.isAlive() && isTankerZombie(currentTarget);
            if (!alreadyTanker || turret.distanceToSqr(priorityTanker) + 4.0D < turret.distanceToSqr(currentTarget)) {
                directTurretToTanker(turret, priorityTanker);
                redirected++;
            }
        }
        return redirected;
    }

    /**
     * Pure coordinate selector returning the index of the closest enabled turret within [minRange, maxRange], or -1.
     */
    public static int selectThrowerTurretIndex(
            double jx,
            double jy,
            double jz,
            double[] tx,
            double[] ty,
            double[] tz,
            boolean[] aliveAndEnabled,
            double minRange,
            double maxRange
    ) {
        if (tx == null || ty == null || tz == null || aliveAndEnabled == null || maxRange <= 0.0D) {
            return -1;
        }
        int n = Math.min(Math.min(tx.length, ty.length), Math.min(tz.length, aliveAndEnabled.length));
        double minSq = Math.max(0.0D, minRange) * Math.max(0.0D, minRange);
        double maxSq = maxRange * maxRange;
        int bestIdx = -1;
        double bestDistSq = Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            if (!aliveAndEnabled[i]) {
                continue;
            }
            double dx = tx[i] - jx;
            double dy = ty[i] - jy;
            double dz = tz[i] - jz;
            double dSq = dx * dx + dy * dy + dz * dz;
            if (dSq >= minSq && dSq <= maxSq && dSq < bestDistSq) {
                bestDistSq = dSq;
                bestIdx = i;
            }
        }
        return bestIdx;
    }

    /**
     * Pure coordinate selector returning the index of the closest eligible player within [minRange, maxRange]
     * (or elevated >= 1.5 blocks above the Juggernaut within maxRange), or -1.
     */
    public static int selectThrowerPlayerIndex(
            double jx,
            double jy,
            double jz,
            double[] px,
            double[] py,
            double[] pz,
            boolean[] eligible,
            double minRange,
            double maxRange
    ) {
        if (px == null || py == null || pz == null || eligible == null || maxRange <= 0.0D) {
            return -1;
        }
        int n = Math.min(Math.min(px.length, py.length), Math.min(pz.length, eligible.length));
        double minSq = Math.max(0.0D, minRange) * Math.max(0.0D, minRange);
        double maxSq = maxRange * maxRange;
        int bestIdx = -1;
        double bestDistSq = Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            if (!eligible[i]) {
                continue;
            }
            double dx = px[i] - jx;
            double dy = py[i] - jy;
            double dz = pz[i] - jz;
            double dSq = dx * dx + dy * dy + dz * dz;
            boolean elevated = py[i] >= jy + 1.5D;
            if ((dSq >= minSq || elevated) && dSq <= maxSq && dSq < bestDistSq) {
                bestDistSq = dSq;
                bestIdx = i;
            }
        }
        return bestIdx;
    }

    /**
     * Selects a throw target for a Juggernaut within [minRange, maxRange]:
     * strictly prioritizes enabled TACZ turrets ahead of players.
     */
    public static LivingEntity selectThrowerTarget(
            Mob juggernaut,
            List<? extends Mob> turrets,
            List<? extends LivingEntity> players,
            double minRange,
            double maxRange
    ) {
        if (juggernaut == null || maxRange <= 0.0D) {
            return null;
        }
        double minSq = Math.max(0.0D, minRange) * Math.max(0.0D, minRange);
        double maxSq = maxRange * maxRange;

        Mob bestTurret = null;
        double bestTurretDistSq = Double.MAX_VALUE;
        if (turrets != null) {
            for (Mob turret : turrets) {
                if (turret == null || !turret.isAlive() || turret.isRemoved() || !isTurretEnabled(turret)) {
                    continue;
                }
                double dSq = juggernaut.distanceToSqr(turret);
                if (dSq >= minSq && dSq <= maxSq && dSq < bestTurretDistSq) {
                    bestTurretDistSq = dSq;
                    bestTurret = turret;
                }
            }
        }
        if (bestTurret != null) {
            return bestTurret;
        }

        LivingEntity bestPlayer = null;
        double bestPlayerDistSq = Double.MAX_VALUE;
        if (players != null) {
            for (LivingEntity player : players) {
                if (player == null || !player.isAlive() || player.isRemoved()) {
                    continue;
                }
                if (player instanceof ServerPlayer sp && !ChunkHeatManager.isEligibleHordeTargetPlayer(sp)) {
                    continue;
                }
                double dSq = juggernaut.distanceToSqr(player);
                boolean elevated = player.getY() >= juggernaut.getY() + 1.5D;
                if ((dSq >= minSq || elevated) && dSq <= maxSq && dSq < bestPlayerDistSq) {
                    bestPlayerDistSq = dSq;
                    bestPlayer = player;
                }
            }
        }
        return bestPlayer;
    }

    /**
     * Computes an arcing ballistic launch vector from {@code from} to {@code to} under Minecraft gravity.
     */
    public static Vec3 computeBallisticThrowVelocity(Vec3 from, Vec3 to) {
        if (from == null || to == null) {
            return new Vec3(0.0D, 0.5D, 0.0D);
        }
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double horiz = Math.hypot(dx, dz);
        double flightTicks = Math.max(16.0D, Math.min(36.0D, horiz * 1.15D));
        double vx = (dx / flightTicks) * 1.12D;
        double vz = (dz / flightTicks) * 1.12D;
        double vy = (dy / flightTicks) + (0.5D * 0.08D * flightTicks) + 0.15D;
        vy = Math.max(0.42D, Math.min(1.35D, vy));
        return new Vec3(vx, vy, vz);
    }

    /**
     * Returns true if a plain horde zombie can be converted into a [CRAWLER] projectile by a Juggernaut.
     */
    public static boolean canBeConvertedToThrowAmmo(Collection<String> tags) {
        if (tags == null || !tags.contains("za_horde_mob")) {
            return false;
        }
        return !tags.contains(TANKER_TAG)
                && !tags.contains(KNIGHT_TAG)
                && !tags.contains(KNIGHT_MOUNT_TAG)
                && !tags.contains(JUGGERNAUT_TAG)
                && !tags.contains(ENGINEER_TAG)
                && !tags.contains(BLOODMOON_WARDEN_TAG)
                && !tags.contains(THROWN_PROJECTILE_TAG)
                && !tags.contains(SAPPER_FUSE_TAG);
    }

    /**
     * Converts a plain horde zombie into a fast [CRAWLER] unit with Speed II and 0.75x scale.
     */
    public static void convertHordeZombieToCrawler(Zombie zombie) {
        if (zombie == null || !zombie.isAlive()) {
            return;
        }
        zombie.addTag("za_horde_mob");
        zombie.addTag(CRAWLER_TAG);
        zombie.addEffect(new MobEffectInstance(MobEffects.SPEED, 7200, 1));
        applySpecialMobIndicator(zombie);
    }

    /**
     * Advances a thrown Sapper's 50-tick landing fuse by 1 tick.
     *
     * @return true when the fuse hits 0 and the Sapper should detonate.
     */
    public static boolean stepThrownSapperFuse(ThrownSapperFuseState state, boolean landedNow) {
        if (state == null) {
            return false;
        }
        if (!state.landed) {
            state.airborneTicks++;
            if (landedNow || state.airborneTicks >= 80) {
                state.landed = true;
            } else {
                return false;
            }
        }
        state.remainingFuseTicks--;
        return state.remainingFuseTicks <= 0;
    }

    /**
     * Every 200 ticks (10s) per alive Juggernaut, grabs a nearby [CRAWLER], [SAPPER], or plain horde zombie
     * within 5 blocks and hurls it along a ballistic arc toward the highest-priority turret or player target.
     */
    public static boolean tickJuggernautThrower(
            ServerLevel level,
            Zombie juggernaut,
            List<? extends Mob> turrets,
            long currentTick
    ) {
        if (level == null || juggernaut == null || !juggernaut.isAlive() || juggernaut.isRemoved()) {
            return false;
        }
        if (Math.floorMod(currentTick + juggernaut.getId(), 20L) != 0L) {
            return false;
        }
        int interval = ZAConfigZDay.juggernautThrowIntervalTicks > 0
                ? ZAConfigZDay.juggernautThrowIntervalTicks
                : DEFAULT_JUGGERNAUT_THROW_INTERVAL_TICKS;
        long lastThrow = JUGGERNAUT_LAST_THROW_TICK.getOrDefault(juggernaut.getUUID(), -999999L);
        if ((currentTick - lastThrow) < interval) {
            return false;
        }

        double minRange = ZAConfigZDay.juggernautThrowMinRange >= 0.0D
                ? ZAConfigZDay.juggernautThrowMinRange
                : DEFAULT_JUGGERNAUT_THROW_MIN_RANGE;
        double maxRange = ZAConfigZDay.juggernautThrowMaxRange > minRange
                ? ZAConfigZDay.juggernautThrowMaxRange
                : DEFAULT_JUGGERNAUT_THROW_MAX_RANGE;

        LivingEntity target = selectThrowerTarget(juggernaut, turrets, level.players(), minRange, maxRange);
        if (target == null) {
            return false;
        }

        double grabRadiusSq = JUGGERNAUT_AMMO_GRAB_RADIUS * JUGGERNAUT_AMMO_GRAB_RADIUS;
        List<Zombie> candidates = level.getEntitiesOfClass(
                Zombie.class,
                juggernaut.getBoundingBox().inflate(JUGGERNAUT_AMMO_GRAB_RADIUS),
                z -> z != null && z != juggernaut && z.isAlive() && !z.isRemoved()
                        && !z.isPassenger()
                        && juggernaut.distanceToSqr(z) <= grabRadiusSq
        );
        if (candidates.isEmpty()) {
            return false;
        }

        Zombie specialAmmo = null;
        double specialDistSq = Double.MAX_VALUE;
        Zombie plainAmmo = null;
        double plainDistSq = Double.MAX_VALUE;

        for (Zombie cand : candidates) {
            Collection<String> tags = cand.entityTags();
            if (tags.contains(THROWN_PROJECTILE_TAG) || tags.contains(SAPPER_FUSE_TAG)) {
                continue;
            }
            double dSq = juggernaut.distanceToSqr(cand);
            if (tags.contains(CRAWLER_TAG) || tags.contains(SAPPER_TAG)) {
                if (dSq < specialDistSq) {
                    specialDistSq = dSq;
                    specialAmmo = cand;
                }
            } else if (canBeConvertedToThrowAmmo(tags) && dSq < plainDistSq) {
                plainDistSq = dSq;
                plainAmmo = cand;
            }
        }

        Zombie ammo = specialAmmo != null ? specialAmmo : plainAmmo;
        if (ammo == null) {
            return false;
        }
        if (specialAmmo == null) {
            convertHordeZombieToCrawler(ammo);
        }

        JUGGERNAUT_LAST_THROW_TICK.put(juggernaut.getUUID(), currentTick);
        ammo.setPos(juggernaut.getX(), juggernaut.getY() + 1.2D, juggernaut.getZ());
        Vec3 velocity = computeBallisticThrowVelocity(
                ammo.position(),
                target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D)
        );
        ammo.setDeltaMovement(velocity);
        ammo.needsSync = true;
        ammo.removeTag(THROWN_LANDED_TAG);
        ammo.addTag(THROWN_PROJECTILE_TAG);
        ammo.setTarget(target);

        if (ammo.entityTags().contains(SAPPER_TAG)) {
            int fuseTicks = ZAConfigZDay.thrownSapperFuseTicks > 0
                    ? ZAConfigZDay.thrownSapperFuseTicks
                    : DEFAULT_THROWN_SAPPER_FUSE_TICKS;
            ammo.addTag(SAPPER_FUSE_TAG);
            ARMED_THROWN_SAPPERS.put(ammo.getUUID(), new ThrownSapperFuseState(fuseTicks));
        }

        try {
            juggernaut.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
            level.playSound(null, juggernaut.getX(), juggernaut.getY(), juggernaut.getZ(),
                    SoundEvents.IRON_GOLEM_ATTACK, SoundSource.HOSTILE, 1.6F, 0.85F);
            level.playSound(null, juggernaut.getX(), juggernaut.getY(), juggernaut.getZ(),
                    SoundEvents.WITHER_SHOOT, SoundSource.HOSTILE, 0.8F, 1.2F);
        } catch (Throwable ignored) {
        }
        return true;
    }

    /**
     * Ticks armed thrown TNT Sappers, emitting smoke/hiss particles on landing and detonating after 50 ticks.
     */
    public static int tickThrownSappers(ServerLevel level, List<? extends Mob> sappers) {
        if (level == null || sappers == null || sappers.isEmpty()) {
            return 0;
        }
        int detonated = 0;
        for (Mob sapper : sappers) {
            if (sapper == null || !sapper.isAlive() || sapper.isRemoved()) {
                continue;
            }
            int defaultFuse = ZAConfigZDay.thrownSapperFuseTicks > 0
                    ? ZAConfigZDay.thrownSapperFuseTicks
                    : DEFAULT_THROWN_SAPPER_FUSE_TICKS;
            ThrownSapperFuseState state = ARMED_THROWN_SAPPERS.computeIfAbsent(
                    sapper.getUUID(),
                    id -> new ThrownSapperFuseState(defaultFuse)
            );
            boolean wasLanded = state.landed;
            boolean landedNow = sapper.onGround() || sapper.entityTags().contains(THROWN_LANDED_TAG);
            boolean shouldExplode = stepThrownSapperFuse(state, landedNow);

            if (!wasLanded && state.landed) {
                try {
                    level.playSound(null, sapper.getX(), sapper.getY(), sapper.getZ(),
                            SoundEvents.TNT_PRIMED, SoundSource.HOSTILE, 1.5F, 1.0F);
                } catch (Throwable ignored) {
                }
            }
            if (shouldExplode) {
                ARMED_THROWN_SAPPERS.remove(sapper.getUUID());
                try {
                    double ex = sapper.getX();
                    double ey = sapper.getY() + 0.5D;
                    double ez = sapper.getZ();
                    sapper.discard();
                    level.explode(sapper, ex, ey, ez, 2.8F, Level.ExplosionInteraction.TNT);
                    detonated++;
                } catch (Throwable ignored) {
                }
            } else if (state.landed && (state.remainingFuseTicks % 4 == 0)) {
                try {
                    level.sendParticles(ParticleTypes.SMOKE, sapper.getX(), sapper.getY() + 1.0D, sapper.getZ(),
                            2, 0.15D, 0.2D, 0.15D, 0.02D);
                    if (state.remainingFuseTicks % 16 == 0) {
                        level.playSound(null, sapper.getX(), sapper.getY(), sapper.getZ(),
                                SoundEvents.TNT_PRIMED, SoundSource.HOSTILE, 1.2F, 1.15F);
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        return detonated;
    }
}
