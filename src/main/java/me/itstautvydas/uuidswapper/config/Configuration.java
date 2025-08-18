package me.itstautvydas.uuidswapper.config;

import com.moandjiezana.toml.Toml;
import me.itstautvydas.uuidswapper.UUIDSwapper;
import me.itstautvydas.uuidswapper.Utils;

import java.util.*;

public class Configuration {
    private Toml config;
    private final Map<String, ServiceConfiguration> services = new HashMap<>();
    private ServiceConfiguration defaultServiceConfig;
    private final UUIDSwapper plugin;

    public Configuration(Toml config, UUIDSwapper plugin) {
        this.plugin = plugin;
        reload(config);
    }

    public void reload(Toml config) {
        var serviceDefaults = config.getTable("online-uuids.service-defaults");

        this.config = config;
        this.defaultServiceConfig = new ServiceConfiguration(serviceDefaults);

        services.clear();
        var list = Utils.getTablesWithDefaults("online-uuids.services", config, serviceDefaults);
        if (list != null) {
            for (var service : list) {
                if (service.containsPrimitive("name") && service.containsPrimitive("endpoint"))
                    services.put(service.getString("name"), new ServiceConfiguration(service));
            }
        }
    }

    public ServiceConfiguration getDefaultService() {
        return defaultServiceConfig;
    }

    public boolean areOnlineUUIDsEnabled() {
        return config.getBoolean("online-uuids.enabled", false);
    }

    private boolean isPlayerExceptional(String value, boolean isUsername) {
        if (!config.getBoolean("online-uuids.exceptions.enabled"))
            return true;
        var reversed = config.getBoolean("online-uuids.exceptions.reversed");
        var list = config.getList("online-uuids.exceptions.list");
        if (isUsername)
            value = "u:" + value;
        return reversed != list.contains(value);
    }

    private boolean isPlayerExceptional(String username) {
        return isPlayerExceptional(username, true);
    }

    private boolean isPlayerExceptional(UUID uuid) {
        return isPlayerExceptional(uuid.toString(), false);
    }

    public boolean stillSwapUuids() {
        return config.getBoolean("online-uuids.swap-uuids", true);
    }

    public boolean isForcedOfflineModeEnabled() {
        return plugin.getServer().getConfiguration().isOnlineMode() && config.getBoolean("forced-offline-mode.enabled", false);
    }

    public boolean isForcedOfflineModeSetByDefault() {
        return config.getBoolean("forced-offline-mode.forced-by-default", true);
    }

    public List<String> getForcedOfflineModeExceptions() {
        return config.getList("forced-offline-mode.exceptions", new ArrayList<>());
    }

    public Map<String, Object> getSwappedUuids() {
        var table = config.getTable("swapped-uuids");
        if (table == null)
            return Utils.EMPTY_MAP;
        return table.toMap();
    }

    public Map<String, Object> getCustomPlayerNames() {
        var table = config.getTable("custom-player-names");
        if (table == null)
            return Utils.EMPTY_MAP;
        return table.toMap();
    }

    public boolean isFilteringEnabled() {
        return config.getBoolean("online-uuids.exceptions.enabled", false);
    }

    public boolean isFilteringReversed() {
        return config.getBoolean("online-uuids.exceptions.reversed", false);
    }

    public String getServiceName() {
        return config.getString("online-uuids.use-service");
    }

    public List<String> getFallbackServices() {
        return config.getList("online-uuids.fallback-services", new ArrayList<>());
    }

    public ServiceConfiguration getService(String name) {
        return services.get(name);
    }

    public long getMaxTimeout() {
        return Math.max(500L, config.getLong("online-uuids.max-timeout", 6000L));
    }

    public long getMinTimeout() {
        return Math.max(100L, config.getLong("online-uuids.min-timeout", 1000L));
    }

    public long getFallbackServiceRememberMilliTime() {
        return Math.max(0L, config.getLong("online-uuids.fallback-service-remember-time", 21600L) * 1000);
    }

    public boolean getCheckDependingOnIPAddress() {
        return config.getBoolean("online-uuids.username-changes.check-depending-on-ip-address", false);
    }

    public boolean getCheckForOnlineUuid() {
        return config.getBoolean("online-uuids.check-for-online-uuid", true);
    }

    public boolean getSendSuccessfulMessagesToConsole() {
        return config.getBoolean("online-uuids.send-messages-to-console", true);
    }

    public boolean getSendErrorMessagesToConsole() {
        return config.getBoolean("online-uuids.send-error-messages-to-console", true);
    }

    public String getDatabaseDriverName() {
        return config.getString("database.driver", "SQLite");
    }

    public String getDatabaseFileName() {
        return config.getString("database.file", "players-data.db");
    }

    public long getDatabaseOpenTime() {
        return Math.max(-1L, config.getLong("database.keep-open-for", 10L));
    }

    public long getDatabaseTimerRepeat() {
        return Math.max(1L, config.getLong("database.timer-repeat-time", 10L));
    }

    public boolean isDatabaseDebugEnabled() {
        return config.getBoolean("database.debug", false);
    }

    public long getDatabaseTimeout() {
        return Math.max(1000L, config.getLong("database.timeout", 5000L));
    }
}
