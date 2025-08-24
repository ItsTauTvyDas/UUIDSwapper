package me.itstautvydas.uuidswapper.helper;

public interface SimplifiedLogger {
    default void logInfo(String message, Object ...args) {
        logInfo(null, message, args);
    }

    default void logWarning(String message, Throwable exception, Object ...args) {
        logWarning(null, message, exception, args);
    }

    default void logError(String message, Throwable exception, Object ...args) {
        logError(null, message, exception, args);
    }

    void logInfo(String prefix, String message, Object ...args);
    void logWarning(String prefix, String message, Throwable exception, Object ...args);
    void logError(String prefix, String message, Throwable exception, Object ...args);
}
