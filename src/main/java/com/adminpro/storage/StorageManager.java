package com.adminpro.storage;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.component.ComponentChanges;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class StorageManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private static MinecraftServer server;

    public static void init(MinecraftServer srv) {
        server = srv;
    }

    public static void saveJson(Path file, Object data) {
        lock.writeLock().lock();
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(data));
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public static <T> T loadJson(Path file, Class<T> clazz, T defaultVal) {
        lock.readLock().lock();
        try {
            if (Files.notExists(file)) return defaultVal;
            String content = Files.readString(file);
            T result = GSON.fromJson(content, clazz);
            return result != null ? result : defaultVal;
        } catch (IOException e) {
            return defaultVal;
        } finally {
            lock.readLock().unlock();
        }
    }

    public static <T> T loadJson(Path file, java.lang.reflect.Type type, T defaultVal) {
        lock.readLock().lock();
        try {
            if (Files.notExists(file)) return defaultVal;
            String content = Files.readString(file);
            T result = GSON.fromJson(content, type);
            return result != null ? result : defaultVal;
        } catch (IOException e) {
            return defaultVal;
        } finally {
            lock.readLock().unlock();
        }
    }

    public static JsonElement serializeItemStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("id", "minecraft:air");
            return obj;
        }
        Identifier itemId = Registries.ITEM.getId(stack.getItem());
        JsonObject obj = new JsonObject();
        obj.addProperty("id", itemId.toString());
        obj.addProperty("count", stack.getCount());

        ComponentChanges changes = stack.getComponentChanges();
        if (!changes.isEmpty() && server != null) {
            RegistryOps<JsonElement> ops = RegistryOps.of(JsonOps.INSTANCE, server.getRegistryManager());
            ComponentChanges.CODEC.encodeStart(ops, changes)
                    .result()
                    .ifPresent(components -> obj.add("components", components));
        }
        return obj;
    }

    public static ItemStack deserializeItemStack(JsonElement json) {
        JsonObject obj = json.getAsJsonObject();
        String id = obj.get("id").getAsString();
        if (id.equals("minecraft:air")) return ItemStack.EMPTY;

        int count = obj.has("count") ? obj.get("count").getAsInt() : 1;
        Identifier itemId = Identifier.tryParse(id);
        if (itemId == null) return ItemStack.EMPTY;

        ItemStack stack = new ItemStack(Registries.ITEM.get(itemId), count);

        if (obj.has("components") && server != null) {
            RegistryOps<JsonElement> ops = RegistryOps.of(JsonOps.INSTANCE, server.getRegistryManager());
            ComponentChanges.CODEC.decode(ops, obj.get("components"))
                    .result()
                    .ifPresent(pair -> stack.applyChanges(pair.getFirst()));
        }
        return stack;
    }
}
