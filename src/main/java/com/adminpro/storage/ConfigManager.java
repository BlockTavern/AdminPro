package com.adminpro.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ConfigData config;

    public static class ConfigData {
        private String notificationPrefix = "§6[管理]§r ";
        private List<String> defaultBanReasons = Arrays.asList("恶意破坏", "语言攻击", "作弊", "其他");
        private List<Long> defaultBanDurations = Arrays.asList(3600L, 21600L, 43200L, 86400L, 259200L, 604800L);
        private List<Long> defaultMuteDurations = Arrays.asList(600L, 3600L, 7200L, 86400L);
        private String rewardLibraryFile = "reward_library.json";
        private String bansFile = "bans.json";
        private String mutesFile = "mutes.json";

        public String getNotificationPrefix() { return notificationPrefix; }
        public List<String> getDefaultBanReasons() { return defaultBanReasons; }
        public List<Long> getDefaultBanDurations() { return defaultBanDurations; }
        public List<Long> getDefaultMuteDurations() { return defaultMuteDurations; }
        public String getRewardLibraryFile() { return rewardLibraryFile; }
        public String getBansFile() { return bansFile; }
        public String getMutesFile() { return mutesFile; }
    }

    public static ConfigData getConfig() {
        if (config == null) {
            config = new ConfigData();
        }
        return config;
    }

    public static void load(MinecraftServer server) {
        Path configDir = server.getRunDirectory().resolve("config").resolve("adminpro");
        Path configFile = configDir.resolve("config.json");

        if (Files.notExists(configFile)) {
            config = new ConfigData();
            save(configDir);
        } else {
            try {
                String content = Files.readString(configFile);
                config = GSON.fromJson(content, ConfigData.class);
                if (config == null) config = new ConfigData();
            } catch (IOException e) {
                config = new ConfigData();
            }
        }
    }

    public static void save(Path configDir) {
        try {
            Files.createDirectories(configDir);
            Path configFile = configDir.resolve("config.json");
            Files.writeString(configFile, GSON.toJson(getConfig()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static Path getDataDir(MinecraftServer server) {
        return server.getRunDirectory().resolve("config").resolve("adminpro");
    }
}
