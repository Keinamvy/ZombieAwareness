package com.corosus.zombieawareness.compat;

import com.corosus.zombieawareness.heat.ChunkHeatManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TaCZIntegration {

    private static final Logger LOGGER = LoggerFactory.getLogger("ZombieAwareness-TaCZ");
    private static final Map<Integer, Long> lastProcessedTickPerEntity = new ConcurrentHashMap<>();
    private static boolean registered = false;

    public static void registerEventsIfLoaded() {
        if (!FabricLoader.getInstance().isModLoaded("tacz")) {
            return;
        }
        if (registered) {
            return;
        }
        registered = true;

        try {
            Class<?> eventClass = Class.forName("com.tacz.guns.api.event.common.GunShootEvent");
            Class<?> callbackClass = Class.forName("com.tacz.guns.api.event.common.GunShootEvent$Callback");

            Field callbackField = eventClass.getField("CALLBACK");
            Object callbackEvent = callbackField.get(null);

            Object proxy = Proxy.newProxyInstance(
                    callbackClass.getClassLoader(),
                    new Class<?>[]{callbackClass},
                    (proxyInstance, method, args) -> {
                        if ("post".equals(method.getName()) && args != null && args.length == 1) {
                            handleGunShootEvent(args[0]);
                        }
                        return null;
                    }
            );

            // Register onto Event<GunShootEvent$Callback>
            boolean registeredViaEvent = false;
            try {
                Method registerMethod = net.fabricmc.fabric.api.event.Event.class.getMethod("register", Object.class);
                registerMethod.invoke(callbackEvent, proxy);
                registeredViaEvent = true;
            } catch (Throwable ignored) {}

            if (!registeredViaEvent) {
                for (Method m : callbackEvent.getClass().getMethods()) {
                    if ("register".equals(m.getName()) && m.getParameterCount() == 1) {
                        m.invoke(callbackEvent, proxy);
                        registeredViaEvent = true;
                        break;
                    }
                }
            }

            if (registeredViaEvent) {
                LOGGER.info("[ZombieAwareness] Successfully registered GunShootEvent callback for TaCZ integration.");
            } else {
                LOGGER.warn("[ZombieAwareness] Could not find register method on GunShootEvent.CALLBACK");
            }
        } catch (Throwable t) {
            LOGGER.warn("[ZombieAwareness] Failed to wire GunShootEvent reflection hook for TaCZ: " + t.getMessage());
        }
    }

    private static void handleGunShootEvent(Object event) {
        if (event == null) return;
        try {
            // Check getLogicalSide().isServer()
            Method getLogicalSide = event.getClass().getMethod("getLogicalSide");
            Object side = getLogicalSide.invoke(event);
            if (side != null) {
                Method isServer = side.getClass().getMethod("isServer");
                Object result = isServer.invoke(side);
                if (result instanceof Boolean b && !b) {
                    return;
                }
            }

            // Get shooter
            Method getShooter = event.getClass().getMethod("getShooter");
            Object shooterObj = getShooter.invoke(event);
            if (!(shooterObj instanceof LivingEntity shooter)) {
                return;
            }

            if (!(shooter.level() instanceof ServerLevel serverLevel)) {
                return;
            }

            int entityId = shooter.getId();
            long currentTick = serverLevel.getGameTime();

            // Same-tick dedup with SoundManager.sendSoundToNearby
            Long lastTick = lastProcessedTickPerEntity.get(entityId);
            if (lastTick != null && lastTick == currentTick) {
                return;
            }
            recordTickProcessed(entityId, currentTick);

            // Determine if suppressed or unsuppressed
            String soundName = "shoot";
            int distance = 64;
            try {
                Method getGunItemStack = event.getClass().getMethod("getGunItemStack");
                Object stackObj = getGunItemStack.invoke(event);
                if (stackObj instanceof net.minecraft.world.item.ItemStack stack) {
                    String stackStr = stack.toString().toLowerCase(Locale.ROOT);
                    if (stackStr.contains("silence") || stackStr.contains("suppressor")) {
                        soundName = "silence";
                        distance = 20;
                    }
                }
            } catch (Throwable ignored) {}

            ChunkHeatManager.onTaCZGunSound(serverLevel, shooter.getX(), shooter.getY(), shooter.getZ(), distance, soundName);
        } catch (Throwable t) {
            LOGGER.debug("[ZombieAwareness] Exception handling TaCZ GunShootEvent: " + t.getMessage());
        }
    }

    public static void onTaCZSoundSent(LivingEntity sourceEntity, int distance, Identifier gunId, String soundName, float volume, float pitch) {
        if (sourceEntity == null || !(sourceEntity.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        int entityId = sourceEntity.getId();
        long currentTick = serverLevel.getGameTime();

        // Same-tick dedup with GunShootEvent
        Long lastTick = lastProcessedTickPerEntity.get(entityId);
        if (lastTick != null && lastTick == currentTick) {
            return;
        }
        recordTickProcessed(entityId, currentTick);

        ChunkHeatManager.onTaCZGunSound(serverLevel, sourceEntity.getX(), sourceEntity.getY(), sourceEntity.getZ(), distance, soundName);
    }

    private static void recordTickProcessed(int entityId, long tick) {
        lastProcessedTickPerEntity.put(entityId, tick);
        if (lastProcessedTickPerEntity.size() > 1024) {
            lastProcessedTickPerEntity.entrySet().removeIf(e -> (tick - e.getValue()) > 100);
        }
    }

    public static void resetDedupForTests() {
        lastProcessedTickPerEntity.clear();
    }
}
