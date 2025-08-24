package me.itstautvydas.uuidswapper.database.implementation;

import me.itstautvydas.uuidswapper.crossplatform.PluginWrapper;
import me.itstautvydas.uuidswapper.data.OnlinePlayerData;
import me.itstautvydas.uuidswapper.data.PlayerData;
import me.itstautvydas.uuidswapper.database.DriverImplementation;

import java.nio.file.Path;
import java.sql.*;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.Logger;

public class SQLiteImplementation extends DriverImplementation {
    private Connection connection = null;
    private Path databaseFilePath;

    private Connection newConnection() throws Exception {
        debug("Trying to open new connection");
        return DriverManager.getConnection("jdbc:sqlite:"
                + databaseFilePath
                + "?journal_mode=WAL&busy_timeout="
                + getConfiguration().getTimeout()
                + "&synchronous=NORMAL");
    }

    private Connection getConnection() throws Exception {
        if (shouldCreateNewConnection(connection))
            return newConnection();
        return connection;
    }

    @Override
    public String getClassToLoad() {
        return "org.sqlite.JDBC";
    }

    @Override
    public void onJarFileLoad(ClassLoader loader, Class<?> driverClass) throws Exception {
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
    }

    @Override
    public String getDownloadUrl() {
        return "https://repo1.maven.org/maven2/org/xerial/sqlite-jdbc/3.49.1.0/sqlite-jdbc-3.49.1.0.jar";
    }

    @Override
    public void init() throws Exception {
        databaseFilePath = PluginWrapper.getCurrent()
                .getDataDirectory()
                .resolve(getConfiguration().getFileName())
                .toAbsolutePath();
        getConnection(); // Trigger connection creation
        debug("Connection initialization");
        debug("Connection timeout => {}", getConfiguration().getTimeout());
        debug("Connection always kept => {}", getManager().shouldConnectionBeAlwaysKept());
        debug("Connection cached => {}", getManager().shouldConnectionBeCached());
    }

    @Override
    public void clearConnection() throws Exception {
        debug("Trying to close connection");
        if (connection == null) {
            debug("Connection was not cached");
            return;
        }
        connection.close();
        connection = null;
        debug("CONNECTION CLOSED");
    }

    @Override
    public boolean isConnectionClosed() throws Exception {
        return connection != null && connection.isClosed();
    }

    @Override
    public void createOnlineUuidCacheTable(boolean useCreatedAt, boolean useUpdatedAt) throws Exception {
        var sql = new StringBuilder("CREATE TABLE IF NOT EXISTS " + ONLINE_UUID_CACHE_TABLE + " ("
                + ONLINE_UUID_CACHE_ORIGINAL_UUID + " TEXT NOT NULL PRIMARY KEY, "
                + ONLINE_UUID_CACHE_ONLINE_UUID + " TEXT NOT NULL, "
                + ONLINE_UUID_CACHE_IP_ADDRESS + " TEXT NOT NULL");
        if (useCreatedAt)
            sql.append(", created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
        if (useUpdatedAt)
            sql.append(", updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
        sql.append(")");

        debug("Trying to create {} table", ONLINE_UUID_CACHE_TABLE);
        var connection = getConnection();
        try (var stmt = connection.createStatement()) {
            stmt.execute(sql.toString());
        }
    }

    @Override
    public void createRandomizedPlayerDataTable() throws Exception {

    }

    @Override
    public void storeOnlinePlayerCache(OnlinePlayerData player) throws Exception {
        debug("Trying to store online player (original UUID => {})", player.getOriginalUuid());
        var sql = "INSERT OR REPLACE INTO " + ONLINE_UUID_CACHE_TABLE + " (" +
                ONLINE_UUID_CACHE_ORIGINAL_UUID + ", " +
                ONLINE_UUID_CACHE_ONLINE_UUID + ", " +
                ONLINE_UUID_CACHE_IP_ADDRESS +
                ") VALUES (?, ?, ?)";

        try (var connection = getConnection(); var prepare = connection.prepareStatement(sql)) {
            prepare.setString(1, player.getOriginalUuid().toString());
            prepare.setString(2, player.getOnlineUuid().toString());
            prepare.setString(3, player.getAddress());
            prepare.executeUpdate();
        }
    }

    @Override
    public OnlinePlayerData getOnlinePlayerCache(String address) throws Exception {
        debug("Trying to get online player by ip address (address => {})", address);

        var sql = "SELECT * FROM " + ONLINE_UUID_CACHE_TABLE + " WHERE " +
                ONLINE_UUID_CACHE_IP_ADDRESS + " = ? LIMIT 1";

        try (var connection = getConnection(); var prepare = connection.prepareStatement(sql)) {
            prepare.setString(1, address);
            try (var resultSet = prepare.executeQuery()) {
                if (resultSet.next())
                    return toPlayerCache(resultSet);
            }
        }
        return null;
    }

    OnlinePlayerData toPlayerCache(ResultSet resultSet) throws Exception {
        UUID originalUuid = UUID.fromString(resultSet.getString(ONLINE_UUID_CACHE_ORIGINAL_UUID));
        UUID onlineUuid = UUID.fromString(resultSet.getString(ONLINE_UUID_CACHE_ONLINE_UUID));
        String ipAddress = resultSet.getString(ONLINE_UUID_CACHE_IP_ADDRESS);

        Long createdAt = null;
        try {
            createdAt = resultSet.getLong(CREATED_AT);
            if (resultSet.wasNull()) createdAt = null;
        } catch (SQLException ex) {
            // Ignore
        }

        Long updatedAt = null;
        try {
            updatedAt = resultSet.getLong(UPDATED_AT);
            if (resultSet.wasNull()) updatedAt = null;
        } catch (SQLException ex) {
            // Ignore
        }

//        return new OnlinePlayerData(originalUuid, onlineUuid, ipAddress, createdAt, updatedAt);
        return null;
    }

    @Override
    public OnlinePlayerData getOnlinePlayerCache(UUID uuid) {
        return null;
    }

    @Override
    public void storeRandomPlayerCache(PlayerData player) {

    }

    @Override
    public PlayerData getRandomPlayerCache(UUID uuid) {
        return null;
    }
}
