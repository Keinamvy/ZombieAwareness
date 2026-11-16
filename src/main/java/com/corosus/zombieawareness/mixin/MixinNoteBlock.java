package com.corosus.zombieawareness.mixin;

import com.corosus.zombieawareness.ZAUtil;
import com.corosus.zombieawareness.ZombieAwareness;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NoteBlock.class)
public abstract class MixinNoteBlock {

    @Inject(method = "triggerEvent",
            at = @At(value = "HEAD"), cancellable = true)
    public void triggerEvent(BlockState pState, Level pLevel, BlockPos pPos, int pId, int pParam, CallbackInfoReturnable<Boolean> cir) {
        ZombieAwareness.unitTest("9");
        if (pPos == null || !(pLevel instanceof ServerLevel sl) || sl.getServer() == null || !sl.getServer().isSameThread()) {
            return;
        }
        ZAUtil.hookSoundEvent(SoundEvents.NOTE_BLOCK_BASS.value(), sl, pPos.getX(), pPos.getY(), pPos.getZ(), 1, 1);
    }
}
