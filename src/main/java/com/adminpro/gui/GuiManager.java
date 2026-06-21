package com.adminpro.gui;

import com.adminpro.player.BanEntry;
import com.adminpro.player.PlayerManager;
import com.adminpro.reward.RewardItem;
import com.adminpro.reward.RewardManager;
import com.adminpro.storage.ConfigManager;
import com.adminpro.util.ItemUtil;
import com.adminpro.util.MessageUtil;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.*;
import java.util.function.BiFunction;

public class GuiManager {
    private static final Map<UUID, GuiSession> sessions = new HashMap<>();

    public static class GuiSession {
        public GuiType type;
        public ServerPlayerEntity admin;
        public ServerPlayerEntity target;
        public boolean notifyOnAction;
        public int slotIndex;
        public SimpleInventory tempInventory;
        public Object extraData;
        public BiFunction<ServerPlayerEntity, Integer, Boolean> handler;

        public GuiSession(GuiType type, ServerPlayerEntity admin) {
            this.type = type;
            this.admin = admin;
        }
    }

    public enum GuiType {
        MAIN_PANEL, PLAYER_DETAIL, GIVE_SELECT, GIVE_FROM_INVENTORY,
        GIVE_FROM_REWARD, REWARD_LIBRARY, BAN_LIST,
        DURATION_SELECT, CONFIRM_ACTION, VIEW_INVENTORY,
        ADD_REWARD, QUANTITY_SELECT
    }

    public static GuiSession getSession(ServerPlayerEntity player) {
        return sessions.get(player.getUuid());
    }

    public static void removeSession(ServerPlayerEntity player) {
        sessions.remove(player.getUuid());
    }

    // ==================== MAIN PANEL ====================

    private static final int MAIN_PANEL_PAGE_SIZE = 36;

    public static void openMainPanel(ServerPlayerEntity admin) {
        openMainPanel(admin, 0);
    }

