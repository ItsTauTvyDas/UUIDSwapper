package me.itstautvydas.uuidswapper.cache;

import java.util.UUID;

public record RandomCache(UUID originalUuid, String randomUsername, UUID randomUuid) {
    public UUID getOriginalUuid() {
        return originalUuid;
    }

    public String getRandomUsername() {
        return randomUsername;
    }

    public UUID getRandomUuid() {
        return randomUuid;
    }
}
