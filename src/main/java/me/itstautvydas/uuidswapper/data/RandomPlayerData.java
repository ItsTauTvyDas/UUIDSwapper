package me.itstautvydas.uuidswapper.data;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@SuppressWarnings("ClassCanBeRecord")
@AllArgsConstructor
@Getter
public class RandomPlayerData {
    private final UUID originalUuid;
    private final String randomUsername;
    private final UUID randomUuid;
}
