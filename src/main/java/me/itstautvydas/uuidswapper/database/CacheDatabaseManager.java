package me.itstautvydas.uuidswapper.database;

import lombok.Getter;
import me.itstautvydas.uuidswapper.config.Configuration;
import me.itstautvydas.uuidswapper.data.DatabaseObject;
import me.itstautvydas.uuidswapper.data.PlayerData;
import me.itstautvydas.uuidswapper.multiplatform.MultiPlatform;
import me.itstautvydas.uuidswapper.data.OnlinePlayerData;

import java.util.*;

public class CacheDatabaseManager {
    @Getter
    private DriverImplementation driver;

    public void clear() {
        if (driver != null) {
            try {
                driver.clean();
            } catch (Exception ex) {
                driver.error("Exception caught while cleaning up", ex);
            }
        }
    }

    public boolean loadDriverFromConfiguration() {
        var name = getConfiguration().getDriverName();
        if (setDriverImplementation(name) == null) {
            MultiPlatform.get().logError("DatabaseManager", "Failed to load %s driver!", null, name);
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
            if (!driver.init()) return false;

            driver.info("%s (%s) initialization.", driver.getName(), driver.getClass().getName());

            var json = driver.toJson();
            for (var key : json.keySet())
                driver.debug("%s => %s", key, json.get(key));

            if (driver instanceof TableBasedDriver tableBasedDriver) {
                try {
                    driver.debug("Trying to create %s table", DriverImplementation.ONLINE_UUID_CACHE_TABLE);
                    tableBasedDriver.createOnlineUuidCacheTable();
                } catch (Exception ex) {
                    driver.error("Failed to create %s table", ex, DriverImplementation.ONLINE_UUID_CACHE_TABLE);
                }

                try {
                    tableBasedDriver.createRandomizedPlayerDataTable();
                } catch (Exception ex) {
                    driver.error("Failed to create %s table", ex, DriverImplementation.RANDOM_PLAYER_CACHE_TABLE);
                }
            }
        } catch (Exception ex) {
            driver.error("Failed to initialize %s driver!", ex, driver.getName());
            return false;
        }
        this.driver = driver;
        return true;
    }

    public boolean storeOnlinePlayerCache(OnlinePlayerData player) {
        try {
            driver.debug("Trying to store online player (original UUID => %s)", player.getOriginalUniqueId());
            driver.storeOnlinePlayerCache(player);
            return true;
        } catch (Exception ex) {
            driver.error("Failed to store online player database for %s", ex, player.getOriginalUniqueId());
            return false;
        }
    }

    public boolean storeRandomPlayerCache(PlayerData player) {
        try {
            driver.debug("Trying to store random player (original UUID => %s)", player.getOriginalUniqueId());
            driver.storeRandomPlayerCache(player);
            return true;
        } catch (Exception ex) {
            driver.error("Failed to store random player database for %s", ex, player.getOriginalUniqueId());
            return false;
        }
    }

    public DatabaseObject<List<OnlinePlayerData>> getOnlinePlayersCache() {
        List<OnlinePlayerData> data = null;
        Exception exception = null;
        try {
            data = driver.getOnlinePlayersCache();
        } catch (Exception ex) {
            exception = ex;
        }
        return new DatabaseObject<>(data, exception, driver, null) {
            @Override
            public DatabaseObject<List<OnlinePlayerData>> printErrorIfAny() {
                driver.error("Failed to get all stored online players", exception);
                return this;
            }
        };
    }

    public DatabaseObject<List<PlayerData>> getRandomPlayersCache() {
        List<PlayerData> data = null;
        Exception exception = null;
        try {
            data = driver.getRandomPlayersCache();
        } catch (Exception ex) {
            exception = ex;
        }
        return new DatabaseObject<>(data, exception, driver, null) {
            @Override
            public DatabaseObject<List<PlayerData>> printErrorIfAny() {
                driver.error("Failed to get all stored random players", exception);
                return this;
            }
        };
    }

    public DatabaseObject<OnlinePlayerData> getOnlinePlayerCache(UUID uuid) {
        OnlinePlayerData data = null;
        Exception exception = null;
        try {
            data = driver.getOnlinePlayerCache(uuid);
        } catch (Exception ex) {
            exception = ex;
        }
        return new DatabaseObject<>(data, exception, driver, uuid) {
            @Override
            public DatabaseObject<OnlinePlayerData> printErrorIfAny() {
                driver.error("Failed to get online player data (%s) from the database", exception, key);
                return this;
            }
        };
    }

    public DatabaseObject<PlayerData> getRandomPlayerCache(UUID uuid) {
        PlayerData data = null;
        Exception exception = null;
        try {
            data = driver.getRandomPlayerCache(uuid);
        } catch (Exception ex) {
            exception = ex;
        }
        return new DatabaseObject<>(data, exception, driver, uuid) {
            @Override
            public DatabaseObject<PlayerData> printErrorIfAny() {
                driver.error("Failed to get random player data (%s) from the database", exception, key);
                return this;
            }
        };
    }

    public Configuration.DatabaseConfiguration getConfiguration() {
        return MultiPlatform.get().getConfiguration().getDatabase();
    }

    public boolean isDriverRunning() {
        try {
            return driver != null && driver.isRunning();
        } catch (Exception ex) {
            return false;
        }
    }
}
