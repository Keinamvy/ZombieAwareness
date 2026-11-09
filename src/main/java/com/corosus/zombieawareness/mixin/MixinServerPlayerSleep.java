package com.corosus.zombieawareness.mixin;

import com.corosus.zombieawareness.bloodmoon.BloodmoonManager;
import com.mojang.datafixers.util.Either;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class MixinServerPlayerSleep {

    @Inject(method = "startSleepInBed", at = @At("HEAD"), cancellable = true)
    public void onStartSleepInBed(AbstractBedBlock bed, BlockState state, BedRule rule, BlockPos pos, CallbackInfoReturnable<Either<Player.BedSleepingProblem, Unit>> cir) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (BloodmoonManager.isBloodmoonActive(player.level())) {
            player.setRespawnPosition(
                new ServerPlayer.RespawnConfig(
                    new LevelData.RespawnData(GlobalPos.of(player.level().dimension(), pos), player.getYRot(), player.getXRot()),
                    false
                ),
                true
            );
            player.sendSystemMessage(Component.literal("§4§l[Bloodmoon] The Crimson Moon rises — you cannot sleep tonight!"));
            cir.setReturnValue(Either.left(Player.BedSleepingProblem.NOT_SAFE));
        }
    }
}
