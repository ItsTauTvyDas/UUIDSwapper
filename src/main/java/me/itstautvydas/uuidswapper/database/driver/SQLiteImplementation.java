package me.itstautvydas.uuidswapper.database.driver;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.ToString;
import me.itstautvydas.uuidswapper.Utils;
import me.itstautvydas.uuidswapper.crossplatform.PluginWrapper;
import me.itstautvydas.uuidswapper.data.OnlinePlayerData;
import me.itstautvydas.uuidswapper.data.PlayerData;
import me.itstautvydas.uuidswapper.data.ProfilePropertyWrapper;
import me.itstautvydas.uuidswapper.database.CacheableConnectionDriverImplementation;

import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@SuppressWarnings({"SqlNoDataSourceInspection", "unused", "FieldMayBeFinal"}) // SHUSH
@Getter
public class SQLiteImplementation extends CacheableConnectionDriverImplementation {
    private transient Connection connection = null;
    private transient Path databaseFilePath;

    private boolean downloadDriver;
    private String downloadUrl;
    @SerializedName("file")
    private String fileName;
    private Map<String, String> args = new HashMap<>();

    private Connection newConnection() throws Exception {
        return DriverManager.getConnection("jdbc:sqlite:"
                + databaseFilePath
                + "?busy_timeout="
                + getTimeout()
                + "&" + args.entrySet()
                    .stream()
                    .map((entry) -> entry.getKey() + "=" + entry.getValue())
                    .collect(Collectors.joining("&")));
    }

    private Connection getConnection() throws Exception {
        if (shouldCreateNewConnection(connection))
            return newConnection();
        return connection;
    }

