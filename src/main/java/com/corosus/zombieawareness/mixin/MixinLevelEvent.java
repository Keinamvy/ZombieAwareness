package com.corosus.zombieawareness.mixin;

import com.corosus.zombieawareness.ZAUtil;
import com.corosus.zombieawareness.ZombieAwareness;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class MixinLevelEvent {

    @Inject(method = "levelEvent",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/PlayerList;broadcast(Lnet/minecraft/world/entity/player/Player;DDDDLnet/minecraft/resources/ResourceKey;Lnet/minecraft/network/protocol/Packet;)V"))
    public void levelEvent(Entity pEntity, int type, BlockPos blockPos, int data, CallbackInfo ci) {
        ZombieAwareness.unitTest("6");
        ServerLevel self = (ServerLevel)(Object)this;
        if (blockPos == null || self.getServer() == null || !self.getServer().isSameThread()) {
            return;
        }
        ZAUtil.hookPlayEvent(type, self, blockPos.getX(), blockPos.getY(), blockPos.getZ(), data);
    }
}
