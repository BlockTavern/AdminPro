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
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.world.GameMode;

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
        public int currentButton;
        public Runnable onClose;

        public GuiSession(GuiType type, ServerPlayerEntity admin) {
            this.type = type;
            this.admin = admin;
        }
    }

    public enum GuiType {
        MAIN_PANEL, PLAYER_DETAIL, GIVE_SELECT, GIVE_FROM_INVENTORY,
        GIVE_FROM_REWARD, REWARD_LIBRARY, BAN_LIST,
        DURATION_SELECT, CONFIRM_ACTION, VIEW_INVENTORY,
        ADD_REWARD, QUANTITY_SELECT, GAME_MODE_SELECT
    }

    public static GuiSession getSession(ServerPlayerEntity player) {
        return sessions.get(player.getUuid());
    }

    public static void removeSession(ServerPlayerEntity player) {
        sessions.remove(player.getUuid());
    }

    // ==================== LAYOUT HELPERS ====================

    private static ItemStack lightGlass() {
        ItemStack stack = new ItemStack(Items.BLACK_STAINED_GLASS_PANE);
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(" "));
        stack.set(DataComponentTypes.HIDE_TOOLTIP, net.minecraft.util.Unit.INSTANCE);
        return stack;
    }

    private static void applyAlternatingPattern(SimpleInventory inv, int[] slots, ItemStack[] items) {
        for (int row = 0; row < slots.length / 9; row++) {
            int rowStart = slots[row * 9];
            for (int col = 0; col < 9; col++) {
                int slot = rowStart + col;
                int itemIndex = row * 9 + col;
                if (itemIndex < items.length && items[itemIndex] != null) {
                    inv.setStack(slot, items[itemIndex]);
                } else if ((row + col) % 2 == 0) {
                    inv.setStack(slot, lightGlass());
                }
            }
        }
    }

    // ==================== MAIN PANEL (管理面板) ====================
    // 布局: 6行大箱子 (54格)
    //
    // Row 0: [边框] [边框] [边框] [边框] [管理面板标题] [边框] [边框] [边框] [边框]
    // Row 1: [玩家头像] [玩家头像] [玩家头像] [玩家头像] [玩家头像] [玩家头像] [玩家头像] [玩家头像] [玩家头像]
    // Row 2: [玩家头像] [玩家头像] [玩家头像] [玩家头像] [玩家头像] [玩家头像] [玩家头像] [玩家头像] [玩家头像]
    // Row 3: [玩家头像] [玩家头像] [玩家头像] [玩家头像] [玩家头像] [玩家头像] [玩家头像] [玩家头像] [玩家头像]
    // Row 4: [玩家头像] [玩家头像] [玩家头像] [玩家头像] [玩家头像] [玩家头像] [玩家头像] [玩家头像] [玩家头像]
    // Row 5: [上一页] [黑玻] [奖励库] [黑名单] [隐身模式] [黑玻] [刷新] [黑玻] [下一页/关闭]
    //
    // 操作: 点击玩家头像 → 进入玩家详情面板
    //       点击管理面板标题 → 显示在线人数和页码
    //       底部按钮: 翻页/奖励库/黑名单/隐身切换/刷新/关闭

    private static final int MAIN_PANEL_PAGE_SIZE = 36;

    private static int getVisiblePlayerCount(ServerPlayerEntity admin) {
        int count = 0;
        PlayerManager pm = PlayerManager.getInstance();
        for (ServerPlayerEntity online : admin.getServer().getPlayerManager().getPlayerList()) {
            if (admin.getUuid().equals(online.getUuid()) || !pm.isVanishedFull(online.getUuid())) {
                count++;
            }
        }
        return count;
    }

    public static void openMainPanel(ServerPlayerEntity admin) {
        openMainPanel(admin, 0);
    }

    public static void openMainPanel(ServerPlayerEntity admin, int page) {
        SimpleInventory inv = new SimpleInventory(54);

        for (int i = 0; i < 9; i++) inv.setStack(i, ItemUtil.createBorder());

        List<ServerPlayerEntity> visiblePlayers = new ArrayList<>();
        PlayerManager pm = PlayerManager.getInstance();
        for (ServerPlayerEntity online : admin.getServer().getPlayerManager().getPlayerList()) {
            if (admin.getUuid().equals(online.getUuid()) || !pm.isVanishedFull(online.getUuid())) {
                visiblePlayers.add(online);
            }
        }

        ItemStack playersLabel = new ItemStack(Items.PLAYER_HEAD);
        playersLabel.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§6§l管理面板"));
        playersLabel.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                Text.literal("§7在线玩家: §b" + visiblePlayers.size() + " §7人"),
                Text.literal("§7第 §f" + (page + 1) + " §7页"),
                Text.literal(""),
                Text.literal("§e点击玩家头像进行管理")
        )));
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

        UUID adminUuid = admin.getUuid();
        boolean isVL = PlayerManager.getInstance().isVanishedLight(adminUuid);
        boolean isVF = PlayerManager.getInstance().isVanishedFull(adminUuid);
        String vanishStatus = isVF ? "§c完全隐身" : isVL ? "§e轻隐身" : "§a可见";
        String vanishLore = isVF ? "§7完全隐身: 玩家列表不可见" : isVL ? "§7轻隐身: 玩家列表可见但不可见" : "§7点击切换隐身模式";

        // Row 5: [上一页] [玻璃] [奖励库] [黑名单] [隐身] [玻璃] [刷新] [玻璃] [关闭]
        if (page > 0) inv.setStack(45, ItemUtil.createButton(new ItemStack(Items.ARROW), "§7上一页"));
        else inv.setStack(45, lightGlass());
        inv.setStack(46, lightGlass());
        inv.setStack(47, ItemUtil.createButton(new ItemStack(Items.CHEST), "§6奖励库",
                List.of(Text.literal("§7查看、添加、删除奖励物品"))));
        inv.setStack(48, ItemUtil.createButton(new ItemStack(Items.WRITABLE_BOOK), "§6黑名单",
                List.of(Text.literal("§7查看和管理被封禁的玩家"))));
        inv.setStack(49, ItemUtil.createButton(new ItemStack(isVF ? Items.WHITE_STAINED_GLASS : isVL ? Items.GRAY_STAINED_GLASS : Items.GLASS), "§7隐身: §r" + vanishStatus,
                List.of(Text.literal(vanishLore), Text.literal("§e点击切换"))));
        inv.setStack(50, lightGlass());
        inv.setStack(51, ItemUtil.createButton(new ItemStack(Items.COMPASS), "§7刷新",
                List.of(Text.literal("§7刷新在线玩家列表"))));
        inv.setStack(52, lightGlass());
        if (page < totalPages - 1) inv.setStack(53, ItemUtil.createButton(new ItemStack(Items.ARROW), "§7下一页"));
        else inv.setStack(53, ItemUtil.createButton(new ItemStack(Items.BARRIER), "§c关闭"));

        GuiSession session = new GuiSession(GuiType.MAIN_PANEL, admin);
        session.slotIndex = page;
        sessions.put(admin.getUuid(), session);

        int finalPage = page;
        openGUI(admin, inv, 6, Text.literal("§8管理面板 - 第" + (page + 1) + "页"),
                (p, s) -> { handleMainPanelClick(p, s, finalPage); return true; });
    }

    private static void handleMainPanelClick(ServerPlayerEntity admin, int slot, int page) {
        int totalPages = Math.max(1, (int) Math.ceil(getVisiblePlayerCount(admin) / (double) MAIN_PANEL_PAGE_SIZE));
        if (slot == 53 && page >= totalPages - 1) { admin.closeHandledScreen(); removeSession(admin); return; }
        if (slot == 53 && page < totalPages - 1) { openMainPanel(admin, page + 1); return; }
        if (slot == 51) { openMainPanel(admin, page); return; }
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

    // ==================== PLAYER DETAIL (玩家详情) ====================
    // 布局: 6行大箱子 (54格)
    //
    // Row 0: [黑玻] [黑玻] [黑玻] [黑玻] [玩家头像] [黑玻] [黑玻] [黑玻] [黑玻]
    // Row 1: [黑玻] [坐标] [黑玻] [传送]  [黑玻]   [旁观] [黑玻] [背包] [黑玻]
    // Row 2: [发放] [黑玻] [禁言] [黑玻]  [封禁]   [黑玻] [游戏模式] [黑玻] [黑玻]
    // Row 3: [灰玻] [灰玻] [灰玻] [灰玻] [灰玻]   [灰玻] [灰玻] [灰玻] [灰玻]
    // Row 4: [玩家信息] [...]  [...]  [...]  [...]   [...]  [...]  [...]  [返回]
    // Row 5: [黑玻] [黑玻] [黑玻] [黑玻] [黑玻]   [黑玻] [黑玻] [黑玻] [黑玻]
    //
    // 操作: 坐标→聊天显示可点击传送 | 传送→直接tp | 旁观→进入旁观模式跟随
    //       背包→查看/操作目标背包 | 发放→选择发放方式 | 禁言/封禁→选择时长
    //       游戏模式→设置生存/创造/冒险/旁观 | 玩家头像→显示详细状态
    // 布局: 6行大箱子 (54格)
    //
    // Row 0: [黑玻] [黑玻] [黑玻] [黑玻] [玩家头颅] [黑玻] [黑玻] [黑玻] [黑玻]
    // Row 1: [黑玻] [查看坐标] [黑玻] [传送过去] [黑玻] [旁观] [黑玻] [查看背包] [黑玻]
    // Row 2: [发放物品] [黑玻] [禁言] [黑玻] [封禁] [黑玻] [游戏模式] [黑玻] [黑玻]
    // Row 3: [灰玻] [灰玻] [灰玻] [灰玻] [灰玻] [灰玻] [灰玻] [灰玻] [灰玻]
    // Row 4: [玩家信息] [黑玻] [黑玻] [黑玻] [黑玻] [黑玻] [黑玻] [黑玻] [返回]
    // Row 5: [黑玻] [黑玻] [黑玻] [黑玻] [黑玻] [黑玻] [黑玻] [黑玻] [黑玻]
    //
    // 操作: 点击头颅 → 显示详细信息(生命/食物/模式)
    //       查看坐标 → 聊天栏显示坐标+可点击传送
    //       传送过去 → 直接传送到玩家位置(不可见)
    //       旁观 → 进入旁观模式跟随玩家视角
    //       查看背包 → 打开玩家背包查看/操作
    //       发放物品 → 选择发放方式
    //       禁言 → 切换禁言状态(已禁言则解除)
    //       封禁 → 切换封禁状态(已封禁则解封)
    //       游戏模式 → 进入游戏模式设置面板
    //       返回 → 回到主面板

    public static void openPlayerDetail(ServerPlayerEntity admin, ServerPlayerEntity target) {
        SimpleInventory inv = new SimpleInventory(54);

        boolean isSelf = admin.getUuid().equals(target.getUuid());

        for (int i = 0; i < 9; i++) inv.setStack(i, lightGlass());
        for (int i = 45; i < 54; i++) inv.setStack(i, lightGlass());

        ItemStack head = ItemUtil.getPlayerHead(target.getName().getString(), target.getUuid());
        head.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§b§l" + target.getName().getString()));
        head.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                Text.literal("§7延迟: " + target.networkHandler.getLatency() + "ms"),
                Text.literal("§7生命: §c" + Math.round(target.getHealth()) + " ❤"),
                Text.literal("§7食物: §6" + target.getHungerManager().getFoodLevel()),
                Text.literal("§7模式: §a" + target.interactionManager.getGameMode().asString()),
                Text.literal(""),
                Text.literal("§e点击查看详细信息")
        )));
        inv.setStack(4, head);

        // Row 1: gap-item-gap-item-gap-item-gap-item-gap
        inv.setStack(10, ItemUtil.createButton(new ItemStack(Items.COMPASS), "§b查看坐标",
                List.of(Text.literal("§7查看 " + target.getName().getString() + " 的位置"),
                        Text.literal("§e点击可传送到该玩家"))));
        inv.setStack(12, ItemUtil.createButton(new ItemStack(Items.END_PORTAL_FRAME), "§d传送过去",
                List.of(Text.literal("§7传送到 " + target.getName().getString() + " 的位置"),
                        Text.literal("§8操作对玩家不可见"))));
        inv.setStack(14, ItemUtil.createButton(new ItemStack(Items.SPYGLASS), "§e旁观",
                List.of(Text.literal("§7跟随 " + target.getName().getString() + " 的视角"),
                        Text.literal("§7进入旁观模式观察该玩家"))));
        inv.setStack(16, ItemUtil.createButton(new ItemStack(Items.SHULKER_BOX), "§b查看背包",
                List.of(Text.literal("§7查看 " + target.getName().getString() + " 的背包"))));

        // Row 2: item-gap-item-gap-item-gap-item-gap-item
        inv.setStack(18, ItemUtil.createButton(new ItemStack(Items.DISPENSER), "§6发放物品",
                List.of(Text.literal("§7向 " + target.getName().getString() + " 发放物品"))));

        if (isSelf) {
            inv.setStack(20, ItemUtil.createButton(new ItemStack(Items.BARRIER), "§7不能对自己操作",
                    List.of(Text.literal("§c无法对自己执行此操作"))));
        } else if (PlayerManager.getInstance().isMuted(target.getUuid())) {
            inv.setStack(20, ItemUtil.createButton(new ItemStack(Items.LIME_DYE), "§a解除禁言",
                    List.of(Text.literal("§7解除 " + target.getName().getString() + " 的禁言"))));
        } else {
            inv.setStack(20, ItemUtil.createButton(new ItemStack(Items.NOTE_BLOCK), "§e禁言",
                    List.of(Text.literal("§7禁止 " + target.getName().getString() + " 发言"))));
        }

        if (isSelf) {
            inv.setStack(22, ItemUtil.createButton(new ItemStack(Items.BARRIER), "§7不能对自己操作",
                    List.of(Text.literal("§c无法对自己执行此操作"))));
        } else if (PlayerManager.getInstance().isBanned(target.getUuid())) {
            inv.setStack(22, ItemUtil.createButton(new ItemStack(Items.EMERALD_BLOCK), "§a解封",
                    List.of(Text.literal("§7解封 " + target.getName().getString()))));
        } else {
            inv.setStack(22, ItemUtil.createButton(new ItemStack(Items.SOUL_SAND), "§c封禁",
                    List.of(Text.literal("§7封禁 " + target.getName().getString()))));
        }

        inv.setStack(24, ItemUtil.createButton(new ItemStack(Items.COMMAND_BLOCK), "§d游戏模式",
                List.of(Text.literal("§7设置 " + target.getName().getString() + " 的游戏模式"))));

        // Row 3: filler
        for (int i = 27; i < 36; i++) inv.setStack(i, ItemUtil.createFiller());

        // Row 4: player info + back
        ItemStack playerInfo = new ItemStack(Items.MAP);
        playerInfo.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§6玩家信息"));
        playerInfo.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                Text.literal("§7名称: §f" + target.getName().getString()),
                Text.literal("§7UUID: §f" + target.getUuid().toString().replace("-", "")),
                Text.literal("§7游戏模式: §f" + target.interactionManager.getGameMode().asString()),
                Text.literal("§7位置: §f" + String.format("%.0f, %.0f, %.0f", target.getX(), target.getY(), target.getZ())),
                Text.literal("§7世界: §f" + target.getServerWorld().getRegistryKey().getValue().toString())
        )));
        inv.setStack(36, playerInfo);
        inv.setStack(53, ItemUtil.createButton(new ItemStack(Items.ARROW), "§7返回"));

        GuiSession session = new GuiSession(GuiType.PLAYER_DETAIL, admin);
        session.target = target;
        sessions.put(admin.getUuid(), session);

        openGUI(admin, inv, 6, Text.literal("§8管理: " + target.getName().getString()),
                (p, s) -> { handlePlayerDetailClick(p, s); return true; });
    }

    private static void handlePlayerDetailClick(ServerPlayerEntity admin, int slot) {
        GuiSession session = getSession(admin);
        if (session == null || session.target == null) return;
        ServerPlayerEntity target = session.target;
        boolean isSelf = admin.getUuid().equals(target.getUuid());

        switch (slot) {
            case 4 -> MessageUtil.send(admin, "§7玩家: §b" + target.getName().getString()
                    + "\n§7状态: §a在线\n§7生命: §c" + Math.round(target.getHealth()) + " ❤\n§7食物: §6" + target.getHungerManager().getFoodLevel()
                    + "\n§7模式: §a" + target.interactionManager.getGameMode().asString());
            case 10 -> {
                String coordMsg = "§b" + target.getName().getString() + " §7的坐标: "
                        + "§f" + target.getServerWorld().getRegistryKey().getValue().toString()
                        + " §7X:§f" + String.format("%.1f", target.getX())
                        + " Y:§f" + String.format("%.1f", target.getY())
                        + " Z:§f" + String.format("%.1f", target.getZ());
                Text teleportText = Text.literal(coordMsg + " §a[传送到此处]")
                        .setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                "/tp " + admin.getName().getString() + " " + target.getX() + " " + target.getY() + " " + target.getZ())));
                admin.sendMessage(teleportText, false);
            }
            case 12 -> {
                admin.teleport(target.getServerWorld(), target.getX(), target.getY(), target.getZ(), target.getYaw(), target.getPitch());
                MessageUtil.send(admin, "§a已传送到 §b" + target.getName().getString() + " §7的位置");
            }
            case 14 -> {
                if (isSelf) return;
                admin.changeGameMode(GameMode.SPECTATOR);
                admin.teleport(target.getServerWorld(), target.getX(), target.getY(), target.getZ(), target.getYaw(), target.getPitch());
                MessageUtil.send(admin, "§e已进入旁观模式，正在跟随 §b" + target.getName().getString() + " §e的视角");
                MessageUtil.send(admin, "§7输入 /gamemode creative 切回创造模式");
                admin.closeHandledScreen();
                removeSession(admin);
            }
            case 16 -> openViewInventory(admin, target);
            case 18 -> openGiveSelect(admin, target);
            case 20 -> {
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
            case 22 -> {
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
            case 24 -> openGameModeSelect(admin, target);
            case 53 -> openMainPanel(admin);
        }
    }

    // ==================== GAME MODE SELECT (游戏模式设置) ====================
    // 布局: 6行大箱子 (54格)
    //
    // Row 0: [黑玻] [黑玻] [黑玻] [黑玻] [玩家头像+当前模式] [黑玻] [黑玻] [黑玻] [黑玻]
    // Row 1: [黑玻] [生存] [黑玻] [创造] [黑玻] [冒险] [黑玻] [旁观] [黑玻]
    // Row 2-4: [黑玻...]
    // Row 4: [...]  [...]  [...]  [...]  [...]   [...]  [...]  [...]  [返回]
    // Row 5: [黑玻...]
    //
    // 当前模式用青柠玻璃+粗体高亮显示
    // 操作: 点击模式→切换游戏模式并刷新面板
    // 布局: 6行大箱子 (54格)
    //
    // Row 0: [黑玻] [黑玻] [黑玻] [黑玻] [玩家头颅+当前模式] [黑玻] [黑玻] [黑玻] [黑玻]
    // Row 1: [黑玻] [生存模式] [黑玻] [创造模式] [黑玻] [冒险模式] [黑玻] [旁观模式] [黑玻]
    // Row 2: [黑玻] [黑玻] [黑玻] [黑玻] [黑玻] [黑玻] [黑玻] [黑玻] [黑玻]
    // Row 3: [黑玻] [黑玻] [黑玻] [黑玻] [黑玻] [黑玻] [黑玻] [黑玻] [黑玻]
    // Row 4: [黑玻] [黑玻] [黑玻] [黑玻] [黑玻] [黑玻] [黑玻] [黑玻] [返回]
    // Row 5: [黑玻] [黑玻] [黑玻] [黑玻] [黑玻] [黑玻] [黑玻] [黑玻] [黑玻]
    //
    // 操作: 点击游戏模式 → 切换玩家游戏模式(当前模式高亮绿色)
    //       返回 → 回到玩家详情面板

    public static void openGameModeSelect(ServerPlayerEntity admin, ServerPlayerEntity target) {
        SimpleInventory inv = new SimpleInventory(54);
        for (int i = 0; i < 9; i++) inv.setStack(i, lightGlass());
        for (int i = 45; i < 54; i++) inv.setStack(i, lightGlass());

        ItemStack head = ItemUtil.getPlayerHead(target.getName().getString(), target.getUuid());
        head.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§6§l设置游戏模式 - " + target.getName().getString()));
        head.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                Text.literal("§7当前模式: §a" + target.interactionManager.getGameMode().asString())
        )));
        inv.setStack(4, head);

        GameMode current = target.interactionManager.getGameMode();

        // Row 1: gap-item-gap-item-gap-item-gap-item-gap
        inv.setStack(10, ItemUtil.createButton(
                new ItemStack(current == GameMode.SURVIVAL ? Items.LIME_STAINED_GLASS : Items.GRASS_BLOCK),
                (current == GameMode.SURVIVAL ? "§a§l" : "§6") + "生存模式",
                List.of(Text.literal("§7切换到生存模式" + (current == GameMode.SURVIVAL ? " §8(当前)" : "")))));
        inv.setStack(12, ItemUtil.createButton(
                new ItemStack(current == GameMode.CREATIVE ? Items.LIME_STAINED_GLASS : Items.BEDROCK),
                (current == GameMode.CREATIVE ? "§a§l" : "§6") + "创造模式",
                List.of(Text.literal("§7切换到创造模式" + (current == GameMode.CREATIVE ? " §8(当前)" : "")))));
        inv.setStack(14, ItemUtil.createButton(
                new ItemStack(current == GameMode.ADVENTURE ? Items.LIME_STAINED_GLASS : Items.MAP),
                (current == GameMode.ADVENTURE ? "§a§l" : "§6") + "冒险模式",
                List.of(Text.literal("§7切换到冒险模式" + (current == GameMode.ADVENTURE ? " §8(当前)" : "")))));
        inv.setStack(16, ItemUtil.createButton(
                new ItemStack(current == GameMode.SPECTATOR ? Items.LIME_STAINED_GLASS : Items.SPYGLASS),
                (current == GameMode.SPECTATOR ? "§a§l" : "§6") + "旁观模式",
                List.of(Text.literal("§7切换到旁观模式" + (current == GameMode.SPECTATOR ? " §8(当前)" : "")))));

        inv.setStack(53, ItemUtil.createButton(new ItemStack(Items.ARROW), "§7返回"));

        GuiSession session = new GuiSession(GuiType.GAME_MODE_SELECT, admin);
        session.target = target;
        sessions.put(admin.getUuid(), session);

        openGUI(admin, inv, 6, Text.literal("§8游戏模式 - " + target.getName().getString()),
                (p, s) -> {
                    GuiSession sess = getSession(p);
                    if (sess == null || sess.target == null) return true;
                    if (s == 53) { openPlayerDetail(p, sess.target); return true; }
                    GameMode mode = null;
                    if (s == 10) mode = GameMode.SURVIVAL;
                    else if (s == 12) mode = GameMode.CREATIVE;
                    else if (s == 14) mode = GameMode.ADVENTURE;
                    else if (s == 16) mode = GameMode.SPECTATOR;
                    if (mode != null) {
                        sess.target.changeGameMode(mode);
                        MessageUtil.send(p, "§a已将 §b" + sess.target.getName().getString() + " §a的游戏模式设置为 §e" + mode.asString());
                        MessageUtil.notifyTarget(sess.target, "§a你的游戏模式已被设置为 §e" + mode.asString());
                        openGameModeSelect(p, sess.target);
                    }
                    return true;
                });
    }

    // ==================== VIEW INVENTORY (查看背包) ====================
    // 布局: 6行大箱子 (54格)
    //
    // Row 0: [0-8]   目标背包第1行 (hotbar)
    // Row 1: [9-17]  目标背包第2行
    // Row 2: [18-26] 目标背包第3行
    // Row 3: [27-35] 目标背包第4行
    // Row 4: [副手] [黑玻...] [返回@53]
    // Row 5: [头盔] [胸甲] [护腿] [靴子] [黑玻...] [返回]
    //
    // 操作: 左键可拿出/放入物品 (SyncSlot同步到目标玩家)
    //       返回→回到玩家详情
    // 布局: 6行大箱子 (54格)
    //
    // Row 0: [物品] [物品] [物品] [物品] [物品] [物品] [物品] [物品] [物品]
    // Row 1: [物品] [物品] [物品] [物品] [物品] [物品] [物品] [物品] [物品]
    // Row 2: [物品] [物品] [物品] [物品] [物品] [物品] [物品] [物品] [物品]
    // Row 3: [物品] [物品] [物品] [物品] [物品] [物品] [物品] [物品] [物品]
    // Row 4: [副手] [黑玻] [黑玻] [黑玻] [黑玻] [黑玻] [黑玻] [黑玻] [返回]
    // Row 5: [头盔] [胸甲] [腿甲] [靴子] [黑玻] [黑玻] [黑玻] [黑玻] [黑玻]
    //
    // 操作: 左键 → 正常操作物品(拿出/放入/移动，修改同步到目标玩家)
    //       返回 → 回到玩家详情面板

    public static void openViewInventory(ServerPlayerEntity admin, ServerPlayerEntity target) {
        SimpleInventory inv = new SimpleInventory(54);

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int guiSlot = row * 9 + col;
                int mcSlot = 9 + row * 9 + col;
                ItemStack stack = target.getInventory().getStack(mcSlot);
                if (!stack.isEmpty()) inv.setStack(guiSlot, stack.copy());
            }
        }

        for (int col = 0; col < 9; col++) {
            int guiSlot = 27 + col;
            int mcSlot = col;
            ItemStack stack = target.getInventory().getStack(mcSlot);
            if (!stack.isEmpty()) inv.setStack(guiSlot, stack.copy());
        }

        ItemStack[] armor = {
                target.getInventory().getArmorStack(3),
                target.getInventory().getArmorStack(2),
                target.getInventory().getArmorStack(1),
                target.getInventory().getArmorStack(0)
        };
        for (int i = 0; i < 4; i++) {
            if (!armor[i].isEmpty()) inv.setStack(36 + i, armor[i].copy());
        }

        for (int i = 41; i <= 43; i++) inv.setStack(i, ItemUtil.createBorder());

        ItemStack offhand = target.getOffHandStack();
        if (!offhand.isEmpty()) inv.setStack(44, offhand.copy());

        for (int i = 45; i < 53; i++) inv.setStack(i, lightGlass());
        inv.setStack(53, ItemUtil.createButton(new ItemStack(Items.ARROW), "§7返回"));

        GuiSession session = new GuiSession(GuiType.VIEW_INVENTORY, admin);
        session.target = target;
        sessions.put(admin.getUuid(), session);

        openGUI(admin, inv, 6, Text.literal("§8背包: " + target.getName().getString()),
                (p, s) -> {
                    if (s == 53) {
                        openPlayerDetail(p, target);
                        return true;
                    }
                    return false;
                });

        if (admin.currentScreenHandler instanceof AdminScreenHandler ash) {
            ash.setTargetPlayer(target);
        }
    }

    // ==================== GIVE SELECT (选择发放方式) ====================
    // 布局: 6行大箱子 (54格)
    //
    // Row 0: [黑玻] [黑玻] [黑玻] [黑玻] [选择发放方式] [黑玻] [黑玻] [黑玻] [黑玻]
    // Row 1-3: [黑玻...]
    // Row 2: [黑玻] [黑玻] [黑玻] [黑玻] [从背包扣取] [黑玻] [从奖励库] [黑玻] [广播发放]
    // Row 4: [黑玻...] [返回@44]
    // Row 5: [黑玻...]
    //
    // 操作: 从背包扣取→打开物品放置界面 | 从奖励库→打开奖励选择
    //       广播发放→给所有在线玩家发放 | 取消→返回玩家详情
    // 布局: 6行大箱子 (54格)
    //
    // Row 0: [黑玻] [黑玻] [黑玻] [黑玻] [选择发放方式标题] [黑玻] [黑玻] [黑玻] [黑玻]
    // Row 1: [黑玻] [黑玻] [黑玻] [黑玻] [黑玻] [黑玻] [黑玻] [黑玻] [黑玻]
    // Row 2: [黑玻] [黑玻] [从背包扣取] [黑玻] [从奖励库选择] [黑玻] [广播发放] [黑玻] [黑玻]
    // Row 3: [黑玻] [黑玻] [黑玻] [黑玻] [黑玻] [黑玻] [黑玻] [黑玻] [黑玻]
    // Row 4: [黑玻] [黑玻] [黑玻] [黑玻] [返回] [黑玻] [黑玻] [黑玻] [黑玻]
    // Row 5: [黑玻] [黑玻] [黑玻] [黑玻] [黑玻] [黑玻] [黑玻] [黑玻] [黑玻]
    //
    // 操作: 从背包扣取 → 从管理员背包放入物品发放
    //       从奖励库选择 → 从奖励库选择物品发放
    //       广播发放 → 给所有在线玩家发放物品
    //       返回 → 回到玩家详情面板

    public static void openGiveSelect(ServerPlayerEntity admin, ServerPlayerEntity target) {
        SimpleInventory inv = new SimpleInventory(54);
        for (int i = 0; i < 9; i++) inv.setStack(i, lightGlass());
        for (int i = 45; i < 54; i++) inv.setStack(i, lightGlass());

        inv.setStack(4, ItemUtil.createButton(new ItemStack(Items.DISPENSER), "§6§l选择发放方式"));

        inv.setStack(20, ItemUtil.createButton(new ItemStack(Items.SHULKER_BOX), "§6从背包扣取",
                List.of(Text.literal("§7从你的背包中扣取物品发放"))));
        inv.setStack(22, ItemUtil.createButton(new ItemStack(Items.GOLD_BLOCK), "§6从奖励库选择",
                List.of(Text.literal("§7从奖励库中发放物品"))));
        inv.setStack(24, ItemUtil.createButton(new ItemStack(Items.ENDER_CHEST), "§6广播发放",
                List.of(Text.literal("§7给当前所有在线玩家发放物品"))));

        inv.setStack(53, ItemUtil.createButton(new ItemStack(Items.ARROW), "§7返回"));

        GuiSession session = new GuiSession(GuiType.GIVE_SELECT, admin);
        session.target = target;
        sessions.put(admin.getUuid(), session);

        openGUI(admin, inv, 6, Text.literal("§8选择发放方式"),
                (p, s) -> {
                    GuiSession sess = getSession(p);
                    if (sess == null) return true;
                    if (sess.target == null) {
                        if (s == 53) { openMainPanel(p); return true; }
                        if (s == 24) { openBroadcastGive(p, 0); return true; }
                        return true;
                    }
                    if (s == 20) openGiveFromInventory(p, sess.target);
                    else if (s == 22) openGiveFromReward(p, sess.target, 0);
                    else if (s == 24) { openBroadcastGive(p, 0); return true; }
                    else if (s == 53) openPlayerDetail(p, sess.target);
                    return true;
                });
    }

    // ==================== GIVE FROM INVENTORY (从背包发放) ====================
    // 布局: 3行大箱子 (27格)
    //
    // Row 0: [空] [空] [空] [空] [空] [空] [空] [空] [空]
    // Row 1: [空] [空] [空] [空] [空] [空] [空] [空] [空]
    // Row 2: [确认发放(通知)] [确认发放(不通知)] [空] [空] [空] [空] [空] [空] [返回]
    //
    // 操作: 在上方空格放入物品 → 点击确认发放
    //       确认发放(通知) → 发放物品并通知目标玩家
    //       确认发放(不通知) → 静默发放物品
    //       返回 → 回到选择发放方式

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
                                ItemStack returnStack = original.copy();
                                if (!p.getInventory().insertStack(returnStack)) {
                                    p.dropItem(returnStack, false);
                                }
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

        if (admin.currentScreenHandler instanceof AdminScreenHandler ash) {
            session.tempInventory = ash.getInventory();
        }
    }

    // ==================== QUANTITY SELECT (选择数量) ====================
    // 布局: 6行大箱子 (54格)
    //
    // Row 0: [黑玻] [黑玻] [黑玻] [黑玻] [选择数量标题] [黑玻] [黑玻] [黑玻] [黑玻]
    // Row 1: [黑玻] [黑玻] [黑玻] [×1] [×8] [×16] [×32] [×64] [黑玻]
    // Row 2: [黑玻] [黑玻] [黑玻] [黑玻] [黑玻] [黑玻] [黑玻] [黑玻] [黑玻]
    // Row 3: [黑玻] [黑玻] [黑玻] [黑玻] [黑玻] [黑玻] [黑玻] [黑玻] [黑玻]
    // Row 4: [黑玻] [黑玻] [黑玻] [黑玻] [返回] [黑玻] [黑玻] [黑玻] [黑玻]
    // Row 5: [黑玻] [黑玻] [黑玻] [黑玻] [黑玻] [黑玻] [黑玻] [黑玻] [黑玻]
    //
    // 操作: 点击数量 → 发放对应数量的奖励物品给目标玩家
    //       返回 → 回到奖励库发放面板

    public static void openQuantitySelect(ServerPlayerEntity admin, ServerPlayerEntity target, int rewardIndex) {
        SimpleInventory inv = new SimpleInventory(54);
        for (int i = 0; i < 9; i++) inv.setStack(i, lightGlass());
        for (int i = 45; i < 54; i++) inv.setStack(i, lightGlass());

        inv.setStack(4, ItemUtil.createButton(new ItemStack(Items.CHEST), "§6§l选择数量"));

        int[] amounts = {1, 8, 16, 32, 64};
        for (int i = 0; i < amounts.length; i++) {
            int a = amounts[i];
            inv.setStack(19 + i, ItemUtil.createButton(new ItemStack(Items.CHEST), "§e×" + a,
                    List.of(Text.literal("§7发放 " + a + " 个"))));
        }

        inv.setStack(53, ItemUtil.createButton(new ItemStack(Items.ARROW), "§7返回"));

        GuiSession session = new GuiSession(GuiType.QUANTITY_SELECT, admin);
        session.target = target;
        session.slotIndex = rewardIndex;
        sessions.put(admin.getUuid(), session);

        openGUI(admin, inv, 6, Text.literal("§8选择数量"),
                (p, s) -> {
                    GuiSession sess = getSession(p);
                    if (sess == null) return true;
                    if (s == 53) { openGiveFromReward(p, sess.target, 0); return true; }
                    if (s >= 19 && s < 19 + amounts.length) {
                        int idx = s - 19;
                        RewardItem reward = RewardManager.getInstance().getReward(sess.slotIndex);
                        if (reward != null) {
                            ItemStack stack = reward.getItem().copy();
                            stack.setCount(amounts[idx]);
                            boolean given = sess.target.getInventory().insertStack(stack.copy());
                            if (!given && !stack.isEmpty()) {
                                sess.target.dropItem(stack, false);
                            }
                            String name = reward.getDisplayName() != null ? reward.getDisplayName() : stack.getItem().getName().getString();
                            MessageUtil.send(p, "§a已发放 §e×" + amounts[idx] + " §b" + name + " §7给 §b" + sess.target.getName().getString());
                            MessageUtil.notifyTarget(sess.target, "你收到了 §e×" + amounts[idx] + " §b" + name);
                        }
                        openGiveFromReward(p, sess.target, 0);
                        return true;
                    }
                    return true;
                });
    }

    // ==================== GIVE FROM REWARD (从奖励库发放) ====================
    // 布局: 6行大箱子 (54格)
    //
    // Row 0: [边框] [边框] [边框] [边框] [边框] [边框] [边框] [边框] [边框]
    // Row 1: [奖励物品] [奖励物品] [奖励物品] [奖励物品] [奖励物品] [奖励物品] [奖励物品] [奖励物品] [奖励物品]
    // Row 2: [奖励物品] [奖励物品] [奖励物品] [奖励物品] [奖励物品] [奖励物品] [奖励物品] [奖励物品] [奖励物品]
    // Row 3: [奖励物品] [奖励物品] [奖励物品] [奖励物品] [奖励物品] [奖励物品] [奖励物品] [奖励物品] [奖励物品]
    // Row 4: [奖励物品] [奖励物品] [奖励物品] [奖励物品] [返回] [奖励物品] [奖励物品] [奖励物品] [奖励物品]
    // Row 5: [上一页] [下一页] [边框] [边框] [边框] [边框] [边框] [边框] [边框]
    //
    // 操作: 点击奖励物品 → 进入数量选择面板
    //       上一页/下一页 → 翻页浏览奖励库
    //       返回 → 回到选择发放方式

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
            display.setCount(1);
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

    // ==================== REWARD LIBRARY (奖励库管理) ====================
    // 布局: 6行大箱子 (54格)
    //
    // Row 0: [边框] [边框] [边框] [边框] [边框] [边框] [边框] [边框] [边框]
    // Row 1: [物品] [白玻] [物品] [白玻] [物品] [白玻] [物品] [白玻] [物品]
    // Row 2: [白玻] [物品] [白玻] [物品] [白玻] [物品] [白玻] [物品] [白玻]
    // Row 3: [物品] [白玻] [物品] [白玻] [物品] [白玻] [物品] [白玻] [物品]
    // Row 4: [白玻] [物品] [白玻] [物品] [返回] [白玻] [新建奖励] [白玻] [物品]
    // Row 5: [上一页] [下一页] [边框] [边框] [边框] [边框] [边框] [边框] [边框]
    //
    // 操作: 左键 → 正常操作(拖动/拿出/放入/移动/换位)
    //       右键 → 删除该奖励
    //       操作后自动同步奖励库顺序
    //       新建奖励 → 进入新建奖励面板
    //       上一页/下一页 → 翻页浏览
    //       返回 → 回到主面板

    public static void openRewardLibrary(ServerPlayerEntity admin) {
        openRewardLibrary(admin, 0);
    }

    private static ItemStack whiteGlass() {
        ItemStack stack = new ItemStack(Items.WHITE_STAINED_GLASS_PANE);
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(" "));
        stack.set(DataComponentTypes.HIDE_TOOLTIP, net.minecraft.util.Unit.INSTANCE);
        return stack;
    }

    public static void openRewardLibrary(ServerPlayerEntity admin, int page) {
        SimpleInventory inv = new SimpleInventory(54);
        List<RewardItem> rewards = RewardManager.getInstance().getAllRewards();
        int maxSlots = 36;
        int totalPages = Math.max(1, (int) Math.ceil(rewards.size() / (double) maxSlots));
        int startIndex = page * maxSlots;

        for (int i = 0; i < 9; i++) inv.setStack(i, ItemUtil.createBorder());
        for (int i = 45; i < 54; i++) inv.setStack(i, ItemUtil.createBorder());

        for (int i = 0; i < maxSlots; i++) {
            int rewardIndex = startIndex + i;
            if (rewardIndex < rewards.size()) {
                RewardItem reward = rewards.get(rewardIndex);
                ItemStack display = reward.getItem().copy();
                display.setCount(1);
                inv.setStack(9 + i, display);
            }
        }

        if (page > 0) inv.setStack(45, ItemUtil.createButton(new ItemStack(Items.ARROW), "§7上一页"));
        if (page < totalPages - 1) inv.setStack(46, ItemUtil.createButton(new ItemStack(Items.ARROW), "§7下一页"));
        inv.setStack(47, ItemUtil.createButton(new ItemStack(Items.ANVIL), "§b新建奖励",
                List.of(Text.literal("§7创建新的奖励物品"))));
        inv.setStack(53, ItemUtil.createButton(new ItemStack(Items.ARROW), "§7返回"));

        GuiSession session = new GuiSession(GuiType.REWARD_LIBRARY, admin);
        session.slotIndex = page;
        session.onClose = () -> syncRewardsFromGUI(admin, startIndex, maxSlots);
        sessions.put(admin.getUuid(), session);

        openGUI(admin, inv, 6, Text.literal("§8奖励库管理 - 第" + (page + 1) + "页"),
                (p, s) -> {
                    GuiSession sess = getSession(p);
                    if (sess == null) return true;
                    if (s == 53) { syncRewardsFromGUI(p, startIndex, maxSlots); openMainPanel(p); return true; }
                    if (s == 47) { syncRewardsFromGUI(p, startIndex, maxSlots); openAddReward(p); return true; }
                    if (s == 45 && page > 0) { syncRewardsFromGUI(p, startIndex, maxSlots); openRewardLibrary(p, page - 1); return true; }
                    if (s == 46 && page < totalPages - 1) { syncRewardsFromGUI(p, startIndex, maxSlots); openRewardLibrary(p, page + 1); return true; }
                    if (s >= 9 && s < 45) {
                        if (sess.currentButton == 1) {
                            syncRewardsFromGUI(p, startIndex, maxSlots);
                            int rewardIndex = startIndex + (s - 9);
                            if (rewardIndex < RewardManager.getInstance().getAllRewards().size()) {
                                RewardManager.getInstance().removeReward(rewardIndex);
                                MessageUtil.send(p, "§c已移除奖励物品");
                            }
                            openRewardLibrary(p, page);
                            return true;
                        }
                        return false;
                    }
                    return true;
                });
    }

    private static void syncRewardsFromGUI(ServerPlayerEntity admin, int startIndex, int maxSlots) {
        if (!(admin.currentScreenHandler instanceof AdminScreenHandler ash)) return;
        SimpleInventory inv = ash.getInventory();
        List<RewardItem> currentRewards = RewardManager.getInstance().getAllRewards();
        List<RewardItem> newRewards = new ArrayList<>();
        for (int i = 0; i < startIndex && i < currentRewards.size(); i++) {
            newRewards.add(currentRewards.get(i));
        }
        for (int i = 0; i < maxSlots; i++) {
            int slot = 9 + i;
            ItemStack stack = inv.getStack(slot);
            if (!stack.isEmpty() && !ItemUtil.isBorderOrFiller(stack)) {
                String name = stack.get(DataComponentTypes.CUSTOM_NAME) instanceof Text t
                        ? t.getString() : stack.getItem().getName().getString();
                newRewards.add(new RewardItem(UUID.randomUUID().toString().replace("-", ""), stack.copy(), name, "", System.currentTimeMillis()));
            }
        }
        int pageEnd = startIndex + maxSlots;
        for (int i = pageEnd; i < currentRewards.size(); i++) {
            newRewards.add(currentRewards.get(i));
        }
        RewardManager.getInstance().replaceAll(newRewards);
    }

    // ==================== ADD REWARD (新建奖励) ====================
    // 布局: 6行大箱子 (54格)
    //
    // Row 0: [黑玻] [黑玻] [黑玻] [黑玻] [新建奖励标题] [黑玻] [黑玻] [黑玻] [黑玻]
    // Row 1: [空] [空] [空] [空] [空] [空] [空] [空] [空]
    // Row 2: [空] [空] [空] [空] [空] [空] [空] [空] [空]
    // Row 3: [空] [空] [空] [空] [空] [空] [空] [空] [空]
    // Row 4: [黑玻] [黑玻] [保存到奖励库] [黑玻] [清空] [黑玻] [黑玻] [黑玻] [返回]
    // Row 5: [黑玻] [黑玻] [黑玻] [黑玻] [黑玻] [黑玻] [黑玻] [黑玻] [黑玻]
    //
    // 操作: 在上方空格放入物品 → 点击保存到奖励库
    //       保存到奖励库 → 将物品逐个保存为奖励(数量显示为1)
    //       清空 → 清空所有已放入的物品
    //       返回 → 回到奖励库管理

    public static void openAddReward(ServerPlayerEntity admin) {
        SimpleInventory inv = new SimpleInventory(54);
        for (int i = 0; i < 9; i++) inv.setStack(i, lightGlass());
        for (int i = 45; i < 54; i++) inv.setStack(i, lightGlass());

        // Row 1-3: placement area (slots 9-35, 27 slots for items)
        for (int i = 9; i < 36; i++) inv.setStack(i, ItemStack.EMPTY);

        inv.setStack(4, ItemUtil.createButton(new ItemStack(Items.ANVIL), "§6§l新建奖励",
                List.of(Text.literal("§7在下方放入物品，点击保存"))));

        inv.setStack(36, lightGlass());
        inv.setStack(37, lightGlass());
        inv.setStack(38, ItemUtil.createButton(new ItemStack(Items.HOPPER), "§a保存到奖励库",
                List.of(Text.literal("§7将下方物品逐个保存为奖励"))));
        inv.setStack(39, lightGlass());
        inv.setStack(40, ItemUtil.createButton(new ItemStack(Items.STRUCTURE_VOID), "§c清空",
                List.of(Text.literal("§7清空所有物品"))));
        inv.setStack(41, lightGlass());
        inv.setStack(42, lightGlass());
        inv.setStack(43, lightGlass());
        inv.setStack(53, ItemUtil.createButton(new ItemStack(Items.ARROW), "§7返回"));

        GuiSession session = new GuiSession(GuiType.ADD_REWARD, admin);
        session.tempInventory = inv;
        sessions.put(admin.getUuid(), session);

        openGUI(admin, inv, 6, Text.literal("§8新建奖励"),
                (p, s) -> {
                    GuiSession sess = getSession(p);
                    if (sess == null) return true;
                    if (s == 53) { openRewardLibrary(p); return true; }
                    if (s == 40) {
                        for (int i = 9; i < 36; i++) sess.tempInventory.setStack(i, ItemStack.EMPTY);
                        MessageUtil.send(p, "§7已清空");
                        return true;
                    }
                    if (s == 38) {
                        boolean added = false;
                        for (int i = 9; i < 36; i++) {
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
                    if (s < 9 || s >= 36) return true;
                    return false;
                });

        if (admin.currentScreenHandler instanceof AdminScreenHandler ash) {
            session.tempInventory = ash.getInventory();
        }
    }

    // ==================== BAN LIST (黑名单管理) ====================
    // 布局: 6行大箱子 (54格)
    //
    // Row 0: [边框] [边框] [边框] [边框] [边框] [边框] [边框] [边框] [边框]
    // Row 1: [封禁玩家头像] [封禁玩家头像] ... (最多45个)
    // Row 2: [封禁玩家头像] ...
    // Row 3: [封禁玩家头像] ...
    // Row 4: [封禁玩家头像] ...
    // Row 5: [上一页] [下一页] [边框] [边框] [边框] [边框] [边框] [边框] [返回]
    //
    // 操作: 点击封禁玩家头像 → 解封该玩家
    //       上一页/下一页 → 翻页浏览
    //       返回 → 回到主面板

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

    // ==================== DURATION SELECT (选择时长) ====================
    // 布局: 6行大箱子 (54格)
    //
    // Row 0: [边框] [边框] [边框] [边框] [边框] [边框] [边框] [边框] [边框]
    // Row 1: [1小时] [3小时] [6小时] [12小时] [1天] [3天] [7天] [30天] [90天]
    // Row 2: [180天] [1年] [10年] [永久] [边框] [边框] [边框] [边框] [边框]
    // Row 3: [边框] [边框] [边框] [边框] [边框] [边框] [边框] [边框] [边框]
    // Row 4: [边框] [边框] [边框] [边框] [边框] [边框] [边框] [边框] [边框]
    // Row 5: [边框] [边框] [边框] [边框] [边框] [边框] [边框] [边框] [返回]
    //
    // 操作: 点击时长 → 进入原因选择面板
    //       返回 → 回到玩家详情面板

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

    // ==================== REASON SELECT (选择原因) ====================
    // 布局: 6行大箱子 (54格)
    //
    // Row 0: [边框] [边框] [边框] [边框] [选择原因标题] [边框] [边框] [边框] [边框]
    // Row 1: [原因1] [原因2] [原因3] [原因4] [原因5] [原因6] [原因7] [原因8] [原因9]
    // Row 2: [原因10] ... (最多36个原因，来自配置文件)
    // Row 3: [边框] [边框] [边框] [边框] [边框] [边框] [边框] [边框] [边框]
    // Row 4: [边框] [边框] [边框] [边框] [边框] [边框] [边框] [边框] [边框]
    // Row 5: [边框] [边框] [边框] [边框] [边框] [边框] [边框] [边框] [返回]
    //
    // 操作: 点击原因 → 执行禁言/封禁(根据之前选择的时长)
    //       返回 → 回到时长选择面板

    public static void openReasonSelect(ServerPlayerEntity admin, ServerPlayerEntity target, String actionType, long duration, String durationLabel) {
        SimpleInventory inv = new SimpleInventory(54);
        for (int i = 0; i < 9; i++) inv.setStack(i, ItemUtil.createBorder());
        for (int i = 45; i < 54; i++) inv.setStack(i, ItemUtil.createBorder());
        List<String> reasons = ConfigManager.getConfig().getDefaultBanReasons();

        inv.setStack(4, ItemUtil.createButton(new ItemStack(Items.PAPER), "§6§l选择原因"));

        for (int i = 0; i < Math.min(reasons.size(), 36); i++) {
            inv.setStack(9 + i, ItemUtil.createButton(new ItemStack(Items.PAPER), "§e" + reasons.get(i),
                    List.of(Text.literal("§7点击选择此原因"))));
        }
        inv.setStack(53, ItemUtil.createButton(new ItemStack(Items.ARROW), "§7返回"));

        GuiSession session = new GuiSession(GuiType.CONFIRM_ACTION, admin);
        session.target = target;
        session.extraData = new Object[]{actionType, duration, durationLabel};
        sessions.put(admin.getUuid(), session);

        openGUI(admin, inv, 6, Text.literal("§8选择原因"),
                (p, s) -> {
                    GuiSession sess = getSession(p);
                    if (sess == null) return true;
                    if (s == 53) { openDurationSelect(p, sess.target, (String)((Object[])sess.extraData)[0]); return true; }
                    Object[] data = (Object[]) sess.extraData;
                    String action = (String) data[0];
                    long dur = (long) data[1];
                    String durLabel = (String) data[2];
                    String reason = "违规行为";
                    if (s >= 9 && s < 9 + reasons.size()) reason = reasons.get(s - 9);

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

    // ==================== BROADCAST GIVE (广播发放) ====================
    // 布局: 6行大箱子 (54格)
    //
    // Row 0: [边框] [边框] [边框] [边框] [边框] [边框] [边框] [边框] [边框]
    // Row 1: [奖励物品] [奖励物品] [奖励物品] [奖励物品] [奖励物品] [奖励物品] [奖励物品] [奖励物品] [奖励物品]
    // Row 2: [奖励物品] [奖励物品] [奖励物品] [奖励物品] [奖励物品] [奖励物品] [奖励物品] [奖励物品] [奖励物品]
    // Row 3: [奖励物品] [奖励物品] [奖励物品] [奖励物品] [奖励物品] [奖励物品] [奖励物品] [奖励物品] [奖励物品]
    // Row 4: [奖励物品] [奖励物品] [奖励物品] [奖励物品] [返回] [奖励物品] [奖励物品] [广播发放] [奖励物品]
    // Row 5: [上一页] [下一页] [边框] [边框] [边框] [边框] [边框] [边框] [边框]
    //
    // 操作: 点击奖励物品 → 进入广播数量选择面板
    //       上一页/下一页 → 翻页浏览
    //       返回 → 回到选择发放方式

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
            display.setCount(1);
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
        SimpleInventory inv = new SimpleInventory(54);
        for (int i = 0; i < 9; i++) inv.setStack(i, lightGlass());
        for (int i = 45; i < 54; i++) inv.setStack(i, lightGlass());

        inv.setStack(4, ItemUtil.createButton(new ItemStack(Items.CHEST), "§6§l选择发放数量"));

        for (int i = 0; i < amounts.length; i++) {
            int a = amounts[i];
            inv.setStack(19 + i, ItemUtil.createButton(new ItemStack(Items.CHEST), "§e×" + a,
                    List.of(Text.literal("§7每人发放 " + a + " 个"))));
        }
        inv.setStack(53, ItemUtil.createButton(new ItemStack(Items.ARROW), "§7返回"));

        GuiSession session = new GuiSession(GuiType.QUANTITY_SELECT, admin);
        session.slotIndex = rewardIndex;
        sessions.put(admin.getUuid(), session);

        openGUI(admin, inv, 6, Text.literal("§8选择发放数量"),
                (p, s) -> {
                    GuiSession sess = getSession(p);
                    if (sess == null) return true;
                    if (s == 53) { openBroadcastGive(p, 0); return true; }
                    if (s >= 19 && s < 19 + amounts.length) {
                        int idx = s - 19;
                        RewardItem reward = RewardManager.getInstance().getReward(sess.slotIndex);
                        if (reward == null) return true;
                        PlayerManager pm = PlayerManager.getInstance();
                        int onlineCount = 0;
                        int offlineCount = 0;
                        for (UUID targetUuid : pm.getKnownPlayers()) {
                            ServerPlayerEntity online = p.getServer().getPlayerManager().getPlayer(targetUuid);
                            ItemStack stack = reward.getItem().copy();
                            stack.setCount(amounts[idx]);
                            if (online != null) {
                                if (!online.getInventory().insertStack(stack)) {
                                    if (!stack.isEmpty()) online.dropItem(stack, false);
                                }
                                MessageUtil.notifyTarget(online, "你收到了广播奖励: §e×" + amounts[idx] + " " + (reward.getDisplayName() != null ? reward.getDisplayName() : stack.getItem().getName().getString()));
                                onlineCount++;
                            } else {
                                RewardManager.getInstance().addToOfflineQueue(targetUuid, stack);
                                offlineCount++;
                            }
                        }
                        String name = reward.getDisplayName() != null ? reward.getDisplayName() : reward.getItem().getItem().getName().getString();
                        MessageUtil.send(p, "§a广播发放完成: §e×" + amounts[idx] + " " + name + " §7给 §b" + onlineCount + " §7名在线玩家" + (offlineCount > 0 ? ", §b" + offlineCount + " §7名离线玩家待领取" : ""));
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

        if (player.currentScreenHandler instanceof AdminScreenHandler ash
                && ash.syncId == player.currentScreenHandler.syncId
                && newInventory.size() == ash.getInventory().size()) {
            ash.setTargetPlayer(null);
            SimpleInventory existing = ash.getInventory();
            existing.clear();
            for (int i = 0; i < newInventory.size(); i++) {
                existing.setStack(i, newInventory.getStack(i));
            }
            session.tempInventory = existing;
            return;
        }

        NamedScreenHandlerFactory factory = new NamedScreenHandlerFactory() {
            @Override
            public ScreenHandler createMenu(int syncId, PlayerInventory inv,
                                             net.minecraft.entity.player.PlayerEntity p) {
                return AdminScreenHandler.create(syncId, inv, newInventory, rows);
            }

            @Override
            public Text getDisplayName() { return title; }
        };
        player.openHandledScreen(factory);
    }
}
