package com.corosus.zombieawareness;

import com.corosus.coroutil.util.CU;
import com.corosus.zombieawareness.config.ZAConfigClient;
import com.corosus.zombieawareness.config.ZAConfigGeneral;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class EntityScent extends Entity {

	/**
	 * 
	 * age has a static max that counts down
	 * 
	 * peak strength is for scaling actual current strength based on age scale
	 * 
	 */
	
	//0 == blood node, 1 == sound node, 2 == wander node
    //public int type = 0;
    public boolean isUsed = false;
    
    private static final EntityDataAccessor<Integer> STRENGTH_PEAK = SynchedEntityData.defineId(EntityScent.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> AGE = SynchedEntityData.defineId(EntityScent.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> TYPE = SynchedEntityData.defineId(EntityScent.class, EntityDataSerializers.BYTE);

    public long lastBuffTime = 0;
    public float lastMultiply = 1F;
    private int localAge = -1;
    
    public static int MAX_AGE = 30*20;

    public EntityScent(EntityType<EntityScent> entityScentEntityType, Level var1) {
        super(entityScentEntityType, var1);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    	builder.define(STRENGTH_PEAK, Integer.valueOf(0));
    	builder.define(AGE, Integer.valueOf(0));
    	builder.define(TYPE, Byte.valueOf((byte) 0));
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }
    
    @Override
    public boolean canBeCollidedWith(Entity other) {
    	return false;
    }

    @Override
    public boolean isSteppingCarefully() {
        return true;
    }

    @Override
    public boolean shouldRender(double p_145770_1_, double p_145770_3_, double p_145770_5_) {
        return true;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double p_70112_1_) {
        return true;
    }

    public float getRange() {
    	float range = (float)getStrengthScaled() / 100.0F * (float) ZAConfigGeneral.maxPFRangeSense;

    	return range;
    }

    public void setStrengthPeak(int strength) {
    	int strTry = strength;
        if (strTry > ZAConfigGeneral.senseMaxStrength) {
        	strTry = ZAConfigGeneral.senseMaxStrength;
        }
        this.getEntityData().set(STRENGTH_PEAK, strTry);
        this.resetAge();
    }
    
    public void resetAge() {
        this.localAge = MAX_AGE;
        this.getEntityData().set(AGE, MAX_AGE);
    }
    
    public int getStrengthPeak() {
        return this.getEntityData().get(STRENGTH_PEAK);
    }
    
    public int getStrengthScaled() {
        return (int)((double)this.getEntityData().get(this.STRENGTH_PEAK) * getAgeScale());
    }
    
    public double getAgeScale() {
        int age = (level() != null && !level().isClientSide() && this.localAge >= 0)
                ? this.localAge
                : this.getEntityData().get(this.AGE);
        return (double)age / (double)MAX_AGE;
    }

    @Override
    public void tick() {
        if (!level().isClientSide()) {
            if (this.localAge < 0) {
                this.localAge = this.getEntityData().get(AGE);
            }
            int decayRate = level().isRaining() && level().canSeeSky(blockPosition()) ? 3 : 1;
            this.localAge = Math.max(0, this.localAge - decayRate);
            // Throttle SynchedEntityData updates to every 20 ticks or on expiry instead of dirtying tracker every tick
            if (this.localAge <= 0 || this.tickCount % 20 == 0) {
                this.getEntityData().set(AGE, this.localAge);
            }
            if (this.localAge <= 0) {
                this.discard();
            }
            return;
        }

        boolean scentDebug = ZAConfigClient.client_debugSensesVisual;
        if (scentDebug) {
            if (level().getGameTime() % 5 == 0) {
                for (int i = 0; i < getStrengthScaled() / 10; i++) {
                    double x = getX() - CU.rand().nextDouble() / 2 + CU.rand().nextDouble();
                    double y = getY() - CU.rand().nextDouble() / 2 + CU.rand().nextDouble();
                    double z = getZ() - CU.rand().nextDouble() / 2 + CU.rand().nextDouble();
                    if (getSenseType() == 0) {
                        level().addParticle(ParticleTypes.HEART, true, true, x, y, z, 0, 0, 0);
                    } else if (getSenseType() == 1) {
                        level().addParticle(ParticleTypes.NOTE, true, true, x, y, z, 0, 0, 0);
                    } else if (getSenseType() == 2) {
                        level().addParticle(ParticleTypes.ANGRY_VILLAGER, true, true, x, y, z, 0, 0, 0);
                    }
                }
            }
        }
    }

    public int getSenseType() {
        return this.getEntityData().get(TYPE);
    }

    public void setSenseType(int type) {
        this.getEntityData().set(TYPE, (byte)type);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput var1) {
        int ageToSave = this.localAge >= 0 ? this.localAge : this.getEntityData().get(AGE);
        var1.putInt("age", ageToSave);
        var1.putInt("strengthpeak", this.getEntityData().get(STRENGTH_PEAK));
        var1.putInt("type", getSenseType());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput var1) {
        int loadedAge = var1.getIntOr("age", 0);
        this.localAge = loadedAge;
        this.getEntityData().set(AGE, loadedAge);
        this.getEntityData().set(STRENGTH_PEAK, var1.getIntOr("strengthpeak", 0));
        this.getEntityData().set(TYPE, var1.getByteOr("type", (byte) 0));
    }
}
