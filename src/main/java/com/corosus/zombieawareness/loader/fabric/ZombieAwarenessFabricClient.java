package com.corosus.zombieawareness.loader.fabric;

import com.corosus.zombieawareness.ZombieAwarenessClient;
import com.corosus.zombieawareness.client.RenderScent;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class ZombieAwarenessFabricClient extends ZombieAwarenessClient implements ClientModInitializer {

	public ZombieAwarenessFabricClient() {
		super();
	}

	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(ZombieAwarenessFabric.SENSE, (context) -> new RenderScent(context));
	}

}
