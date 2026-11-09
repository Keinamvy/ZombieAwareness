package com.corosus.zombieawareness.zday;

import com.corosus.zombieawareness.config.ZAConfigZDay;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class ZDayCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("zday")
                .executes(ctx -> executeStatus(ctx.getSource()))
                .then(Commands.literal("status")
                    .executes(ctx -> executeStatus(ctx.getSource())))
                .then(Commands.literal("log")
                    .executes(ctx -> executeLog(ctx.getSource())))
                .then(Commands.literal("teststage")
                    .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .then(Commands.argument("day", IntegerArgumentType.integer(-1))
                        .executes(ctx -> executeTestStage(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "day")))))
                .then(Commands.literal("bloodmoon")
                    .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .then(Commands.argument("active", BoolArgumentType.bool())
                        .executes(ctx -> executeBloodmoon(ctx.getSource(), BoolArgumentType.getBool(ctx, "active")))))
        );
    }

    private static int executeStatus(CommandSourceStack source) {
        int worldDay = ZDayProgressionManager.getActiveWorldDay();
        ZDayStage currentStage = ZDayProgressionManager.getCurrentStage();
        long activeTicks = ZDayProgressionManager.getActivePlayTicks();
        int nextDay = ZDayProgressionManager.getNextUnlockDay(currentStage.getStageNumber());

        source.sendSuccess(() -> Component.literal("§4§l=== [Z-Day Apocalypse Status] ==="), false);
        source.sendSuccess(() -> Component.literal("§7Active World Day: §eDay " + worldDay + (ZDayProgressionManager.getForcedWorldDay() > 0 ? " §c(FORCED OVERRIDE)" : "")), false);
        source.sendSuccess(() -> Component.literal("§7Current Threat Level: §cStage " + currentStage.getStageNumber() + "/10 — §6" + currentStage.getTitle()), false);
        source.sendSuccess(() -> Component.literal("§7Description: §f" + currentStage.getDescription()), false);
        source.sendSuccess(() -> Component.literal("§7Active Play Ticks: §e" + activeTicks + " §7(Day Time: §e" + (activeTicks % 24000L) + "/24000§7)"), false);

        if (nextDay > 0) {
            source.sendSuccess(() -> Component.literal("§7Next Escalation: §6Day " + nextDay + " §7(" + (nextDay - worldDay) + " day(s) away)"), false);
        } else {
            source.sendSuccess(() -> Component.literal("§4§lMaximum Threat Level Reached (Total Collapse)"), false);
        }

        boolean bloodmoonDay = (worldDay >= ZAConfigZDay.dayStage4Bloodmoon)
                && ((worldDay - ZAConfigZDay.dayStage4Bloodmoon) % ZAConfigZDay.bloodmoonFrequencyDays == 0);
        source.sendSuccess(() -> Component.literal("§7Bloodmoon Status: " + (bloodmoonDay ? "§c§lTONIGHT IS A BLOODMOON!" : "§aNormal Night")), false);

        ServerPlayer player = source.getPlayer();
        if (player != null) {
            try {
                Class<?> heatClass = Class.forName("com.corosus.zombieawareness.heat.ChunkHeatManager");
                int heat = (int) heatClass.getMethod("getHeatAtBlockPos", int.class, int.class, long.class)
                        .invoke(null, player.getBlockX(), player.getBlockZ(), player.level().getGameTime());
                source.sendSuccess(() -> Component.literal("§7Local Chunk Heat: §e" + heat + " / " + ZAConfigZDay.chunkHeatThreshold), false);
            } catch (Throwable ignored) {
            }
        }
        return 1;
    }

    private static int executeLog(CommandSourceStack source) {
        int currentStageNum = ZDayProgressionManager.getCurrentStage().getStageNumber();
        int worldDay = ZDayProgressionManager.getActiveWorldDay();
        source.sendSuccess(() -> Component.literal("§4§l=== [Z-Day Threat Dossier — Day " + worldDay + "] ==="), false);
        for (ZDayStage s : ZDayStage.getAllUnlockedStages(currentStageNum)) {
            source.sendSuccess(() -> Component.literal("§6[Stage " + s.getStageNumber() + " - Day " + s.getUnlockDay() + "] " + s.getTitle()), false);
            source.sendSuccess(() -> Component.literal("  §f" + s.getDescription()), false);
        }
        source.sendSuccess(() -> Component.literal("§7Total unlocked stages: §e" + currentStageNum + "/10"), false);
        return 1;
    }

    private static int executeTestStage(CommandSourceStack source, int day) {
        if (day < 0) {
            ZDayProgressionManager.setForcedWorldDay(-1);
            source.sendSuccess(() -> Component.literal("§a[Z-Day] Cleared forced world day override. Server now follows active play clock."), true);
        } else {
            ZDayProgressionManager.setForcedWorldDay(day);
            ZDayStage stage = ZDayProgressionManager.getCurrentStage();
            source.sendSuccess(() -> Component.literal("§e[Z-Day] Forced world day set to Day " + day + " (Stage " + stage.getStageNumber() + ": " + stage.getTitle() + ")."), true);
        }
        return 1;
    }

    private static int executeBloodmoon(CommandSourceStack source, boolean active) {
        try {
            Class<?> bm = Class.forName("com.corosus.zombieawareness.bloodmoon.BloodmoonManager");
            bm.getMethod("setForcedBloodmoon", Boolean.class).invoke(null, active);
            source.sendSuccess(() -> Component.literal("§c[Z-Day] Bloodmoon forced state set to " + active + "."), true);
        } catch (Throwable t) {
            source.sendFailure(Component.literal("§c[Z-Day] BloodmoonManager not available: " + t.getMessage()));
        }
        return 1;
    }
}
