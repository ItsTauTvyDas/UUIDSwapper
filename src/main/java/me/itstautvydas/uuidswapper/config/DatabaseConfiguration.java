package me.itstautvydas.uuidswapper.config;

import com.moandjiezana.toml.Toml;

public class DatabaseConfiguration {
    private final Toml config;
    private final Toml mainConfig;

    public DatabaseConfiguration(Toml config, Toml mainConfig) {
        this.config = config;
        this.mainConfig = mainConfig;
    }

    public String getDatabaseDriverName() {
        return config.getString("driver", "SQLite");
    }

    public String getDatabaseFileName() {
        return config.getString("file", "players-data.db");
    }

    public long getDatabaseOpenTime() {
        return Math.max(-1L, config.getLong("keep-open-for", 10L));
    }

    public long getDatabaseTimerRepeat() {
        return Math.max(1L, config.getLong("timer-repeat-time", 10L));
    }

    public boolean isDatabaseDebugEnabled() {
        return config.getBoolean("debug", false);
    }

    public boolean isDatabaseEnabled() {
        return config.getBoolean("enabled", true);
    }

    public long getDatabaseTimeout() {
        return Math.max(1000L, config.getLong("timeout", 5000L));
    }

    public String getDatabaseDriverDownloadLink() {
        return config.getString("download-link");
    }

    public boolean shouldDatabaseBeDownloaded() {
        return config.getBoolean("download-driver", true);
    }

    public boolean shouldOnlineUuidsTableUseCreatedAt() {
        return mainConfig.getBoolean("online-uuids.caching.use-created-at", true);
    }

    public boolean shouldOnlineUuidsTableUseUpdatedAt() {
        return mainConfig.getBoolean("online-uuids.caching.use-updated-at", true);
    }
}
