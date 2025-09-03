package me.itstautvydas.uuidswapper.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import me.itstautvydas.uuidswapper.json.Jsonable;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Getter
public class PlayerData implements Jsonable {
    private final UUID originalUniqueId;
    private final String username;
    private final UUID uniqueId;
    @Setter
    private List<ProfilePropertyWrapper> properties;

    public OnlinePlayerData toOnlineData() {
        return new OnlinePlayerData(originalUniqueId, uniqueId, properties);
    }
}
