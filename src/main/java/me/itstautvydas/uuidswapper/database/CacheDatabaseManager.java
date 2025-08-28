package me.itstautvydas.uuidswapper.database;

import lombok.Getter;
import me.itstautvydas.uuidswapper.config.Configuration;
import me.itstautvydas.uuidswapper.crossplatform.PluginTaskWrapper;
import me.itstautvydas.uuidswapper.crossplatform.PluginWrapper;
import me.itstautvydas.uuidswapper.data.OnlinePlayerData;
import me.itstautvydas.uuidswapper.database.implementation.MemoryCacheImplementation;
import me.itstautvydas.uuidswapper.database.implementation.SQLiteImplementation;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public class CacheDatabaseManager {
    @Getter
    private DriverImplementation driver;
    private long timeCounter;
    private PluginTaskWrapper timer;

    private final Map<String, Supplier<DriverImplementation>> registry = new HashMap<>();

    public CacheDatabaseManager() {
        registerDriver("SQLite", SQLiteImplementation::new);
        registerDriver("Memory", MemoryCacheImplementation::new);
        registerDriver("Json", MemoryCacheImplementation::new);
    }

    public void registerDriver(String name, Supplier<DriverImplementation> driver) {
        registry.put(name, driver);
    }

    public void shutdown() {
        clean();
        registry.clear();
    }

    private void clean() {
        if (timer != null)
            timer.cancel();
        if (driver != null) {
            clearConnection();
        }
    }

    public void clearConnection() {
        if (driver == null)
            return;
        try {
            driver.debug("Trying to close connection");
            if (!driver.clearConnection())
                driver.debug("Connection was not cached");
            driver.debug("CONNECTION CLOSED");
        } catch (Exception ex) {
            PluginWrapper.getCurrent().logError("DatabaseManager", "Failed to close %s driver's connection!", ex, driver.getName());
        }
    }

    public void resetTimer() {
        resetCounter();
        if (timer != null)
            timer.cancel();
        var keepOpen = getConfiguration().getKeepOpenTime();
        if (keepOpen <= 0)
            return;
        var repeat = getConfiguration().getTimerRepeatTime();
        timer = PluginWrapper.getCurrent().scheduleTask(() -> {
            if (timeCounter == -1)
                return;
            timeCounter += repeat;
            if (timeCounter > keepOpen) {
                clearConnection();
                lockCounter();
            }
        }, repeat, 0);
    }

    public void resetCounter() {
        timeCounter = 0;
    }

    public void lockCounter() {
        timeCounter = -1;
    }

    public boolean loadDriverFromConfiguration() {
        clean();
        var name = getConfiguration().getDriverName();
        if (setDriverImplementation(name) == null) {
            PluginWrapper.getCurrent().logError("DatabaseManager", "Failed to load %s driver!", null, name);
            return false;
        }
        return true;
    }

    public Boolean setDriverImplementation(String name) {
        var driver = registry.get(name);
        if (driver == null)
            return null;
        return setDriverImplementation(driver.get(), name);
    }

    public boolean setDriverImplementation(DriverImplementation driver, String name) {
        try {
            driver.init(this, name);
            if (!driver.downloadDriverAndLoad())
                return false;
            Objects.requireNonNull(driver);
            driver.init();
            driver.debug("Connection initialization.");
            driver.debug("Connection timeout => %s", getConfiguration().getTimeout());
            driver.debug("Connection always kept => %s", shouldConnectionBeAlwaysKept());
            driver.debug("Connection cached => %s", shouldConnectionBeCached());

            try {
                driver.debug("Trying to create %s table", DriverImplementation.ONLINE_UUID_CACHE_TABLE);
                driver.createOnlineUuidCacheTable();
            } catch (Exception ex) {
                PluginWrapper.getCurrent().logError("Failed to create %s table", ex, DriverImplementation.ONLINE_UUID_CACHE_TABLE);
            }
            
            try {
                driver.createRandomizedPlayerDataTable();
            } catch (Exception ex) {
                PluginWrapper.getCurrent().logError("Failed to create %s table", ex, DriverImplementation.RANDOM_PLAYER_CACHE_TABLE);
            }
        } catch (Exception ex) {
            PluginWrapper.getCurrent().logError(driver.getPrefix(), "Failed to implement %s driver!", ex, name);
            return false;
        }
        this.driver = driver;
        return true;
    }

    public void storeOnlinePlayerCache(OnlinePlayerData player) {
        try {
            driver.debug("Trying to store online player (original UUID => %s)", player.getOriginalUniqueId());
            driver.storeOnlinePlayerCache(player);
        } catch (Exception ex) {
            PluginWrapper.getCurrent().logInfo(driver.getPrefix(), "Failed to store online player database for %s", ex, player.getOriginalUniqueId());
        }
    }

    public boolean shouldConnectionBeCached() {
        return getConfiguration().getKeepOpenTime() > 0;
    }

    public boolean shouldConnectionBeAlwaysKept() {
        return getConfiguration().getKeepOpenTime() <= -1;
    }

    public Configuration.DatabaseConfiguration getConfiguration() {
        return PluginWrapper.getCurrent().getConfiguration().getDatabase();
    }

    public boolean isDriverRunning() {
        try {
            return driver != null && !driver.isConnectionClosed();
        } catch (Exception ex) {
            return false;
        }
    }
}
