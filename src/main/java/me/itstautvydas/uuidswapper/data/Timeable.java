package me.itstautvydas.uuidswapper.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@RequiredArgsConstructor
@Getter
@ToString
public class Timeable {
    private final long createdAt, updatedAt;
}
