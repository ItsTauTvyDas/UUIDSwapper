package me.itstautvydas.uuidswapper.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Getter
public class PlayerData {
    private final UUID originalUniqueId;
    private final String username;
    private final UUID uniqueId;
    @Setter
    private List<ProfileProperty> properties;
}
