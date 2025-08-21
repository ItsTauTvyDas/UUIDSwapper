package me.itstautvydas.uuidswapper.data;

import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
public class OnlinePlayerData extends FetchedPlayerData {
    private final String address;
    private final Long createdAt;
    private final Long updatedAt;

    public OnlinePlayerData(UUID originalUuid, UUID onlineUuid, List<ProfileProperty> properties, String address, Long createdAt, Long updatedAt) {
        super(originalUuid, onlineUuid, properties);
        this.address = address;
        this.createdAt = createdAt == null ? System.currentTimeMillis() : createdAt;
        this.updatedAt = updatedAt == null ? System.currentTimeMillis() : updatedAt;
    }

    public OnlinePlayerData(UUID originalUuid, UUID onlineUuid, List<ProfileProperty> properties, String address) {
        super(originalUuid, onlineUuid, properties);
        this.address = address;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }
}
