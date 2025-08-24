package me.itstautvydas.uuidswapper.crossplatform;

import com.google.gson.*;
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
import me.itstautvydas.uuidswapper.helper.SimplifiedLogger;
import me.itstautvydas.uuidswapper.json.RequiredPropertyAdapterFactory;
import me.itstautvydas.uuidswapper.json.SortedJsonSerializer;
import me.itstautvydas.uuidswapper.json.StringListToStringAdapter;
import me.itstautvydas.uuidswapper.service.PlayerDataFetcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

@Getter
public abstract class PluginWrapper<P, T, L, S, M> implements SimplifiedLogger {
    private static PluginWrapper<?, ?, ?, ?, ?> CURRENT;
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
            .registerTypeAdapterFactory(new RequiredPropertyAdapterFactory())
            .registerTypeAdapter(LinkedTreeMap.class, new SortedJsonSerializer())
            .registerTypeAdapter(String.class, new StringListToStringAdapter())
            .create();

    public static Object createPaperWrapperInstance() throws Exception {
        var paperLoaderClass = Class.forName("me.itstautvydas." + BuildConstants.NAME.toLowerCase() + ".crossplatform.wrapper.PaperPluginWrapper");
        var constructor = paperLoaderClass.getConstructor();
        return constructor.newInstance();
    }

    @SuppressWarnings("unchecked")
    public static <P, T, L, S, M> void init(PlatformType type, P plugin, S serverObject, L loggerObject, Path dataDirectory) {
        if (CURRENT != null)
            throw new RuntimeException("Cross-platform implementation is already done!");
        PluginWrapper<P, T, L, S, M> implementation;
        try {
            implementation = switch (type) {
                case VELOCITY -> (PluginWrapper<P, T, L, S, M>) new VelocityPluginWrapper();
                case BUNGEE -> (PluginWrapper<P, T, L, S, M>) new BungeeCordPluginWrapper();
                case PAPER -> (PluginWrapper<P, T, L, S, M>) createPaperWrapperInstance();
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
        implementation.logInfo("CrossPlatform", "Initiated {} implementation.", type.getName());
        implementation.onInit();
    }

    public static PluginWrapper<?, ?, ?, ?, ?> getCurrent() {
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
//            logInfo("Copying configuration file...");
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
                logInfo("Copying configuration file...");
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
            logError("Could not initialize configuration!", null);
            throw new RuntimeException(ex);
        }
        database = new CacheDatabaseManager();
    }

    public void onEnable() {
        if (database != null)
            database.resetTimer();
        registerCommand();
    }

    public void onDisable() {
        if (database != null)
            database.shutdown();
    }

    private void log(Map.Entry<String, String> entry) {
        var key = entry.getKey();
        if (key.startsWith("u:"))
            key = key.substring(2);
        logInfo(null, "# {} => {}", key, entry.getValue());
    }

    public void reloadConfiguration() throws Exception {
        Files.createDirectories(dataDirectory);
        driversDirectory = dataDirectory.resolve("drivers");
        Files.createDirectories(driversDirectory);

//        updateConfiguration();
//        if (configuration == null)
            configuration = (Configuration) loadConfiguration(false);

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
            @NotNull String username,
            @Nullable UUID uniqueId,
            @NotNull String address,
            boolean cacheFetchedData,
            @Nullable Runnable switchToOfflineMode,
            @NotNull Consumer<Message> disconnectHandler) {
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
            var offlineUuid = Utils.generateOfflineUniqueId(username);
            if (!offlineUuid.equals(uniqueId)) {
                if (configuration.getOnlineAuthentication().isSendMessagesToConsole())
                    PluginWrapper.getCurrent().logInfo("[PlayerDataFetcher]:", "Player {} has online UUID ({}), skipping fetching.", username, uniqueId);
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
                cacheFetchedData,
                true,
                false,
                this
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

        var prefix = "Swapper";
        if (newUsername != null || newUniqueId != null)
            logInfo(prefix, "Player's ({}/{}) new profile is:", username, uniqueId);

        if (newUsername != null) {
            profile.setFirst(newUsername);
            logInfo(prefix, "  # Username => {}", newUsername);
        }

        if (newUniqueId != null) {
            profile.setSecond(UUID.fromString(newUniqueId));
            logInfo(prefix, "  # Unique ID => {}", newUniqueId);
        }
    }

    private Map<String, Object> placeholders() {
        var map = new HashMap<String, Object>();
        map.put("command", "uuidswapper-" + platformType.getName().toLowerCase());
        map.put("prefix", configuration.getCommandMessages().getPrefix());
        return map;
    }

    public SimplifiedLogger createLogger(M messageAcceptor) {
        return new SimplifiedLogger() {
            void sendMessage(String message, Object... args) {
                PluginWrapper.this.sendMessage(
                        messageAcceptor,
                        x -> message.formatted(args),
                        null
                );
            }

            @Override
            public void logInfo(String prefix, String message, Object... args) {
                sendMessage(Utils.toLoggerMessage(prefix, message), args);
            }

            @Override
            public void logWarning(String prefix, String message, Throwable exception, Object... args) {
                sendMessage(Utils.toLoggerMessage(prefix, message), args);
                Utils.printException(exception, x -> PluginWrapper.this.logWarning(prefix, x, null));
            }

            @Override
            public void logError(String prefix, String message, Throwable exception, Object... args) {
                sendMessage(Utils.toLoggerMessage(prefix, message), args);
                Utils.printException(exception, x -> PluginWrapper.this.logError(prefix, x, null));
            }
        };
    }

    public void onNoArgsCommand(M messageAcceptor) {
        sendMessage(messageAcceptor, Configuration.CommandMessagesConfiguration::getNoArguments, placeholders());
    }

    public void onReloadCommand(M messageAcceptor) {
        var placeholders = placeholders();
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
                    .noneMatch(x -> x.getJsonPathToProperties() != null ||
                            x.getJsonPathToTextures() != null))
                fetch = false;
            data = PlayerDataFetcher.pretend(originalUniqueId, username, uniqueId, fetch, createLogger(messageAcceptor));
            var placeholders = placeholders();
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
    public abstract void registerCommand();
    public abstract boolean isServerOnlineMode();
    public abstract PluginTaskWrapper<T> scheduleTask(Runnable run, Long repeatInSeconds, long delayInSeconds);
}
