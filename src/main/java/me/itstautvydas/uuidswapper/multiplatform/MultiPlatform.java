package me.itstautvydas.uuidswapper.multiplatform;

import com.google.gson.*;
import lombok.Getter;
import me.itstautvydas.BuildConstants;
import me.itstautvydas.uuidswapper.Utils;
import me.itstautvydas.uuidswapper.config.Configuration;
import me.itstautvydas.uuidswapper.config.ConfigurationErrorCollector;
import me.itstautvydas.uuidswapper.database.DriverImplementation;
import me.itstautvydas.uuidswapper.multiplatform.wrapper.BungeeCordPluginWrapper;
import me.itstautvydas.uuidswapper.multiplatform.wrapper.VelocityPluginWrapper;
import me.itstautvydas.uuidswapper.data.Message;
import me.itstautvydas.uuidswapper.data.OnlinePlayerData;
import me.itstautvydas.uuidswapper.data.PlayerData;
import me.itstautvydas.uuidswapper.data.ProfilePropertyWrapper;
import me.itstautvydas.uuidswapper.database.CacheDatabaseManager;
import me.itstautvydas.uuidswapper.enums.PlatformType;
import me.itstautvydas.uuidswapper.helper.BiObjectHolder;
import me.itstautvydas.uuidswapper.helper.ObjectHolder;
import me.itstautvydas.uuidswapper.helper.SimplifiedLogger;
import me.itstautvydas.uuidswapper.json.*;
import me.itstautvydas.uuidswapper.randomizer.PlayerRandomizer;
import me.itstautvydas.uuidswapper.service.PlayerDataFetcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

@Getter
public abstract class MultiPlatform<P, L, S, M> implements SimplifiedLogger {
    private static final String CONFIGURATION_PREFIX = "Configuration";
    private static MultiPlatform<?, ?, ?, ?> CURRENT;
    private static SimplifiedLogger CURRENT_LOGGER;