    @Override
    public boolean init() throws Exception {
        supportsCaching = true;
        isDatabase = true;
        databaseFilePath = PluginWrapper.getCurrent()
                .getDataDirectory()
                .resolve(getFileName())
                .toAbsolutePath();
        if (!downloadDriver(this, downloadDriver, downloadUrl))
            return false;
        var classLoaded = loadClass(this, "org.sqlite.JDBC", (loader, driverClass) -> {
            try {
                var driver = (Driver) driverClass.getDeclaredConstructor().newInstance();
                DriverManager.registerDriver(new Driver() {
                    @Override
                    public Connection connect(String url, Properties info) throws SQLException {
                        return driver.connect(url, info);
                    }

                    @Override
                    public boolean acceptsURL(String url) throws SQLException {
                        return driver.acceptsURL(url);
                    }

                    @Override
                    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
                        return driver.getPropertyInfo(url, info);
                    }

                    @Override
                    public int getMajorVersion() {
                        return driver.getMajorVersion();
                    }

                    @Override
                    public int getMinorVersion() {
                        return driver.getMinorVersion();
                    }

                    @Override
                    public boolean jdbcCompliant() {
                        return driver.jdbcCompliant();
                    }

                    @Override
                    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
                        return driver.getParentLogger();
                    }
                });
                return null;
            } catch (Exception ex) {
                return ex;
            }
        });
        if (classLoaded)
            getConnection(); // Trigger connection creation
        return classLoaded;
    }

    @Override
    public boolean clearConnection() throws Exception {
        if (connection == null)
            return false;
        connection.close();
        connection = null;
        return true;
    }

    @Override
    public boolean isConnectionClosed() throws Exception {
        return connection != null && connection.isClosed();
    }

    @Override
    public void createOnlineUuidCacheTable() throws Exception {
        var connection = getConnection();
        try (var stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS " + ONLINE_UUID_CACHE_TABLE + " ("
                    + ONLINE_UUID_CACHE_ORIGINAL_UUID + " TEXT NOT NULL PRIMARY KEY, "
                    + ONLINE_UUID_CACHE_ONLINE_UUID + " TEXT NOT NULL, "
                    + CREATED_AT + " TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                    + UPDATED_AT + " TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        }
    }

    @Override
    public void createRandomizedPlayerDataTable() throws Exception {
        var connection = getConnection();
        try (var stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS " + RANDOM_PLAYER_CACHE_TABLE + " ("
                    + RANDOM_PLAYER_CACHE_ORIGINAL_UUID + " TEXT NOT NULL PRIMARY KEY, "
                    + RANDOM_PLAYER_CACHE_UUID + " TEXT NULL, "
                    + RANDOM_PLAYER_CACHE_USERNAME + " TEXT NULL, "
                    + CREATED_AT + " TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                    + UPDATED_AT + " TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        }
    }

    @Override
    public void storeOnlinePlayerCache(OnlinePlayerData player) throws Exception {
        var sql = "INSERT OR REPLACE INTO " + ONLINE_UUID_CACHE_TABLE + " (" +
                ONLINE_UUID_CACHE_ORIGINAL_UUID + ", " +
                ONLINE_UUID_CACHE_ONLINE_UUID +
                ONLINE_UUID_CACHE_PROPERTIES +
                ") VALUES (?, ?, ?)";
        try (var connection = getConnection(); var prepare = connection.prepareStatement(sql)) {
            prepare.setString(1, player.getOriginalUniqueId().toString());
            prepare.setString(2, player.getOnlineUniqueId().toString());
            prepare.setString(3, player.propertiesToJsonString());
            prepare.executeUpdate();
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public OnlinePlayerData getOnlinePlayerCache(UUID originalUniqueId) throws Exception {
        var sql = "SELECT * FROM " + ONLINE_UUID_CACHE_TABLE + " WHERE " + ONLINE_UUID_CACHE_ORIGINAL_UUID + " = ? LIMIT 1";

        try (var connection = getConnection(); var prepare = connection.prepareStatement(sql)) {
            prepare.setString(1, originalUniqueId.toString());
            try (var resultSet = prepare.executeQuery()) {
                if (resultSet.next())
                    return new OnlinePlayerData(
                            UUID.fromString(resultSet.getString(ONLINE_UUID_CACHE_ORIGINAL_UUID)),
                            UUID.fromString(resultSet.getString(ONLINE_UUID_CACHE_ONLINE_UUID)),
                            (List<ProfilePropertyWrapper>) Utils.DEFAULT_GSON.fromJson(
                                    resultSet.getString(ONLINE_UUID_CACHE_PROPERTIES),
                                    List.class
                            )
                    ) {
                        {
                            setUpdatedAt(resultSet.getLong(UPDATED_AT));
                            setCreatedAt(resultSet.getLong(CREATED_AT));
                        }
                    };
            }
        }
        return null;
    }

    @Override
    public void storeRandomPlayerCache(PlayerData player) throws Exception {
        var sql = "INSERT OR REPLACE INTO " + RANDOM_PLAYER_CACHE_TABLE + " (" +
                RANDOM_PLAYER_CACHE_ORIGINAL_UUID + ", " +
                RANDOM_PLAYER_CACHE_UUID + ", " +
                RANDOM_PLAYER_CACHE_USERNAME +
                ") VALUES (?, ?, ?)";
        try (var connection = getConnection(); var prepare = connection.prepareStatement(sql)) {
            prepare.setString(1, player.getOriginalUniqueId().toString());
            prepare.setString(2, player.getUniqueId().toString());
            prepare.setString(3, player.getUsername());
            prepare.executeUpdate();
        }
    }

    @Override
    public PlayerData getRandomPlayerCache(UUID originalUniqueId) throws Exception {
        var sql = "SELECT * FROM " + RANDOM_PLAYER_CACHE_TABLE + " WHERE " + RANDOM_PLAYER_CACHE_ORIGINAL_UUID + " = ? LIMIT 1";

        try (var connection = getConnection(); var prepare = connection.prepareStatement(sql)) {
            prepare.setString(1, originalUniqueId.toString());
            try (var resultSet = prepare.executeQuery()) {
                if (resultSet.next())
                    return new PlayerData(
                            UUID.fromString(resultSet.getString(RANDOM_PLAYER_CACHE_ORIGINAL_UUID)),
                            resultSet.getString(RANDOM_PLAYER_CACHE_USERNAME),
                            UUID.fromString(resultSet.getString(RANDOM_PLAYER_CACHE_UUID))
                    );
            }
        }
        return null;
    }
}
