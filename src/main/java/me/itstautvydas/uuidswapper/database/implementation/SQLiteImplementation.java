package me.itstautvydas.uuidswapper.database.implementation;

import me.itstautvydas.uuidswapper.database.DriverImplementation;
import me.itstautvydas.uuidswapper.database.PlayerCache;
import me.itstautvydas.uuidswapper.database.RandomCache;
import org.slf4j.event.Level;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.sql.*;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.Logger;

public class SQLiteImplementation extends DriverImplementation {
    private Connection connection = null;
    private Path databaseFilePath;

    private Connection newConnection() throws Exception {
        getLogger().debug(Level.INFO, "Trying to open new connection");
        return DriverManager.getConnection("jdbc:sqlite:"
                + databaseFilePath
                + "?journal_mode=WAL&busy_timeout="
                + getConfiguration().getDatabaseTimeout()
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
        databaseFilePath = getManager().getPlugin()
                .getDataDirectory()
                .resolve(getConfiguration().getDatabaseFileName())
                .toAbsolutePath();
        getConnection(); // Trigger connection creation
        getLogger().debug(Level.INFO, "Connection initialization");
        getLogger().debug(Level.INFO, "Connection timeout => {}", getConfiguration().getDatabaseTimeout());
        getLogger().debug(Level.INFO, "Connection always kept => {}", getManager().shouldConnectionBeAlwaysKept());
        getLogger().debug(Level.INFO, "Connection cached => {}", getManager().shouldConnectionBeCached());
    }

    @Override
    public void clearConnection() {
        getLogger().debug(Level.INFO, "Trying to close connection");
        if (connection == null) {
            getLogger().debug(Level.INFO, "Connection was not cached");
            return;
        }
        try {
            connection.close();
        } catch (Exception ex) {
            getLogger().log(Level.ERROR, ex.getMessage(), ex);
        }
        connection = null;
        getLogger().debug(Level.INFO, "CONNECTION CLOSED");
    }

    @Override
    public boolean isConnectionClosed() {
        try {
            return connection != null && connection.isClosed();
        } catch (Exception ex) {
            getLogger().log(Level.ERROR, ex.getMessage(), ex);
            return true;
        }
    }

    @Override
    public void createOnlineUuidCacheTable(boolean useCreatedAt, boolean useUpdatedAt) {
        var sql = new StringBuilder("CREATE TABLE IF NOT EXISTS " + ONLINE_UUID_CACHE_TABLE + " ("
                + ONLINE_UUID_CACHE_ORIGINAL_UUID + " TEXT NOT NULL PRIMARY KEY, "
                + ONLINE_UUID_CACHE_ONLINE_UUID + " TEXT NOT NULL, "
                + ONLINE_UUID_CACHE_IP_ADDRESS + " TEXT NOT NULL");
        if (useCreatedAt)
            sql.append(", created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
        if (useUpdatedAt)
            sql.append(", updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
        sql.append(")");

        try {
            getLogger().debug(Level.INFO, "Trying to create {} table", ONLINE_UUID_CACHE_TABLE);
            var connection = getConnection();
            try (var stmt = connection.createStatement()) {
                stmt.execute(sql.toString());
            }
        } catch (Exception ex) {
            getLogger().log(Level.ERROR, "Failed to create {} table", ex, ONLINE_UUID_CACHE_TABLE);
        }
    }

    @Override
    public void createRandomizedPlayerDataTable() {

    }

    @Override
    public void storeOnlinePlayerCache(PlayerCache player) {
        getLogger().debug(Level.INFO, "Trying to store online player (original UUID => {})", player.getOriginalUuid());
        var sql = "INSERT OR REPLACE INTO " + ONLINE_UUID_CACHE_TABLE + " (" +
                ONLINE_UUID_CACHE_ORIGINAL_UUID + ", " +
                ONLINE_UUID_CACHE_ONLINE_UUID + ", " +
                ONLINE_UUID_CACHE_IP_ADDRESS +
                ") VALUES (?, ?, ?)";

        try (var connection = getConnection(); var prepare = connection.prepareStatement(sql)) {
            prepare.setString(1, player.originalUuid().toString());
            prepare.setString(2, player.onlineUuid().toString());
            prepare.setString(3, player.address());
            prepare.executeUpdate();
        } catch (Exception ex) {
            getLogger().log(Level.ERROR, "Failed to store online player database for {}", ex, player.originalUuid());
        }
    }

    @Override
    public PlayerCache getOnlinePlayerCache(InetSocketAddress address) {
        var addressString = address.getAddress().getHostAddress();
        getLogger().debug(Level.INFO, "Trying to get online player by ip address (address => {})", addressString);

        var sql = "SELECT * FROM " + ONLINE_UUID_CACHE_TABLE + " WHERE " +
                ONLINE_UUID_CACHE_IP_ADDRESS + " = ? LIMIT 1";

        try (var connection = getConnection(); var prepare = connection.prepareStatement(sql)) {
            prepare.setString(1, addressString);
            try (var resultSet = prepare.executeQuery()) {
                if (resultSet.next())
                    return toPlayerCache(resultSet);
            }

        } catch (Exception ex) {
            getLogger().log(Level.ERROR, "Failed to get online player database by address {}", ex, addressString);
        }
        return null;
    }

    PlayerCache toPlayerCache(ResultSet resultSet) throws SQLException {
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

        return new PlayerCache(originalUuid, ipAddress, onlineUuid, createdAt, updatedAt);
    }

    @Override
    public PlayerCache getOnlinePlayerCache(UUID uuid) {
        return null;
    }

    @Override
    public void storeRandomPlayerCache(RandomCache player) {

    }

    @Override
    public RandomCache getRandomPlayerCache(UUID uuid) {
        return null;
    }
}
