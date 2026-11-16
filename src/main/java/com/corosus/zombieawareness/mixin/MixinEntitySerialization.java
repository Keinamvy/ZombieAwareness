package com.corosus.zombieawareness.mixin;

import com.corosus.zombieawareness.ZombieAwareness;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public abstract class MixinEntitySerialization {

    @Inject(method = "addAdditionalSaveData",
            at = @At(value = "HEAD"))
    public void hook(ValueOutput data, CallbackInfo ci) {
        ZombieAwareness.unitTest("2");
        Mob ent = ((Mob)(Object)this);
        data.store("za_data", CompoundTag.CODEC, ZombieAwareness.getPersistentDataSafe(ent));
    }

    @Inject(method = "readAdditionalSaveData",
            at = @At(value = "HEAD"))
    public void hook2(ValueInput data, CallbackInfo ci) {
        ZombieAwareness.unitTest("3");
        Mob ent = ((Mob)(Object)this);
        ZombieAwareness.setPersistentDataSafe(ent, data.read("za_data", CompoundTag.CODEC).orElseGet(CompoundTag::new));
    }
}
