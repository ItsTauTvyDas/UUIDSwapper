package me.itstautvydas.uuidswapper.helper;

import org.slf4j.Logger;
import org.slf4j.event.Level;

import java.util.function.Supplier;

public abstract class SimpleLogger {
    private final Logger logger;
    private final Supplier<Boolean> isDebugEnabled;

    public SimpleLogger(Logger logger, Supplier<Boolean> isDebugEnabled) {
        this.logger = logger;
        this.isDebugEnabled = isDebugEnabled;
    }

    public abstract String getLoggerPrefix();

    public void log(Level level, String string, Object ...args) {
        string = getLoggerPrefix() + string;
        logger.atLevel(level).log(string, args);
    }

    public void debug(Level level, String string, Object ...args) {
        if (isDebugEnabled == null || !isDebugEnabled.get())
            return;
        string = getLoggerPrefix() + " DEBUG - " + string;
        logger.atLevel(level).log(string, args);
    }

    public void log(Level level, String string, Throwable throwable, Object ...args) {
        string = getLoggerPrefix() + string;
        logger.atLevel(level)
                .setMessage(throwable.getMessage())
                .setCause(throwable)
                .log(string, args);
    }
}
