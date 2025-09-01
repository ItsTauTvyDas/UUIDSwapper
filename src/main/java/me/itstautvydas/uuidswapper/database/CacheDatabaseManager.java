package me.itstautvydas.uuidswapper.database;

import lombok.Getter;
import me.itstautvydas.uuidswapper.config.Configuration;
import me.itstautvydas.uuidswapper.crossplatform.PluginTaskWrapper;
import me.itstautvydas.uuidswapper.crossplatform.PluginWrapper;
import me.itstautvydas.uuidswapper.data.OnlinePlayerData;

import java.util.Objects;

public class CacheDatabaseManager {
    @Getter
    private DriverImplementation driver;
    private long timeCounter;
    private PluginTaskWrapper timer;

    public void shutdown() {
        clean();
    }

    private void clean() {
        if (timer != null)
            timer.cancel();
        if (driver != null) {
            if (!driver.supportsCaching)
                return;
            clearConnection();
        }
    }

    public void clearConnection() {
        if (driver == null)
            return;
        if (!driver.supportsCaching)
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
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        if (driver != null && !driver.supportsCaching)
            return;
        if (driver instanceof CacheableConnectionDriverImplementation cacheable) {
            var keepOpen = cacheable.getKeepOpenTime();
            if (keepOpen <= 0)
                return;
            var repeat = cacheable.getTimerRepeatTime();
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
        var driver = getConfiguration().getDriver(name);
        if (driver == null)
            return null;
        return setDriverImplementation(driver);
    }

    public boolean setDriverImplementation(DriverImplementation driver) {
        try {
            Objects.requireNonNull(driver);
            if (!driver.init())
                return false;
            driver.debug("Connection initialization.");
            if (driver instanceof CacheableConnectionDriverImplementation cacheable) {
                driver.debug("Connection timeout => %s", cacheable.getTimeout());
                driver.debug("Connection always kept => %s", cacheable.shouldConnectionBeAlwaysKept());
                driver.debug("Connection cached => %s", cacheable.shouldConnectionBeCached());
            }

            if (driver.connectionBased) {
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
            }
        } catch (Exception ex) {
            PluginWrapper.getCurrent().logError(driver.getPrefix(), "Failed to initialize %s driver!", ex, driver.getName());
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
