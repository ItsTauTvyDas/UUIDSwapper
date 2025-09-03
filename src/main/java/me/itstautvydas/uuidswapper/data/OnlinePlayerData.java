package me.itstautvydas.uuidswapper.data;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import me.itstautvydas.uuidswapper.json.Jsonable;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@ToString @Getter @Setter
public class OnlinePlayerData implements Jsonable {
    private final UUID originalUniqueId;
    private UUID onlineUniqueId;
    private List<ProfilePropertyWrapper> properties;
    private Long createdAt;
    private Long updatedAt;

    public OnlinePlayerData(UUID originalUniqueId, UUID onlineUniqueId, List<ProfilePropertyWrapper> properties) {
        this.originalUniqueId = originalUniqueId;
        this.onlineUniqueId = onlineUniqueId;
        this.properties = properties;
    }

    public void updateTime(Long createdAt, Long updatedAt) {
        this.createdAt = createdAt == null ? System.currentTimeMillis() : createdAt;
        this.updatedAt = updatedAt == null ? (createdAt == null ? System.currentTimeMillis() : createdAt) : updatedAt;
    }

    public boolean isOnlineUniqueId() {
        return !Objects.equals(originalUniqueId, onlineUniqueId);
    }

    public UUID getOnlineUniqueId() {
        return onlineUniqueId == null ? originalUniqueId : onlineUniqueId;
    }

    public PlayerData toSimpleData(String username) {
        var data = new PlayerData(originalUniqueId, username, onlineUniqueId);
        data.setProperties(properties);
        return data;
    }
}
