package me.itstautvydas.uuidswapper.data;

import lombok.Getter;

import java.util.List;
import java.util.UUID;

@SuppressWarnings("ClassCanBeRecord")
@Getter
public class OnlinePlayerData {
    private final UUID originalUuid;
    private final UUID onlineUuid;
    private final List<ProfileProperty> properties;
    private final String address;
    private final Long createdAt;
    private final Long updatedAt;

    public OnlinePlayerData(UUID originalUuid, UUID onlineUuid, List<ProfileProperty> properties, String address, Long createdAt, Long updatedAt) {
        this.originalUuid = originalUuid;
        this.onlineUuid = onlineUuid;
        this.properties = properties;
        this.address = address;
        this.createdAt = createdAt == null ? System.currentTimeMillis() : createdAt;
        this.updatedAt = updatedAt == null ? System.currentTimeMillis() : updatedAt;
    }
}
