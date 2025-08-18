package me.itstautvydas.uuidswapper.enums;

public enum ResponseHandlerState {
    BEFORE_UUID,
    AFTER_UUID,
    AFTER_REQUEST,
    ON_FALLBACK,
    ON_DISCONNECT,
    ON_CONNECT;

    public static ResponseHandlerState fromString(String name, ResponseHandlerState defaultValue) {
        try {
            return valueOf(name);
        } catch (Exception ex) {
            return defaultValue;
        }
    }
}
