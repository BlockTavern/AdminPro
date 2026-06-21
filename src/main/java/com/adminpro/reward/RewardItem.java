package com.adminpro.reward;

import net.minecraft.item.ItemStack;

public class RewardItem {
    private String id;
    private ItemStack item;
    private String displayName;
    private String description;
    private long createTime;

    public RewardItem() {}

    public RewardItem(String id, ItemStack item, String displayName, String description, long createTime) {
        this.id = id;
        this.item = item;
        this.displayName = displayName;
        this.description = description;
        this.createTime = createTime;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public ItemStack getItem() { return item; }
    public void setItem(ItemStack item) { this.item = item; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public long getCreateTime() { return createTime; }
    public void setCreateTime(long createTime) { this.createTime = createTime; }
}
