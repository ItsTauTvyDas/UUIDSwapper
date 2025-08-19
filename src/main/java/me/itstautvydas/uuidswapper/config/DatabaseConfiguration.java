package me.itstautvydas.uuidswapper.config;

import com.moandjiezana.toml.Toml;

public class DatabaseConfiguration {
    private final Toml config;

    public DatabaseConfiguration(Toml config) {
        this.config = config;
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

    public String getDatabaseDriverDownloadLink() {
        return config.getString("database.download-link");
    }

    public boolean shouldDatabaseBeDownloaded() {
        return config.getBoolean("database.download-driver", true);
    }

    public boolean shouldOnlineUuidsTableUseCreatedAt() {
        return config.getBoolean("online-uuids.caching.use-created-at", true);
    }

    public boolean shouldOnlineUuidsTableUseUpdatedAt() {
        return config.getBoolean("online-uuids.caching.use-updated-at", true);
    }
}
