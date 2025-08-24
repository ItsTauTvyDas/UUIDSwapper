package me.itstautvydas.uuidswapper.database;

import lombok.Getter;
import me.itstautvydas.uuidswapper.config.Configuration;
import me.itstautvydas.uuidswapper.crossplatform.PluginWrapper;
import me.itstautvydas.uuidswapper.data.OnlinePlayerData;
import me.itstautvydas.uuidswapper.data.PlayerData;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Getter
public abstract class DriverImplementation {
    public static final String CREATED_AT = "created_at";
    public static final String UPDATED_AT = "updated_at";

    public static final String ONLINE_UUID_CACHE_TABLE = "online_uuid_cache";
    public static final String ONLINE_UUID_CACHE_ORIGINAL_UUID = "original_uuid";
    public static final String ONLINE_UUID_CACHE_ONLINE_UUID = "online_uuid";
    public static final String ONLINE_UUID_CACHE_IP_ADDRESS = "ip_address";

    public static final String RANDOM_PLAYER_CACHE_TABLE = "random_player";
    public static final String RANDOM_PLAYER_CACHE_ORIGINAL_UUID = "original_uuid";
    public static final String RANDOM_PLAYER_CACHE_USERNAME = "username";
    public static final String RANDOM_PLAYER_CACHE_UUID = "uuid";

    private CacheDatabaseManager manager;
    private String name;
    private String prefix;

    protected final void init(CacheDatabaseManager manager, String driverName) {
        this.manager = manager;
        this.name = driverName;
        this.prefix = "DatabaseManager/" + driverName;
    }

    protected boolean downloadDriverAndLoad() {
        var driverPath = PluginWrapper.getCurrent().getDriversDirectory().resolve(name.replace(" ", "_") + ".jar");
        // Download driver
        if (!Files.exists(driverPath)) {
            if (!getConfiguration().isDownloadDriver()) {
                PluginWrapper.getCurrent().logError(prefix,
                        "Driver's file doesn't exist (/drivers/{}) and download function is disabled!",
                        null, driverPath.getFileName());
                return false;
            }
            var url = getDownloadUrl();
            if (url == null)
                url = getConfiguration().getDriverDownloadLink();
            if (url == null) {
                PluginWrapper.getCurrent().logError(prefix,
                        "Can't download driver, no link provided either by driver's implementation author or in the configuration",
                        null, driverPath.getFileName());
                return false;
            }
            PluginWrapper.getCurrent().logInfo(prefix, "Downloading {}...", driverPath.getFileName());
            try (var in = new URL(url).openStream()) {
                Files.copy(in, driverPath, StandardCopyOption.REPLACE_EXISTING);
                PluginWrapper.getCurrent().logInfo(prefix, "Driver successfully downloaded! Saved as /drivers/{}", driverPath.getFileName());
            } catch (Exception ex) {
                PluginWrapper.getCurrent().logError(prefix, "Failed to download driver from {}!", ex, url);
                return false;
            }
        } else {
            PluginWrapper.getCurrent().logInfo(prefix, "Driver was found, loading {}", driverPath.getFileName());
        }
        // Load class
        String classToLoad = getClassToLoad();
        try {
            var loader = new URLClassLoader(
                    new URL[]{driverPath.toUri().toURL()},
                    this.getClass().getClassLoader()
            );

            if (classToLoad != null) {
                var driverClass = Class.forName(classToLoad, true, loader);
                onJarFileLoad(loader, driverClass);
                PluginWrapper.getCurrent().logInfo(prefix, "{} class loaded", classToLoad);
                return true;
            }
            onJarFileLoad(loader, null);
            return true;
        } catch (Exception ex) {
            PluginWrapper.getCurrent().logError(prefix, "Failed to load {}!", ex, driverPath);
            return false;
        }
    }

    public final boolean shouldCreateNewConnection(Object connection) {
        if (getManager().shouldConnectionBeCached())
            getManager().resetCounter();
        return connection == null;
    }
    
    public void debug(String message, Object ...args) {
        if (!getConfiguration().isDebugEnabled())
            return;
        PluginWrapper.getCurrent().logInfo(prefix, "DEBUG - " + message, args);
    }

    public Configuration.DatabaseConfiguration getConfiguration() {
        return manager.getConfiguration();
    }

    public void onJarFileLoad(ClassLoader loader, Class<?> clazz) throws Exception {
        // Do nothing
    }

    public String getDownloadUrl() {
        return null;
    }

    public String getClassToLoad() {
        return null;
    }

    public abstract void init() throws Exception;
    public abstract void clearConnection() throws Exception;
    public abstract boolean isConnectionClosed() throws Exception;

    public abstract void createOnlineUuidCacheTable(boolean useCreatedAt, boolean useUpdatedAt) throws Exception;
    public abstract void createRandomizedPlayerDataTable() throws Exception;

    public abstract void storeOnlinePlayerCache(OnlinePlayerData player) throws Exception;
    public abstract OnlinePlayerData getOnlinePlayerCache(String address) throws Exception;
    public abstract OnlinePlayerData getOnlinePlayerCache(UUID uuid) throws Exception;

    public abstract void storeRandomPlayerCache(PlayerData player) throws Exception;
    public abstract PlayerData getRandomPlayerCache(UUID uuid) throws Exception;
}
