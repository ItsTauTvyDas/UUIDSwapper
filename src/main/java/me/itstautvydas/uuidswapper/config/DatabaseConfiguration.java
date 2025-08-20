package me.itstautvydas.uuidswapper.config;

import me.itstautvydas.uuidswapper.crossplatform.ConfigurationWrapper;

public class DatabaseConfiguration {
    private final ConfigurationWrapper config;
    private final ConfigurationWrapper mainConfig;

    public DatabaseConfiguration(ConfigurationWrapper config, ConfigurationWrapper mainConfig) {
        this.config = config;
        this.mainConfig = mainConfig;
    }

    public String getDriverName() {
        return config.getString("driver", "SQLite");
    }

    public String getFileName() {
        return config.getString("file", "players-data.db");
    }

    public long getOpenTime() {
        return Math.max(-1L, config.getLong("keep-open-for", 10L));
    }

    public long getTimerRepeat() {
        return Math.max(1L, config.getLong("timer-repeat-time", 10L));
    }

    public boolean isDebugEnabled() {
        return config.getBoolean("debug", false);
    }

    public boolean isEnabled() {
        return config.getBoolean("enabled", true);
    }

    public long getTimeout() {
        return Math.max(1000L, config.getLong("timeout", 5000L));
    }

    public String getDriverDownloadLink() {
        return config.getString("download-link");
    }

    public boolean shouldDriverBeDownloaded() {
        return config.getBoolean("download-driver", true);
    }

    public boolean shouldOnlineUuidsTableUseCreatedAt() {
        return mainConfig.getBoolean("online-uuids.caching.use-created-at", true);
    }

    public boolean shouldOnlineUuidsTableUseUpdatedAt() {
        return mainConfig.getBoolean("online-uuids.caching.use-updated-at", true);
    }
}
