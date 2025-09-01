package me.itstautvydas.uuidswapper.database;

import lombok.Getter;
import me.itstautvydas.uuidswapper.config.Configuration;
import me.itstautvydas.uuidswapper.crossplatform.PluginWrapper;
import me.itstautvydas.uuidswapper.data.OnlinePlayerData;
import me.itstautvydas.uuidswapper.data.PlayerData;
import me.itstautvydas.uuidswapper.json.PostProcessable;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.function.BiFunction;

@Getter
public abstract class DriverImplementation implements PostProcessable {
    public static final String CREATED_AT = "created_at";
    public static final String UPDATED_AT = "updated_at";

    public static final String ONLINE_UUID_CACHE_TABLE = "online_uuid_cache";
    public static final String ONLINE_UUID_CACHE_ORIGINAL_UUID = "original_uuid";
    public static final String ONLINE_UUID_CACHE_ONLINE_UUID = "online_uuid";
    public static final String ONLINE_UUID_CACHE_PROPERTIES = "properties";

    public static final String RANDOM_PLAYER_CACHE_TABLE = "random_player";
    public static final String RANDOM_PLAYER_CACHE_ORIGINAL_UUID = "original_uuid";
    public static final String RANDOM_PLAYER_CACHE_USERNAME = "username";
    public static final String RANDOM_PLAYER_CACHE_UUID = "uuid";

    private String name; // Expose this to GSON
    private transient String prefix;
    protected transient boolean supportsCaching;
    protected transient boolean isDatabase;

    public void postProcessed() {
        this.prefix = "DatabaseManager/" + name;
    }

    private static Path getDriverPath(DriverImplementation driver) {
        return PluginWrapper.getCurrent().getDriversDirectory().resolve(driver.name.replace(" ", "_") + ".jar");
    }

    public static boolean downloadDriver(DriverImplementation driver, boolean enabled, String downloadUrl) {
        var driverPath = getDriverPath(driver);
        if (!Files.exists(driverPath)) {
            if (!enabled) {
                driver.info(
                        "Driver's file doesn't exist (/drivers/%s) and download function is disabled!",
                        null, driverPath.getFileName()
                );
                return false;
            }
            if (downloadUrl == null) {
                driver.info(
                        "Can't download driver, no link provided either by driver's driver author or in the configuration",
                        null, driverPath.getFileName()
                );
                return false;
            }
            driver.info("Downloading %s...", driverPath.getFileName());
            try (var in = new URL(downloadUrl).openStream()) {
                Files.copy(in, driverPath, StandardCopyOption.REPLACE_EXISTING);
                driver.info("Driver successfully downloaded! Saved as /drivers/%s", driverPath.getFileName());
            } catch (Exception ex) {
                driver.info("Failed to download driver from %s!", ex, downloadUrl);
                return false;
            }
        } else {
            driver.info("Driver was found, loading %s", driverPath.getFileName());
        }
        return true;
    }

    public static boolean loadClass(DriverImplementation driver, String classToLoad, BiFunction<ClassLoader, Class<?>, Exception> onJarLoaded) {
        var driverPath = getDriverPath(driver);
        try {
            var loader = new URLClassLoader(
                    new URL[] {
                            driverPath.toUri().toURL()
                    }, DriverImplementation.class.getClassLoader()
            );

            if (classToLoad != null) {
                var driverClass = Class.forName(classToLoad, true, loader);
                var exception = onJarLoaded.apply(loader, driverClass);
                driver.info("%s class loaded", classToLoad);
                if (exception != null)
                    throw exception;
                return true;
            }
            var exception = onJarLoaded.apply(loader, null);
            if (exception != null)
                throw exception;
            return true;
        } catch (Exception ex) {
            driver.info("Failed to load %s!", ex, driverPath);
            return false;
        }
    }

    public final boolean shouldCreateNewConnection(Object connection) {
        debug("Trying to open new connection");
        if (this instanceof CacheableConnectionDriverImplementation cacheable && cacheable.shouldConnectionBeCached())
            getManager().resetCounter();
        return connection == null;
    }

    public void debug(String message, Object ...args) {
        if (!getConfiguration().isDebugEnabled())
            return;
        PluginWrapper.getCurrent().logInfo(prefix, "[DEBUG] " + message, args);
    }

    public void info(String message, Object ...args) {
        if (!getConfiguration().isDebugEnabled())
            return;
        PluginWrapper.getCurrent().logInfo(prefix, message, args);
    }

    public Configuration.DatabaseConfiguration getConfiguration() {
        return getManager().getConfiguration();
    }

    public final CacheDatabaseManager getManager() {
        return PluginWrapper.getCurrent().getDatabase();
    }

    public abstract boolean init() throws Exception;
    public abstract boolean clearConnection() throws Exception;
    public abstract boolean isConnectionClosed() throws Exception;

    public abstract void createOnlineUuidCacheTable() throws Exception;
    public abstract void createRandomizedPlayerDataTable() throws Exception;

    public abstract void storeOnlinePlayerCache(OnlinePlayerData player) throws Exception;
    public abstract OnlinePlayerData getOnlinePlayerCache(UUID uuid) throws Exception;

    public abstract void storeRandomPlayerCache(PlayerData player) throws Exception;
    public abstract PlayerData getRandomPlayerCache(UUID uuid) throws Exception;
}
