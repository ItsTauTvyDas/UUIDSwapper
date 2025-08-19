package me.itstautvydas.uuidswapper.database;

import me.itstautvydas.uuidswapper.config.DatabaseConfiguration;
import me.itstautvydas.uuidswapper.helper.SimpleLogger;
import org.slf4j.event.Level;

import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

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
    private SimpleLogger logger;
    private String name;

    protected final void init(CacheDatabaseManager manager, String driverName) {
        this.manager = manager;
        this.name = driverName;
        this.logger = new SimpleLogger(manager.getPlugin().getLogger(), () -> getConfiguration().isDatabaseDebugEnabled()) {
            @Override
            public String getLoggerPrefix() {
                return "[DatabaseManager/" + driverName + "]: ";
            }
        };
    }

    protected boolean downloadDriverAndLoad() {
        var driverPath = getManager().getPlugin().getDriversDirectory().resolve(name.replace(" ", "_") + ".jar");
        // Download driver
        if (!Files.exists(driverPath)) {
            if (!getConfiguration().shouldDatabaseBeDownloaded()) {
                getLogger().log(Level.ERROR,
                        "Driver's file doesn't exist (/drivers/{}) and download function is disabled!",
                        driverPath.getFileName());
                return false;
            }
            var url = getDownloadUrl();
            if (url == null)
                url = getConfiguration().getDatabaseDriverDownloadLink();
            if (url == null) {
                getLogger().log(Level.ERROR,
                        "Can't download driver, no link provided either by driver's implementation author or in the configuration",
                        driverPath.getFileName());
                return false;
            }
            getLogger().log(Level.INFO, "Downloading {}...", driverPath.getFileName());
            try (var in = new URL(url).openStream()) {
                Files.copy(in, driverPath, StandardCopyOption.REPLACE_EXISTING);
                getLogger().log(Level.INFO, "Driver successfully downloaded! Saved as /drivers/{}", driverPath.getFileName());
            } catch (Exception ex) {
                getLogger().log(Level.ERROR,
                        "Failed to download driver from {}!",
                        ex, url);
                return false;
            }
        } else {
            getLogger().log(Level.INFO, "Driver was found, loading {}", driverPath.getFileName());
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
                getLogger().log(Level.INFO, "{} class loaded", classToLoad);
                return true;
            }
            onJarFileLoad(loader, null);
            return true;
        } catch (Exception ex) {
            getLogger().log(Level.ERROR,
                    "Failed to load {}!",
                    ex, driverPath);
            return false;
        }
    }

    public final boolean shouldCreateNewConnection(Object connection) {
        if (getManager().shouldConnectionBeCached()) {
            getManager().resetCounter();
        }
        return connection == null;
    }

    public final String getName() {
        return name;
    }

    public final SimpleLogger getLogger() {
        return logger;
    }

    public final CacheDatabaseManager getManager() {
        return manager;
    }

    public DatabaseConfiguration getConfiguration() {
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
    public abstract void clearConnection();
    public abstract boolean isConnectionClosed();

    public abstract void createOnlineUuidCacheTable(boolean useCreatedAt, boolean useUpdatedAt);
    public abstract void createRandomizedPlayerDataTable();

    public abstract void storeOnlinePlayerCache(PlayerCache player);
    public abstract PlayerCache getOnlinePlayerCache(InetSocketAddress address);
    public abstract PlayerCache getOnlinePlayerCache(UUID uuid);

    public abstract void storeRandomPlayerCache(RandomCache player);
    public abstract RandomCache getRandomPlayerCache(UUID uuid);
}
