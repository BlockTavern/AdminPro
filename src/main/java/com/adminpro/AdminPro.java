package com.adminpro;

import com.adminpro.command.AdminCommand;
import com.adminpro.gui.GuiManager;
import com.adminpro.player.PlayerManager;
import com.adminpro.reward.RewardManager;
import com.adminpro.storage.ConfigManager;
import com.adminpro.storage.StorageManager;
import com.adminpro.util.MessageUtil;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;

public class AdminPro implements ModInitializer {

    private static MinecraftServer server;

    @Override
    public void onInitialize() {
        System.out.println("[AdminPro] 正在初始化...");

        ServerLifecycleEvents.SERVER_STARTING.register(srv -> {
            server = srv;
            StorageManager.init(srv);
            ConfigManager.load(srv);
            MessageUtil.setPrefix(ConfigManager.getConfig().getNotificationPrefix());
            PlayerManager.init(srv);
            RewardManager.init(srv);
            System.out.println("[AdminPro] 各模块已初始化");
        });

        CommandRegistrationCallback.EVENT.register(AdminCommand::register);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, srv) -> {
            ServerPlayerEntity player = handler.getPlayer();

            // Ban check
            if (PlayerManager.getInstance().isBanned(player.getUuid())) {
                var banEntry = PlayerManager.getInstance().getBanEntry(player.getUuid());
                if (banEntry != null) {
                    String msg = "§c你已被封禁！\n§7原因: " + banEntry.getReason()
                            + "\n§7执行人: " + banEntry.getBannedBy()
                            + "\n§7期限: " + (banEntry.isPermanent() ? "永久" :
                            PlayerManager.formatDuration(
                                    (banEntry.getExpiryTime() - System.currentTimeMillis()) / 1000));
                    handler.disconnect(Text.literal(msg));
                    return;
                }
            }

            // Track all known players (for offline broadcast give)
            PlayerManager.getInstance().recordJoin(player.getUuid(), player.getName().getString());

            // Sync vanish state for newly joined player (hide vanished players from them)
            PlayerManager.getInstance().syncVanishToPlayer(player);

            // Offline reward queue delivery
            List<ItemStack> queued = RewardManager.getInstance().flushOfflineQueue(player.getUuid());
            if (!queued.isEmpty()) {
                int count = 0;
                for (ItemStack stack : queued) {
                    if (!player.getInventory().insertStack(stack.copy())) {
                        player.dropItem(stack, false);
                    }
                    count++;
                }
                player.sendMessage(Text.literal("§6[系统]§r 你收到了离线期间发放的 §e" + count + " §6组物品"), false);
            }
        });

        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            if (PlayerManager.getInstance().isMuted(sender.getUuid())) {
                sender.sendMessage(Text.literal("§c你已被禁言，无法发言"));
                return false;
            }
            return true;
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, srv) -> {
            GuiManager.removeSession(handler.getPlayer());
        });

        System.out.println("[AdminPro] 初始化完成！");
    }

    public static MinecraftServer getServer() {
        return server;
    }
}
