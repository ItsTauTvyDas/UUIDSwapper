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
public class OnlinePlayerData extends Timeable implements Jsonable, Queueable {
    private final UUID originalUniqueId;
    private UUID uniqueId;
    private List<ProfilePropertyWrapper> properties;

    public OnlinePlayerData(UUID originalUniqueId, UUID onlineUniqueId, List<ProfilePropertyWrapper> properties) {
        super(0, 0);
        this.originalUniqueId = originalUniqueId;
        this.uniqueId = onlineUniqueId;
        this.properties = properties;
    }

    public OnlinePlayerData(UUID originalUniqueId, UUID onlineUniqueId, List<ProfilePropertyWrapper> properties, Timeable time) {
        super(time == null ? 0 : time.getCreatedAt(), time == null ? 0 : time.getUpdatedAt());
        this.originalUniqueId = originalUniqueId;
        this.uniqueId = onlineUniqueId;
        this.properties = properties;
    }

    public PlayerData toPlayerData(String username) {
        return new PlayerData(
                originalUniqueId,
                uniqueId,
                username,
                properties,
                this
        );
    }

    public boolean isOnlineUniqueId() {
        return !Objects.equals(originalUniqueId, uniqueId);
    }

    public UUID getUniqueId() {
        return uniqueId == null ? originalUniqueId : uniqueId;
    }
}
