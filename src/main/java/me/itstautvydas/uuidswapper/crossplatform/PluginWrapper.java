package me.itstautvydas.uuidswapper.crossplatform;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.Getter;
import me.itstautvydas.uuidswapper.Utils;
import me.itstautvydas.uuidswapper.config.Configuration;
import me.itstautvydas.uuidswapper.crossplatform.wrappers.BungeeCordPluginWrapper;
import me.itstautvydas.uuidswapper.crossplatform.wrappers.VelocityPluginWrapper;
import me.itstautvydas.uuidswapper.data.Message;
import me.itstautvydas.uuidswapper.data.OnlinePlayerData;
import me.itstautvydas.uuidswapper.data.ProfileProperty;
import me.itstautvydas.uuidswapper.database.CacheDatabaseManager;
import me.itstautvydas.uuidswapper.enums.PlatformType;
import me.itstautvydas.uuidswapper.helper.BiObjectHolder;
import me.itstautvydas.uuidswapper.json.RequiredPropertyAdapterFactory;
import me.itstautvydas.uuidswapper.json.StringListToStringAdapter;
import me.itstautvydas.uuidswapper.service.PlayerDataFetcher;

import java.io.FileReader;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@Getter
public abstract class PluginWrapper<P, T, L, S> {
    private static PluginWrapper<?, ?, ?, ?> CURRENT;
    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingStrategy(f -> {
                String fieldName = f.getName();
                StringBuilder sb = new StringBuilder();
                for (char c : fieldName.toCharArray()) {
                    if (Character.isUpperCase(c)) {
                        sb.append('-').append(Character.toLowerCase(c));
                    } else {
                        sb.append(c);
                    }
                }
                return sb.toString();
            })
            .registerTypeAdapterFactory(new RequiredPropertyAdapterFactory())
            .registerTypeAdapter(String.class, new StringListToStringAdapter())
            .create();

    @SuppressWarnings("unchecked")
    public static <P, T, L, S> void init(PlatformType type, P plugin, S serverObject, L loggerObject, Path dataDirectory) {
        if (CURRENT != null)
            throw new RuntimeException("Cross-platform implementation is already done!");
        PluginWrapper<P, T, L, S> implementation = switch (type) {
            case VELOCITY -> (PluginWrapper<P, T, L, S>) new VelocityPluginWrapper();
            case BUNGEE -> (PluginWrapper<P, T, L, S>) new BungeeCordPluginWrapper();
        };
        implementation.server = serverObject;
        implementation.logger = loggerObject;
        implementation.dataDirectory = dataDirectory;
        implementation.handle = plugin;
        implementation.onInit();
        implementation.logInfo("Initiated " + type.getName() + " implementation.");
        CURRENT = implementation;
    }

    public static PluginWrapper<?, ?, ?, ?> getCurrent() {
        return CURRENT;
    }

    @Getter
    protected PlatformType platformType;
    protected P handle;
    protected L logger;
    protected S server;
    @Getter
    protected Path dataDirectory;


    @Getter
    private Configuration configuration;
    private Path driversDirectory;
    @Getter
    private CacheDatabaseManager database;

    public void loadConfiguration() throws Exception {
        var configurationPath = dataDirectory.resolve("configuration.json");
        if (Files.notExists(configurationPath)) {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream("configuration.json")) {
                if (in != null) {
                    logInfo("Copying configuration file...");
                    Files.copy(in, configurationPath);
                }
            }
        }

