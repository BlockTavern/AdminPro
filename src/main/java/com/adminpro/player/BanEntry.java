package com.adminpro.player;

import java.util.UUID;

public class BanEntry {
    private UUID playerUuid;
    private String playerName;
    private String reason;
    private long banTime;
    private long expiryTime;
    private boolean permanent;
    private String bannedBy;

    public BanEntry() {}

    public BanEntry(UUID playerUuid, String playerName, String reason, long banTime, long expiryTime, boolean permanent, String bannedBy) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.reason = reason;
        this.banTime = banTime;
        this.expiryTime = expiryTime;
        this.permanent = permanent;
        this.bannedBy = bannedBy;
    }

    public UUID getPlayerUuid() { return playerUuid; }
    public void setPlayerUuid(UUID playerUuid) { this.playerUuid = playerUuid; }

    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public long getBanTime() { return banTime; }
    public void setBanTime(long banTime) { this.banTime = banTime; }

    public long getExpiryTime() { return expiryTime; }
    public void setExpiryTime(long expiryTime) { this.expiryTime = expiryTime; }

    public boolean isPermanent() { return permanent; }
    public void setPermanent(boolean permanent) { this.permanent = permanent; }

    public String getBannedBy() { return bannedBy; }
    public void setBannedBy(String bannedBy) { this.bannedBy = bannedBy; }

    public boolean isExpired() {
        return !permanent && System.currentTimeMillis() >= expiryTime;
    }

    public boolean isActive() {
        return permanent || System.currentTimeMillis() < expiryTime;
    }
}
