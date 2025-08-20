package me.itstautvydas.uuidswapper.config;

import me.itstautvydas.uuidswapper.crossplatform.ConfigurationWrapper;
import me.itstautvydas.uuidswapper.crossplatform.CrossPlatformImplementation;
import me.itstautvydas.uuidswapper.Utils;

import java.util.*;

public class Configuration {
    private final Map<String, ServiceConfiguration> services = new HashMap<>();
    private ServiceConfiguration defaultServiceConfig;
    private ConfigurationWrapper config;

    public Configuration() {
        reload();
    }

    public void reload() {
        this.config = CrossPlatformImplementation.getCurrent().getConfig();
        var serviceDefaults = config.getSection("online-uuids.service-defaults");
        this.defaultServiceConfig = new ServiceConfiguration(serviceDefaults);

        services.clear();
//        var list = Utils.getTablesWithDefaults("online-uuids.services", config, serviceDefaults);
        var list = config.getSections("online-uuids.services", serviceDefaults);
        if (list != null) {
            for (var service : list) {
                if (service.contains("name") && service.contains("endpoint"))
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
        return CrossPlatformImplementation.getCurrent().isServerOnlineMode() && config.getBoolean("forced-offline-mode.enabled", false);
    }

    public boolean isForcedOfflineModeSetByDefault() {
        return config.getBoolean("forced-offline-mode.forced-by-default", true);
    }

    public List<String> getForcedOfflineModeExceptions() {
        return config.getList("forced-offline-mode.exceptions", new ArrayList<>());
    }

    public Map<String, Object> getSwappedUuids() {
        var table = config.getSection("swapped-uuids");
        if (table == null)
            return Utils.EMPTY_MAP;
        return table.toMap();
    }

    public Map<String, Object> getCustomPlayerNames() {
        var table = config.getSection("custom-player-names");
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

    public boolean shouldCacheOnlineUuids() {
        return config.getBoolean("online-uuids.caching.enabled", true);
    }

    public DatabaseConfiguration getDatabaseConfiguration() {
        return new DatabaseConfiguration(config.getSection("database"), config);
    }

    public long getServiceConnectionThrottle() {
        return config.getLong("online-uuids.service-connection-throttle", 2000L);
    }

    public String getServiceConnectionThrottleMessage() {
        return config.getString("online-uuids.service-connection-throttled-message", "::{multiplayer.disconnect.generic}");
    }

    public boolean isConnectionThrottleDialogEnabled() {
        return false;
//        return config.getBoolean("online-uuids.connection-throttle-dialog.enabled", true);
    }

    public boolean isConnectionThrottleDialogDynamic() {
        return config.getBoolean("online-uuids.connection-throttle-dialog.dynamic", false);
    }

    public String getConnectionThrottleDialogMessage() {
        return config.getString("online-uuids.connection-throttle-dialog.message", "Connecting...");
    }

    public String getConnectionThrottleDialogTitle() {
        return config.getString("online-uuids.connection-throttle-dialog.title", "Service connection throttled!");
    }

    public String getConnectionThrottleDialogButtonText() {
        return config.getString("online-uuids.connection-throttle-dialog.button", "::{menu.disconnect}");
    }
}