        try (var reader = new FileReader(configurationPath.toFile())) {
            configuration = GSON.fromJson(reader, Configuration.class);
        }
    }

    private void onInit() {
        database = new CacheDatabaseManager();
    }

    public void onEnable() {
        try {
            reloadConfiguration();
            if (database != null)
                database.init();
        } catch (Exception ex) {
            logError("Could not initialize plugin!", ex);
        }
    }

    public void onDisable() {
        database.shutdown();
    }

    private void log(Map.Entry<String, String> entry) {
        logInfo(null, "# {} => {}", entry.getKey(), entry.getValue());
    }

    public void reloadConfiguration() throws Exception {
        Files.createDirectories(dataDirectory);
        driversDirectory = dataDirectory.resolve("drivers");
        Files.createDirectories(driversDirectory);

        loadConfiguration();

        logInfo("Configuration loaded.");
        logInfo("Using online UUIDs => {}", configuration.getOnlineAuthentication().isEnabled());

        logInfo("Loaded {} swapped UUIDs.", configuration.getSwappedUuids().size());
        for (var entry : configuration.getSwappedUuids().entrySet())
            log(entry);

        logInfo("Loaded {} custom player usernames.", configuration.getCustomPlayerNames().size());
        for (var entry : configuration.getCustomPlayerNames().entrySet())
            log(entry);
    }

    public CompletableFuture<BiObjectHolder<OnlinePlayerData, Message>> onPlayerLogin(
            String username, UUID uniqueId, String address,
            boolean cacheFetchedData,
            Runnable switchToOfflineMode, Consumer<Message> disconnectHandler) {
        if (!configuration.getOnlineAuthentication().isEnabled())
            return null;

        if (PluginWrapper.getCurrent().isServerOnlineMode()) {
            if (switchToOfflineMode == null) {
                disconnectHandler.accept(new Message("multiplayer.disconnect.generic", true));
                return null;
            }
            switchToOfflineMode.run();
        }

        if (configuration.getOnlineAuthentication().isCheckForOnlineUuid() && uniqueId != null) {
            var offlineUuid = generateOfflinePlayerUuid(username);
            if (!offlineUuid.equals(uniqueId)) {
                if (configuration.getOnlineAuthentication().isSendMessagesToConsole())
                    PluginWrapper.getCurrent().logInfo("Player {} has online UUID ({}), skipping fetching.", username, uniqueId);
                return null;
            }
        }

        if (PlayerDataFetcher.isThrottled(uniqueId)) {
            disconnectHandler.accept(new Message(configuration.getOnlineAuthentication().getServiceConnectionThrottledMessage(), false));
            return null;
        }

        return PlayerDataFetcher.getPlayerData(
                username,
                uniqueId,
                address,
                cacheFetchedData
        ).thenApply(fetchedData -> {
            if (fetchedData.containsSecond())
                disconnectHandler.accept(fetchedData.getSecond());
            return fetchedData;
        });
    }

    public void onGameProfileRequest(BiObjectHolder<String, UUID> profile, List<ProfileProperty> properties) {
        var username = profile.getFirst();
        var uniqueId = profile.getSecond();

        if (configuration.getOnlineAuthentication().isEnabled()) {
            var fetched = PlayerDataFetcher.pullPlayerData(uniqueId);
            if (fetched != null) {
                profile.setSecond(fetched.getOnlineUuid());
                if (fetched.getProperties() != null)
                    properties.addAll(fetched.getProperties());
                uniqueId = fetched.getOnlineUuid();
            }
        }

        var swappedUsernames = configuration.getCustomPlayerNames();
        var swappedUuids = configuration.getSwappedUuids();

        var newUsername = Utils.getSwappedValue(swappedUsernames, username, uniqueId);
        var newUniqueId = Utils.getSwappedValue(swappedUuids, username, uniqueId);

        if (newUsername != null || newUniqueId != null)
            logInfo(null, "Player's ({} {}) new profile is:", username, uniqueId);

        if (newUsername != null) {
            profile.setFirst(newUsername);
            logInfo(null, " # Username => {}", newUsername);
        }

        if (newUniqueId != null) {
            profile.setSecond(UUID.fromString(newUniqueId));
            logInfo(null, " # Unique ID => {}", newUniqueId);
        }
    }

    public UUID generateOfflinePlayerUuid(String username) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
    }

    public void logInfo(String message, Object ...args) {
        logInfo(null, message, args);
    }

    public void logWarning(String message, Throwable exception, Object ...args) {
        logWarning(null, message, exception, args);
    }

    public void logError(String message, Throwable exception, Object ...args) {
        logError(null, message, exception, args);
    }

    public abstract boolean isServerOnlineMode();
    public abstract void logInfo(String prefix, String message, Object ...args);
    public abstract void logWarning(String prefix, String message, Throwable exception, Object ...args);
    public abstract void logError(String prefix, String message, Throwable exception, Object ...args);
    public abstract PluginTaskWrapper<T> scheduleTask(Runnable run, Long repeatInSeconds, long delayInSeconds);
}
