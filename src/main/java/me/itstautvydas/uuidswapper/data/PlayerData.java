package me.itstautvydas.uuidswapper.data;

import lombok.Getter;
import me.itstautvydas.uuidswapper.json.Jsonable;

import java.util.List;
import java.util.UUID;

@Getter
public class PlayerData extends OnlinePlayerData implements Jsonable {
    private final String username;

    public PlayerData(UUID originalUniqueId, UUID uniqueId, String username, List<ProfilePropertyWrapper> properties, Timeable time) {
        super(originalUniqueId, uniqueId, properties, time);
        this.username = username;
    }

    public PlayerData(UUID originalUniqueId, UUID uniqueId, String username, List<ProfilePropertyWrapper> properties) {
        super(originalUniqueId, uniqueId, properties, null);
        this.username = username;
    }
}
