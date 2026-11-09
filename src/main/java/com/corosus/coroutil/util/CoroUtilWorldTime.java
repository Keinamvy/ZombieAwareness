package com.corosus.coroutil.util;

import net.minecraft.world.level.Level;

public class CoroUtilWorldTime {
    public static int getDayLength() {
        return 24000;
    }

    public static boolean isNight(Level level) {
        long time = level.getDefaultClockTime() % getDayLength();
        return time >= getNightFirstTick() && time <= getDayFirstTick();
    }

    public static boolean isNightPadded(Level level) {
        return isNightPadded(level, 5);
    }

    public static boolean isNightPadded(Level level, int pad) {
        long time = level.getDefaultClockTime() % getDayLength();
        return time >= (getNightFirstTick() + pad) && time <= (getDayFirstTick() - pad);
    }

    public static int getNightFirstTick() {
        return 12542;
    }

    public static int getDayFirstTick() {
        return 23460;
    }
}
