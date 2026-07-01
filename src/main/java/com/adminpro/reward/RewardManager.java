package com.adminpro.reward;

import com.adminpro.storage.ConfigManager;
import com.adminpro.storage.StorageManager;
import com.google.gson.*;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class RewardManager {
    private static RewardManager instance;
    private final MinecraftServer server;
    private final Path dataDir;
    private final List<RewardItem> rewards = new CopyOnWriteArrayList<>();
    private final Map<UUID, List<ItemStack>> offlineQueue = new HashMap<>();
    private static final String OFFLINE_QUEUE_FILE = "offline_reward_queue.json";

    private static final Gson REWARD_GSON = new GsonBuilder().setPrettyPrinting()
            .registerTypeAdapter(RewardItem.class, new JsonSerializer<RewardItem>() {
                @Override
                public JsonElement serialize(RewardItem src, Type typeOfSrc, JsonSerializationContext context) {
                    JsonObject obj = new JsonObject();
                    obj.addProperty("id", src.getId());
                    obj.add("item", StorageManager.serializeItemStack(src.getItem()));
                    obj.addProperty("displayName", src.getDisplayName());
                    obj.addProperty("description", src.getDescription());
                    obj.addProperty("createTime", src.getCreateTime());
                    return obj;
                }
            })
            .registerTypeAdapter(RewardItem.class, new JsonDeserializer<RewardItem>() {
                @Override
                public RewardItem deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
                    JsonObject obj = json.getAsJsonObject();
                    RewardItem reward = new RewardItem();
                    reward.setId(obj.get("id").getAsString());
                    reward.setItem(StorageManager.deserializeItemStack(obj.get("item")));
                    reward.setDisplayName(obj.has("displayName") ? obj.get("displayName").getAsString() : null);
                    reward.setDescription(obj.has("description") ? obj.get("description").getAsString() : null);
                    reward.setCreateTime(obj.get("createTime").getAsLong());
                    return reward;
                }
            })
            .create();

    public RewardManager(MinecraftServer server) {
        this.server = server;
        this.dataDir = ConfigManager.getDataDir(server);
        load();
    }

    public static RewardManager getInstance() { return instance; }

    public static void init(MinecraftServer server) {
        instance = new RewardManager(server);
    }

    public void save() {
        Path file = dataDir.resolve(ConfigManager.getConfig().getRewardLibraryFile());
        try {
            Files.createDirectories(file.getParent());
            JsonArray arr = new JsonArray();
            for (RewardItem r : rewards) {
                arr.add(REWARD_GSON.toJsonTree(r, RewardItem.class));
            }
            Files.writeString(file, REWARD_GSON.toJson(arr));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void addReward(ItemStack item, String displayName, String description) {
        String id = UUID.randomUUID().toString().replace("-", "");
        RewardItem reward = new RewardItem(id, item.copy(), displayName, description, System.currentTimeMillis());
        rewards.add(reward);
        save();
    }

    public void removeReward(String id) {
        rewards.removeIf(r -> r.getId().equals(id));
        save();
    }

    public void removeReward(int index) {
        if (index >= 0 && index < rewards.size()) {
            rewards.remove(index);
            save();
        }
    }

    public void replaceAll(List<RewardItem> newRewards) {
        rewards.clear();
        rewards.addAll(newRewards);
        save();
    }

    public RewardItem getReward(String id) {
        return rewards.stream().filter(r -> r.getId().equals(id)).findFirst().orElse(null);
    }

    public RewardItem getReward(int index) {
        if (index >= 0 && index < rewards.size()) {
            return rewards.get(index);
        }
        return null;
    }

    public List<RewardItem> getAllRewards() {
        return Collections.unmodifiableList(rewards);
    }

    public int getRewardCount() {
        return rewards.size();
    }

    public boolean giveRewardToPlayer(int index, ServerPlayerEntity target, boolean notify) {
        RewardItem reward = getReward(index);
        if (reward == null) return false;

        ItemStack stack = reward.getItem().copy();
        boolean given = target.getInventory().insertStack(stack);
        if (!given && !stack.isEmpty()) {
            target.dropItem(stack, false);
        }
        if (notify && target != null) {
            com.adminpro.util.MessageUtil.notifyTarget(target, "你收到了奖励: §e" + (reward.getDisplayName() != null ? reward.getDisplayName() : stack.getItem().getName().getString()));
        }
        return true;
    }

    public boolean giveRewardToPlayer(String id, ServerPlayerEntity target, boolean notify) {
        RewardItem reward = getReward(id);
        if (reward == null) return false;

        ItemStack stack = reward.getItem().copy();
        boolean given = target.getInventory().insertStack(stack);
        if (!given && !stack.isEmpty()) {
            target.dropItem(stack, false);
        }
        if (notify && target != null) {
            com.adminpro.util.MessageUtil.notifyTarget(target, "你收到了奖励: §e" + (reward.getDisplayName() != null ? reward.getDisplayName() : stack.getItem().getName().getString()));
        }
        return true;
    }

    // ==================== OFFLINE QUEUE ====================

    public void addToOfflineQueue(UUID uuid, ItemStack stack) {
        offlineQueue.computeIfAbsent(uuid, k -> new ArrayList<>()).add(stack.copy());
        saveOfflineQueue();
    }

    public List<ItemStack> flushOfflineQueue(UUID uuid) {
        List<ItemStack> items = offlineQueue.remove(uuid);
        saveOfflineQueue();
        return items != null ? items : Collections.emptyList();
    }

    private void loadOfflineQueue() {
        Path file = dataDir.resolve(OFFLINE_QUEUE_FILE);
        try {
            if (Files.notExists(file)) return;
            String content = Files.readString(file);
            JsonObject obj = JsonParser.parseString(content).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                UUID uuid = UUID.fromString(entry.getKey());
                JsonArray arr = entry.getValue().getAsJsonArray();
                List<ItemStack> items = new ArrayList<>();
                for (JsonElement elem : arr) {
                    ItemStack stack = StorageManager.deserializeItemStack(elem);
                    if (stack != null && !stack.isEmpty()) items.add(stack);
                }
                if (!items.isEmpty()) offlineQueue.put(uuid, items);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveOfflineQueue() {
        Path file = dataDir.resolve(OFFLINE_QUEUE_FILE);
        try {
            Files.createDirectories(file.getParent());
            JsonObject obj = new JsonObject();
            for (Map.Entry<UUID, List<ItemStack>> entry : offlineQueue.entrySet()) {
                JsonArray arr = new JsonArray();
                for (ItemStack stack : entry.getValue()) {
                    arr.add(StorageManager.serializeItemStack(stack));
                }
                obj.add(entry.getKey().toString(), arr);
            }
            Files.writeString(file, REWARD_GSON.toJson(obj));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Map<UUID, List<ItemStack>> getOfflineQueue() {
        return Collections.unmodifiableMap(offlineQueue);
    }

    public void reload() {
        load();
    }

    private void load() {
        Path file = dataDir.resolve(ConfigManager.getConfig().getRewardLibraryFile());
        try {
            if (Files.notExists(file)) return;
            String content = Files.readString(file);
            JsonArray arr = JsonParser.parseString(content).getAsJsonArray();
            rewards.clear();
            for (int i = 0; i < arr.size(); i++) {
                RewardItem item = REWARD_GSON.fromJson(arr.get(i), RewardItem.class);
                if (item != null) rewards.add(item);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        loadOfflineQueue();
    }
}
