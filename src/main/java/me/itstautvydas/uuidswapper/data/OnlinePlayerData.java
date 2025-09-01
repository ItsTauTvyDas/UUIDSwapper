package me.itstautvydas.uuidswapper.data;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import me.itstautvydas.uuidswapper.Utils;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@ToString @Getter @Setter
public class OnlinePlayerData {
    private final UUID originalUniqueId;
    private UUID onlineUniqueId;
    private List<ProfilePropertyWrapper> properties;
    private Long createdAt;
    private Long updatedAt;

    public OnlinePlayerData(UUID originalUniqueId, UUID onlineUniqueId, List<ProfilePropertyWrapper> properties) {
        this.originalUniqueId = originalUniqueId;
        this.onlineUniqueId = onlineUniqueId;
        this.properties = properties;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }

    public boolean isOnlineUniqueId() {
        return !Objects.equals(originalUniqueId, onlineUniqueId);
    }

    public UUID getOnlineUniqueId() {
        return onlineUniqueId == null ? originalUniqueId : onlineUniqueId;
    }

    public String propertiesToJsonString() {
        return Utils.DEFAULT_GSON.toJson(properties);
    }
}
