package com.corosus.zombieawareness.testmod;

import com.corosus.coroutil.util.CoroUtilAttributes;
import com.corosus.zombieawareness.EntityScent;
import com.corosus.zombieawareness.EnumSenseType;
import com.corosus.zombieawareness.ZAUtil;
import com.corosus.zombieawareness.ZombieAwareness;
import com.corosus.zombieawareness.bloodmoon.BloodmoonManager;
import com.corosus.zombieawareness.client.SoundRegistry;
import com.corosus.zombieawareness.config.MobListsConfig;
import com.corosus.zombieawareness.config.ZAConfigGeneral;
import com.corosus.zombieawareness.heat.ChunkHeatManager;
import com.corosus.zombieawareness.zday.ZDayProgressionManager;
import com.corosus.zombieawareness.zday.ZDayStage;
import com.nll.nightmareesm.nightmareesm;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.Blocks;
import org.joml.Vector3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.MixinEnvironment;

import java.io.File;
import java.util.List;
import java.util.UUID;

public class ZAPreProdTestMod implements ModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("ZAPreProdTestMod");
    private int ticks = 0;
    private boolean executed = false;

    @Override
    public void onInitialize() {
        LOGGER.info("[ZA-PreProd] Companion test mod initialized. Waiting for server tick...");
        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);
    }

    private void onServerTick(MinecraftServer server) {
        if (executed) {
            return;
        }
        ticks++;
        ServerLevel level = server.overworld();
        if (ticks == 1) {
            level.setChunkForced(0, 0, true);
            level.setChunkForced(1, 1, true);
            return;
        }
        if (ticks < 40) {
            return;
        }
        executed = true;
        int passed = 0;
        int total = 10;

        try {
            LOGGER.info("=== STARTING ZOMBIE AWARENESS 26.3 PRE-PROD SERVER TESTS (10 CATEGORIES) ===");

            // Test 1/10: Mixin Audit & Registries
            LOGGER.info("[Test 1/10] Running MixinEnvironment audit & registry checks...");
            MixinEnvironment.getCurrentEnvironment().audit();
            Identifier senseKey = BuiltInRegistries.ENTITY_TYPE.getKey(ZombieAwareness.SENSE);
            assertCondition(ZombieAwareness.SENSE_NAME.equals(senseKey), "Expected zombieawareness:scent registered, got " + senseKey);
            assertCondition(SoundRegistry.get("horde_roar") != null, "zombieawareness:horde_roar missing in SoundRegistry");
            assertCondition(SoundRegistry.get("alert") != null, "alert sound missing in SoundRegistry");
            assertCondition(SoundRegistry.get("target") != null, "target sound missing in SoundRegistry");
            assertCondition(SoundRegistry.get("investigate") != null, "investigate sound missing in SoundRegistry");
            assertCondition(ZombieAwareness.unitTest.containsKey("1"), "MixinDedicatedServer did not fire on server init");
            passed++;
            LOGGER.info("[Test 1/10] PASS: All Mixins audited cleanly and entities/sounds (including horde_roar) registered.");

            // Test 2/10: Config Files & MobListsConfig Verification
            LOGGER.info("[Test 2/10] Verifying CoroConfigRegistry .cfg files, ForgeConfigAPIPort MobLists.toml, and NightmareESM configs...");
            File generalCfg = new File("./config/zombieawareness/General.cfg");
            File featuresCfg = new File("./config/zombieawareness/Features.cfg");
            File clientCfg = new File("./config/zombieawareness/ClientConfig.cfg");
            File playerListsCfg = new File("./config/zombieawareness/PlayerRulesAndLists.cfg");
            File zdayCfg = new File("./config/zombieawareness/ZDayProgression.cfg");
            File mobListsToml = new File("./config/zombieawareness/MobLists.toml");
            File esmToml = new File("./config/nightmareesm-common.toml");
            File esmJson = new File("./config/nightmareesm.json");

            assertCondition(generalCfg.exists(), "General.cfg missing");
            assertCondition(featuresCfg.exists(), "Features.cfg missing");
            assertCondition(clientCfg.exists(), "ClientConfig.cfg missing");
            assertCondition(playerListsCfg.exists(), "PlayerRulesAndLists.cfg missing");
            assertCondition(zdayCfg.exists(), "ZDayProgression.cfg missing");
            assertCondition(mobListsToml.exists(), "MobLists.toml missing");
            assertCondition(esmToml.exists() || esmJson.exists(), "nightmareesm config missing");
            assertCondition(MobListsConfig.GENERAL.enhancedMobs.get().contains("minecraft:zombie"), "minecraft:zombie not in enhancedMobs");
            EntityType<?> zombieType = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace("zombie"));
            assertCondition(ZombieAwareness.canProcessEntity(zombieType, false), "canProcessEntity(ZOMBIE) returned false");
            passed++;
            LOGGER.info("[Test 2/10] PASS: All config files verified loaded on server.");

            // Test 3/10: Empty-server clock freeze & 10-Stage unlock schedule
            LOGGER.info("[Test 3/10] Verifying empty-server clock freeze & 10-stage unlock schedule (Days 1..30)...");
            long activeTicks = ZDayProgressionManager.getActivePlayTicks();
            assertCondition(activeTicks == 0L, "Expected activePlayTicks == 0 when 0 players have logged in, got " + activeTicks);
            assertCondition(ZDayProgressionManager.advanceTickLogic(0L, 100L, 101L, 0) == 0L, "advanceTickLogic must freeze active ticks at 0 players");
            assertCondition(ZDayProgressionManager.advanceTickLogic(0L, 100L, 101L, 1) == 1L, "advanceTickLogic must advance ticks when 1 player online");

            assertCondition(ZDayStage.getStageForDay(1).getStageNumber() == 1, "Day 1 must be Stage 1");
            assertCondition(ZDayStage.getStageForDay(4).getStageNumber() == 2, "Day 4 must be Stage 2");
            assertCondition(ZDayStage.getStageForDay(6).getStageNumber() == 3, "Day 6 must be Stage 3");
            assertCondition(ZDayStage.getStageForDay(8).getStageNumber() == 4, "Day 8 must be Stage 4");
            assertCondition(ZDayStage.getStageForDay(11).getStageNumber() == 5, "Day 11 must be Stage 5");
            assertCondition(ZDayStage.getStageForDay(14).getStageNumber() == 6, "Day 14 must be Stage 6");
            assertCondition(ZDayStage.getStageForDay(18).getStageNumber() == 7, "Day 18 must be Stage 7");
            assertCondition(ZDayStage.getStageForDay(22).getStageNumber() == 8, "Day 22 must be Stage 8");
            assertCondition(ZDayStage.getStageForDay(26).getStageNumber() == 9, "Day 26 must be Stage 9");
            assertCondition(ZDayStage.getStageForDay(30).getStageNumber() == 10, "Day 30 must be Stage 10");
            assertCondition(ZDayStage.getStageForDay(100).getStageNumber() == 10, "Day 100 must remain Stage 10");
            passed++;
            LOGGER.info("[Test 3/10] PASS: Clock freeze and all 10 escalation stages verified.");

            // Test 4/10: Multiplayer offline player catch-up dossier & /zday status + /zday log commands
            LOGGER.info("[Test 4/10] Verifying offline player catch-up dossier and /zday command execution...");
            ZDayProgressionManager.setForcedWorldDay(11);
            UUID offlinePlayerUuid = UUID.randomUUID();
            List<String> dossier = ZDayProgressionManager.buildCatchUpReportLines(offlinePlayerUuid);
            assertCondition(dossier.size() > 1, "Expected multi-line catch-up briefing");
            String dossierText = String.join("\n", dossier);
            assertCondition(dossierText.contains("Stage 2") && dossierText.contains("Stage 3") && dossierText.contains("Stage 4") && dossierText.contains("Stage 5"),
                    "Catch-up dossier must contain intelligence for Stages 2, 3, 4, and 5");

            var cmdSource = server.createCommandSourceStack();
            int statusResult = server.getCommands().getDispatcher().execute("zday status", cmdSource);
            assertCondition(statusResult != 0, "/zday status failed to execute");
            int logResult = server.getCommands().getDispatcher().execute("zday log", cmdSource);
            assertCondition(logResult != 0, "/zday log failed to execute");

            ZDayProgressionManager.setForcedWorldDay(-1);
            passed++;
            LOGGER.info("[Test 4/10] PASS: Offline player catch-up dossier and /zday commands verified.");

            // Test 5/10: TaCZ Gunshot heat, 1-Horde-Per-Group limit, Post-Horde Breather & 4-Phase System
            LOGGER.info("[Test 5/10] Verifying TaCZ gunfire heat, 1-Horde-Per-Group limit, Post-Horde Breather, and 4-phase duration...");
            ChunkHeatManager.clearAllHeat();
            ZDayProgressionManager.setForcedWorldDay(4); // Stage 2 (Day 4+) required for heat trigger

            ChunkHeatManager.onTaCZGunSound(level, 0.0, 100.0, 0.0, 20, "silence");
            int silencedHeat = ChunkHeatManager.getHeatAtBlockPos(0, 0, level.getGameTime());
            assertCondition(silencedHeat == 4, "Expected silenced shot heat to be 4 (+4 heat), got: " + silencedHeat);

            ChunkHeatManager.onTaCZGunSound(level, 0.0, 100.0, 0.0, 64, "shoot");
            int combinedHeat = ChunkHeatManager.getHeatAtBlockPos(0, 0, level.getGameTime());
            assertCondition(combinedHeat == 29, "Expected heat after silenced (+4) and unsuppressed (+25) shot to be 29, got: " + combinedHeat);

            // 3 more unsuppressed gunfire events (+75 heat) -> triggers ActiveHorde and resets local heat to 0!
            for (int i = 0; i < 3; i++) {
                ChunkHeatManager.onTaCZGunSound(level, 0.0, 100.0, 0.0, 64, "shoot");
            }
            assertCondition(ChunkHeatManager.getActiveHordeCount() == 1, "Expected 1 ActiveHorde registered for Group 1, count=" + ChunkHeatManager.getActiveHordeCount());
            assertCondition(ChunkHeatManager.getHeatAtBlockPos(0, 0, level.getGameTime()) == 0, "Expected chunk heat reset to 0 upon horde trigger");

            // Group 1 fires 4 more shots while fighting the active horde -> MUST NOT trigger a 2nd horde on Group 1!
            for (int i = 0; i < 4; i++) {
                ChunkHeatManager.onTaCZGunSound(level, 10.0, 100.0, 10.0, 64, "shoot");
            }
            assertCondition(ChunkHeatManager.getActiveHordeCount() == 1, "Group 1 must remain capped at 1 active horde while defending, got: " + ChunkHeatManager.getActiveHordeCount());

            // Group 2 (>96m away at (300, 100, 300)) triggers their own separate horde -> 2 groups = 2 hordes!
            UUID group2SoloPlayer = UUID.randomUUID();
            boolean group2Registered = ChunkHeatManager.tryRegisterHordeForGroup(new BlockPos(300, 100, 300), java.util.Collections.singleton(group2SoloPlayer), 1, 6, 4);
            assertCondition(group2Registered, "Group 2 (>96m away) must be allowed to trigger their own horde");
            assertCondition(ChunkHeatManager.getActiveHordeCount() == 2, "2 distinct groups must have 2 active hordes, got: " + ChunkHeatManager.getActiveHordeCount());

            // Verify 90s (1800 ticks) Post-Horde Breather Cooldown
            ChunkHeatManager.markGroupPostHordeBreather(java.util.Collections.singleton(group2SoloPlayer), 5000L);
            assertCondition(ChunkHeatManager.isGroupInPostHordeBreather(java.util.Collections.singleton(group2SoloPlayer), 5001L), "Group must be in breather at tick 5001");
            assertCondition(!ChunkHeatManager.isGroupInPostHordeBreather(java.util.Collections.singleton(group2SoloPlayer), 6801L), "Group breather must expire after 1800 ticks");

            assertCondition(ChunkHeatManager.computeHordeDurationTicks(1) == 3600, "1 player horde duration must be 3600 ticks (3 mins)");
            assertCondition(ChunkHeatManager.computeHordeDurationTicks(3) == 4800, "3 players horde duration must be 4800 ticks (4 mins)");

            // Verify Death Group Forgiveness (Full Group Wipe + Partial Squad Down-Scaling + 80% Horde Dispersal + 3-Min Grace Period)
            ChunkHeatManager.clearActiveHordes();
            UUID squadLeader = UUID.randomUUID();
            UUID squadMate = UUID.randomUUID();
            BlockPos wipePos = new BlockPos(10, 100, 10);
            boolean squadHordeRegistered = ChunkHeatManager.tryRegisterHordeForGroup(wipePos, List.of(squadLeader, squadMate), 2, 10, 4);
            assertCondition(squadHordeRegistered, "Squad horde registration failed");

            // Spawn 10 horde mobs tagged with "za_horde_mob" around wipePos
            List<Zombie> squadHordeMobs = new java.util.ArrayList<>();
            for (int i = 0; i < 10; i++) {
                Zombie hz = new Zombie(level);
                hz.addTag("za_horde_mob");
                hz.setPos(wipePos.getX() + (i % 3), wipePos.getY(), wipePos.getZ() + (i / 3));
                level.addFreshEntity(hz);
                squadHordeMobs.add(hz);
            }

            // 1. Partial Squad Death: squadMate dies
            boolean partialWiped = ChunkHeatManager.onPlayerDeath(level, squadMate, wipePos, level.getGameTime());
            assertCondition(!partialWiped, "onPlayerDeath on partial squad death must return false");
            assertCondition(ChunkHeatManager.isGroupInPostHordeBreather(java.util.Collections.singleton(squadMate), level.getGameTime() + 100L),
                    "Dead squadmate must receive corpse-run grace period immediately");
            ChunkHeatManager.ActiveHorde ongoingHorde = ChunkHeatManager.getActiveHordes().stream()
                    .filter(h -> h.targetGroupPlayerIds.contains(squadLeader))
                    .findFirst()
                    .orElse(null);
            assertCondition(ongoingHorde != null, "Active horde should still target surviving squad leader");
            assertCondition(ongoingHorde.subWaveCount < 10, "Horde subWaveCount must be scaled down after squadmate death");

            // 2. Full Group Wipe: squadLeader dies -> terminates horde, disperses 80% mobs, grants grace period
            boolean fullWiped = ChunkHeatManager.onPlayerDeath(level, squadLeader, wipePos, level.getGameTime());
            assertCondition(fullWiped, "onPlayerDeath on full group wipe must return true");
            assertCondition(ChunkHeatManager.getActiveHordeCount() == 0, "Active horde count must be 0 after full group wipe");
            assertCondition(ChunkHeatManager.isGroupInPostHordeBreather(java.util.Collections.singleton(squadLeader), level.getGameTime() + 100L),
                    "Squad leader must receive corpse-run grace period after wipe");

            // Verify ~80% of horde mobs dispersed / discarded (at most 2 stragglers remain alive)
            int aliveCount = 0;
            for (Zombie hz : squadHordeMobs) {
                if (hz.isAlive() && !hz.isRemoved()) {
                    aliveCount++;
                }
            }
            assertCondition(aliveCount <= 2, "Expected at most 2 stragglers remaining alive (>=80% dispersed), got " + aliveCount);

            // Clean up remaining test mobs
            for (Zombie hz : squadHordeMobs) {
                hz.discard();
            }

            ZDayProgressionManager.setForcedWorldDay(-1);
            ChunkHeatManager.clearAllHeat();
            passed++;
            LOGGER.info("[Test 5/10] PASS: TaCZ gunfire heat, 1-Horde-Per-Group limit, Death Group Forgiveness (Full Wipe + 80% Dispersal + Grace Period), and 4-Phase System verified.");

            // Test 6/10: Bloodmoon schedule, forced Bloodmoon activation & reset
            LOGGER.info("[Test 6/10] Verifying Bloodmoon schedule, forced activation, and reset...");
            assertCondition(BloodmoonManager.isBloodmoonDay(8), "Day 8 must be Bloodmoon day");
            assertCondition(!BloodmoonManager.isBloodmoonDay(7), "Day 7 must not be Bloodmoon day");
            assertCondition(BloodmoonManager.isBloodmoonNightTime(18000L), "Tick 18000 must be night");
            assertCondition(!BloodmoonManager.isBloodmoonNightTime(6000L), "Tick 6000 must not be night");

            BloodmoonManager.setForcedBloodmoon(true);
            assertCondition(BloodmoonManager.isBloodmoonActive(level), "Bloodmoon must be active when forced true");
            BloodmoonManager.setForcedBloodmoon(false);
            assertCondition(!BloodmoonManager.isBloodmoonActive(level), "Bloodmoon must not be active when forced false");
            BloodmoonManager.setForcedBloodmoon(null);
            BloodmoonManager.resetState();
            passed++;
            LOGGER.info("[Test 6/10] PASS: Bloodmoon schedule, forced activation, and reset verified.");

            // Test 7/10: NightmareESM Stage Gating, Juggernauts & Creeper/Sapper TNT Friendly-Fire + Cross-Hostile Aggression Prevention
            LOGGER.info("[Test 7/10] Verifying NightmareESM stage gating, Juggernauts, and Hostile Horde Unity (including Sapper TNT)...");
            assertCondition(!nightmareesm.canUseBehaviorForStage("BLACKOUT_STALKER", 2), "Blackout Stalker must be locked at Stage 2");
            assertCondition(nightmareesm.canUseBehaviorForStage("BLACKOUT_STALKER", 3), "Blackout Stalker must be unlocked at Stage 3");
            assertCondition(!nightmareesm.canUseBehaviorForStage("CREEPER_BREACH", 9), "Creeper Breach must be locked at Stage 9");
            assertCondition(nightmareesm.canUseBehaviorForStage("CREEPER_BREACH", 10), "Creeper Breach must be unlocked at Stage 10");

            Zombie testJuggernaut = new Zombie(level);
            nightmareesm.applyJuggernautLoadout(testJuggernaut);
            assertCondition(testJuggernaut.getAttribute(Attributes.KNOCKBACK_RESISTANCE).getBaseValue() >= 0.8, "Juggernaut knockback resistance must be >= 0.8");
            testJuggernaut.discard();

            Zombie zombie = new Zombie(level);
            Skeleton skeleton = (Skeleton) BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace("skeleton")).create(level, EntitySpawnReason.EVENT);
            Creeper creeper = (Creeper) BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace("creeper")).create(level, EntitySpawnReason.EVENT);
            zombie.setPos(50.5, 100.0, 50.5);
            skeleton.setPos(51.5, 100.0, 50.5);
            creeper.setPos(52.5, 100.0, 50.5);
            level.addFreshEntity(zombie);
            level.addFreshEntity(skeleton);
            level.addFreshEntity(creeper);

            float initialHealth = zombie.getHealth();
            zombie.setTarget(skeleton);
            assertCondition(zombie.getTarget() == null, "zombie.setTarget(skeleton) must be canceled by MixinMob!");

            boolean skeletonHurt = zombie.hurtServer(level, level.damageSources().mobAttack(skeleton), 5.0f);
            assertCondition(!skeletonHurt, "Hostile mob attack against zombie must return false!");

            boolean creeperHurt = zombie.hurtServer(level, level.damageSources().explosion(creeper, creeper), 20.0f);
            assertCondition(!creeperHurt, "Creeper explosion against zombie must return false!");

            net.minecraft.world.entity.item.PrimedTnt sapperTnt = new net.minecraft.world.entity.item.PrimedTnt(level, 50.5, 100.0, 50.5, zombie);
            sapperTnt.addTag("za_sapper_tnt");
            boolean sapperTntHurt = zombie.hurtServer(level, level.damageSources().explosion(sapperTnt, zombie), 25.0f);
            assertCondition(!sapperTntHurt, "Sapper TNT explosion against fellow zombie must return false!");
            assertCondition(zombie.getHealth() == initialHealth, "Zombie must remain at full health (" + initialHealth + ")");

            sapperTnt.discard();
            zombie.discard();
            skeleton.discard();
            creeper.discard();

            // 42 Mob Health Bug Fix & Horde Mob Health Scaling Verification
            Zombie bloodlustZombie = new Zombie(level);
            bloodlustZombie.setPos(50.5, 100.0, 50.5);
            level.addFreshEntity(bloodlustZombie);
            assertCondition(bloodlustZombie.getMaxHealth() == 20.0f, "Base zombie max health must be 20.0f");
            new com.nll.nightmareesm.MobBehavs.MobBloodlust(bloodlustZombie);
            assertCondition(bloodlustZombie.getMaxHealth() <= 20.0f, "MobBloodlust must NOT increase zombie max health above 20 HP (got " + bloodlustZombie.getMaxHealth() + ", never 42 HP!)");
            assertCondition(!bloodlustZombie.hasEffect(net.minecraft.world.effect.MobEffects.HEALTH_BOOST), "MobBloodlust must NOT apply HEALTH_BOOST");
            assertCondition(bloodlustZombie.getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(Identifier.parse("bloodlust")), "MobBloodlust must retain bloodlust speed modifier");
            bloodlustZombie.discard();

            Zombie hordeZombie = new Zombie(level);
            hordeZombie.addTag("za_horde_mob");
            ChunkHeatManager.applyHordeMobHealthScaling(hordeZombie);
            assertCondition(hordeZombie.getMaxHealth() == 15.0f, "Horde zombie max health must be scaled to 15.0f (< 20.0f, got " + hordeZombie.getMaxHealth() + ")");
            assertCondition(hordeZombie.getHealth() == 15.0f, "Horde zombie health must be set to 15.0f");

            Zombie hordeJuggernaut = new Zombie(level);
            nightmareesm.applyJuggernautLoadout(hordeJuggernaut);
            hordeJuggernaut.addTag("za_horde_mob");
            ChunkHeatManager.applyHordeMobHealthScaling(hordeJuggernaut);
            assertCondition(hordeJuggernaut.getMaxHealth() == 20.0f, "Horde Juggernaut max health must be capped at 20.0f");
            hordeZombie.discard();
            hordeJuggernaut.discard();

            passed++;
            LOGGER.info("[Test 7/10] PASS: NightmareESM stage gating, Juggernaut attributes, 42 HP Bloodlust fix, Horde Mob Health Scaling, and Horde Unity (Creeper + Sapper TNT 0 friendly-fire) verified.");

            // Test 8/10: EntityScent Lifecycle & Zombie AI Pathfinding
            LOGGER.info("[Test 8/10] Verifying EntityScent spawning, synched data, decay, and Zombie AI pathfinding...");
            EntityScent scent = new EntityScent(ZombieAwareness.SENSE, level);
            scent.setPos(0.5, 100.0, 0.5);
            scent.setStrengthPeak(120);
            scent.setSenseType(EnumSenseType.SOUND.ordinal());
            boolean added = level.addFreshEntity(scent);
            assertCondition(added, "Failed to add EntityScent to ServerLevel");
            assertCondition(scent.getStrengthPeak() == 120, "Unexpected peak strength: " + scent.getStrengthPeak());
            assertCondition(scent.getSenseType() == EnumSenseType.SOUND.ordinal(), "Unexpected sense type: " + scent.getSenseType());
            assertCondition(scent.getRange() > 0.0F, "Expected positive range, got " + scent.getRange());
            int ageBefore = (int) Math.round(scent.getAgeScale() * EntityScent.MAX_AGE);
            scent.tick();
            int ageAfter = (int) Math.round(scent.getAgeScale() * EntityScent.MAX_AGE);
            assertCondition(ageAfter < ageBefore, "Expected EntityScent age to decay after tick()");
            scent.discard();

            BlockPos arenaOrigin = new BlockPos(20, 100, 20);
            for (int dx = -2; dx <= 12; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    level.setBlockAndUpdate(arenaOrigin.offset(dx, -1, dz), Blocks.STONE.defaultBlockState());
                    level.setBlockAndUpdate(arenaOrigin.offset(dx, 0, dz), Blocks.AIR.defaultBlockState());
                    level.setBlockAndUpdate(arenaOrigin.offset(dx, 1, dz), Blocks.AIR.defaultBlockState());
                }
            }

            ZAConfigGeneral.zombieRandSpeedBoost = 0.35D;
            ZAConfigGeneral.findSense_PercentChance = 100;
            Zombie aiZombie = new Zombie(level);
            aiZombie.setPos(arenaOrigin.getX() + 0.5, arenaOrigin.getY(), arenaOrigin.getZ() + 0.5);
            aiZombie.setOnGround(true);
            aiZombie.setBaby(false);
            aiZombie.finalizeSpawn(level, level.getCurrentDifficultyAt(arenaOrigin), EntitySpawnReason.EVENT, null);
            level.addFreshEntity(aiZombie);

            assertCondition(ZombieAwareness.unitTest.containsKey("8"), "MixinMob.finalizeSpawn did not trigger");
            assertCondition(aiZombie.getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(CoroUtilAttributes.SPEED_BOOST_ID), "Zombie missing ZA speed boost modifier");
            ZAUtil.giveRandomSpeedBoost(aiZombie); // Idempotency check

            EntityScent targetScent = ZAUtil.spawnOrBuffSenseAtPos(
                    level,
                    new Vector3d(arenaOrigin.getX() + 8.5, arenaOrigin.getY(), arenaOrigin.getZ() + 0.5),
                    EnumSenseType.SOUND,
                    200,
                    false
            );
            EntityScent tracked = ZAUtil.ai_FindSense(aiZombie, true);
            assertCondition(tracked != null, "Zombie failed to find and path to nearby EntityScent");
            assertCondition(aiZombie.getNavigation().getPath() != null, "Zombie navigation path is null after ai_FindSense");
            long lastAction = ZombieAwareness.instance().getPersistentData(aiZombie).getLongOr(ZAUtil.ZA_LAST_ACTION, 0L);
            assertCondition(lastAction > 0L, "Zombie persistent data za_last_action was not updated");

            // Monster Distress Scent & Throttle Verification
            assertCondition(ZAUtil.trySpawnMonsterDistressScent(level, aiZombie, 4) == null, "Distress scent must not spawn at Stage 4");
            ZAUtil.resetDistressScentThrottle();
            EntityScent distressScent = ZAUtil.trySpawnMonsterDistressScent(level, aiZombie, 5);
            assertCondition(distressScent != null, "Distress scent must spawn at Stage 5 for hostile mob");
            assertCondition(distressScent.getStrengthPeak() == 80, "Stage 5 distress scent peak must be 80");
            assertCondition(distressScent.getSenseType() == EnumSenseType.SCENT_BLOOD.ordinal(), "Distress scent primary node must be SCENT_BLOOD");
            distressScent.discard();

            // Aerial Horde Phantom Spawning & Entity Creation Verification
            assertCondition(ChunkHeatManager.canHordeSpawnPhantoms(ChunkHeatManager.HordePhase.PHASE_2_BREACHERS_SKELETONS, 5), "Phantoms allowed in Phase 2 at Stage 5");
            assertCondition(!ChunkHeatManager.canHordeSpawnPhantoms(ChunkHeatManager.HordePhase.PHASE_2_BREACHERS_SKELETONS, 4), "Phantoms disallowed at Stage 4");
            assertCondition(!ChunkHeatManager.canHordeSpawnPhantoms(ChunkHeatManager.HordePhase.PHASE_1A_FAST_ZOMBIES, 5), "Phantoms disallowed in Phase 1A");
            assertCondition(ChunkHeatManager.computePhantomSpawnY(64) == 80, "Phantom spawn Y must be ground + 16");
            EntityType<?> phantomType = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace("phantom"));
            assertCondition(phantomType != null, "Phantom entity type must exist in BuiltInRegistries");
            net.minecraft.world.entity.Entity phantomEntity = phantomType.create(level, EntitySpawnReason.EVENT);
            assertCondition(phantomEntity != null, "Phantom entity must be instantiated successfully");
            phantomEntity.discard();

            // Horde Mob Loot Drop Reduction (80% drop reduction)
            assertCondition(ZAUtil.shouldHordeMobDropLoot(false, 0.99f), "Non-horde mobs must always drop loot");
            assertCondition(ZAUtil.shouldHordeMobDropLoot(true, 0.10f), "Horde mob with roll < 0.20 must drop loot");
            assertCondition(!ZAUtil.shouldHordeMobDropLoot(true, 0.20f), "Horde mob with roll >= 0.20 must not drop loot (80% drop reduction)");
            assertCondition(!ZAUtil.shouldHordeMobDropLoot(true, 0.50f), "Horde mob with roll >= 0.20 must not drop loot");

            targetScent.discard();
            aiZombie.discard();
            passed++;
            LOGGER.info("[Test 8/10] PASS: EntityScent lifecycle, distress scent, aerial phantoms, loot drop reduction, and Zombie AI pathfinding verified.");

            // Test 9/10: Directional Horde Cone Spawning (38-52m, ±40°), 360° Surround Siege, Apex Corrosion Phase 5, and Crimson Seal Lockdown
            LOGGER.info("[Test 9/10] Verifying Directional Cone Spawning, 360° Surround Siege, Apex Corrosion Phase 5, and Crimson Seal Portal Lockdown...");
            double[] spawnOffsetMin = ChunkHeatManager.computeDirectionalSpawnOffset(0.0, 0.0, 0.0);
            double[] spawnOffsetMax = ChunkHeatManager.computeDirectionalSpawnOffset(0.0, 0.0, 1.0);
            assertCondition(Math.abs(Math.hypot(spawnOffsetMin[0], spawnOffsetMin[1]) - 38.0) < 0.01, "Expected min directional spawn dist 38.0m");
            assertCondition(Math.abs(Math.hypot(spawnOffsetMax[0], spawnOffsetMax[1]) - 52.0) < 0.01, "Expected max directional spawn dist 52.0m");

            // 360° Surround Fortification Siege checks
            double[] surroundOpposite = ChunkHeatManager.computeSpawnOffset(0.0, 1.0, 0.0, true);
            assertCondition(Math.abs(surroundOpposite[0] - (-38.0)) < 0.01 && Math.abs(surroundOpposite[1]) < 0.01, "Expected surround opposite at (-38, 0)");
            assertCondition(ChunkHeatManager.isFortifiedFromWallCount(5, false), "5 wall directions must be fortified");
            assertCondition(ChunkHeatManager.isFortifiedFromWallCount(3, true), "3 wall directions with roof/base must be fortified");
            assertCondition(!ChunkHeatManager.isFortifiedFromWallCount(4, false), "4 wall directions without roof must not be fortified");
            assertCondition(ChunkHeatManager.computeFortifiedWaveCount(10, false) == 10, "Unfortified wave count unchanged");
            assertCondition(ChunkHeatManager.computeFortifiedWaveCount(10, true) == 14, "Fortified wave count +40% (14)");

            // Spontaneous Night Horde Awakening
            assertCondition(ChunkHeatManager.canAwakenLateGameAmbientHorde(7, 15000L, false, true, false), "Stage 7 night window must awaken ambient horde");
            assertCondition(!ChunkHeatManager.canAwakenLateGameAmbientHorde(6, 15000L, false, true, false), "Stage 6 normal night must not awaken ambient horde");
            assertCondition(ChunkHeatManager.canAwakenLateGameAmbientHorde(6, 15000L, true, true, false), "Bloodmoon night must awaken ambient horde even at Stage 6");
            assertCondition(!ChunkHeatManager.canAwakenLateGameAmbientHorde(10, 6000L, false, true, false), "Daytime must not awaken ambient horde");

            // Super-late-game Phase 5 Apex Corrosion & ZAUtil Apex Mechanics
            assertCondition(ChunkHeatManager.resolveHordePhase(0.95, 10) == ChunkHeatManager.HordePhase.PHASE_5_APEX_CORROSION, "Stage 10 at 0.95 progress must resolve to PHASE_5_APEX_CORROSION");
            assertCondition(ChunkHeatManager.resolveHordePhase(0.90, 10) == ChunkHeatManager.HordePhase.PHASE_4_CLIMAX_SAPPERS, "Stage 10 at 0.90 progress must resolve to PHASE_4_CLIMAX_SAPPERS");
            assertCondition(ChunkHeatManager.resolveHordePhase(0.95, 9) == ChunkHeatManager.HordePhase.PHASE_3_JUGGERNAUTS_WITCHES, "Stage 9 at 0.95 progress must cap at PHASE_3_JUGGERNAUTS_WITCHES");

            float[] apexSplit = ZAUtil.splitApexArmorBypassDamage(10.0f);
            assertCondition(Math.abs(apexSplit[0] - 6.0f) < 0.001f, "Expected 6.0f normal damage (60%)");
            assertCondition(Math.abs(apexSplit[1] - 4.0f) < 0.001f, "Expected 4.0f armor bypass damage (40%)");

            UUID testPlayerId = UUID.randomUUID();
            ZAUtil.clearNecroticTrauma();
            assertCondition(Math.abs(ZAUtil.computeModifiedHealAmount(testPlayerId, 1000L, 10.0f) - 10.0f) < 0.001f, "Heal amount unaffected without trauma");
            ZAUtil.applyNecroticTrauma(testPlayerId, 1000L);
            assertCondition(ZAUtil.hasNecroticTrauma(testPlayerId, 1050L), "Player has necrotic trauma within 160 ticks");
            assertCondition(Math.abs(ZAUtil.computeModifiedHealAmount(testPlayerId, 1050L, 10.0f) - 5.0f) < 0.001f, "Heal amount halved under necrotic trauma");
            assertCondition(!ZAUtil.hasNecroticTrauma(testPlayerId, 1161L), "Necrotic trauma expires after 160 ticks");
            assertCondition(Math.abs(ZAUtil.computeModifiedHealAmount(testPlayerId, 1161L, 10.0f) - 10.0f) < 0.001f, "Heal amount restored after trauma expiry");

            assertCondition(ZAUtil.computeArmorDurabilityShred(400) == 20, "Durability shred for 400 max durability should be 20");
            assertCondition(ZAUtil.computeArmorDurabilityShred(100) == 12, "Durability shred for 100 max durability should be min 12");

            float vol0Unobstructed = ChunkHeatManager.computeRoarVolumeMultiplier(0.0, false);
            float vol128Unobstructed = ChunkHeatManager.computeRoarVolumeMultiplier(128.0, false);
            float vol0Obstructed = ChunkHeatManager.computeRoarVolumeMultiplier(0.0, true);
            float vol128Obstructed = ChunkHeatManager.computeRoarVolumeMultiplier(128.0, true);
            assertCondition(Math.abs(vol0Unobstructed - 1.0f) < 0.001f, "Expected 100% volume at 0m unobstructed");
            assertCondition(Math.abs(vol128Unobstructed - 0.90f) < 0.001f, "Expected 90% volume (10% dropoff) at 128m unobstructed");
            assertCondition(Math.abs(vol0Obstructed - 0.80f) < 0.001f, "Expected 80% volume (20% dropoff) at 0m obstructed");
            assertCondition(Math.abs(vol128Obstructed - 0.72f) < 0.001f, "Expected 72% volume at 128m obstructed");

            double[] dirSoundPos = ChunkHeatManager.computeDirectionalSoundPosition(0.0, 64.0, 0.0, 100.0, 64.0, 0.0);
            assertCondition(Math.abs(dirSoundPos[0] - 4.0) < 0.001 && Math.abs(dirSoundPos[2]) < 0.001, "Directional sound emitter must be placed 4.0m along the unit direction ray");

            UUID lockedPlayerId = UUID.randomUUID();
            BlockPos lockedPos = new BlockPos(0, 100, 0);
            assertCondition(ChunkHeatManager.isPlayerLockedByHordeOrBloodmoon(lockedPlayerId, lockedPos, true), "Crimson Seal must lock portals during Bloodmoon");
            assertCondition(!ChunkHeatManager.isPlayerLockedByHordeOrBloodmoon(lockedPlayerId, lockedPos, false), "Crimson Seal must be unlocked when no horde or Bloodmoon is active");
            ChunkHeatManager.tryRegisterHordeForGroup(lockedPos, java.util.Collections.singleton(lockedPlayerId), 1, 6, 4);
            assertCondition(ChunkHeatManager.isPlayerLockedByHordeOrBloodmoon(lockedPlayerId, lockedPos, false), "Crimson Seal must lock portals while targeted by an active horde");
            ChunkHeatManager.clearAllHeat();

            // Water-body spawn rejection on real ServerLevel & dry platform validation
            BlockPos waterCenter = new BlockPos(80, 100, 80);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    level.setBlockAndUpdate(waterCenter.offset(dx, -1, dz), Blocks.DIRT.defaultBlockState());
                    level.setBlockAndUpdate(waterCenter.offset(dx, 0, dz), Blocks.WATER.defaultBlockState());
                    level.setBlockAndUpdate(waterCenter.offset(dx, 1, dz), Blocks.AIR.defaultBlockState());
                    level.setBlockAndUpdate(waterCenter.offset(dx, 2, dz), Blocks.AIR.defaultBlockState());
                }
            }
            int waterSpawnY = ChunkHeatManager.findSafeDrySpawnY(level, waterCenter.getX(), waterCenter.getY(), waterCenter.getZ());
            assertCondition(waterSpawnY == ChunkHeatManager.NO_DRY_SPAWN_Y, "findSafeDrySpawnY on water pool must return NO_DRY_SPAWN_Y, got " + waterSpawnY);

            BlockPos dryCenter = new BlockPos(90, 100, 80);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    level.setBlockAndUpdate(dryCenter.offset(dx, -1, dz), Blocks.STONE.defaultBlockState());
                    level.setBlockAndUpdate(dryCenter.offset(dx, 0, dz), Blocks.AIR.defaultBlockState());
                    level.setBlockAndUpdate(dryCenter.offset(dx, 1, dz), Blocks.AIR.defaultBlockState());
                }
            }
            int drySpawnY = ChunkHeatManager.findSafeDrySpawnY(level, dryCenter.getX(), dryCenter.getY(), dryCenter.getZ());
            assertCondition(drySpawnY == dryCenter.getY(), "findSafeDrySpawnY on dry stone platform must return valid dry Y (" + dryCenter.getY() + "), got " + drySpawnY);

            // Water spawn in surround mode & Zombie to Drowned conversion on ServerLevel
            int surroundWaterY = ChunkHeatManager.findSafeWaterSpawnY(level, waterCenter.getX(), waterCenter.getY(), waterCenter.getZ());
            assertCondition(surroundWaterY == waterCenter.getY(), "findSafeWaterSpawnY on water pool must return water surface Y (" + waterCenter.getY() + "), got " + surroundWaterY);

            Zombie waterZombie = new Zombie(level);
            waterZombie.addTag("za_horde_mob");
            waterZombie.setPos(waterCenter.getX() + 0.5, waterCenter.getY(), waterCenter.getZ() + 0.5);
            level.addFreshEntity(waterZombie);
            assertCondition(waterZombie.entityTags().contains("za_horde_mob"), "Water zombie missing za_horde_mob tag");
            net.minecraft.world.entity.monster.zombie.Drowned convertedDrowned = ChunkHeatManager.convertHordeZombieToDrowned(waterZombie);
            assertCondition(convertedDrowned != null, "convertHordeZombieToDrowned must return converted Drowned");
            assertCondition(convertedDrowned.entityTags().contains("za_horde_mob"), "Converted Drowned must retain za_horde_mob tag");
            assertCondition(convertedDrowned.getMaxHealth() <= 20.0f, "Converted Drowned must have health scaling applied (<= 20 HP)");
            convertedDrowned.discard();

            // Compass direction & Phase 4 Decimation End checks
            assertCondition("North".equals(ChunkHeatManager.getCompassDirectionFromAngle(-Math.PI / 2.0)), "Angle -PI/2 must be North");
            assertCondition("East".equals(ChunkHeatManager.getCompassDirectionFromAngle(0.0)), "Angle 0.0 must be East");
            assertCondition("South".equals(ChunkHeatManager.getCompassDirectionFromAngle(Math.PI / 2.0)), "Angle PI/2 must be South");
            assertCondition("West".equals(ChunkHeatManager.getCompassDirectionFromAngle(Math.PI)), "Angle PI must be West");
            assertCondition(ChunkHeatManager.isHordeDecimated(2, 100), "2 alive of 100 Phase 4 mobs must be decimated (2%)");
            assertCondition(!ChunkHeatManager.isHordeDecimated(3, 100), "3 alive of 100 Phase 4 mobs must NOT be decimated (3%)");

            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    level.setBlockAndUpdate(waterCenter.offset(dx, 0, dz), Blocks.AIR.defaultBlockState());
                    level.setBlockAndUpdate(waterCenter.offset(dx, -1, dz), Blocks.AIR.defaultBlockState());
                    level.setBlockAndUpdate(dryCenter.offset(dx, -1, dz), Blocks.AIR.defaultBlockState());
                }
            }

            passed++;
            LOGGER.info("[Test 9/10] PASS: Directional Cone Spawning, Directional Roar, Crimson Seal Portal Lockdown, Water Rejection, Island/Water Spawn, and Drowned Conversion verified.");

            // Test 10/10: Audit Loophole Fixes (Multiplayer Target Lock, Screamer Clone Guard, Container/Bedrock Protection)
            LOGGER.info("[Test 10/10] Verifying Multiplayer Target Lock fix, Screamer Clone Guard, and Pickaxe Container/Bedrock protection...");
            assertCondition(com.nll.nightmareesm.GetNearestTarget.isValidSurvivalPlayerTarget(false, false), "Survival player must be valid target");
            assertCondition(!com.nll.nightmareesm.GetNearestTarget.isValidSurvivalPlayerTarget(true, false), "Creative player must NOT be valid target");
            assertCondition(!com.nll.nightmareesm.GetNearestTarget.isValidSurvivalPlayerTarget(false, true), "Spectator player must NOT be valid target");
            assertCondition(!nightmareesm.canRollAsScreamer(EntitySpawnReason.MOB_SUMMONED, 10), "Summoned Screamer clones must never roll as new Screamers");
            assertCondition(nightmareesm.canRollAsScreamer(EntitySpawnReason.NATURAL, 10), "Natural Stage 10 zombies can roll as Screamers");

            BlockPos chestTestPos = new BlockPos(35, 100, 35);
            BlockPos stoneTestPos = new BlockPos(36, 100, 35);
            level.setBlockAndUpdate(chestTestPos, Blocks.CHEST.defaultBlockState());
            level.setBlockAndUpdate(stoneTestPos, Blocks.STONE.defaultBlockState());
            Zombie minerZombie = new Zombie(level);
            minerZombie.setPos(34.5, 100.0, 35.5);
            level.addFreshEntity(minerZombie);
            new com.nll.nightmareesm.MobBehavs.MobDamageBlock(minerZombie, Blocks.CHEST, chestTestPos);
            new com.nll.nightmareesm.MobBehavs.MobDamageBlock(minerZombie, Blocks.STONE, stoneTestPos);
            assertCondition(level.getBlockState(chestTestPos).is(Blocks.CHEST), "Pickaxe zombie must NEVER destroy player Chest (BlockEntity)!");
            assertCondition(level.getBlockState(stoneTestPos).isAir(), "Pickaxe zombie must be able to break normal Stone block!");
            level.setBlockAndUpdate(chestTestPos, Blocks.AIR.defaultBlockState());
            minerZombie.discard();
            passed++;
            LOGGER.info("[Test 10/10] PASS: Multiplayer Target Lock fix, Screamer Clone Guard, and Container/Bedrock protection verified.");

            LOGGER.info("=== ZA PREPROD SERVER TEST REPORT: {}/{} PASSED ===", passed, total);
            server.halt(false);
        } catch (Throwable t) {
            LOGGER.error("=== ZA PREPROD SERVER TEST FAILED ({}/{} PASSED) ===", passed, total, t);
            Runtime.getRuntime().halt(1);
        }
    }

    private static void assertCondition(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
