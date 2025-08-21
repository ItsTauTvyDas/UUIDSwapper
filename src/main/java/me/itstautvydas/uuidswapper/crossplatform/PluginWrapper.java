package me.itstautvydas.uuidswapper.crossplatform;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import lombok.Getter;
import me.itstautvydas.uuidswapper.config.Configuration;
import me.itstautvydas.uuidswapper.database.CacheDatabaseManager;
import me.itstautvydas.uuidswapper.loader.UUIDSwapperBungeeCord;
import me.itstautvydas.uuidswapper.loader.UUIDSwapperVelocity;
import org.slf4j.Logger;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

@Getter
public abstract class PluginWrapper<P, T, L, S> {
    private static PluginWrapper<?, ?, ?, ?> CURRENT;

    @SuppressWarnings("unchecked")
    public static <P, T, L, S> void init(PlatformType type, P plugin, S serverObject, L loggerObject, Path configurationPath) {
        if (CURRENT != null)
            throw new RuntimeException("Cross-platform implementation is already done!");
        PluginWrapper<P, T, L, S> implementation = switch (type) {
            case VELOCITY -> (PluginWrapper<P, T, L, S>) new VelocityPluginWrapper();
            case BUNGEE -> (PluginWrapper<P, T, L, S>) new BungeeCordPluginWrapper();
        };
        implementation.server = serverObject;
        implementation.logger = loggerObject;
        implementation.configurationPath = configurationPath;
        implementation.handle = plugin;
        implementation.onInit();
        implementation.logInfo("Initiated " + type.name + " implementation.");
        CURRENT = implementation;
    }

    public static PluginWrapper<?, ?, ?, ?> getCurrent() {
        return CURRENT;
    }

    @SuppressWarnings("unchecked")
    public static PluginWrapper<UUIDSwapperBungeeCord, net.md_5.bungee.api.scheduler.ScheduledTask, java.util.logging.Logger, net.md_5.bungee.api.ProxyServer> getBungeeCord() {
        return (PluginWrapper<UUIDSwapperBungeeCord, net.md_5.bungee.api.scheduler.ScheduledTask, java.util.logging.Logger, net.md_5.bungee.api.ProxyServer>) CURRENT;
    }

    @SuppressWarnings("unchecked")
    public static PluginWrapper<UUIDSwapperVelocity, ScheduledTask, Logger, ProxyServer> getVelocity() {
        return (PluginWrapper<UUIDSwapperVelocity, ScheduledTask, Logger, ProxyServer>) CURRENT;
    }

    @Getter
    protected PlatformType platformType;
    protected ConfigurationWrapper configHandle;
    protected P handle;
    protected L logger;
    protected S server;
    @Getter
    protected Path configurationPath;

    @Getter
    private Configuration configuration;
    private Path driversDirectory;
    @Getter
    private CacheDatabaseManager database;

    public void loadConfiguration() throws Exception {
        if (Files.notExists(configurationPath)) {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(getResourceConfigurationName())) {
                if (in != null) {
                    logInfo("Copying configuration file...");
                    Files.copy(in, configurationPath);
                }
            }
        }
    }

    private void onInit() {
        database = new CacheDatabaseManager();
        configuration = new Configuration();
    }

    public void onEnable() {
        try {
            reloadConfiguration();
            if (database != null)
                database.init();
        } catch (Exception ex) {
            logError("Could not initialize plugin!", ex);
        }
    }

    public void onDisable() {
        database.shutdown();
    }

    public Path getDataDirectory() {
        return configurationPath.getParent();
    }

    private void log(Map.Entry<String, Object> entry) {
        logInfo("# {} => {}", entry.getKey(), entry.getValue());
    }

    public void reloadConfiguration() throws Exception {
        var dataDirectory = configurationPath.getParent();
        Files.createDirectories(dataDirectory);

        driversDirectory = dataDirectory.resolve("drivers");
        Files.createDirectories(driversDirectory);

        loadConfiguration();
        configuration.load(configHandle);

        logInfo("Configuration loaded.");
        logInfo("Using online UUIDs => {}", configuration.areOnlineUuidsEnabled());

        logInfo("Loaded {} swapped UUIDs.", configuration.getSwappedUuids().size());
        for (var entry : configuration.getSwappedUuids().entrySet())
            log(entry);

        logInfo("Loaded {} custom player usernames.", configuration.getCustomPlayerNames().size());
        for (var entry : configuration.getCustomPlayerNames().entrySet())
            log(entry);
    }

    public void onPlayerLogin(String username, UUID uniqueId, InetSocketAddress address) {

    }

    public UUID generateOfflinePlayerUuid(String username) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
    }

    public void logInfo(String message, Object ...args) {
        logInfo(null, message, args);
    }

    public void logWarning(String message, Throwable exception, Object ...args) {
        logWarning(null, message, exception, args);
    }

    public void logError(String message, Throwable exception, Object ...args) {
        logError(null, message, exception, args);
    }

    public abstract String getResourceConfigurationName();
    public abstract boolean isServerOnlineMode();
    public abstract void logInfo(String prefix, String message, Object ...args);
    public abstract void logWarning(String prefix, String message, Throwable exception, Object ...args);
    public abstract void logError(String prefix, String message, Throwable exception, Object ...args);
    public abstract PluginTaskWrapper<T> scheduleTask(Runnable run, Long repeatInSeconds, long delayInSeconds);
}
