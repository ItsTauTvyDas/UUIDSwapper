package me.itstautvydas.uuidswapper.cache;

import java.util.UUID;

public record PlayerCache(UUID originalUuid, String username, String address, UUID onlineUuid, Long createdAt, Long updatedAt) {
    public UUID getOriginalUuid() {
        return originalUuid;
    }

    public String getUsername() {
        return username;
    }

    public String getAddress() {
        return address;
    }

    public UUID getOnlineUuid() {
        return onlineUuid;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public Long getUpdatedAt() {
        return updatedAt;
    }
}
