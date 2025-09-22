package me.itstautvydas.uuidswapper.database.driver;

import com.google.common.reflect.TypeToken;
import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import me.itstautvydas.uuidswapper.Utils;
import me.itstautvydas.uuidswapper.database.Queueable;
import me.itstautvydas.uuidswapper.database.TableBasedDriver;
import me.itstautvydas.uuidswapper.multiplatform.MultiPlatform;
import me.itstautvydas.uuidswapper.data.OnlinePlayerData;
import me.itstautvydas.uuidswapper.data.PlayerData;
import me.itstautvydas.uuidswapper.data.ProfilePropertyWrapper;
import me.itstautvydas.uuidswapper.database.ScheduledSavingDriverImplementation;
import me.itstautvydas.uuidswapper.processor.ReadMeCallSuperClass;
import me.itstautvydas.uuidswapper.processor.ReadMeDescription;
import me.itstautvydas.uuidswapper.processor.ReadMeTitle;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@SuppressWarnings({"SqlNoDataSourceInspection", "FieldMayBeFinal"}) // SHUSH
@Getter
@ReadMeTitle(value = "(Database Driver) SQLite Implementation", order = -997)
@ReadMeDescription("SQLite (JDBC) file-based driver to use for caching player data. The driver is not bundled with the plugin but you have the ability to automatically download it and load it.")
@ReadMeCallSuperClass()
public class SQLiteImplementation extends ScheduledSavingDriverImplementation<Connection> implements TableBasedDriver {
    private transient Path databaseFilePath;
    private transient String connectionUrl;

    @ReadMeDescription("Should SQLite driver be downloaded")
    private boolean downloadDriver;
    @ReadMeDescription("Download URL to download driver from")
    private String downloadUrl;
    @SerializedName("file")
    @ReadMeDescription("File to store SQLite database")
    private String fileName;
    @ReadMeDescription("Database timeout in milliseconds")
    private long timeout;
    @ReadMeDescription("SQLite arguments")
    private Map<String, String> args = new HashMap<>();

    private transient BlockingQueue<Queueable> queue = new LinkedBlockingQueue<>();

    private Connection createConnection() throws SQLException {
        return DriverManager.getConnection(connectionUrl);
    }

