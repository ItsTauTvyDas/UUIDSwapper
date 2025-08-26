package me.itstautvydas.uuidswapper.crossplatform;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.internal.LinkedTreeMap;
import lombok.Getter;
import me.itstautvydas.BuildConstants;
import me.itstautvydas.uuidswapper.Utils;
import me.itstautvydas.uuidswapper.config.Configuration;
import me.itstautvydas.uuidswapper.crossplatform.wrapper.BungeeCordPluginWrapper;
import me.itstautvydas.uuidswapper.crossplatform.wrapper.VelocityPluginWrapper;
import me.itstautvydas.uuidswapper.data.Message;
import me.itstautvydas.uuidswapper.data.OnlinePlayerData;
import me.itstautvydas.uuidswapper.data.PlayerData;
import me.itstautvydas.uuidswapper.data.ProfileProperty;
import me.itstautvydas.uuidswapper.database.CacheDatabaseManager;
import me.itstautvydas.uuidswapper.enums.PlatformType;
import me.itstautvydas.uuidswapper.helper.BiObjectHolder;
import me.itstautvydas.uuidswapper.helper.ObjectHolder;
import me.itstautvydas.uuidswapper.helper.SimplifiedLogger;
import me.itstautvydas.uuidswapper.json.InvalidFieldsCollectorAdapterFactory;
import me.itstautvydas.uuidswapper.json.RequiredPropertyAdapterFactory;
import me.itstautvydas.uuidswapper.json.SortedJsonSerializer;
import me.itstautvydas.uuidswapper.json.StringListToStringAdapter;
import me.itstautvydas.uuidswapper.randomizer.PlayerRandomizer;
import me.itstautvydas.uuidswapper.service.PlayerDataFetcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

@Getter
public abstract class PluginWrapper<P, L, S, M> implements SimplifiedLogger {
    private static final String CONFIGURATION_PREFIX = "Configuration";
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
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .registerTypeAdapterFactory(new InvalidFieldsCollectorAdapterFactory())
            .registerTypeAdapterFactory(new RequiredPropertyAdapterFactory())
            .registerTypeAdapter(LinkedTreeMap.class, new SortedJsonSerializer())
            .registerTypeAdapter(String.class, new StringListToStringAdapter())
            .create();

    public static Object createPaperWrapperInstance(PlatformType type) throws Exception {
        var paperLoaderClass = Class.forName("me.itstautvydas." + BuildConstants.NAME.toLowerCase()
                + ".crossplatform.wrapper."
                + type.getName() + "PluginWrapper");
        var constructor = paperLoaderClass.getConstructor();
        return constructor.newInstance();
    }

    @SuppressWarnings("unchecked")
    public static <P, L, S, M> void init(PlatformType type, P plugin, S serverObject, L loggerObject, Path dataDirectory) {
        if (CURRENT != null)
            throw new RuntimeException("Cross-platform implementation is already done!");
        PluginWrapper<P, L, S, M> implementation;

        try {
            implementation = switch (type) {
                case VELOCITY -> (PluginWrapper<P, L, S, M>) new VelocityPluginWrapper();
                case BUNGEE -> (PluginWrapper<P, L, S, M>) new BungeeCordPluginWrapper();
                case PAPER, FOLIA -> (PluginWrapper<P, L, S, M>) createPaperWrapperInstance(type);
            };
        } catch (Exception ex) {
            throw new RuntimeException("Failed to initialize " + type.getName() + "PluginWrapper!", ex);
        }
        implementation.platformType = type;
        implementation.server = serverObject;
        implementation.logger = loggerObject;
        implementation.dataDirectory = dataDirectory;
        implementation.handle = plugin;
        CURRENT = implementation;
        implementation.logInfo("CrossPlatform", "Initiated %s implementation.", type.getName());
        implementation.onInit();
    }

    @NotNull
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
    @Getter
    private PlayerRandomizer playerRandomizer;

    public final Path getConfigurationPath() {
        return dataDirectory.resolve("configuration.json");
    }

//    @SuppressWarnings("StatementWithEmptyBody")
//    public void updateConfiguration() throws Exception {
//        var configurationPath = getConfigurationPath();
//
//        if (Files.notExists(configurationPath))
//            return;
//
//        var md = MessageDigest.getInstance("MD5");
//        try (var is = new FileInputStream(configurationPath.toFile());
//             var dis = new DigestInputStream(is, md)) {
//            var buffer = new byte[8192];
//            while (dis.read(buffer) != -1) {}
//        }
//        var digest = md.digest();
//        var hex = new StringBuilder(digest.length * 2);
//        for (byte b : digest) hex.append(String.format("%02x", b));
//        var hash = hex.toString();
//
//        if (hash.equals(BuildConstants.CONFIG_VERSION)) return;
//
//        var defaultConfiguration = loadDefaultConfiguration();
//        var currentConfiguration = (JsonObject) loadConfiguration(true);
//
//        Utils.merge(defaultConfiguration, currentConfiguration);
//        try (var writer = java.nio.file.Files.newBufferedWriter(
//                configurationPath,
//                java.nio.charset.StandardCharsets.UTF_8,
//                java.nio.file.StandardOpenOption.CREATE,
//                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING)) {
//            GSON.toJson(currentConfiguration, writer);
//        }
//        configuration = GSON.fromJson(currentConfiguration, Configuration.class);
//        logInfo("Configuration file was updated because new version was found in the JAR.");
//    }

//    public JsonObject loadDefaultConfiguration() throws Exception {
//        JsonObject configuration;
//        try (InputStream in = getClass().getClassLoader().getResourceAsStream("configuration.json")) {
//            if (in == null)
//                throw new FileNotFoundException("configuration.json not found on classpath");
//            try (var isr = new InputStreamReader(in)) {
//                configuration = JsonParser.parseReader(isr).getAsJsonObject();
//            } catch (JsonParseException e) {
//                throw new IOException("Invalid configuration.json format", e);
//            }
//        }
//        return configuration;
//    }

