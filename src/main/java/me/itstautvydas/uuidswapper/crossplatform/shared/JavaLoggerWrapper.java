package me.itstautvydas.uuidswapper.crossplatform.shared;

import me.itstautvydas.uuidswapper.Utils;
import me.itstautvydas.uuidswapper.crossplatform.PluginWrapper;

import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class JavaLoggerWrapper<P, S, M>  extends PluginWrapper<P, Logger, S, M> {
    @Override
    public void logInfo(String prefix, String message, Object... args) {
        logger.log(Level.INFO, Utils.toLoggerMessage(prefix, message, args));
    }

    @Override
    public void logWarning(String prefix, String message, Throwable exception, Object... args) {
        logger.log(Level.WARNING, Utils.toLoggerMessage(prefix, message, args));
        Utils.printException(exception, logger::warning);
    }

    @Override
    public void logError(String prefix, String message, Throwable exception, Object... args) {
        logger.log(Level.SEVERE, Utils.toLoggerMessage(prefix, message, args));
        Utils.printException(exception, logger::severe);
    }
}
