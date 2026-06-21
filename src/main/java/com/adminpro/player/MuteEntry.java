package com.adminpro.player;

import java.util.UUID;

public class MuteEntry {
    private UUID playerUuid;
    private String playerName;
    private String reason;
    private long muteTime;
    private long expiryTime;
    private boolean permanent;
    private String mutedBy;

    public MuteEntry() {}

    public MuteEntry(UUID playerUuid, String playerName, String reason, long muteTime, long expiryTime, boolean permanent, String mutedBy) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.reason = reason;
        this.muteTime = muteTime;
        this.expiryTime = expiryTime;
        this.permanent = permanent;
        this.mutedBy = mutedBy;
    }

    public UUID getPlayerUuid() { return playerUuid; }
    public void setPlayerUuid(UUID playerUuid) { this.playerUuid = playerUuid; }

    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public long getMuteTime() { return muteTime; }
    public void setMuteTime(long muteTime) { this.muteTime = muteTime; }

    public long getExpiryTime() { return expiryTime; }
    public void setExpiryTime(long expiryTime) { this.expiryTime = expiryTime; }

    public boolean isPermanent() { return permanent; }
    public void setPermanent(boolean permanent) { this.permanent = permanent; }

    public String getMutedBy() { return mutedBy; }
    public void setMutedBy(String mutedBy) { this.mutedBy = mutedBy; }

    public boolean isExpired() {
        return !permanent && System.currentTimeMillis() >= expiryTime;
    }

    public boolean isActive() {
        return permanent || System.currentTimeMillis() < expiryTime;
    }
}