    public Object loadConfiguration(boolean asJsonObject) throws Exception {
        var configurationPath = getConfigurationPath();
        if (Files.notExists(configurationPath)) {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream("configuration.json")) {
                if (in == null)
                    throw new FileNotFoundException("configuration.json not found on classpath");
                logInfo(CONFIGURATION_PREFIX, "Copying configuration file...");
                Files.copy(in, configurationPath);
            }
        }

        try (var reader = new FileReader(configurationPath.toFile())) {
            if (asJsonObject)
                return JsonParser.parseReader(reader).getAsJsonObject();
            else
                return GSON.fromJson(reader, Configuration.class);
        } catch (JsonParseException e) {
            throw new IOException("Invalid " + configurationPath.getFileName() + " format", e);
        }
    }

    private void onInit() {
        try {
            reloadConfiguration();
        } catch (Exception ex) {
            logError(CONFIGURATION_PREFIX, "Could not initialize configuration!", null);
            throw new RuntimeException(ex);
        }
        database = new CacheDatabaseManager();
    }

    public void onEnable() {
        if (database != null)
            database.resetTimer();
        registerCommand((BuildConstants.NAME + "-" + platformType.getName()).toLowerCase());
    }

    public void onDisable() {
        if (database != null)
            database.shutdown();
    }

    private void log(Map.Entry<String, String> entry) {
        var key = entry.getKey();
        if (key.startsWith("u:"))
            key = "(Username) " + key.substring(2);
        else
            key = "(UUID) " + key;
        logInfo(CONFIGURATION_PREFIX, "# %s => %s", key, entry.getValue());
    }

    public void reloadConfiguration() throws Exception {
        Files.createDirectories(dataDirectory);
        driversDirectory = dataDirectory.resolve("drivers");
        Files.createDirectories(driversDirectory);

        InvalidFieldsCollectorAdapterFactory.INVALID_FIELDS.put(GSON, new ArrayList<>());
        configuration = (Configuration) loadConfiguration(false);
        for (var service : configuration.getOnlineAuthentication().getServices()) {
            service.sortResponseHandlers();
            service.setDefaults(configuration.getOnlineAuthentication().getServiceDefaults());
        }
        var invalidFields = InvalidFieldsCollectorAdapterFactory.INVALID_FIELDS.get(GSON);
        if (invalidFields != null) {
            for (var field : invalidFields)
                logWarning(CONFIGURATION_PREFIX + "/Checker", "Unknown JSON field: " + field, null);
        }
        InvalidFieldsCollectorAdapterFactory.INVALID_FIELDS.remove(GSON);

        if (configuration.getPlayerRandomizer().isEnabled() &&
                (configuration.getPlayerRandomizer().getUniqueIdSettings().isRandomize() ||
                        configuration.getPlayerRandomizer().getUsernameSettings().isRandomize())) {
            playerRandomizer = new PlayerRandomizer(configuration.getPlayerRandomizer());
        } else {
            playerRandomizer = null;
        }

        logInfo(CONFIGURATION_PREFIX, "Configuration loaded.");
        logInfo(CONFIGURATION_PREFIX, "Using online UUIDs => %s", configuration.getOnlineAuthentication().isEnabled());

        if (configuration.getSwappedUniqueIds().isEnabled()) {
            logInfo(CONFIGURATION_PREFIX, "Loaded %s swapped UUIDs.", configuration.getSwappedUniqueIds().getMap().size());
            for (var entry : configuration.getSwappedUniqueIds().getMap().entrySet())
                log(entry);
        }

        if (configuration.getSwappedPlayerNames().isEnabled()) {
            logInfo(CONFIGURATION_PREFIX, "Loaded %s custom player usernames.", configuration.getSwappedPlayerNames().getMap().size());
            for (var entry : configuration.getSwappedPlayerNames().getMap().entrySet())
                log(entry);
        }
    }

    public void onPlayerDisconnect(String username, UUID uniqueId) {
        if (playerRandomizer != null)
            playerRandomizer.removeGeneratedPlayer(username, uniqueId);
    }

    @NotNull
    public CompletableFuture<BiObjectHolder<OnlinePlayerData, Message>> onPlayerLogin(
            @NotNull String username,
            @Nullable UUID uniqueId,
            @Nullable List<ProfileProperty> properties,
            @NotNull String address,
            boolean cacheFetchedData,
            @Nullable Runnable switchToOfflineMode,
            @NotNull Consumer<Message> disconnectHandler) {
        // I don't want to deal with nulls
        var dummy = CompletableFuture.completedFuture((BiObjectHolder<OnlinePlayerData, Message>)null);

        if (playerRandomizer == null) {
            if (!configuration.getOnlineAuthentication().isEnabled())
                return dummy;

            if (PluginWrapper.getCurrent().isServerOnlineMode()) {
                if (switchToOfflineMode == null) {
                    disconnectHandler.accept(new Message(Utils.GENERIC_DISCONNECT_MESSAGE, true));
                    return dummy;
                }
                switchToOfflineMode.run();
            }
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
                    disconnectHandler.accept(new Message(Utils.GENERIC_DISCONNECT_MESSAGE, true));
                    return dummy;
                }
            }
            if (configuration.getPlayerRandomizer().getUniqueIdSettings().isRandomize())
                playerRandomizer.nextUniqueId(uniqueId);
        }

        if (configuration.getOnlineAuthentication().isCheckForOnlineUuid() && uniqueId != null) {
            var offlineUuid = Utils.generateOfflineUniqueId(username);
            if (!offlineUuid.equals(uniqueId)) {
                boolean fetchForProperties = false;
                if (configuration.getOnlineAuthentication().isSendMessagesToConsole()) {
                    PluginWrapper.getCurrent().logInfo("PlayerDataFetcher", "Player %s connected with premium account, skipping fetching.", username, uniqueId);
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

        return PlayerDataFetcher.getPlayerData(
                username,
                uniqueId,
                address,
                cacheFetchedData && playerRandomizer == null,
                playerRandomizer == null,
                false,
                this
        ).handle((fetchedData, ex) -> {
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

    public boolean onGameProfileRequest(
            @NotNull BiObjectHolder<String, UUID> profile,
            @NotNull List<ProfileProperty> properties
    ) {
        boolean changed = false;
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

        if (configuration.getOnlineAuthentication().isEnabled()) {
            var fetched = PlayerDataFetcher.pullPlayerData(profile.getSecond());
            if (fetched != null) {
                profile.setSecond(fetched.getOnlineUniqueId());
                if (fetched.getProperties() != null)
                    properties.addAll(fetched.getProperties());
                changed = true;
            }
        }

        String swappedUsername = null;
        String swappedUniqueId = null;

        if (configuration.getSwappedUniqueIds().isEnabled()) {
            var swappedUuids = configuration.getSwappedUniqueIds().getMap();
            swappedUniqueId = Utils.getSwappedValue(swappedUuids, profile.getFirst(), profile.getSecond());
        }

        if (configuration.getSwappedPlayerNames().isEnabled()) {
            var swappedUsernames = configuration.getSwappedPlayerNames().getMap();
            swappedUsername = Utils.getSwappedValue(swappedUsernames, profile.getFirst(), profile.getSecond());
        }

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
                ((PluginWrapper<?, ?, ?, M>) PluginWrapper.getCurrent()).sendMessage(
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
                Utils.printException(exception, x -> PluginWrapper.getCurrent().logWarning(prefix, x, null));
            }

            @Override
            public void logError(String prefix, String message, Throwable exception, Object... args) {
                sendMessage(Utils.toLoggerMessage(prefix, message, args));
                Utils.printException(exception, x -> PluginWrapper.getCurrent().logError(prefix, x, null));
            }
        };
    }

    public void onNoArgsCommand(M messageAcceptor) {
        sendMessage(messageAcceptor, Configuration.CommandMessagesConfiguration::getNoArguments, getCommandBasePlaceholders());
    }

    public void onReloadCommand(M messageAcceptor) {
        var placeholders = getCommandBasePlaceholders();
        if (PlayerDataFetcher.isBusy()) {
            sendMessage(messageAcceptor, Configuration.CommandMessagesConfiguration::getReloadFetcherBusy, placeholders);
            return;
        }

        try {
            reloadConfiguration();
            database.clearConnection();
            if (!database.loadDriverFromConfiguration()) {
                placeholders.put("driver", database.getDriver());
                sendMessage(messageAcceptor, Configuration.CommandMessagesConfiguration::getReloadDatabaseDriverFailed, placeholders);
            }
            database.resetTimer();
            sendMessage(messageAcceptor, Configuration.CommandMessagesConfiguration::getReloadSuccess, placeholders);
        } catch (Exception ex) {
            placeholders.put("exception_message", ex.getMessage());
            Utils.printException(ex, x -> logWarning("ReloadCommand", "Failed to reload configuration!", ex));
            sendMessage(messageAcceptor, Configuration.CommandMessagesConfiguration::getReloadFetcherBusy, placeholders);
        }
    }

    public void onPretendCommand(M messageAcceptor, UUID originalUniqueId, UUID uniqueId, String username,
                                 boolean tryFetchProperties, Consumer<Runnable> async) {
        async.accept(() -> {
            PlayerData data;
            boolean fetch = tryFetchProperties;
            if (fetch && PluginWrapper.getCurrent()
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
}
