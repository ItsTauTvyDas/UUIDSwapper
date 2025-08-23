package me.itstautvydas.uuidswapper.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum PlatformType {
    VELOCITY("Velocity"),
    BUNGEE("BungeeCord"),
    PAPER("Paper");
    final String name;
}
