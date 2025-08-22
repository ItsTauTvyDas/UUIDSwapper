package me.itstautvydas.uuidswapper.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@SuppressWarnings("ClassCanBeRecord")
@RequiredArgsConstructor
@Getter
public class Message {
    private final String message;
    private final boolean translatable;

    public boolean hasMessage() {
        return message != null;
    }
}
