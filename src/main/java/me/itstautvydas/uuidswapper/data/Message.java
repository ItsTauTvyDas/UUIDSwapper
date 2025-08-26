package me.itstautvydas.uuidswapper.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import me.itstautvydas.uuidswapper.Utils;

import java.util.Map;

@AllArgsConstructor
@Getter @ToString
public class Message {
    private String message;
    private final boolean translatable;

    public boolean hasMessage() {
        return message != null;
    }

    public Message replacePlaceholders(Map<String, Object> placeholders) {
        this.message = Utils.replacePlaceholders(message, placeholders);
        return this;
    }
}
