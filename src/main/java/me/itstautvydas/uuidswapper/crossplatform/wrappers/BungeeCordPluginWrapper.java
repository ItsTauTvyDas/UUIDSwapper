package me.itstautvydas.uuidswapper.crossplatform.wrappers;

import me.itstautvydas.uuidswapper.Utils;
import me.itstautvydas.uuidswapper.crossplatform.PluginTaskWrapper;
import me.itstautvydas.uuidswapper.crossplatform.PluginWrapper;
import me.itstautvydas.uuidswapper.enums.PlatformType;
import me.itstautvydas.uuidswapper.loader.UUIDSwapperBungeeCord;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.scheduler.ScheduledTask;
import org.jetbrains.annotations.NotNull;

import java.io.PrintWriter;
import java.io.Writer;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class BungeeCordPluginWrapper extends PluginWrapper<UUIDSwapperBungeeCord, ScheduledTask, Logger, ProxyServer> {
    @Override
    public boolean isServerOnlineMode() {
        return server.getConfig().isOnlineMode();
    }

    @Override
    public void logInfo(String prefix, String message, Object... args) {
        if (prefix != null)
            message = prefix + message;
        message = Utils.fixLogMessageFormat(message, PlatformType.BUNGEE);
        logger.log(java.util.logging.Level.INFO, message, args);
    }

    @Override
    public void logWarning(String prefix, String message, Throwable exception, Object... args) {
        if (prefix != null)
            message = prefix + message;
        message = Utils.fixLogMessageFormat(message, PlatformType.BUNGEE);
        logger.log(java.util.logging.Level.WARNING, message, args);
        printException(exception, false);
    }

    @Override
    public void logError(String prefix, String message, Throwable exception, Object... args) {
        if (prefix != null)
            message = prefix + message;
        message = Utils.fixLogMessageFormat(message, PlatformType.BUNGEE);
        logger.log(java.util.logging.Level.SEVERE, message, args);
        printException(exception, true);
    }

    @Override
    public PluginTaskWrapper<ScheduledTask> scheduleTask(Runnable run, Long repeatInSeconds, long delayInSeconds) {
        ScheduledTask task;
        if (repeatInSeconds == null)
            task = server.getScheduler().schedule(handle, run, delayInSeconds, TimeUnit.SECONDS);
        else
            task = server.getScheduler().schedule(handle, run, delayInSeconds, repeatInSeconds, TimeUnit.SECONDS);
        return new PluginTaskWrapper<>(task) {
            @Override
            public void cancel() {
                handle.cancel();
            }
        };
    }

    private void printException(Throwable exception, boolean isError) {
        if (exception == null) return;
        exception.printStackTrace(new PrintWriter(new Writer() {
            @Override
            public void write(char @NotNull []cbuf, int off, int len) {
                var str = new String(cbuf, off, len).trim();
                if (str.isBlank())
                    return;
                if (isError)
                    logger.severe(str);
                else
                    logger.warning(str);
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        }));
    }
}