    public static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_DASHES)
            .registerTypeAdapterFactory(new UnknownFieldCollectorAdapterFactory())
            .registerTypeAdapterFactory(new RequiredPropertyAdapterFactory())
            .registerTypeAdapterFactory(new PostProcessingAdapterFactory())
            .registerTypeAdapterFactory(new StrictEnumTypeAdapterFactory())
            .registerTypeAdapterFactory(new DriverPolymorphicAdapterFactory())
            .registerTypeAdapter(String.class, new StringListToStringAdapter())
            .create();

    public static Object createWrapperInstance(PlatformType type)
            throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        var loaderClass = Class.forName("me.itstautvydas." + BuildConstants.NAME.toLowerCase()
                + ".multiplatform.wrapper."
                + type.getName() + "PluginWrapper");
        var constructor = loaderClass.getConstructor();
        return constructor.newInstance();
    }

    @SuppressWarnings("unchecked")
    public static <P, L, S, M> void init(PlatformType type, P plugin, S serverObject, L loggerObject, Path dataDirectory) {
        if (CURRENT != null)
            throw new RuntimeException("Cross-platform implementation is already done!");
        MultiPlatform<P, L, S, M> implementation;

        try {
            implementation = (MultiPlatform<P, L, S, M>) createWrapperInstance(type);
            CURRENT = implementation;
            CURRENT_LOGGER = implementation; // a workaround to get logger when CURRENT might be null
        } catch (Exception ex) {
            throw new RuntimeException("Failed to initialize " + type.getName() + "PluginWrapper!", ex);
        }
        implementation.platformType = type;
        implementation.server = serverObject;
        implementation.logger = loggerObject;
        implementation.dataDirectory = dataDirectory;
        implementation.handle = plugin;
        implementation.logInfo("CrossPlatform", "Initiated %s implementation.", type.getName());
        implementation.onInit();
    }

    public static MultiPlatform<?, ?, ?, ?> get() {
        return CURRENT;
    }

    public static boolean onPluginEnable() {
        if (CURRENT_LOGGER == null) // Prevent from someone calling this twice
            return false;
        if (CURRENT != null) {
            CURRENT_LOGGER = null;
            CURRENT.onEnable();
            return true;
        }
        if (ConfigurationErrorCollector.isSevere(GSON)) {
            CURRENT_LOGGER.logError(
                    CONFIGURATION_PREFIX,
                    ConfigurationErrorCollector.ERROR_MESSAGE + " The plugin will not work.",
                    null
            );
            CURRENT_LOGGER = null;
        }
        return false;
    }

    public static boolean onPluginDisable() {
        if (CURRENT != null) {
            CURRENT.onDisable();
            CURRENT = null; // Prevent from someone calling this twice
            return true;
        }
        return false;
    }

    @Getter
    protected PlatformType platformType;
    protected P handle;
    protected L logger;
    protected S server;
    @Getter
    protected Path dataDirectory;
    @Getter
    protected Path driversDirectory;

    @Getter
    private Configuration configuration;
    @Getter
    private JsonElement rawConfiguration;
    @Getter
    private CacheDatabaseManager database;
    @Getter
    private PlayerRandomizer playerRandomizer;

    public final Path getConfigurationPath() {
        return dataDirectory.resolve("configuration.json");
    }

    public void saveDefaultConfiguration() throws Exception {
        var configurationPath = getConfigurationPath();
        if (Files.notExists(configurationPath)) {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream("configuration.json")) {
                if (in == null)
                    throw new FileNotFoundException("configuration.json not found on classpath");
                logInfo(CONFIGURATION_PREFIX, "Copying default configuration file...");
                Files.copy(in, configurationPath);
            }
        }
    }

    public static <CC> BiObjectHolder<CC, JsonElement> loadConfiguration(Path configurationPath, Class<CC> configurationClass) throws Exception {
        try (var reader = new FileReader(configurationPath.toFile())) {
            JsonElement root = JsonParser.parseReader(reader);
            CC configuration = null;
            if (configurationClass != null)
                configuration = GSON.fromJson(root, configurationClass);
            return new BiObjectHolder<>(configuration, root);
        }
    }

    private void onInit() {
        try {
            reloadConfiguration();
        } catch (Exception ex) {
            CURRENT = null;
            logError(CONFIGURATION_PREFIX, "Could not initialize configuration!", null);
            if (ex instanceof RuntimeException)
                throw (RuntimeException)ex; // Very silly workaround lol, I just don't like seeing "Caused by..." okay?
            throw new RuntimeException(ex);
        }
        database = new CacheDatabaseManager();
        if (database.getConfiguration().isEnabled())
            database.loadDriverFromConfiguration();
    }

    public void onEnable() {
        registerCommand((BuildConstants.NAME + "-" + platformType.getName()).toLowerCase());
    }

    public void onDisable() {
        if (database != null)
            database.clear();
    }

    private void logSwappedUuid(Map.Entry<String, String> entry) {
        var key = entry.getKey();
        if (Utils.isValidUuid(key))
            key = "(UUID) " + key;
        else
            key = "(Username) " + key;
        logInfo(CONFIGURATION_PREFIX, "# %s => %s", key, entry.getValue());
    }

    public long reloadConfiguration() throws Exception {
        logInfo(CONFIGURATION_PREFIX, "Loading configuration...");
        driversDirectory = dataDirectory.resolve("drivers");
        Files.createDirectories(driversDirectory); // also creates the main one

        var start = System.nanoTime();
        saveDefaultConfiguration();

        var configurations = loadConfiguration(getConfigurationPath(), Configuration.class);
        configuration = configurations.getFirst();
        rawConfiguration = configurations.getSecond();
        ConfigurationErrorCollector.print(GSON, (message) -> logWarning(CONFIGURATION_PREFIX, message, null));
        ConfigurationErrorCollector.throwIfAnySevereErrors(GSON);

        for (var service : configuration.getOnlineAuthentication().getServices())
            service.setDefaults(configuration.getOnlineAuthentication().getServiceDefaults());

        if (configuration.getPlayerRandomizer().isEnabled() &&
                (configuration.getPlayerRandomizer().getUniqueIdSettings().isRandomize() ||
                        configuration.getPlayerRandomizer().getUsernameSettings().isRandomize())) {
            playerRandomizer = new PlayerRandomizer(configuration.getPlayerRandomizer());
        } else {
            playerRandomizer = null;
        }

        logInfo(CONFIGURATION_PREFIX, "Using online UUIDs => %s", configuration.getOnlineAuthentication().isEnabled());

        if (configuration.getSwappedUniqueIds().isEnabled()) {
            logInfo(CONFIGURATION_PREFIX, "Loaded %s swapped UUIDs.", configuration.getSwappedUniqueIds().getSwap().size());
            for (var entry : configuration.getSwappedUniqueIds().getSwap().entrySet())
                logSwappedUuid(entry);
        }

        if (configuration.getSwappedPlayerNames().isEnabled()) {
            logInfo(CONFIGURATION_PREFIX, "Loaded %s custom player usernames.", configuration.getSwappedPlayerNames().getSwap().size());
            for (var entry : configuration.getSwappedPlayerNames().getSwap().entrySet())
                logSwappedUuid(entry);
        }

        var took = System.nanoTime() - start;
        logInfo(CONFIGURATION_PREFIX, "Configuration loaded in %sms", TimeUnit.NANOSECONDS.toMillis(took));
        return took;
    }

    public void handlePlayerDisconnect(String username, UUID uniqueId) {
        if (playerRandomizer != null)
            playerRandomizer.removeGeneratedPlayer(username, uniqueId);
        PlayerDataFetcher.clearPlayerDataCache();
    }

    /**
     * Try setting player's connection to offline mode on online/secure server
     * @param switchToOfflineMode Runnable - set Runnable object for setting online mode to false
     */
    public void forceOfflineModeIfNeeded(Runnable switchToOfflineMode) {
        if (switchToOfflineMode == null)
            return;
        if (!configuration.getOnlineAuthentication().isEnabled())
            return;
        if (configuration.getOnlineAuthentication().isAllowOfflinePlayers() && MultiPlatform.get().isServerOnlineMode()) {
            switchToOfflineMode.run();
        }
    }

    /**
     * @param username Original username
     * @param uniqueId Original unique ID
     * @param properties Properties to override
     * @param cacheFetchedData Should the fetched data be cached?
     * @param disconnectHandler Disconnect handler
     * @return Completable future for async
     */
    @NotNull
    public CompletableFuture<BiObjectHolder<OnlinePlayerData, Message>> handlePlayerLogin(
            @NotNull String username,
            @Nullable UUID uniqueId,
            @Nullable List<ProfilePropertyWrapper> properties,
            boolean cacheFetchedData,
            @NotNull Consumer<Message> disconnectHandler) {
        // I don't want to deal with nulls, passing completed future with null inside
        var dummy = CompletableFuture.completedFuture((BiObjectHolder<OnlinePlayerData, Message>)null);

        if (playerRandomizer == null) {
            if (!configuration.getOnlineAuthentication().isEnabled())
                return dummy;
        } else {
            playerRandomizer.removeGeneratedPlayer(uniqueId);
            if (configuration.getPlayerRandomizer().getUsernameSettings().isRandomize()) {
                try {
                    playerRandomizer.nextUsername(uniqueId);
                } catch (IllegalStateException ex) {
                    disconnectHandler.accept(new Message(
                            configuration.getPlayerRandomizer().getUsernameSettings().getOutOfUsernamesDisconnectMessage(),
                            false
                    ));
                    return dummy;
                } catch (IllegalArgumentException ex) {
                    disconnectHandler.accept(new Message(Utils.GENERIC_DISCONNECT_MESSAGE_ID, true));
                    return dummy;
                }
            }
            if (configuration.getPlayerRandomizer().getUniqueIdSettings().isRandomize())
                playerRandomizer.nextUniqueId(uniqueId);
        }

        if (configuration.getOnlineAuthentication().isCheckForOnlineUniqueId() && uniqueId != null) {
            var offlineUuid = Utils.generateOfflineUniqueId(username);
            if (!offlineUuid.equals(uniqueId)) {
                boolean fetchForProperties = false;
                if (configuration.getOnlineAuthentication().isSendMessagesToConsole()) {
                    MultiPlatform.get().logInfo("PlayerDataFetcher", "Player %s connected with premium account, skipping fetching.", username, uniqueId);
                    if (playerRandomizer != null && configuration.getPlayerRandomizer().isUseProperties()) {
                        if (properties != null)
                            PlayerDataFetcher.setPlayerProperties(uniqueId, properties);
                        else
                            fetchForProperties = true;
                    }
                }
                if (!fetchForProperties)
                    return dummy;
            }
        }

        if (playerRandomizer != null && !configuration.getPlayerRandomizer().isFetchPropertiesFromServices()) {
            if (configuration.getPlayerRandomizer().isUseProperties() && properties != null)
                PlayerDataFetcher.setPlayerProperties(uniqueId, properties);
            return dummy;
        }

        var timeLeft = new ObjectHolder<Long>(null);
        if (PlayerDataFetcher.isThrottled(uniqueId, timeLeft)) {
            disconnectHandler.accept(new Message(configuration.getOnlineAuthentication().getServiceConnectionThrottledMessage(), false)
                    .replacePlaceholders(Map.of("time-left", timeLeft.get())));
            return dummy;
        }

        return new PlayerDataFetcher(username, uniqueId, this)
                .setCacheFetchedData(cacheFetchedData && playerRandomizer == null)
                .setCacheDatabase(playerRandomizer == null)
                .setCheckDatabaseCache(true)
                .updateMessages()
                .execute()
                .handle((fetchedData, ex) -> {
                    if (ex != null) {
                        fetchedData = new BiObjectHolder<>(
                                null,
                                new Message(configuration.getOnlineAuthentication()
                                        .getServiceDefaults()
                                        .getUnknownErrorDisconnectMessage(),
                                        false));
                        // Won't check for configuration if error messages are enabled, this is NOT a laughing matter.
                        // https://static.itstautvydas.me/funny/this-is-no-laughing-matter.jpg
                        logError("PlayerDataFetcher", "Failed to internally handle services requests!", ex);
                    } else if (fetchedData.containsFirst()) {
                        if (playerRandomizer != null && configuration.getPlayerRandomizer().isFetchPropertiesFromServices() &&
                                fetchedData.getFirst().getProperties() != null)
                            PlayerDataFetcher.setPlayerProperties(uniqueId, fetchedData.getFirst().getProperties());
                    }
                    if (fetchedData.containsSecond())
                        disconnectHandler.accept(fetchedData.getSecond());
                    return fetchedData;
                });
    }

    public boolean handleGameProfileRequest(
            @NotNull BiObjectHolder<String, UUID> profile,
            @NotNull List<ProfilePropertyWrapper> properties
    ) {
        var pretendData = PlayerDataFetcher.pullPretender(profile.getSecond());
        if (pretendData != null) {
            profile.setFirst(pretendData.getUsername());
            profile.setSecond(pretendData.getUniqueId());
            properties.addAll(pretendData.getProperties());
            return true;
        }

        if (configuration.getPlayerRandomizer().isEnabled() && playerRandomizer != null) {
            var prefix = "PlayerRandomizer";

            var randomUsername = playerRandomizer.getGeneratedUsername(profile.getSecond());
            var randomUniqueId = playerRandomizer.getGeneratedUniqueId(profile.getSecond());

            if (randomUsername != null || randomUniqueId != null) {
                var fetched = PlayerDataFetcher.pullPlayerData(profile.getSecond());
                logInfo(prefix, "Player's (%s/%s) new random profile is:", profile.getFirst(), profile.getSecond());
                if (randomUsername != null) {
                    logInfo(prefix, "  # Random username => %s", randomUsername);
                    profile.setFirst(randomUsername);
                }

                if (randomUniqueId != null) {
                    logInfo(prefix, "  # Random unique ID => %s", randomUniqueId);
                    profile.setSecond(randomUniqueId);
                }

                if (fetched != null) {
                    if (fetched.getProperties() != null) {
                        properties.addAll(fetched.getProperties());
                        logInfo(prefix, "  # Successfully applied %s profile properties!", fetched.isOnlineUniqueId() ? "online" : "current");
                    }
                }
            }
            return true;
        }

        boolean changed = false;
        if (configuration.getOnlineAuthentication().isEnabled()) {
            var fetched = PlayerDataFetcher.pullPlayerData(profile.getSecond());
            if (fetched != null) {
                profile.setSecond(fetched.getUniqueId());
                if (fetched.getProperties() != null)
                    properties.addAll(fetched.getProperties());
                changed = true;
            }
        }

        String swappedUsername = null;
        String swappedUniqueId = null;

        if (configuration.getSwappedUniqueIds().isEnabled())
            swappedUniqueId = Utils.getSwappedValue(
                    configuration.getSwappedUniqueIds().getSwap(),
                    profile.getFirst(),
                    profile.getSecond()
            );

        if (configuration.getSwappedPlayerNames().isEnabled())
            swappedUsername = Utils.getSwappedValue(
                    configuration.getSwappedPlayerNames().getSwap(),
                    profile.getFirst(),
                    profile.getSecond()
            );

        if (swappedUsername != null || swappedUniqueId != null) {
            var prefix = "PlayerSwapper";
            logInfo(prefix, "Player's (%s/%s) new profile is:", profile.getFirst(), profile.getSecond());
            changed = true;
            if (swappedUsername != null) {
                profile.setFirst(swappedUsername);
                logInfo(prefix, "  # Username => %s", swappedUsername);
            }
            if (swappedUniqueId != null) {
                profile.setSecond(Utils.toUniqueId(swappedUniqueId));
                logInfo(prefix, "  # Unique ID => %s", swappedUniqueId);
            }
        }

        return changed;
    }

    private Map<String, Object> getCommandBasePlaceholders() {
        var map = new HashMap<String, Object>();
        map.put("command", "uuidswapper-" + platformType.getName().toLowerCase());
        map.put("prefix", configuration.getCommandMessages().getPrefix());
        return map;
    }

    public static <M> SimplifiedLogger createLogger(M messageAcceptor) {
        return new SimplifiedLogger() {
            @SuppressWarnings("unchecked")
            void sendMessage(String message, Object... args) {
                ((MultiPlatform<?, ?, ?, M>) MultiPlatform.get()).sendMessage(
                        messageAcceptor,
                        x -> message.formatted(args),
                        null
                );
            }

            @Override
            public void logInfo(String prefix, String message, Object... args) {
                sendMessage(Utils.toLoggerMessage(prefix, message, args));
            }

            @Override
            public void logWarning(String prefix, String message, Throwable exception, Object... args) {
                sendMessage(Utils.toLoggerMessage(prefix, message, args));
                Utils.printException(exception, x -> MultiPlatform.get().logWarning(prefix, x, null));
            }

            @Override
            public void logError(String prefix, String message, Throwable exception, Object... args) {
                sendMessage(Utils.toLoggerMessage(prefix, message, args));
                Utils.printException(exception, x -> MultiPlatform.get().logError(prefix, x, null));
            }
        };
    }

    public void onNoArgsCommand(M messageAcceptor) {
        sendMessage(messageAcceptor, Configuration.CommandMessagesConfiguration::getNoArguments, getCommandBasePlaceholders());
    }

    public void onReloadCommand(M messageAcceptor) {
        var placeholders = getCommandBasePlaceholders();
        var old = configuration;
        var old0 = rawConfiguration;
        try {
            PlayerDataFetcher.forgetLastUsedService();
            var took = reloadConfiguration();
            placeholders.put("took", took);
            database.clear();
            if (!database.loadDriverFromConfiguration()) {
                placeholders.put("driver", database.getDriver());
                sendMessage(messageAcceptor, Configuration.CommandMessagesConfiguration::getReloadDatabaseDriverFailed, placeholders);
            }
            sendMessage(messageAcceptor, Configuration.CommandMessagesConfiguration::getReloadSuccess, placeholders);
        } catch (Exception ex) {
            configuration = old;
            rawConfiguration = old0;
            Utils.addExceptionPlaceholders(ex, placeholders);
            Utils.printException(ex, x -> logWarning("ReloadCommand", "Failed to reload the configuration!", ex));
            sendMessage(messageAcceptor, Configuration.CommandMessagesConfiguration::getReloadFailed, placeholders);
        }
    }

    protected enum DebugCommandCacheType {
        PLAYER_DATA_FETCHER_FETCHED,
        PLAYER_DATA_FETCHER_PRETEND,
        PLAYER_DATA_FETCHER_THROTTLED,
        PLAYER_DATA_FETCHER_LAST_SERVICE,
        DATABASE_FETCHED_PLAYERS,
        DATABASE_RANDOM_PLAYERS,
        PLAYER_DATA_MEMORY_CACHE
    }

    private String playerDataJson(Jsonable object, int counter, Object key) {
        var json = object.toJson();
        json.remove("original-unique-id");
        String str = "";
        List<ProfilePropertyWrapper> properties;
        if (object instanceof OnlinePlayerData data)
            properties = data.getProperties();
        else if (object instanceof PlayerData data)
            properties = data.getProperties();
        else
            return "<unknown player data object>";
        if (properties != null) // Properties are too long and useless to even show it
            json.addProperty("properties", "<valid properties>");
        else
            json.addProperty("properties", "<not set>");
        str += "\n%s# %s: &e%s&r".formatted(counter, key, json);
        return str;
    }

    @SuppressWarnings("DuplicatedCode")
    protected String getDebugMessage(DebugCommandCacheType type) {
        DriverImplementation driver = null;
        if (getDatabase().isDriverRunning())
            driver = getDatabase().getDriver();

        return switch (type) {
            case PLAYER_DATA_FETCHER_FETCHED, PLAYER_DATA_FETCHER_PRETEND -> {
                var str = type == DebugCommandCacheType.PLAYER_DATA_FETCHER_FETCHED ?
                        "[PlayerDataFetcher.class] UUID -> OnlinePlayerData.class"
                        : "[PlayerDataFetcher.class] UUID -> PlayerData.class";
                var map = type == DebugCommandCacheType.PLAYER_DATA_FETCHER_FETCHED ?
                        PlayerDataFetcher.getFetchedPlayerDataMap()
                        : PlayerDataFetcher.getPretendMap();
                var it = map.entrySet().iterator();
                if (!it.hasNext())
                    yield str + "\n<no cached data>";
                for (int i = 1; it.hasNext(); i++) {
                    var entry = it.next();
                    str += playerDataJson(entry.getValue(), i, entry.getKey());
                }
                yield str;
            }
            case PLAYER_DATA_FETCHER_THROTTLED -> {
                var str = "[PlayerDataFetcher.class] UUID -> time in milliseconds (long)";
                var it = PlayerDataFetcher.getThrottledConnections().entrySet().iterator();
                if (!it.hasNext())
                    yield str + "\n<no cached data>";
                for (int i = 1; it.hasNext(); i++) {
                    var entry = it.next();
                    str += "\n%s# %s: &e%s&r".formatted(i, entry.getKey(), entry.getValue());
                }
                yield str;
            }
            case PLAYER_DATA_FETCHER_LAST_SERVICE -> """
                    [PlayerDataFetcher.class]
                    Last used service -> &e%s&r
                    Last used at -> %s""".formatted(
                            PlayerDataFetcher.getLastUsedService(),
                            PlayerDataFetcher.getLastUsedServiceAt()
                    );
            case DATABASE_FETCHED_PLAYERS -> {
                if (driver == null)
                    yield "Database (driver) is not running!";
                var databaseObject = getDatabase().getOnlinePlayersCache();
                if (databaseObject.hasError())
                    yield "\n<error: " + databaseObject.exception.getMessage() + ">";
                var str = "[" + driver.getClass().getName() + "] UUID -> OnlinePlayerData.class\n";
                var it = databaseObject.object.iterator();
                if (!it.hasNext())
                    yield str + "\n<no cached data>";
                for (int i = 1; it.hasNext(); i++) {
                    var player = it.next();
                    str += playerDataJson(player, i, player.getOriginalUniqueId());
                }
                yield str;
            }
            case DATABASE_RANDOM_PLAYERS -> {
                if (driver == null)
                    yield "Database (driver) is not running!";
                var databaseObject = getDatabase().getRandomPlayersCache();
                if (databaseObject.hasError())
                    yield "\n<error: " + databaseObject.exception.getMessage() + ">";
                var str = "[" + driver.getClass().getName() + "] UUID -> PlayerData.class\n";
                var it = databaseObject.object.iterator();
                if (!it.hasNext())
                    yield str + "\n<no cached data>";
                for (int i = 1; it.hasNext(); i++) {
                    var player = it.next();
                    str += playerDataJson(player, i, player.getOriginalUniqueId());
                }
                yield str;
            }
            case PLAYER_DATA_MEMORY_CACHE -> {
                var str = "[PlayerDataFetcher.class] UUID -> OnlinePlayerData.class\n";
                var it = PlayerDataFetcher.getCachedPlayerDataMap().entrySet().iterator();
                if (!it.hasNext())
                    yield str + "\n<no cached data>";
                for (int i = 1; it.hasNext(); i++) {
                    var entry = it.next();
                    str += playerDataJson(entry.getValue(), i, entry.getKey());
                }
                yield str;
            }
        };
    }

    public void onPretendCommand(M messageAcceptor, UUID originalUniqueId, UUID uniqueId, String username, boolean tryFetchProperties) {
        scheduleTaskAsync(() -> {
            PlayerData data;
            boolean fetch = tryFetchProperties;
            if (fetch && MultiPlatform.get()
                    .getConfiguration()
                    .getOnlineAuthentication()
                    .getServices()
                    .stream()
                    .noneMatch(x -> x.getJsonPathToProperties() != null))
                fetch = false;
            data = PlayerDataFetcher.pretend(originalUniqueId, username, uniqueId, fetch, createLogger(messageAcceptor));
            var placeholders = getCommandBasePlaceholders();
            if (data != null) {
                placeholders.put("new_username", data.getUsername());
                placeholders.put("new_uuid", data.getUniqueId());
                sendMessage(messageAcceptor, Configuration.CommandMessagesConfiguration::getPlayerPretendSuccess, placeholders);
            } else {
                sendMessage(messageAcceptor, Configuration.CommandMessagesConfiguration::getPlayerPretendFailed, placeholders);
            }
        });
    }

    public abstract void sendMessage(M sender, Function<Configuration.CommandMessagesConfiguration, String> message, Map<String, Object> placeholders);
    public abstract void registerCommand(String commandName);
    public abstract boolean isServerOnlineMode();
    public abstract PluginTaskWrapper scheduleTask(Runnable run, @Nullable Long repeatInSeconds, long delayInSeconds);
    public abstract PluginTaskWrapper scheduleTaskAsync(Runnable run);
}
