package me.itstautvydas.uuidswapper.crossplatform;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum PlatformType {
    VELOCITY("Velocity"),
    BUNGEE("BungeeCord");
    final String name;
}
