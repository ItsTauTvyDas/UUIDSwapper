package me.itstautvydas.uuidswapper.crossplatform;

import com.moandjiezana.toml.Toml;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import me.itstautvydas.uuidswapper.loader.UUIDSwapperVelocity;
import org.slf4j.Logger;
import org.slf4j.event.Level;

import java.util.concurrent.TimeUnit;

public class VelocityPluginWrapper extends PluginWrapper<UUIDSwapperVelocity, ScheduledTask, Logger, ProxyServer> {
    @Override
    public void loadConfiguration() throws Exception {
        super.loadConfiguration();
        var handle = new Toml().read(configurationPath.toFile());
        configHandle = new ConfigurationWrapper.VelocityConfigurationWrapper(handle, null);
    }

    @Override
    public String getResourceConfigurationName() {
        return "config-velocity.toml";
    }

    @Override
    public boolean isServerOnlineMode() {
        return server.getConfiguration().isOnlineMode();
    }

    @Override
    public void logInfo(String prefix, String message, Object... args) {
        if (prefix != null)
            message = prefix + message;
        logger.info(message, args);
    }

    @Override
    public void logWarning(String prefix, String message, Throwable exception, Object... args) {
        if (prefix != null)
            message = prefix + message;
        logger.atLevel(Level.WARN)
                .setMessage(exception == null ? null : exception.getMessage())
                .setCause(exception)
                .log(message, args);
    }

    @Override
    public void logError(String prefix, String message, Throwable exception, Object... args) {
        if (prefix != null)
            message = prefix + message;
        logger.atLevel(Level.ERROR)
                .setMessage(exception == null ? null : exception.getMessage())
                .setCause(exception)
                .log(message, args);
    }

    @Override
    public PluginTaskWrapper<ScheduledTask> scheduleTask(Runnable run, Long repeatInSeconds, long delayInSeconds) {
        var builder = server.getScheduler().buildTask(handle, run);
        if (repeatInSeconds != null)
            builder.repeat(repeatInSeconds, TimeUnit.SECONDS);
        builder.delay(delayInSeconds, TimeUnit.SECONDS);
        return new PluginTaskWrapper<>(builder.schedule()) {
            @Override
            public void cancel() {
                handle.cancel();
            }
        };
    }
}
