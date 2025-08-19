package me.itstautvydas.uuidswapper.database;

import com.velocitypowered.api.scheduler.ScheduledTask;
import me.itstautvydas.uuidswapper.UUIDSwapper;
import me.itstautvydas.uuidswapper.config.DatabaseConfiguration;
import me.itstautvydas.uuidswapper.database.implementation.SQLiteImplementation;
import org.slf4j.event.Level;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public class CacheDatabaseManager {
    private final UUIDSwapper plugin;
    private DriverImplementation driver;
    private long timeCounter;
    private ScheduledTask timer;

    private final Map<String, Supplier<DriverImplementation>> registry = new HashMap<>();

    public CacheDatabaseManager(UUIDSwapper plugin) {
        this.plugin = plugin;
    }

    public void init() {
        registerDriver("SQLite", SQLiteImplementation::new);
        initDriverFromConfiguration();
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
        if (driver != null)
            driver.clearConnection();
    }

    public void resetTimer() {
        resetCounter();
        if (timer != null)
            timer.cancel();
        var keepOpen = getConfiguration().getDatabaseOpenTime();
        if (keepOpen <= 0)
            return;
        var repeat = getConfiguration().getDatabaseTimerRepeat();
        timer = plugin.getServer().getScheduler().buildTask(plugin, () -> {
            if (timeCounter == -1)
                return;
            timeCounter += repeat;
            if (timeCounter > keepOpen) {
                driver.clearConnection();
                lockCounter();
            }
        }).repeat(Duration.ofSeconds(repeat)).schedule();
    }

    public void resetCounter() {
        timeCounter = 0;
    }

    public void lockCounter() {
        timeCounter = -1;
    }

    public void initDriverFromConfiguration() {
        clean();
        var name = getConfiguration().getDatabaseDriverName();
        if (setDriverImplementation(name) == null)
            plugin.getLogger().error("[DatabaseManager]: Failed to load {} driver!", name);
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

            driver.createOnlineUuidCacheTable(
                    getConfiguration().shouldOnlineUuidsTableUseCreatedAt(),
                    getConfiguration().shouldOnlineUuidsTableUseUpdatedAt()
            );
            driver.createRandomizedPlayerDataTable();
        } catch (Exception ex) {
            driver.getLogger().log(Level.ERROR, "Failed to implement SQLite driver!", ex);
            return false;
        }
        this.driver = driver;
        resetTimer();
        return true;
    }

    public void cacheOnlinePlayer(UUID originalUuid, UUID onlineUuid, InetSocketAddress address) {

    }

    public boolean shouldConnectionBeCached() {
        return getConfiguration().getDatabaseOpenTime() > 0;
    }

    public boolean shouldConnectionBeAlwaysKept() {
        return getConfiguration().getDatabaseOpenTime() <= -1;
    }

    public UUIDSwapper getPlugin() {
        return plugin;
    }

    public DatabaseConfiguration getConfiguration() {
        return plugin.getConfiguration().getDatabaseConfiguration();
    }

    public DriverImplementation getDriver() {
        return driver;
    }

    public boolean isDriverRunning() {
        return driver != null && !driver.isConnectionClosed();
    }
}
