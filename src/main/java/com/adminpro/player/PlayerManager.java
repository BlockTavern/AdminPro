package com.adminpro.player;

import com.adminpro.storage.ConfigManager;
import com.adminpro.storage.StorageManager;
import com.adminpro.util.MessageUtil;
import net.minecraft.entity.EntityType;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerManager {
    private static final Field PACKET_ENTRIES_FIELD;

    static {
        try {
            PACKET_ENTRIES_FIELD = findEntriesField();
        } catch (Exception e) {
            throw new RuntimeException("Failed to access PlayerListS2CPacket entries field", e);
        }
    }

    private static Field findEntriesField() throws NoSuchFieldException {
        for (Field field : PlayerListS2CPacket.class.getDeclaredFields()) {
            if (List.class.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                return field;
            }
        }
        throw new NoSuchFieldException("No List field found in PlayerListS2CPacket");
    }

    private static PlayerManager instance;
    private final MinecraftServer server;
    private final Path dataDir;
    private final Map<UUID, BanEntry> bans = new ConcurrentHashMap<>();
    private final Map<UUID, MuteEntry> mutes = new ConcurrentHashMap<>();
    private final Set<UUID> voiceBanned = ConcurrentHashMap.newKeySet();
    private final Set<UUID> vanishedLight = ConcurrentHashMap.newKeySet();
    private final Set<UUID> vanishedFull = ConcurrentHashMap.newKeySet();
    private final Set<UUID> knownPlayers = ConcurrentHashMap.newKeySet();
    private boolean serverStarted = false;

    public PlayerManager(MinecraftServer server) {
        this.server = server;
        this.dataDir = ConfigManager.getDataDir(server);
        loadAll();
    }

    public static PlayerManager getInstance() { return instance; }

    public static void init(MinecraftServer server) {
        instance = new PlayerManager(server);
    }

    private void loadAll() {
        ConfigManager.ConfigData config = ConfigManager.getConfig();
        Path bansFile = dataDir.resolve(config.getBansFile());
        Path mutesFile = dataDir.resolve(config.getMutesFile());

        java.lang.reflect.Type banListType = new TypeToken<List<BanEntry>>(){}.getType();
        List<BanEntry> banList = StorageManager.loadJson(bansFile, banListType, new ArrayList<>());
        banList.forEach(e -> bans.put(e.getPlayerUuid(), e));

        java.lang.reflect.Type muteListType = new TypeToken<List<MuteEntry>>(){}.getType();
        List<MuteEntry> muteList = StorageManager.loadJson(mutesFile, muteListType, new ArrayList<>());
        muteList.forEach(e -> mutes.put(e.getPlayerUuid(), e));

        loadKnownPlayers();
    }

    private void saveBans() {
        Path bansFile = dataDir.resolve(ConfigManager.getConfig().getBansFile());
        StorageManager.saveJson(bansFile, new ArrayList<>(bans.values()));
    }

    private void saveMutes() {
        Path mutesFile = dataDir.resolve(ConfigManager.getConfig().getMutesFile());
        StorageManager.saveJson(mutesFile, new ArrayList<>(mutes.values()));
    }

    // --- Ban Management ---

    public void banPlayer(UUID playerUuid, String playerName, String reason, long durationSeconds, String bannedBy) {
        long now = System.currentTimeMillis();
        boolean permanent = durationSeconds <= 0;
        long expiryTime = permanent ? -1 : now + durationSeconds * 1000;

        BanEntry entry = new BanEntry(playerUuid, playerName, reason, now, expiryTime, permanent, bannedBy);
        bans.put(playerUuid, entry);
        saveBans();

        ServerPlayerEntity target = server.getPlayerManager().getPlayer(playerUuid);
        if (target != null) {
            target.networkHandler.disconnect(MessageUtil.text("§c你已被封禁！\n§7原因: " + reason + "\n§7期限: " + (permanent ? "永久" : formatDuration(durationSeconds)) + "\n§7执行人: " + bannedBy));
        }
    }

    public void unbanPlayer(UUID playerUuid) {
        bans.remove(playerUuid);
        saveBans();
    }

    public boolean isBanned(UUID playerUuid) {
        BanEntry entry = bans.get(playerUuid);
        if (entry == null) return false;
        if (entry.isExpired()) {
            bans.remove(playerUuid);
            saveBans();
            return false;
        }
        return entry.isActive();
    }

    public BanEntry getBanEntry(UUID playerUuid) {
        BanEntry entry = bans.get(playerUuid);
        if (entry != null && entry.isExpired()) {
            bans.remove(playerUuid);
            saveBans();
            return null;
        }
        return entry;
    }

    public Map<UUID, BanEntry> getAllBans() {
        cleanupExpiredBans();
        return Collections.unmodifiableMap(bans);
    }

    public List<BanEntry> getBanList() {
        cleanupExpiredBans();
        return new ArrayList<>(bans.values());
    }

    private void cleanupExpiredBans() {
        bans.values().removeIf(BanEntry::isExpired);
    }

    // --- Mute Management ---

    public void mutePlayer(UUID playerUuid, String playerName, String reason, long durationSeconds, String mutedBy) {
        long now = System.currentTimeMillis();
        boolean permanent = durationSeconds <= 0;
        long expiryTime = permanent ? -1 : now + durationSeconds * 1000;

        MuteEntry entry = new MuteEntry(playerUuid, playerName, reason, now, expiryTime, permanent, mutedBy);
        mutes.put(playerUuid, entry);
        saveMutes();

        ServerPlayerEntity target = server.getPlayerManager().getPlayer(playerUuid);
        if (target != null) {
            MessageUtil.notifyTarget(target, "你已被禁言！" + (permanent ? "（永久）" : "时长: " + formatDuration(durationSeconds)));
        }
    }

    public void unmutePlayer(UUID playerUuid) {
        mutes.remove(playerUuid);
        saveMutes();
    }

    public boolean isMuted(UUID playerUuid) {
        MuteEntry entry = mutes.get(playerUuid);
        if (entry == null) return false;
        if (entry.isExpired()) {
            mutes.remove(playerUuid);
            saveMutes();
            return false;
        }
        return entry.isActive();
    }

    public MuteEntry getMuteEntry(UUID playerUuid) {
        MuteEntry entry = mutes.get(playerUuid);
        if (entry != null && entry.isExpired()) {
            mutes.remove(playerUuid);
            saveMutes();
            return null;
        }
        return entry;
    }

    public Map<UUID, MuteEntry> getAllMutes() {
        cleanupExpiredMutes();
        return Collections.unmodifiableMap(mutes);
    }

    private void cleanupExpiredMutes() {
        mutes.values().removeIf(MuteEntry::isExpired);
    }

    // --- Vanish Management ---

    public boolean isVanished(UUID uuid) {
        return vanishedLight.contains(uuid) || vanishedFull.contains(uuid);
    }

    public boolean isVanishedLight(UUID uuid) {
        return vanishedLight.contains(uuid);
    }

    public boolean isVanishedFull(UUID uuid) {
        return vanishedFull.contains(uuid);
    }

    public void setVanishLight(UUID uuid) {
        vanishedLight.add(uuid);
        vanishedFull.remove(uuid);
        applyVanishToOthers(uuid, true);
    }

    public void setVanishFull(UUID uuid) {
        vanishedFull.add(uuid);
        vanishedLight.remove(uuid);
        applyVanishToOthers(uuid, true);
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
        if (player != null) removeFromTabList(player);
    }

    public void unVanish(UUID uuid) {
        vanishedLight.remove(uuid);
        vanishedFull.remove(uuid);
        applyVanishToOthers(uuid, false);
    }

    private void applyVanishToOthers(UUID uuid, boolean vanish) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
        if (player == null) return;
        player.setInvisible(vanish);
        for (ServerPlayerEntity other : server.getPlayerManager().getPlayerList()) {
            if (other.getUuid().equals(uuid)) continue;
            if (vanish) {
                other.networkHandler.sendPacket(new EntitiesDestroyS2CPacket(player.getId()));
            } else {
                other.networkHandler.sendPacket(new PlayerListS2CPacket(PlayerListS2CPacket.Action.ADD_PLAYER, player));
                other.networkHandler.sendPacket(new EntitySpawnS2CPacket(
                        player.getId(), player.getUuid(),
                        player.getX(), player.getY(), player.getZ(),
                        player.getYaw(), player.getPitch(),
                        EntityType.PLAYER, 0, Vec3d.ZERO, player.getHeadYaw()));
            }
        }
    }

    public void syncVanishToPlayer(ServerPlayerEntity viewer) {
        for (ServerPlayerEntity other : server.getPlayerManager().getPlayerList()) {
            if (other.getUuid().equals(viewer.getUuid())) continue;
            if (isVanishedFull(other.getUuid())) {
                viewer.networkHandler.sendPacket(new EntitiesDestroyS2CPacket(other.getId()));
                removeFromTabListForViewer(other, viewer);
            } else if (isVanishedLight(other.getUuid())) {
                viewer.networkHandler.sendPacket(new EntitiesDestroyS2CPacket(other.getId()));
            }
        }
    }

    // --- Tab List Vanish (reflection) ---

    private void removeFromTabList(ServerPlayerEntity player) {
        try {
            List<PlayerListS2CPacket.Entry> entries = List.of(new PlayerListS2CPacket.Entry(
                    player.getUuid(), player.getGameProfile(), false,
                    player.networkHandler.getLatency(),
                    player.interactionManager.getGameMode(),
                    player.getPlayerListName(),
                    null
            ));
            PlayerListS2CPacket packet = new PlayerListS2CPacket(
                    EnumSet.of(PlayerListS2CPacket.Action.UPDATE_LISTED),
                    Collections.emptyList()
            );
            PACKET_ENTRIES_FIELD.set(packet, entries);
            for (ServerPlayerEntity other : server.getPlayerManager().getPlayerList()) {
                if (other.getUuid().equals(player.getUuid())) continue;
                other.networkHandler.sendPacket(packet);
            }
        } catch (Exception e) {
            System.err.println("[AdminPro] Failed to remove player from tab list: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void removeFromTabListForViewer(ServerPlayerEntity player, ServerPlayerEntity viewer) {
        try {
            List<PlayerListS2CPacket.Entry> entries = List.of(new PlayerListS2CPacket.Entry(
                    player.getUuid(), player.getGameProfile(), false,
                    player.networkHandler.getLatency(),
                    player.interactionManager.getGameMode(),
                    player.getPlayerListName(),
                    null
            ));
            PlayerListS2CPacket packet = new PlayerListS2CPacket(
                    EnumSet.of(PlayerListS2CPacket.Action.UPDATE_LISTED),
                    Collections.emptyList()
            );
            PACKET_ENTRIES_FIELD.set(packet, entries);
            viewer.networkHandler.sendPacket(packet);
        } catch (Exception e) {
            System.err.println("[AdminPro] Failed to remove player from tab list for viewer: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // --- Known Players ---

    public void recordJoin(UUID uuid, String name) {
        knownPlayers.add(uuid);
        saveKnownPlayers();
    }

    public Set<UUID> getKnownPlayers() {
        return Collections.unmodifiableSet(knownPlayers);
    }

    public boolean isKnownPlayer(UUID uuid) {
        return knownPlayers.contains(uuid);
    }

    private void saveKnownPlayers() {
        Path file = dataDir.resolve(ConfigManager.getConfig().getBansFile()).getParent().resolve("known_players.json");
        List<String> list = knownPlayers.stream().map(UUID::toString).toList();
        StorageManager.saveJson(file, list);
    }

    private void loadKnownPlayers() {
        Path file = dataDir.resolve(ConfigManager.getConfig().getBansFile()).getParent().resolve("known_players.json");
        java.lang.reflect.Type listType = new TypeToken<List<String>>(){}.getType();
        List<String> list = StorageManager.loadJson(file, listType, new ArrayList<>());
        list.forEach(s -> knownPlayers.add(UUID.fromString(s)));
    }

    public boolean canSee(ServerPlayerEntity viewer, ServerPlayerEntity target) {
        if (viewer.getUuid().equals(target.getUuid())) return true;
        if (isVanishedFull(target.getUuid())) return false;
        if (isVanishedLight(target.getUuid()) && !viewer.hasPermissionLevel(2)) return false;
        return true;
    }

    // --- Voice Ban Management ---

    public boolean isVoiceBanned(UUID playerUuid) {
        return voiceBanned.contains(playerUuid);
    }

    public void voiceBanPlayer(UUID playerUuid) {
        voiceBanned.add(playerUuid);
    }

    public void unVoiceBanPlayer(UUID playerUuid) {
        voiceBanned.remove(playerUuid);
    }

    public void reload() {
        bans.clear();
        mutes.clear();
        loadAll();
    }

    public static String formatDuration(long seconds) {
        if (seconds <= 0) return "永久";
        if (seconds < 60) return seconds + "秒";
        if (seconds < 3600) return (seconds / 60) + "分钟";
        if (seconds < 86400) return (seconds / 3600) + "小时";
        return (seconds / 86400) + "天";
    }
}
