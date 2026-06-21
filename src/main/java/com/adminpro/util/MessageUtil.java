package com.adminpro.util;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

public class MessageUtil {
    private static String prefix = "§6[管理]§r ";

    public static void setPrefix(String p) { prefix = p; }

    public static void send(ServerPlayerEntity player, String message) {
        player.sendMessage(Text.literal(prefix + message), false);
    }

    public static void sendRaw(ServerPlayerEntity player, String message) {
        player.sendMessage(Text.literal(message), false);
    }

    public static void sendSystem(ServerPlayerEntity player, String message) {
        player.sendMessage(Text.literal("§6[系统]§r " + message), false);
    }

    public static void notifyTarget(ServerPlayerEntity target, String message) {
        if (target != null) {
            target.sendMessage(Text.literal("§6[系统]§r " + message), false);
            target.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.0f, 1.0f);
        }
    }

    public static void broadcast(ServerPlayerEntity source, String message) {
        if (source != null) {
            send(source, message);
        }
    }

    public static Text text(String content) {
        return Text.literal(content);
    }
}
