package me.itstautvydas.uuidswapper.database;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import me.itstautvydas.uuidswapper.annotation.RequiredProperty;
import me.itstautvydas.uuidswapper.config.Configuration;
import me.itstautvydas.uuidswapper.json.Jsonable;
import me.itstautvydas.uuidswapper.multiplatform.MultiPlatform;
import me.itstautvydas.uuidswapper.data.OnlinePlayerData;
import me.itstautvydas.uuidswapper.data.PlayerData;
import me.itstautvydas.uuidswapper.json.PostProcessable;
import me.itstautvydas.uuidswapper.processor.ReadMeDescription;
import me.itstautvydas.uuidswapper.processor.ReadMeExtraFields;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;

@Getter
@ReadMeExtraFields({
        // JSON's class field is only used in DriverPolymorphicAdapterFactory, no need to have it in the class itself
        "class;required=true", "(Do not edit unless you are implementing your own driver!!!) Driver class path to load and initiate for later use, if class is defined without a package, it's treated as built-in one", "String"
})
public abstract class DriverImplementation implements PostProcessable, Jsonable {
    public static final String CREATED_AT = "created_at";
    public static final String UPDATED_AT = "updated_at";

    public static final String ONLINE_UUID_CACHE_TABLE = "online_uuid_cache";
    public static final String RANDOM_PLAYER_CACHE_TABLE = "random_player";

    public static final String KEY_OVERWRITE_UUID = "modified_uuid";
    public static final String KEY_USERNAME = "username";
    public static final String KEY_ORIGINAL_UUID = "uuid";
    public static final String KEY_PROPERTIES = "properties";

    @RequiredProperty
    @SerializedName("name")
    @ReadMeDescription("Name of the driver")
    private String name; // Expose this to GSON

    private transient String prefix;

    @Override
    public void postProcessed() {
        this.prefix = "DatabaseManager/" + name;
    }

    public Path getDriverPath() {
        return MultiPlatform.get().getDriversDirectory().resolve(name.replace(" ", "_") + ".jar");
    }

    protected boolean downloadDriver(boolean enabled, String downloadUrl) {
        var driverPath = getDriverPath();
        if (!Files.exists(driverPath)) {
            if (!enabled) {
                error(
                        "Driver's file doesn't exist (/drivers/%s) and download function is disabled!",
                        null, driverPath.getFileName()
                );
                return false;
            }
            if (downloadUrl == null) {
                error(
                        "Can't download driver, no link provided either by driver's driver author or in the configuration",
                        null, driverPath.getFileName()
                );
                return false;
            }
            info("Downloading %s...", driverPath.getFileName());
            try (var in = new URL(downloadUrl).openStream()) {
                Files.copy(in, driverPath, StandardCopyOption.REPLACE_EXISTING);
                info("Driver successfully downloaded! Saved as /drivers/%s", driverPath.getFileName());
            } catch (Exception ex) {
                error("Failed to download driver from %s!", ex, downloadUrl);
                return false;
            }
        } else {
            info("Driver was found, loading %s", driverPath.getFileName());
        }
        return true;
    }

    protected boolean loadClass(String classToLoad, BiFunction<ClassLoader, Class<?>, Exception> onJarLoaded) {
        var driverPath = getDriverPath();
        try {
            var loader = new URLClassLoader(
                    new URL[] {
                            driverPath.toUri().toURL()
                    }, DriverImplementation.class.getClassLoader()
            );

            if (classToLoad != null) {
                var driverClass = Class.forName(classToLoad, true, loader);
                var exception = onJarLoaded.apply(loader, driverClass);
                info("%s class loaded", classToLoad);
                if (exception != null)
                    throw exception;
                return true;
            }
            var exception = onJarLoaded.apply(loader, null);
            if (exception != null)
                throw exception;
            return true;
        } catch (Exception ex) {
            error("Failed to load %s!", ex, driverPath);
            return false;
        }
    }

    public void debug(String message, Object ...args) {
        if (!getConfiguration().isDebugEnabled())
            return;
        MultiPlatform.get().logInfo(prefix, "[DEBUG] " + message, args);
    }

    public void info(String message, Object ...args) {
        if (!getConfiguration().isDebugEnabled())
            return;
        MultiPlatform.get().logInfo(prefix, message, args);
    }

    public void error(String message, Throwable throwable, Object ...args) {
        if (!getConfiguration().isDebugEnabled())
            return;
        MultiPlatform.get().logError(prefix, message, throwable, args);
    }

    public Configuration.DatabaseConfiguration getConfiguration() {
        return getManager().getConfiguration();
    }

    public final CacheDatabaseManager getManager() {
        return MultiPlatform.get().getDatabase();
    }

    public abstract boolean init() throws Exception;
    public abstract void clean();
    public abstract boolean isRunning();

    public abstract void storeOnlinePlayerCache(OnlinePlayerData player) throws Exception;
    public abstract OnlinePlayerData getOnlinePlayerCache(UUID uuid) throws Exception;
    public abstract List<OnlinePlayerData> getOnlinePlayersCache() throws Exception;

    public abstract void storeRandomPlayerCache(PlayerData player) throws Exception;
    public abstract PlayerData getRandomPlayerCache(UUID uuid) throws Exception;
    public abstract List<PlayerData> getRandomPlayersCache() throws Exception;
}
