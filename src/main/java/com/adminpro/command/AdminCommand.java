package com.adminpro.command;

import com.adminpro.gui.GuiManager;
import com.adminpro.player.PlayerManager;
import com.adminpro.storage.ConfigManager;
import com.adminpro.util.MessageUtil;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.*;

public class AdminCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                 CommandRegistryAccess registryAccess,
                                 CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(literal("admin")
                .requires(src -> src.hasPermissionLevel(2))
                .executes(ctx -> {
                    ServerCommandSource source = ctx.getSource();
                    if (source.getEntity() instanceof ServerPlayerEntity player) {
                        GuiManager.openMainPanel(player);
                    } else {
                        source.sendMessage(Text.literal("§c该命令只能由玩家执行"));
                    }
                    return 1;
                })
                .then(literal("help")
                        .executes(ctx -> {
                            ctx.getSource().sendMessage(Text.literal("""
                                    §6===== AdminPro 帮助 =====
                                    §e/admin §7- 打开管理面板
                                    §e/admin help §7- 显示帮助
                                    §e/admin reload §7- 重载配置
                                    §e/admin ban <玩家> <原因> [时长] §7- 封禁玩家
                                    §e/admin unban <玩家> §7- 解封玩家
                                    §e/admin mute <玩家> <原因> [时长] §7- 禁言玩家
                                    §e/admin unmute <玩家> §7- 解除禁言"""));
                            return 1;
                        }))
                .then(literal("reload")
                        .executes(ctx -> {
                            ConfigManager.load(ctx.getSource().getServer());
                            PlayerManager.getInstance().reload();
                            ctx.getSource().sendMessage(Text.literal("§a配置文件已重载"));
                            return 1;
                        }))
                .then(literal("ban")
                        .then(argument("player", StringArgumentType.word())
                                .then(argument("reason", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            String playerName = StringArgumentType.getString(ctx, "player");
                                            String reason = StringArgumentType.getString(ctx, "reason");
                                            ServerPlayerEntity target = ctx.getSource().getServer().getPlayerManager().getPlayer(playerName);
                                            if (target == null) {
                                                ctx.getSource().sendMessage(Text.literal("§c玩家不在线"));
                                                return 0;
                                            }
                                            if (ctx.getSource().getEntity() instanceof ServerPlayerEntity src && src.getUuid().equals(target.getUuid())) {
                                                ctx.getSource().sendMessage(Text.literal("§c不能封禁自己"));
                                                return 0;
                                            }
                                            PlayerManager.getInstance().banPlayer(target.getUuid(),
                                                    target.getName().getString(), reason, -1,
                                                    ctx.getSource().getName());
                                            ctx.getSource().sendMessage(Text.literal("§c已永久封禁 §b" + playerName));
                                            return 1;
                                        }))))
                .then(literal("unban")
                        .then(argument("player", StringArgumentType.word())
                                .executes(ctx -> {
                                    String playerName = StringArgumentType.getString(ctx, "player");
                                    var bans = PlayerManager.getInstance().getBanList();
                                    boolean found = false;
                                    for (var entry : bans) {
                                        if (entry.getPlayerName().equalsIgnoreCase(playerName)) {
                                            PlayerManager.getInstance().unbanPlayer(entry.getPlayerUuid());
                                            ctx.getSource().sendMessage(Text.literal("§a已解封 §b" + playerName));
                                            found = true;
                                            break;
                                        }
                                    }
                                    if (!found) {
                                        ctx.getSource().sendMessage(Text.literal("§c未找到该玩家的封禁记录"));
                                    }
                                    return found ? 1 : 0;
                                })))
                .then(literal("mute")
                        .then(argument("player", StringArgumentType.word())
                                .then(argument("reason", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            String playerName = StringArgumentType.getString(ctx, "player");
                                            String reason = StringArgumentType.getString(ctx, "reason");
                                            ServerPlayerEntity target = ctx.getSource().getServer().getPlayerManager().getPlayer(playerName);
                                            if (target == null) {
                                                ctx.getSource().sendMessage(Text.literal("§c玩家不在线"));
                                                return 0;
                                            }
                                            if (ctx.getSource().getEntity() instanceof ServerPlayerEntity src && src.getUuid().equals(target.getUuid())) {
                                                ctx.getSource().sendMessage(Text.literal("§c不能禁言自己"));
                                                return 0;
                                            }
                                            PlayerManager.getInstance().mutePlayer(target.getUuid(),
                                                    target.getName().getString(), reason, -1,
                                                    ctx.getSource().getName());
                                            ctx.getSource().sendMessage(Text.literal("§e已永久禁言 §b" + playerName));
                                            return 1;
                                        }))))
                .then(literal("unmute")
                        .then(argument("player", StringArgumentType.word())
                                .executes(ctx -> {
                                    String playerName = StringArgumentType.getString(ctx, "player");
                                    var mutes = PlayerManager.getInstance().getAllMutes();
                                    boolean found = false;
                                    for (var entry : mutes.values()) {
                                        if (entry.getPlayerName().equalsIgnoreCase(playerName)) {
                                            PlayerManager.getInstance().unmutePlayer(entry.getPlayerUuid());
                                            ctx.getSource().sendMessage(Text.literal("§a已解除禁言 §b" + playerName));
                                            found = true;
                                            break;
                                        }
                                    }
                                    if (!found) {
                                        ctx.getSource().sendMessage(Text.literal("§c未找到该玩家的禁言记录"));
                                    }
                                    return found ? 1 : 0;
                                })))
        );
    }
}
