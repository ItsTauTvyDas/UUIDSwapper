package me.itstautvydas.uuidswapper.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@SuppressWarnings("ClassCanBeRecord")
@RequiredArgsConstructor
@Getter
public class ProfileProperty {
    private final String name;
    private final String value;
    private final String signature;
}
