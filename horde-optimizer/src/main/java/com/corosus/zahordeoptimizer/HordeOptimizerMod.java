package com.corosus.zahordeoptimizer;

import com.corosus.zahordeoptimizer.cluster.HordeClusterService;
import com.corosus.zahordeoptimizer.config.HordeOptimizerConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fabric entrypoint for the ZA Horde Optimizer addon mod.
 */
public class HordeOptimizerMod implements ModInitializer {

    public static final String MOD_ID = "za_horde_optimizer";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        HordeOptimizerConfig.loadOrCreate(HordeOptimizerConfig.DEFAULT_CONFIG_FILE);

        ServerTickEvents.END_LEVEL_TICK.register(HordeClusterService::onEndLevelTick);

        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> HordeClusterService.onEntityRemoved(entity));

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> HordeClusterService.onEntityRemoved(entity));

        ServerLevelEvents.UNLOAD.register((server, level) -> HordeClusterService.onLevelUnload(level));

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> HordeClusterService.clearAll());

        LOGGER.info("ZA Horde Optimizer initialized (collision suppression={}, cluster optimization={})",
                HordeOptimizerConfig.disableHordeCollision,
                HordeOptimizerConfig.enableClusterOptimization);
    }
}
