package me.itstautvydas.uuidswapper.data;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@ToString @Getter @Setter
public class OnlinePlayerData {
    private final UUID originalUniqueId;
    private UUID onlineUniqueId;
    private List<ProfileProperty> properties;
    private String address;
    private final Long createdAt;
    private Long updatedAt;

    public OnlinePlayerData(UUID originalUniqueId, UUID onlineUniqueId, List<ProfileProperty> properties, String address) {
        this.originalUniqueId = originalUniqueId;
        this.onlineUniqueId = onlineUniqueId;
        this.properties = properties;
        this.address = address;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }

    public boolean isOnlineUniqueId() {
        return !Objects.equals(originalUniqueId, onlineUniqueId);
    }

    public UUID getOnlineUniqueId() {
        return onlineUniqueId == null ? originalUniqueId : onlineUniqueId;
    }
}
