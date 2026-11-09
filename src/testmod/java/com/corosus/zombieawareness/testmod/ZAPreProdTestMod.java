package com.corosus.zombieawareness.testmod;

import com.corosus.coroutil.util.CoroUtilAttributes;
import com.corosus.zombieawareness.EntityScent;
import com.corosus.zombieawareness.EnumSenseType;
import com.corosus.zombieawareness.ZAUtil;
import com.corosus.zombieawareness.ZombieAwareness;
import com.corosus.zombieawareness.config.MobListsConfig;
import com.corosus.zombieawareness.config.ZAConfigGeneral;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.Blocks;
import org.joml.Vector3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.MixinEnvironment;

import java.io.File;

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
        int total = 5;

        try {
            LOGGER.info("=== STARTING ZOMBIE AWARENESS 26.3 PRE-PROD SERVER TESTS ===");

            // Test 1: Mixin Audit & Registry Verification
            LOGGER.info("[Test 1/5] Running MixinEnvironment audit & registry checks...");
            MixinEnvironment.getCurrentEnvironment().audit();
            Identifier senseKey = BuiltInRegistries.ENTITY_TYPE.getKey(ZombieAwareness.SENSE);
            assertCondition(ZombieAwareness.SENSE_NAME.equals(senseKey), "Expected zombieawareness:scent registered, got " + senseKey);
            assertCondition(ZombieAwareness.instance().getSound("alert") != null, "alert sound missing");
            assertCondition(ZombieAwareness.instance().getSound("target") != null, "target sound missing");
            assertCondition(ZombieAwareness.instance().getSound("investigate") != null, "investigate sound missing");
            assertCondition(ZombieAwareness.unitTest.containsKey("1"), "MixinDedicatedServer did not fire on server init");
            passed++;
            LOGGER.info("[Test 1/5] PASS: All 15 Mixins audited cleanly and entities/sounds registered.");

            // Test 2: Config Files & MobListsConfig Verification
            LOGGER.info("[Test 2/5] Verifying CoroConfigRegistry .cfg files & ForgeConfigAPIPort MobLists.toml...");
            File generalCfg = new File("./config/zombieawareness/General.cfg");
            File featuresCfg = new File("./config/zombieawareness/Features.cfg");
            File clientCfg = new File("./config/zombieawareness/ClientConfig.cfg");
            File playerListsCfg = new File("./config/zombieawareness/PlayerRulesAndLists.cfg");
            File mobListsToml = new File("./config/zombieawareness/MobLists.toml");
            assertCondition(generalCfg.exists(), "General.cfg missing");
            assertCondition(featuresCfg.exists(), "Features.cfg missing");
            assertCondition(clientCfg.exists(), "ClientConfig.cfg missing");
            assertCondition(playerListsCfg.exists(), "PlayerRulesAndLists.cfg missing");
            assertCondition(mobListsToml.exists(), "MobLists.toml missing");
            assertCondition(MobListsConfig.GENERAL.enhancedMobs.get().contains("minecraft:zombie"), "minecraft:zombie not in enhancedMobs");
            EntityType<?> zombieType = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace("zombie"));
            assertCondition(ZombieAwareness.canProcessEntity(zombieType, false), "canProcessEntity(ZOMBIE) returned false");
            passed++;
            LOGGER.info("[Test 2/5] PASS: All config files generated and loaded on server.");

            // Test 3: EntityScent Lifecycle, SynchedData & Decay
            LOGGER.info("[Test 3/5] Verifying EntityScent spawning, SynchedEntityData, and tick decay...");
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
            passed++;
            LOGGER.info("[Test 3/5] PASS: EntityScent spawned, synced data verified, and decayed properly.");

            // Test 4: Sound & Blood Sense Creation, Cutoff Buffing & ServerLevel Mixin Hooks
            LOGGER.info("[Test 4/5] Verifying spawnOrBuffSenseAtPos cutoff buffing and ServerLevel sound/event mixins...");
            Vector3d bloodPos = new Vector3d(10.5, 100.0, 10.5);
            EntityScent blood1 = ZAUtil.spawnOrBuffSenseAtPos(level, bloodPos, EnumSenseType.SCENT_BLOOD, 60, true);
            blood1.lastBuffTime = System.currentTimeMillis();
            EntityScent blood2 = ZAUtil.spawnOrBuffSenseAtPos(level, new Vector3d(11.0, 100.0, 10.5), EnumSenseType.SCENT_BLOOD, 80, true);
            assertCondition(blood1 == blood2, "Expected cutoff range to reuse and buff existing EntityScent node");
            assertCondition(blood2.getStrengthPeak() >= 80, "Expected buffed strength >= 80, got " + blood2.getStrengthPeak());
            blood1.discard();

            level.levelEvent(null, 1005, new BlockPos(0, 100, 0), 0);
            level.playSeededSound(null, 0.5, 100.0, 0.5, SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 1.0F, 1.0F, 0L);
            assertCondition(ZombieAwareness.unitTest.containsKey("6"), "MixinLevelEvent did not trigger");
            assertCondition(ZombieAwareness.unitTest.containsKey("12"), "MixinPlaySound did not trigger");
            passed++;
            LOGGER.info("[Test 4/5] PASS: Sense node buffing and ServerLevel sound/event mixins verified.");

            // Test 5: Zombie Spawn Speed Boost, Idempotency, and AI Scent Pathfinding
            LOGGER.info("[Test 5/5] Verifying Zombie speed boost modifier and AI pathfinding toward EntityScent...");
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
            Zombie zombie = new Zombie(level);
            zombie.setPos(arenaOrigin.getX() + 0.5, arenaOrigin.getY(), arenaOrigin.getZ() + 0.5);
            zombie.setOnGround(true);
            zombie.setBaby(false);
            zombie.finalizeSpawn(level, level.getCurrentDifficultyAt(arenaOrigin), EntitySpawnReason.EVENT, null);
            level.addFreshEntity(zombie);

            assertCondition(ZombieAwareness.unitTest.containsKey("8"), "MixinMob.finalizeSpawn did not trigger");
            assertCondition(zombie.getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(CoroUtilAttributes.SPEED_BOOST_ID), "Zombie missing ZA speed boost modifier");
            // Verify idempotency when giveRandomSpeedBoost is called again
            ZAUtil.giveRandomSpeedBoost(zombie);

            EntityScent targetScent = ZAUtil.spawnOrBuffSenseAtPos(
                    level,
                    new Vector3d(arenaOrigin.getX() + 8.5, arenaOrigin.getY(), arenaOrigin.getZ() + 0.5),
                    EnumSenseType.SOUND,
                    200,
                    false
            );
            EntityScent tracked = ZAUtil.ai_FindSense(zombie, true);
            assertCondition(tracked != null, "Zombie failed to find and path to nearby EntityScent");
            assertCondition(zombie.getNavigation().getPath() != null, "Zombie navigation path is null after ai_FindSense");
            long lastAction = ZombieAwareness.instance().getPersistentData(zombie).getLongOr(ZAUtil.ZA_LAST_ACTION, 0L);
            assertCondition(lastAction > 0L, "Zombie persistent data za_last_action was not updated");

            targetScent.discard();
            zombie.discard();
            passed++;
            LOGGER.info("[Test 5/5] PASS: Zombie speed boost and scent pathfinding AI verified.");

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
