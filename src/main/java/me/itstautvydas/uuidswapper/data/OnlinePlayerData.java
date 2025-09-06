package me.itstautvydas.uuidswapper.data;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import me.itstautvydas.uuidswapper.database.Queueable;
import me.itstautvydas.uuidswapper.json.Jsonable;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@ToString @Getter @Setter
public class OnlinePlayerData implements Jsonable, Queueable {
    private final UUID originalUniqueId;
    private UUID uniqueId;
    private List<ProfilePropertyWrapper> properties;
    private Long createdAt;
    private Long updatedAt;

    public OnlinePlayerData(UUID originalUniqueId, UUID onlineUniqueId, List<ProfilePropertyWrapper> properties) {
        this.originalUniqueId = originalUniqueId;
        this.uniqueId = onlineUniqueId;
        this.properties = properties;
    }

    public OnlinePlayerData updateTime(Long createdAt, Long updatedAt) {
        this.createdAt = createdAt == null ? System.currentTimeMillis() : createdAt;
        this.updatedAt = updatedAt == null ? (createdAt == null ? System.currentTimeMillis() : createdAt) : updatedAt;
        return this;
    }

    public boolean isOnlineUniqueId() {
        return !Objects.equals(originalUniqueId, uniqueId);
    }

    public UUID getUniqueId() {
        return uniqueId == null ? originalUniqueId : uniqueId;
    }

    public PlayerData toSimpleData(String username) {
        var data = new PlayerData(originalUniqueId, username, uniqueId);
        data.setProperties(properties);
        return data;
    }
}
