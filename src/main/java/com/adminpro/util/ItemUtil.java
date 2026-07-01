package com.adminpro.util;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import com.mojang.authlib.GameProfile;

import java.util.List;
import java.util.UUID;

public class ItemUtil {
    public static ItemStack createButton(ItemStack base, String name, List<Text> lore) {
        ItemStack stack = base.copy();
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name));
        if (lore != null && !lore.isEmpty()) {
            stack.set(DataComponentTypes.LORE, new LoreComponent(lore));
        }
        return stack;
    }

    public static ItemStack createButton(ItemStack base, String name) {
        return createButton(base, name, null);
    }

    public static ItemStack getPlayerHead(String playerName, UUID uuid) {
        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        GameProfile profile = new GameProfile(uuid, playerName);
        head.set(DataComponentTypes.PROFILE, new ProfileComponent(profile));
        return head;
    }

    public static ItemStack getOnlineIndicator() {
        ItemStack stack = new ItemStack(Items.LIME_STAINED_GLASS_PANE);
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§a在线"));
        return stack;
    }

    public static ItemStack getOfflineIndicator() {
        ItemStack stack = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§7离线"));
        return stack;
    }

    public static ItemStack createFiller() {
        ItemStack stack = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(" "));
        stack.set(DataComponentTypes.HIDE_TOOLTIP, net.minecraft.util.Unit.INSTANCE);
        return stack;
    }

    public static ItemStack createBorder() {
        ItemStack stack = new ItemStack(Items.BLACK_STAINED_GLASS_PANE);
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(" "));
        stack.set(DataComponentTypes.HIDE_TOOLTIP, net.minecraft.util.Unit.INSTANCE);
        return stack;
    }

    public static boolean isBorderOrFiller(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.getItem() == Items.BLACK_STAINED_GLASS_PANE || stack.getItem() == Items.GRAY_STAINED_GLASS_PANE || stack.getItem() == Items.LIGHT_GRAY_STAINED_GLASS_PANE || stack.getItem() == Items.WHITE_STAINED_GLASS_PANE) return true;
        if (stack.contains(DataComponentTypes.HIDE_TOOLTIP)) return true;
        return false;
    }

    public static boolean isFiller(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() == Items.GRAY_STAINED_GLASS_PANE;
    }

    public static boolean isSimilar(ItemStack a, ItemStack b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return ItemStack.areItemsAndComponentsEqual(a, b);
    }
}