    @Override
    public boolean init() {
        databaseFilePath = MultiPlatform.get()
                .getDataDirectory()
                .resolve(fileName)
                .toAbsolutePath();
        connectionUrl = "jdbc:sqlite:"
                + databaseFilePath
                + "?busy_timeout="
                + getTimeout()
                + "&" + args.entrySet()
                .stream()
                .map((entry) -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));
        if (!downloadDriver(downloadDriver, downloadUrl))
            return false;
        return loadClass("org.sqlite.JDBC", (loader, driverClass) -> {
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
    }

    @Override
    public void clean() {
        queue.clear();
        destroyTimer();
        databaseFilePath = null;
        connectionUrl = null;
    }

    @Override
    public boolean isRunning() {
        Objects.requireNonNull(databaseFilePath, "Database file path is null");
        Objects.requireNonNull(connectionUrl, "Database file path is null");
        Objects.requireNonNull(getTimer(), "Database file path is null");
        return true;
    }

    private PlayerData toRandomPlayerData(ResultSet resultSet) throws SQLException {
        return toOnlinePlayerData(resultSet).toPlayerData(resultSet.getString(KEY_USERNAME));
    }

    private OnlinePlayerData toOnlinePlayerData(ResultSet resultSet) throws SQLException {
        var properties = resultSet.getString(KEY_PROPERTIES);
        return new OnlinePlayerData(
                UUID.fromString(resultSet.getString(KEY_ORIGINAL_UUID)),
                UUID.fromString(resultSet.getString(KEY_OVERWRITE_UUID)),
                properties == null ? null : Utils.DEFAULT_GSON.fromJson(
                        properties,
                        new TypeToken<@NotNull List<ProfilePropertyWrapper>>(){}.getType()
                )
        );
    }

    @Override
    public void createOnlineUuidCacheTable() throws Exception {
        try (var connection = createConnection(); var stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS " + ONLINE_UUID_CACHE_TABLE + " ("
                    + KEY_ORIGINAL_UUID + " TEXT NOT NULL PRIMARY KEY, "
                    + KEY_OVERWRITE_UUID + " TEXT NOT NULL, "
                    + KEY_PROPERTIES + " TEXT NOT NULL, "
                    + CREATED_AT + " TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                    + UPDATED_AT + " TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        }
    }

    @Override
    public void createRandomizedPlayerDataTable() throws Exception {
        try (var connection = createConnection(); var stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS " + RANDOM_PLAYER_CACHE_TABLE + " ("
                    + KEY_ORIGINAL_UUID + " TEXT NOT NULL PRIMARY KEY, "
                    + KEY_OVERWRITE_UUID + " TEXT NULL, "
                    + KEY_USERNAME + " TEXT NULL, "
                    + CREATED_AT + " TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                    + UPDATED_AT + " TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        }
    }

    @Override
    public OnlinePlayerData getOnlinePlayerCache(UUID originalUniqueId) throws Exception {
        var sql = "SELECT * FROM " + ONLINE_UUID_CACHE_TABLE + " WHERE " + KEY_ORIGINAL_UUID + " = ? LIMIT 1";

        try (var connection = createConnection(); var prepare = connection.prepareStatement(sql)) {
            prepare.setString(1, originalUniqueId.toString());
            try (var resultSet = prepare.executeQuery()) {
                if (resultSet.next())
                    return toOnlinePlayerData(resultSet);
            }
        }
        return null;
    }

    @Override
    public List<OnlinePlayerData> getOnlinePlayersCache() throws Exception {
        var list = new ArrayList<OnlinePlayerData>();
        var sql = "SELECT * FROM " + ONLINE_UUID_CACHE_TABLE;

        try (var conn = createConnection(); var pre = conn.prepareStatement(sql); var resultSet = pre.executeQuery()) {
            while (resultSet.next())
                list.add(toOnlinePlayerData(resultSet));
        }
        return list.isEmpty() ? null : list;
    }

    @Override
    public List<PlayerData> getRandomPlayersCache() throws Exception {
        var list = new ArrayList<PlayerData>();
        var sql = "SELECT * FROM " + RANDOM_PLAYER_CACHE_TABLE;

        try (var conn = createConnection(); var pre = conn.prepareStatement(sql); var resultSet = pre.executeQuery()) {
            while (resultSet.next())
                list.add(toRandomPlayerData(resultSet));
        }
        return list.isEmpty() ? null : list;
    }

    @Override
    public PlayerData getRandomPlayerCache(UUID originalUniqueId) throws Exception {
        var sql = "SELECT * FROM " + RANDOM_PLAYER_CACHE_TABLE + " WHERE " + KEY_ORIGINAL_UUID + " = ? LIMIT 1";

        try (var connection = createConnection(); var prepare = connection.prepareStatement(sql)) {
            prepare.setString(1, originalUniqueId.toString());
            try (var resultSet = prepare.executeQuery()) {
                if (resultSet.next())
                    return toRandomPlayerData(resultSet);
            }
        }
        return null;
    }

    @Override
    public Connection onBatchStart() throws Exception {
        var connection = createConnection();
        connection.setAutoCommit(false);
        return connection;
    }

    @SuppressWarnings("ConstantValue")
    @Override
    public void onBatchCommit(List<Queueable> batch, Connection connection) throws Exception {
        connection.setAutoCommit(false);
        Map<String, PreparedStatement> statements = new HashMap<>();
        for (Queueable data : batch) {
            String sql;
            if (data instanceof PlayerData) {
                sql = "INSERT OR REPLACE INTO " + RANDOM_PLAYER_CACHE_TABLE + " ("
                        + KEY_ORIGINAL_UUID + ", "
                        + KEY_OVERWRITE_UUID + ", "
                        + KEY_USERNAME + ", "
                        + KEY_PROPERTIES
                        + ") VALUES (?, ?, ?, ?)";
            } else if (data instanceof OnlinePlayerData) {
                sql = "INSERT OR REPLACE INTO " + ONLINE_UUID_CACHE_TABLE + " ("
                        + KEY_ORIGINAL_UUID + ", "
                        + KEY_OVERWRITE_UUID + ", "
                        + KEY_PROPERTIES
                        + ") VALUES (?, ?, ?)";
            } else {
                continue;
            }

            PreparedStatement ps = statements.get(sql);
            if (ps == null) {
                ps = connection.prepareStatement(sql);
                statements.put(sql, ps);
            }

            if (data instanceof PlayerData player) {
                ps.setString(1, player.getOriginalUniqueId().toString());
                ps.setString(2, player.getUniqueId().toString());
                ps.setString(3, player.getUsername());
                ps.setString(4, Utils.DEFAULT_GSON.toJson(player.getProperties()));
            } else if (data instanceof OnlinePlayerData player) {
                ps.setString(1, player.getOriginalUniqueId().toString());
                ps.setString(2, player.getUniqueId().toString());
                ps.setString(3, Utils.DEFAULT_GSON.toJson(player.getProperties()));
            }

            ps.addBatch();
        }

        for (PreparedStatement ps : statements.values()) {
            ps.executeBatch();
            ps.close();
        }

        try {
            connection.commit();
        } catch (Exception ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    @Override
    public void onBatchEnd(Connection connection) throws Exception {
        if (connection == null) return;
        try {
            connection.commit();
        } catch (Exception e) {
            try {
                connection.rollback();
            } catch (Exception ignore) {}
            throw e;
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (Exception ignore) {}
            try {
                connection.close();
            } catch (Exception ignore) {}
        }
    }
}
