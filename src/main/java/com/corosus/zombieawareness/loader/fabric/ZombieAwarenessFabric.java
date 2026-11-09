package com.corosus.zombieawareness.loader.fabric;

import com.corosus.zombieawareness.EntityScent;
import com.corosus.zombieawareness.ZombieAwareness;
import com.corosus.zombieawareness.bloodmoon.BloodmoonManager;
import com.corosus.zombieawareness.config.MobListsConfig;
import com.corosus.zombieawareness.heat.ChunkHeatManager;
import com.corosus.zombieawareness.zday.ZDayCommands;
import com.corosus.zombieawareness.zday.ZDayProgressionManager;
import fuzs.forgeconfigapiport.fabric.api.v5.ConfigRegistry;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.PlayerList;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.fml.config.ModConfig;

import java.io.File;
import java.util.function.Supplier;

public class ZombieAwarenessFabric extends ZombieAwareness implements ModInitializer {

	public static MinecraftServer minecraftServer = null;

	public static Supplier<EntityType<EntityScent>> SENSE_SUP;

	static {
		SENSE = EntityType.Builder.
				of(EntityScent::new, MobCategory.MISC)
				.updateInterval(20)
				.clientTrackingRange(128)
				.sized(0f, 0f).build(SENSE_KEY);

		SENSE_SUP = registerImpl(BuiltInRegistries.ENTITY_TYPE, SENSE_NAME, () -> SENSE);
	}

	static <T extends Entity> Supplier<EntityType<T>> register(String name, EntityType.Builder<T> builder) {
		Identifier id = Identifier.fromNamespaceAndPath(ZombieAwareness.MODID, name);
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, id);
		return registerImpl(BuiltInRegistries.ENTITY_TYPE, id, () -> builder.build(key));
	}

	public static <T> Supplier<T> registerImpl(Registry<? super T> registry, Identifier id, Supplier<T> obj) {
		T register = Registry.register(registry, id, obj.get());
		return () -> register;
	}

	public ZombieAwarenessFabric() {
		super();

		ConfigRegistry.INSTANCE.register(MODID, ModConfig.Type.COMMON, MobListsConfig.CONFIG, ZombieAwareness.MODID + File.separator + "MobLists.toml");

		init();
	}

	@Override
	public void onInitialize() {
		ServerLifecycleEvents.SERVER_STARTED.register((server) -> {
			ZombieAwarenessFabric.minecraftServer = server;
		});

		ServerLifecycleEvents.SERVER_STARTING.register(ZDayProgressionManager::initServer);

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			ZDayProgressionManager.tickServer(server);
			ServerLevel overworld = server.overworld();
			if (overworld != null) {
				BloodmoonManager.tickServer(overworld);
				ChunkHeatManager.tickServer(overworld);
			}
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ZDayProgressionManager.onPlayerJoin(handler.getPlayer());
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			ZDayCommands.register(dispatcher);
		});
	}

	@Override
	public PlayerList getPlayerList() {
		return minecraftServer != null ? minecraftServer.getPlayerList() : null;
	}

	@Override
	public boolean isModInstalled(String modID) {
		return FabricLoader.getInstance().isModLoaded(modID);
	}

	@Override
	public SoundEvent register(String name) {
		SoundEvent soundEvent = super.register(name);
		Registry.register(BuiltInRegistries.SOUND_EVENT, Identifier.fromNamespaceAndPath(ZombieAwareness.MODID, name), soundEvent);
		return soundEvent;
	}
}
