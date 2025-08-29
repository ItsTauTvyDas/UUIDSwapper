package me.itstautvydas.uuidswapper.exception;

import com.google.gson.JsonParseException;

public class ConfigurationException extends JsonParseException {
    public ConfigurationException(String msg) {
        super(msg);
    }
}