    public static void openMainPanel(ServerPlayerEntity admin, int page) {
        SimpleInventory inv = new SimpleInventory(54);

        for (int i = 0; i < 9; i++) inv.setStack(i, ItemUtil.createBorder());
        for (int i = 45; i < 54; i++) inv.setStack(i, ItemUtil.createBorder());

        List<ServerPlayerEntity> visiblePlayers = new ArrayList<>();
        PlayerManager pm = PlayerManager.getInstance();
        for (ServerPlayerEntity online : admin.getServer().getPlayerManager().getPlayerList()) {
            if (admin.getUuid().equals(online.getUuid()) || !pm.isVanishedFull(online.getUuid())) {
                visiblePlayers.add(online);
            }
        }

        ItemStack playersLabel = new ItemStack(Items.PLAYER_HEAD);
        playersLabel.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§6§l在线玩家 §7(" + visiblePlayers.size() + "人) §7- 第" + (page + 1) + "页"));
        inv.setStack(4, playersLabel);

        int startIndex = page * MAIN_PANEL_PAGE_SIZE;
        int slot = 9;
        for (int i = startIndex; i < visiblePlayers.size() && slot < 45; i++) {
            ServerPlayerEntity online = visiblePlayers.get(i);
            ItemStack head = ItemUtil.getPlayerHead(online.getName().getString(), online.getUuid());
            boolean isVanished = pm.isVanished(online.getUuid());
            String nameTag = isVanished ? "§7" + online.getName().getString() + " §8[隐身]" : "§b" + online.getName().getString();
            head.set(DataComponentTypes.CUSTOM_NAME, Text.literal(nameTag));
            head.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                    Text.literal("§7延迟: " + online.networkHandler.getLatency() + "ms"),
                    Text.literal(isVanished ? "§7状态: §8隐身中" : "§e点击管理该玩家")
            )));
            inv.setStack(slot, head);
            slot++;
        }

        int totalPages = Math.max(1, (int) Math.ceil(visiblePlayers.size() / (double) MAIN_PANEL_PAGE_SIZE));
        if (page > 0) inv.setStack(45, ItemUtil.createButton(new ItemStack(Items.ARROW), "§7上一页"));
        if (page < totalPages - 1) inv.setStack(46, ItemUtil.createButton(new ItemStack(Items.ARROW), "§7下一页"));

        UUID adminUuid = admin.getUuid();
        boolean isVL = PlayerManager.getInstance().isVanishedLight(adminUuid);
        boolean isVF = PlayerManager.getInstance().isVanishedFull(adminUuid);
        String vanishStatus = isVF ? "§c完全隐身" : isVL ? "§e轻隐身" : "§a可见";
        String vanishLore = isVF ? "§7完全隐身: 玩家列表不可见" : isVL ? "§7轻隐身: 玩家列表可见但不可见" : "§7点击切换隐身模式";
        inv.setStack(47, ItemUtil.createButton(new ItemStack(Items.CHEST), "§6「奖励库管理」",
                List.of(Text.literal("§7查看、添加、删除奖励物品"))));
        inv.setStack(48, ItemUtil.createButton(new ItemStack(Items.WRITABLE_BOOK), "§6「黑名单管理」",
                List.of(Text.literal("§7查看和管理被封禁的玩家"))));
        inv.setStack(49, ItemUtil.createButton(new ItemStack(isVF ? Items.WHITE_STAINED_GLASS : isVL ? Items.GRAY_STAINED_GLASS : Items.GLASS), "§7隐身模式: §r" + vanishStatus,
                List.of(Text.literal(vanishLore), Text.literal("§e点击切换"))));
        inv.setStack(50, ItemUtil.createButton(new ItemStack(Items.COMPASS), "§7刷新",
                List.of(Text.literal("§7刷新在线玩家列表"))));
        inv.setStack(53, ItemUtil.createButton(new ItemStack(Items.BARRIER), "§c关闭面板"));

        GuiSession session = new GuiSession(GuiType.MAIN_PANEL, admin);
        session.slotIndex = page;
        sessions.put(admin.getUuid(), session);

        int finalPage = page;
        openGUI(admin, inv, 6, Text.literal("§8管理面板"),
                (p, s) -> { handleMainPanelClick(p, s, finalPage); return true; });
    }

    private static void handleMainPanelClick(ServerPlayerEntity admin, int slot, int page) {
        if (slot == 53) { admin.closeHandledScreen(); removeSession(admin); return; }
        if (slot == 50) { openMainPanel(admin, page); return; }
        if (slot == 47) { openRewardLibrary(admin); return; }
        if (slot == 48) { openBanList(admin); return; }
        if (slot == 49) {
            UUID uuid = admin.getUuid();
            PlayerManager pm = PlayerManager.getInstance();
            if (pm.isVanishedFull(uuid)) pm.unVanish(uuid);
            else if (pm.isVanishedLight(uuid)) pm.setVanishFull(uuid);
            else pm.setVanishLight(uuid);
            MessageUtil.send(admin, "§7隐身模式已切换");
            openMainPanel(admin, page);
            return;
        }
        if (slot == 45) { openMainPanel(admin, page - 1); return; }
        if (slot == 46) { openMainPanel(admin, page + 1); return; }

        if (slot >= 9 && slot < 45) {
            SimpleInventory inv = (SimpleInventory) admin.currentScreenHandler.getSlot(0).inventory;
            ItemStack stack = inv.getStack(slot);
            if (stack != null && !stack.isEmpty() && stack.getItem() == Items.PLAYER_HEAD) {
                Text nameText = stack.get(DataComponentTypes.CUSTOM_NAME);
                if (nameText != null) {
                    String raw = nameText.getString();
                    String playerName = raw.replaceAll("^§.", "").replaceAll(" §8\\[隐身\\]$", "").trim();
                    ServerPlayerEntity target = admin.getServer().getPlayerManager().getPlayer(playerName);
                    if (target != null) {
                        openPlayerDetail(admin, target);
                    } else {
                        MessageUtil.send(admin, "§c该玩家已离线");
                    }
                }
            }
        }
    }

    // ==================== PLAYER DETAIL ====================

    public static void openPlayerDetail(ServerPlayerEntity admin, ServerPlayerEntity target) {
        SimpleInventory inv = new SimpleInventory(36);

        for (int i = 0; i < 9; i++) inv.setStack(i, ItemUtil.createBorder());
        for (int i = 18; i < 27; i++) inv.setStack(i, ItemUtil.createFiller());

        boolean isSelf = admin.getUuid().equals(target.getUuid());
        ItemStack head = ItemUtil.getPlayerHead(target.getName().getString(), target.getUuid());
        head.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§b" + target.getName().getString()));
        head.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                Text.literal("§7延迟: " + target.networkHandler.getLatency() + "ms"),
                Text.literal("§7生命: " + Math.round(target.getHealth()) + " ❤"),
                Text.literal("§e点击查看信息")
        )));
        inv.setStack(9, head);

        inv.setStack(11, ItemUtil.createButton(new ItemStack(Items.DIAMOND_BLOCK), "§b查看背包",
                List.of(Text.literal("§7查看 " + target.getName().getString() + " 的背包"))));
        inv.setStack(12, ItemUtil.createButton(new ItemStack(Items.CHEST), "§6发放物品",
                List.of(Text.literal("§7向 " + target.getName().getString() + " 发放物品"))));

        if (isSelf) {
            inv.setStack(13, ItemUtil.createButton(new ItemStack(Items.BARRIER), "§7不能对自己操作",
                    List.of(Text.literal("§c无法对自己执行此操作"))));
        } else if (PlayerManager.getInstance().isMuted(target.getUuid())) {
            inv.setStack(13, ItemUtil.createButton(new ItemStack(Items.LIME_DYE), "§a解除禁言",
                    List.of(Text.literal("§7解除 " + target.getName().getString() + " 的禁言"))));
        } else {
            inv.setStack(13, ItemUtil.createButton(new ItemStack(Items.CLOCK), "§e禁言",
                    List.of(Text.literal("§7禁止 " + target.getName().getString() + " 发言"))));
        }

        if (isSelf) {
            inv.setStack(14, ItemUtil.createButton(new ItemStack(Items.BARRIER), "§7不能对自己操作",
                    List.of(Text.literal("§c无法对自己执行此操作"))));
        } else if (PlayerManager.getInstance().isVoiceBanned(target.getUuid())) {
            inv.setStack(14, ItemUtil.createButton(new ItemStack(Items.LIME_DYE), "§a解除禁止语音",
                    List.of(Text.literal("§7解除 " + target.getName().getString() + " 的语音限制"))));
        } else {
            inv.setStack(14, ItemUtil.createButton(new ItemStack(Items.JUKEBOX), "§e禁止语音",
                    List.of(Text.literal("§7禁止 " + target.getName().getString() + " 使用语音"))));
        }

        if (isSelf) {
            inv.setStack(15, ItemUtil.createButton(new ItemStack(Items.BARRIER), "§7不能对自己操作",
                    List.of(Text.literal("§c无法对自己执行此操作"))));
        } else if (PlayerManager.getInstance().isBanned(target.getUuid())) {
            inv.setStack(15, ItemUtil.createButton(new ItemStack(Items.EMERALD_BLOCK), "§a解封",
                    List.of(Text.literal("§7解封 " + target.getName().getString()))));
        } else {
            inv.setStack(15, ItemUtil.createButton(new ItemStack(Items.IRON_BARS), "§c封禁",
                    List.of(Text.literal("§7封禁 " + target.getName().getString()))));
        }

        inv.setStack(17, ItemUtil.createButton(new ItemStack(Items.ARROW), "§7返回"));

        inv.setStack(18, ItemUtil.createButton(new ItemStack(Items.COMPASS), "§b查看坐标",
                List.of(Text.literal("§7查看 " + target.getName().getString() + " 的当前位置"))));
        inv.setStack(19, ItemUtil.createButton(new ItemStack(Items.ENDER_PEARL), "§d传送过去",
                List.of(Text.literal("§7传送到 " + target.getName().getString() + " 的位置"),
                        Text.literal("§7操作对玩家不可见"))));

        ItemStack playerInfo = new ItemStack(Items.PAPER);
        playerInfo.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§6玩家信息"));
        playerInfo.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                Text.literal("§7名称: " + target.getName().getString()),
                Text.literal("§7UUID: §f" + target.getUuid().toString().replace("-", "")),
                Text.literal("§7游戏模式: " + target.interactionManager.getGameMode().asString())
        )));
        inv.setStack(27, playerInfo);

        GuiSession session = new GuiSession(GuiType.PLAYER_DETAIL, admin);
        session.target = target;
        sessions.put(admin.getUuid(), session);

        openGUI(admin, inv, 4, Text.literal("§8管理: " + target.getName().getString()),
                (p, s) -> { handlePlayerDetailClick(p, s); return true; });
    }

    private static void handlePlayerDetailClick(ServerPlayerEntity admin, int slot) {
        GuiSession session = getSession(admin);
        if (session == null || session.target == null) return;
        ServerPlayerEntity target = session.target;
        boolean isSelf = admin.getUuid().equals(target.getUuid());

        switch (slot) {
            case 9 -> MessageUtil.send(admin, "§7玩家: §b" + target.getName().getString()
                    + "\n§7状态: §a在线\n§7生命: §c" + Math.round(target.getHealth()) + " ❤\n§7食物: §6" + target.getHungerManager().getFoodLevel());
            case 11 -> openViewInventory(admin, target);
            case 12 -> openGiveSelect(admin, target);
            case 13 -> {
                if (isSelf) return;
                if (PlayerManager.getInstance().isMuted(target.getUuid())) {
                    PlayerManager.getInstance().unmutePlayer(target.getUuid());
                    MessageUtil.send(admin, "§a已解除 " + target.getName().getString() + " 的禁言");
                    MessageUtil.notifyTarget(target, "§a你已被解除禁言");
                    openPlayerDetail(admin, target);
                } else {
                    openDurationSelect(admin, target, "mute");
                }
            }
            case 14 -> {
                if (isSelf) return;
                if (PlayerManager.getInstance().isVoiceBanned(target.getUuid())) {
                    PlayerManager.getInstance().unVoiceBanPlayer(target.getUuid());
                    MessageUtil.send(admin, "§a已解除 " + target.getName().getString() + " 的语音限制");
                    MessageUtil.notifyTarget(target, "§a你已被解除语音限制");
                } else {
                    PlayerManager.getInstance().voiceBanPlayer(target.getUuid());
                    MessageUtil.send(admin, "§e已标记 " + target.getName().getString() + " 禁止语音");
                    MessageUtil.notifyTarget(target, "你已被管理员禁止使用语音");
                }
                openPlayerDetail(admin, target);
            }
            case 15 -> {
                if (isSelf) return;
                if (PlayerManager.getInstance().isBanned(target.getUuid())) {
                    PlayerManager.getInstance().unbanPlayer(target.getUuid());
                    MessageUtil.send(admin, "§a已解封 " + target.getName().getString());
                    MessageUtil.notifyTarget(target, "§a你已被解封");
                    openPlayerDetail(admin, target);
                } else {
                    openDurationSelect(admin, target, "ban");
                }
            }
            case 17 -> openMainPanel(admin);
            case 18 -> {
                MessageUtil.send(admin, "§b" + target.getName().getString() + " §7的坐标:\n"
                        + "§7世界: §f" + target.getServerWorld().getRegistryKey().getValue().toString() + "\n"
                        + "§7X: §f" + String.format("%.1f", target.getX()) + "\n"
                        + "§7Y: §f" + String.format("%.1f", target.getY()) + "\n"
                        + "§7Z: §f" + String.format("%.1f", target.getZ()));
            }
            case 19 -> {
                admin.teleport(target.getServerWorld(), target.getX(), target.getY(), target.getZ(), target.getYaw(), target.getPitch());
                MessageUtil.send(admin, "§a已传送到 §b" + target.getName().getString() + " §7的位置");
            }
        }
    }

    // ==================== VIEW INVENTORY ====================

    public static void openViewInventory(ServerPlayerEntity admin, ServerPlayerEntity target) {
        SimpleInventory inv = new SimpleInventory(54);
        for (int i = 0; i < 36; i++) {
            ItemStack stack = target.getInventory().getStack(i);
            if (!stack.isEmpty()) inv.setStack(i, stack.copy());
        }
        ItemStack offhand = target.getOffHandStack();
        if (!offhand.isEmpty()) inv.setStack(36, offhand.copy());

        int armorSlot = 45;
        ItemStack[] armor = new ItemStack[]{
                target.getInventory().getArmorStack(3),
                target.getInventory().getArmorStack(2),
                target.getInventory().getArmorStack(1),
                target.getInventory().getArmorStack(0)
        };
        for (int i = 0; i < 4; i++) {
            if (!armor[i].isEmpty()) inv.setStack(armorSlot + i, armor[i].copy());
        }
        inv.setStack(53, ItemUtil.createButton(new ItemStack(Items.ARROW), "§7返回"));

        GuiSession session = new GuiSession(GuiType.VIEW_INVENTORY, admin);
        session.target = target;
        sessions.put(admin.getUuid(), session);

        openGUI(admin, inv, 6, Text.literal("§8背包: " + target.getName().getString()),
                (p, s) -> {
                    GuiSession sess = getSession(p);
                    if (sess == null || sess.target == null) return true;
                    if (s == 53) { openPlayerDetail(p, sess.target); return true; }
                    return false;
                });
    }

    // ==================== GIVE SELECT ====================

    public static void openGiveSelect(ServerPlayerEntity admin, ServerPlayerEntity target) {
        SimpleInventory inv = new SimpleInventory(9);
        inv.setStack(0, ItemUtil.createButton(new ItemStack(Items.CHEST), "§6从背包扣取",
                List.of(Text.literal("§7从你的背包中扣取物品发放"))));
        inv.setStack(1, ItemUtil.createButton(new ItemStack(Items.GOLD_BLOCK), "§6从奖励库选择",
                List.of(Text.literal("§7从奖励库中发放物品"))));
        inv.setStack(2, ItemUtil.createButton(new ItemStack(Items.ENDER_CHEST), "§6广播发放",
                List.of(Text.literal("§7给当前所有在线玩家发放物品"))));
        inv.setStack(8, ItemUtil.createButton(new ItemStack(Items.BARRIER), "§c取消"));

        GuiSession session = new GuiSession(GuiType.GIVE_SELECT, admin);
        session.target = target;
        sessions.put(admin.getUuid(), session);

        openGUI(admin, inv, 1, Text.literal("§8选择发放方式"),
                (p, s) -> {
                    GuiSession sess = getSession(p);
                    if (sess == null) return true;
                    if (sess.target == null) {
                        if (s == 8) { openMainPanel(p); return true; }
                        if (s == 2) { openBroadcastGive(p, 0); return true; }
                        return true;
                    }
                    if (s == 0) openGiveFromInventory(p, sess.target);
                    else if (s == 1) openGiveFromReward(p, sess.target, 0);
                    else if (s == 2) { openBroadcastGive(p, 0); return true; }
                    else if (s == 8) openPlayerDetail(p, sess.target);
                    return true;
                });
    }

    // ==================== GIVE FROM INVENTORY ====================

    public static void openGiveFromInventory(ServerPlayerEntity admin, ServerPlayerEntity target) {
        SimpleInventory inv = new SimpleInventory(27);
        for (int i = 0; i < 18; i++) inv.setStack(i, ItemStack.EMPTY);

        inv.setStack(18, ItemUtil.createButton(new ItemStack(Items.GREEN_DYE), "§a确认发放（通知）",
                List.of(Text.literal("§7发放物品并通知玩家"))));
        inv.setStack(19, ItemUtil.createButton(new ItemStack(Items.LIME_DYE), "§a确认发放（不通知）",
                List.of(Text.literal("§7发放物品，不通知玩家"))));
        inv.setStack(26, ItemUtil.createButton(new ItemStack(Items.ARROW), "§7返回"));

        GuiSession session = new GuiSession(GuiType.GIVE_FROM_INVENTORY, admin);
        session.target = target;
        session.tempInventory = inv;
        sessions.put(admin.getUuid(), session);

        openGUI(admin, inv, 3, Text.literal("§8放入物品 - 发放给 " + target.getName().getString()),
                (p, s) -> {
                    GuiSession sess = getSession(p);
                    if (sess == null) return true;

                    if (s == 18 || s == 19) {
                        boolean notify = s == 18;
                        int given = 0;
                        int failed = 0;
                        for (int i = 0; i < 18; i++) {
                            ItemStack original = sess.tempInventory.getStack(i);
                            if (!original.isEmpty()) {
                                sess.tempInventory.setStack(i, ItemStack.EMPTY);
                                // Return original to admin (copy instead of consume)
                                ItemStack returnStack = original.copy();
                                if (!p.getInventory().insertStack(returnStack)) {
                                    p.dropItem(returnStack, false);
                                }
                                // Give copy to target
                                ItemStack toGive = original.copy();
                                if (!sess.target.getInventory().insertStack(toGive)) {
                                    if (!toGive.isEmpty()) {
                                        p.getInventory().insertStack(toGive);
                                        failed++;
                                    }
                                }
                                given++;
                            }
                        }
                        MessageUtil.send(p, "§a已发放 §e" + given + " §a组物品给 §b" + sess.target.getName().getString()
                                + (failed > 0 ? " §c(" + failed + "组未能全数放入, 已退回你的背包)" : ""));
                        if (notify) {
                            MessageUtil.notifyTarget(sess.target, "管理员给你发放了 " + given + " 组物品");
                        }
                        sess.tempInventory.clear();
                        openPlayerDetail(p, sess.target);
                        return true;
                    } else if (s == 26) {
                        sess.tempInventory.clear();
                        openGiveSelect(p, sess.target);
                        return true;
                    }
                    return false;
                });
    }

    // ==================== QUANTITY SELECT ====================

    public static void openQuantitySelect(ServerPlayerEntity admin, ServerPlayerEntity target, int rewardIndex) {
        SimpleInventory inv = new SimpleInventory(9);
        for (int i = 0; i < 9; i++) inv.setStack(i, ItemUtil.createBorder());

        int[] amounts = {1, 8, 16, 32, 64};
        for (int i = 0; i < amounts.length; i++) {
            int a = amounts[i];
            inv.setStack(i, ItemUtil.createButton(new ItemStack(Items.CHEST), "§e×" + a,
                    List.of(Text.literal("§7发放 " + a + " 个"))));
        }
        inv.setStack(8, ItemUtil.createButton(new ItemStack(Items.ARROW), "§7返回"));

        GuiSession session = new GuiSession(GuiType.QUANTITY_SELECT, admin);
        session.target = target;
        session.slotIndex = rewardIndex;
        sessions.put(admin.getUuid(), session);

        openGUI(admin, inv, 1, Text.literal("§8选择数量"),
                (p, s) -> {
                    GuiSession sess = getSession(p);
                    if (sess == null) return true;
                    if (s == 8) { openGiveFromReward(p, sess.target, 0); return true; }
                    if (s >= 0 && s < amounts.length) {
                        RewardItem reward = RewardManager.getInstance().getReward(sess.slotIndex);
                        if (reward != null) {
                            ItemStack stack = reward.getItem().copy();
                            stack.setCount(amounts[s]);
                            boolean given = sess.target.getInventory().insertStack(stack.copy());
                            if (!given && !stack.isEmpty()) {
                                sess.target.dropItem(stack, false);
                            }
                            String name = reward.getDisplayName() != null ? reward.getDisplayName() : stack.getItem().getName().getString();
                            MessageUtil.send(p, "§a已发放 §e×" + amounts[s] + " §b" + name + " §7给 §b" + sess.target.getName().getString());
                            MessageUtil.notifyTarget(sess.target, "你收到了 §e×" + amounts[s] + " §b" + name);
                        }
                        openGiveFromReward(p, sess.target, 0);
                        return true;
                    }
                    return true;
                });
    }

    // ==================== GIVE FROM REWARD ====================

    public static void openGiveFromReward(ServerPlayerEntity admin, ServerPlayerEntity target, int page) {
        RewardManager rm = RewardManager.getInstance();
        List<RewardItem> rewards = rm.getAllRewards();
        int maxSlots = 36;
        int totalPages = Math.max(1, (int) Math.ceil(rewards.size() / (double) maxSlots));
        int startIndex = page * maxSlots;

        SimpleInventory inv = new SimpleInventory(54);
        for (int i = 0; i < 9; i++) inv.setStack(i, ItemUtil.createBorder());
        for (int i = 45; i < 54; i++) inv.setStack(i, ItemUtil.createBorder());

        for (int i = 0; i < maxSlots && (startIndex + i) < rewards.size(); i++) {
            RewardItem reward = rewards.get(startIndex + i);
            ItemStack display = reward.getItem().copy();
            Text name = reward.getDisplayName() != null
                    ? Text.literal("§e" + reward.getDisplayName())
                    : Text.literal("§e" + display.getItem().getName().getString());
            display.set(DataComponentTypes.CUSTOM_NAME, name);
            inv.setStack(9 + i, display);
        }

        if (page > 0) inv.setStack(45, ItemUtil.createButton(new ItemStack(Items.ARROW), "§7上一页"));
        if (page < totalPages - 1) inv.setStack(46, ItemUtil.createButton(new ItemStack(Items.ARROW), "§7下一页"));
        inv.setStack(53, ItemUtil.createButton(new ItemStack(Items.ARROW), "§7返回"));

        GuiSession session = new GuiSession(GuiType.GIVE_FROM_REWARD, admin);
        session.target = target;
        session.slotIndex = page;
        sessions.put(admin.getUuid(), session);

        openGUI(admin, inv, 6, Text.literal("§8奖励库 - 发放给 " + target.getName().getString()),
                (p, s) -> {
                    GuiSession sess = getSession(p);
                    if (sess == null) return true;
                    if (s == 45 && page > 0) openGiveFromReward(p, sess.target, page - 1);
                    else if (s == 46 && page < totalPages - 1) openGiveFromReward(p, sess.target, page + 1);
                    else if (s == 53) openGiveSelect(p, sess.target);
                    else if (s >= 9 && s < 9 + maxSlots) {
                        int rewardIndex = startIndex + (s - 9);
                        if (rewardIndex < rewards.size()) {
                            openQuantitySelect(p, sess.target, rewardIndex);
                        }
                    }
                    return true;
                });
    }

    // ==================== REWARD LIBRARY ====================

    public static void openRewardLibrary(ServerPlayerEntity admin) {
        openRewardLibrary(admin, 0);
    }

    public static void openRewardLibrary(ServerPlayerEntity admin, int page) {
        SimpleInventory inv = new SimpleInventory(54);
        List<RewardItem> rewards = RewardManager.getInstance().getAllRewards();
        int maxSlots = 36;
        int totalPages = Math.max(1, (int) Math.ceil(rewards.size() / (double) maxSlots));
        int startIndex = page * maxSlots;

        for (int i = 0; i < 9; i++) inv.setStack(i, ItemUtil.createBorder());
        for (int i = 45; i < 54; i++) inv.setStack(i, ItemUtil.createBorder());

        for (int i = 0; i < maxSlots && (startIndex + i) < rewards.size(); i++) {
            RewardItem reward = rewards.get(startIndex + i);
            ItemStack display = reward.getItem().copy();
            Text name = reward.getDisplayName() != null
                    ? Text.literal("§e" + reward.getDisplayName())
                    : Text.literal("§e" + display.getItem().getName().getString());
            display.set(DataComponentTypes.CUSTOM_NAME, name);
            inv.setStack(9 + i, display);
        }

        if (page > 0) inv.setStack(45, ItemUtil.createButton(new ItemStack(Items.ARROW), "§7上一页"));
        if (page < totalPages - 1) inv.setStack(46, ItemUtil.createButton(new ItemStack(Items.ARROW), "§7下一页"));
        inv.setStack(47, ItemUtil.createButton(new ItemStack(Items.ANVIL), "§b新建奖励",
                List.of(Text.literal("§7创建新的奖励物品"))));
        inv.setStack(53, ItemUtil.createButton(new ItemStack(Items.ARROW), "§7返回"));

        GuiSession session = new GuiSession(GuiType.REWARD_LIBRARY, admin);
        session.slotIndex = page;
        sessions.put(admin.getUuid(), session);

        openGUI(admin, inv, 6, Text.literal("§8奖励库管理 - 第" + (page + 1) + "页"),
                (p, s) -> {
                    GuiSession sess = getSession(p);
                    if (sess == null) return true;
                    if (s == 53) { openMainPanel(p); return true; }
                    if (s == 47) { openAddReward(p); return true; }
                    if (s == 45 && page > 0) { openRewardLibrary(p, page - 1); return true; }
                    if (s == 46 && page < totalPages - 1) { openRewardLibrary(p, page + 1); return true; }
                    int rewardSlot = startIndex + (s - 9);
                    if (s >= 9 && s < 9 + maxSlots && rewardSlot >= 0 && rewardSlot < rewards.size()) {
                        RewardManager.getInstance().removeReward(rewardSlot);
                        MessageUtil.send(p, "§c已移除奖励物品");
                        openRewardLibrary(p, page);
                    }
                    return true;
                });
    }

    // ==================== ADD REWARD ====================

    public static void openAddReward(ServerPlayerEntity admin) {
        SimpleInventory inv = new SimpleInventory(27);
        for (int i = 0; i < 27; i++) inv.setStack(i, ItemUtil.createBorder());

        // Item placement slots: rows 1-2 (slots 0-17)
        for (int i = 0; i < 18; i++) inv.setStack(i, ItemStack.EMPTY);

        // Action buttons in row 3
        inv.setStack(18, ItemUtil.createButton(new ItemStack(Items.HOPPER), "§a保存所有物品到奖励库",
                List.of(Text.literal("§7将上方物品逐个保存为奖励"))));
        inv.setStack(22, ItemUtil.createButton(new ItemStack(Items.STRUCTURE_VOID), "§c清空",
                List.of(Text.literal("§7清空所有物品"))));
        inv.setStack(26, ItemUtil.createButton(new ItemStack(Items.ARROW), "§7返回"));

        GuiSession session = new GuiSession(GuiType.ADD_REWARD, admin);
        session.tempInventory = inv;
        sessions.put(admin.getUuid(), session);

        openGUI(admin, inv, 3, Text.literal("§8新建奖励"),
                (p, s) -> {
                    GuiSession sess = getSession(p);
                    if (sess == null) return true;
                    if (s == 26) { openRewardLibrary(p); return true; }
                    if (s == 22) {
                        for (int i = 0; i < 18; i++) sess.tempInventory.setStack(i, ItemStack.EMPTY);
                        MessageUtil.send(p, "§7已清空");
                        return true;
                    }
                    if (s == 18) {
                        boolean added = false;
                        for (int i = 0; i < 18; i++) {
                            ItemStack stack = sess.tempInventory.getStack(i);
                            if (!stack.isEmpty()) {
                                String displayName = stack.get(DataComponentTypes.CUSTOM_NAME) instanceof Text t
                                        ? t.getString() : stack.getItem().getName().getString();
                                RewardManager.getInstance().addReward(stack, displayName, "");
                                sess.tempInventory.setStack(i, ItemStack.EMPTY);
                                added = true;
                            }
                        }
                        if (added) {
                            MessageUtil.send(p, "§a奖励已保存");
                            openRewardLibrary(p);
                        } else {
                            MessageUtil.send(p, "§c请先放入物品");
                        }
                        return true;
                    }
                    return s >= 18 && s < 27;
                });
    }

    // ==================== BAN LIST ====================

    public static void openBanList(ServerPlayerEntity admin) {
        openBanList(admin, 0);
    }

    public static void openBanList(ServerPlayerEntity admin, int page) {
        List<BanEntry> bans = PlayerManager.getInstance().getBanList();
        int maxSlots = 45;
        int totalPages = Math.max(1, (int) Math.ceil(bans.size() / (double) maxSlots));
        int startIndex = page * maxSlots;
        SimpleInventory inv = new SimpleInventory(54);
        for (int i = 0; i < 9; i++) inv.setStack(i, ItemUtil.createBorder());
        for (int i = 45; i < 54; i++) inv.setStack(i, ItemUtil.createBorder());

        for (int i = 0; i < maxSlots && (startIndex + i) < bans.size(); i++) {
            BanEntry entry = bans.get(startIndex + i);
            ItemStack head = ItemUtil.getPlayerHead(entry.getPlayerName(), entry.getPlayerUuid());
            String timeStr = entry.isPermanent() ? "§c永久" : "§e" + PlayerManager.formatDuration(
                    Math.max(0, (entry.getExpiryTime() - System.currentTimeMillis()) / 1000));
            head.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§c" + entry.getPlayerName()));
            head.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                    Text.literal("§7原因: " + entry.getReason()),
                    Text.literal("§7执行人: " + entry.getBannedBy()),
                    Text.literal("§7剩余: " + timeStr),
                    Text.literal("§e点击解封")
            )));
            inv.setStack(9 + i, head);
        }

        if (page > 0) inv.setStack(45, ItemUtil.createButton(new ItemStack(Items.ARROW), "§7上一页"));
        if (page < totalPages - 1) inv.setStack(46, ItemUtil.createButton(new ItemStack(Items.ARROW), "§7下一页"));
        inv.setStack(53, ItemUtil.createButton(new ItemStack(Items.ARROW), "§7返回"));

        GuiSession session = new GuiSession(GuiType.BAN_LIST, admin);
        session.slotIndex = page;
        sessions.put(admin.getUuid(), session);

        openGUI(admin, inv, 6, Text.literal("§8黑名单管理 - 第" + (page + 1) + "页"),
                (p, s) -> {
                    GuiSession sess = getSession(p);
                    int curPage = sess != null ? sess.slotIndex : 0;
                    if (s == 53) { openMainPanel(p); return true; }
                    if (s == 45 && curPage > 0) { openBanList(p, curPage - 1); return true; }
                    if (s == 46 && curPage < totalPages - 1) { openBanList(p, curPage + 1); return true; }
                    if (s >= 9 && s < 9 + maxSlots) {
                        int banIndex = startIndex + (s - 9);
                        List<BanEntry> banList = PlayerManager.getInstance().getBanList();
                        if (banIndex >= 0 && banIndex < banList.size()) {
                            BanEntry entry = banList.get(banIndex);
                            PlayerManager.getInstance().unbanPlayer(entry.getPlayerUuid());
                            MessageUtil.send(p, "§a已解封 §b" + entry.getPlayerName());
                            openBanList(p, curPage);
                        }
                    }
                    return true;
                });
    }

    // ==================== DURATION SELECT ====================

    public static void openDurationSelect(ServerPlayerEntity admin, ServerPlayerEntity target, String actionType) {
        String[] labels = {"1小时", "3小时", "6小时", "12小时", "1天", "3天", "7天", "30天", "90天", "180天", "1年", "10年", "永久"};
        long[] durValues = {3600, 10800, 21600, 43200, 86400, 259200, 604800, 2592000, 7776000, 15552000, 31536000, 315360000, -1};

        SimpleInventory inv = new SimpleInventory(54);
        for (int i = 0; i < 9; i++) inv.setStack(i, ItemUtil.createBorder());
        for (int i = 45; i < 54; i++) inv.setStack(i, ItemUtil.createBorder());

        for (int i = 0; i < labels.length; i++) {
            int slot = 9 + i;
            ItemStack icon;
            if (durValues[i] == -1) icon = new ItemStack(Items.BEDROCK);
            else if (durValues[i] >= 86400) icon = new ItemStack(Items.CLOCK);
            else icon = new ItemStack(Items.SAND);
            inv.setStack(slot, ItemUtil.createButton(icon, "§6" + labels[i],
                    List.of(Text.literal("§7" + (durValues[i] == -1 ? "永久" : "时长: " + labels[i])))));
        }
        inv.setStack(53, ItemUtil.createButton(new ItemStack(Items.ARROW), "§7返回"));

        GuiSession session = new GuiSession(GuiType.DURATION_SELECT, admin);
        session.target = target;
        session.extraData = actionType;
        sessions.put(admin.getUuid(), session);

        openGUI(admin, inv, 6, Text.literal("§8选择时长 - " + target.getName().getString()),
                (p, s) -> {
                    GuiSession sess = getSession(p);
                    if (sess == null) return true;
                    if (s == 53) { openPlayerDetail(p, sess.target); return true; }
                    if (s >= 9 && s < 9 + labels.length) {
                        int idx = s - 9;
                        if (p.getUuid().equals(sess.target.getUuid())) {
                            MessageUtil.send(p, "§c不能对自己执行此操作");
                            return true;
                        }
                        long duration = durValues[idx];
                        String action = (String) sess.extraData;
                        openReasonSelect(p, sess.target, action, duration, labels[idx]);
                    }
                    return true;
                });
    }

    // ==================== REASON SELECT ====================

    public static void openReasonSelect(ServerPlayerEntity admin, ServerPlayerEntity target, String actionType, long duration, String durationLabel) {
        SimpleInventory inv = new SimpleInventory(27);
        List<String> reasons = ConfigManager.getConfig().getDefaultBanReasons();

        for (int i = 0; i < Math.min(reasons.size(), 18); i++) {
            inv.setStack(i, ItemUtil.createButton(new ItemStack(Items.PAPER), "§e" + reasons.get(i),
                    List.of(Text.literal("§7点击选择此原因"))));
        }
        inv.setStack(26, ItemUtil.createButton(new ItemStack(Items.ARROW), "§7返回"));

        GuiSession session = new GuiSession(GuiType.CONFIRM_ACTION, admin);
        session.target = target;
        session.extraData = new Object[]{actionType, duration, durationLabel};
        sessions.put(admin.getUuid(), session);

        openGUI(admin, inv, 3, Text.literal("§8选择原因"),
                (p, s) -> {
                    GuiSession sess = getSession(p);
                    if (sess == null) return true;
                    if (s == 26) { openDurationSelect(p, sess.target, (String)((Object[])sess.extraData)[0]); return true; }
                    Object[] data = (Object[]) sess.extraData;
                    String action = (String) data[0];
                    long dur = (long) data[1];
                    String durLabel = (String) data[2];
                    String reason = "违规行为";
                    if (s >= 0 && s < reasons.size()) reason = reasons.get(s);

                    String adminName = p.getName().getString();
                    if (action.equals("ban")) {
                        PlayerManager.getInstance().banPlayer(sess.target.getUuid(),
                                sess.target.getName().getString(), reason, dur, adminName);
                        MessageUtil.send(p, "§c已封禁 §b" + sess.target.getName().getString() + " §7(" + durLabel + ") §7原因: " + reason);
                    } else {
                        PlayerManager.getInstance().mutePlayer(sess.target.getUuid(),
                                sess.target.getName().getString(), reason, dur, adminName);
                        MessageUtil.send(p, "§e已禁言 §b" + sess.target.getName().getString() + " §7(" + durLabel + ") §7原因: " + reason);
                    }
                    openPlayerDetail(p, sess.target);
                    return true;
                });
    }

    // ==================== BROADCAST GIVE ====================

    public static void openBroadcastGive(ServerPlayerEntity admin, int page) {
        RewardManager rm = RewardManager.getInstance();
        List<RewardItem> rewards = rm.getAllRewards();
        int maxSlots = 36;
        int totalPages = Math.max(1, (int) Math.ceil(rewards.size() / (double) maxSlots));
        int startIndex = page * maxSlots;

        SimpleInventory inv = new SimpleInventory(54);
        for (int i = 0; i < 9; i++) inv.setStack(i, ItemUtil.createBorder());
        for (int i = 45; i < 54; i++) inv.setStack(i, ItemUtil.createBorder());

        for (int i = 0; i < maxSlots && (startIndex + i) < rewards.size(); i++) {
            RewardItem reward = rewards.get(startIndex + i);
            ItemStack display = reward.getItem().copy();
            Text name = reward.getDisplayName() != null
                    ? Text.literal("§e" + reward.getDisplayName())
                    : Text.literal("§e" + display.getItem().getName().getString());
            display.set(DataComponentTypes.CUSTOM_NAME, name);
            inv.setStack(9 + i, display);
        }

        if (page > 0) inv.setStack(45, ItemUtil.createButton(new ItemStack(Items.ARROW), "§7上一页"));
        if (page < totalPages - 1) inv.setStack(46, ItemUtil.createButton(new ItemStack(Items.ARROW), "§7下一页"));
        inv.setStack(49, ItemUtil.createButton(new ItemStack(Items.LIME_DYE), "§6广播发放",
                List.of(Text.literal("§7给当前所有在线玩家发放选中奖励"))));
        inv.setStack(53, ItemUtil.createButton(new ItemStack(Items.ARROW), "§7返回"));

        GuiSession session = new GuiSession(GuiType.GIVE_FROM_REWARD, admin);
        session.target = null;
        session.slotIndex = page;
        sessions.put(admin.getUuid(), session);

        openGUI(admin, inv, 6, Text.literal("§8广播发放"),
                (p, s) -> {
                    GuiSession sess = getSession(p);
                    if (sess == null) return true;
                    if (s == 45 && page > 0) { openBroadcastGive(p, page - 1); return true; }
                    if (s == 46 && page < totalPages - 1) { openBroadcastGive(p, page + 1); return true; }
                    if (s == 53) { openMainPanel(p); return true; }
                    if (s >= 9 && s < 9 + maxSlots) {
                        int rewardIndex = startIndex + (s - 9);
                        if (rewardIndex < rewards.size()) {
                            openBroadcastQuantitySelect(p, rewardIndex);
                        }
                    }
                    return true;
                });
    }

    public static void openBroadcastQuantitySelect(ServerPlayerEntity admin, int rewardIndex) {
        int[] amounts = {1, 8, 16, 32, 64};
        SimpleInventory inv = new SimpleInventory(9);
        for (int i = 0; i < 9; i++) inv.setStack(i, ItemUtil.createBorder());
        for (int i = 0; i < amounts.length; i++) {
            int a = amounts[i];
            inv.setStack(i, ItemUtil.createButton(new ItemStack(Items.CHEST), "§e×" + a,
                    List.of(Text.literal("§7每人发放 " + a + " 个"))));
        }
        inv.setStack(8, ItemUtil.createButton(new ItemStack(Items.ARROW), "§7返回"));

        GuiSession session = new GuiSession(GuiType.QUANTITY_SELECT, admin);
        session.slotIndex = rewardIndex;
        sessions.put(admin.getUuid(), session);

        openGUI(admin, inv, 1, Text.literal("§8选择发放数量"),
                (p, s) -> {
                    GuiSession sess = getSession(p);
                    if (sess == null) return true;
                    if (s == 8) { openBroadcastGive(p, 0); return true; }
                    if (s >= 0 && s < amounts.length) {
                        RewardItem reward = RewardManager.getInstance().getReward(sess.slotIndex);
                        if (reward == null) return true;
                        PlayerManager pm = PlayerManager.getInstance();
                        int onlineCount = 0;
                        int offlineCount = 0;
                        for (UUID targetUuid : pm.getKnownPlayers()) {
                            ServerPlayerEntity online = p.getServer().getPlayerManager().getPlayer(targetUuid);
                            ItemStack stack = reward.getItem().copy();
                            stack.setCount(amounts[s]);
                            if (online != null) {
                                if (!online.getInventory().insertStack(stack)) {
                                    if (!stack.isEmpty()) online.dropItem(stack, false);
                                }
                                MessageUtil.notifyTarget(online, "你收到了广播奖励: §e×" + amounts[s] + " " + (reward.getDisplayName() != null ? reward.getDisplayName() : stack.getItem().getName().getString()));
                                onlineCount++;
                            } else {
                                RewardManager.getInstance().addToOfflineQueue(targetUuid, stack);
                                offlineCount++;
                            }
                        }
                        String name = reward.getDisplayName() != null ? reward.getDisplayName() : reward.getItem().getItem().getName().getString();
                        MessageUtil.send(p, "§a广播发放完成: §e×" + amounts[s] + " " + name + " §7给 §b" + onlineCount + " §7名在线玩家" + (offlineCount > 0 ? ", §b" + offlineCount + " §7名离线玩家待领取" : ""));
                        openMainPanel(p);
                        return true;
                    }
                    return true;
                });
    }

    // ==================== UTILITY ====================

    private static void openGUI(ServerPlayerEntity player, SimpleInventory newInventory, int rows,
                                 Text title, BiFunction<ServerPlayerEntity, Integer, Boolean> clickHandler) {
        GuiSession session = getSession(player);
        if (session == null) {
            session = new GuiSession(GuiType.MAIN_PANEL, player);
            sessions.put(player.getUuid(), session);
        }
        session.handler = clickHandler;

        // Reuse existing screen with same row count to avoid cursor reset
        if (player.currentScreenHandler instanceof AdminScreenHandler ash
                && ash.syncId == player.currentScreenHandler.syncId
                && newInventory.size() == ash.getInventory().size()) {
            SimpleInventory existing = ash.getInventory();
            existing.clear();
            for (int i = 0; i < newInventory.size(); i++) {
                existing.setStack(i, newInventory.getStack(i));
            }
            return;
        }

        NamedScreenHandlerFactory factory = new NamedScreenHandlerFactory() {
            @Override
            public ScreenHandler createMenu(int syncId, net.minecraft.entity.player.PlayerInventory inv,
                                             net.minecraft.entity.player.PlayerEntity p) {
                return AdminScreenHandler.create(syncId, inv, newInventory, rows);
            }

            @Override
            public Text getDisplayName() { return title; }
        };
        player.openHandledScreen(factory);
    }
}
