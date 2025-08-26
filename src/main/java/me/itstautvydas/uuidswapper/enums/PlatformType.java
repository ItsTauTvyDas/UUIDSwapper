package me.itstautvydas.uuidswapper.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.itstautvydas.uuidswapper.Utils;

@RequiredArgsConstructor
@Getter
public enum PlatformType {
    VELOCITY("Velocity"),
    BUNGEE("BungeeCord"),
    PAPER("Paper"),
    FOLIA("Folia");
    final String name;

    public PlatformType verifyFolia() {
        if (this == PAPER && Utils.isRunningFolia())
            return FOLIA;
        return this;
    }
}
