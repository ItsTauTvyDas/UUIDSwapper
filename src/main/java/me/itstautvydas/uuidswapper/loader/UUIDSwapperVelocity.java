package me.itstautvydas.uuidswapper.loader;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.inject.Inject;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.player.GameProfileRequestEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.util.GameProfile;
import com.velocitypowered.api.util.UuidUtils;
import me.itstautvydas.BuildConstants;
import me.itstautvydas.uuidswapper.Utils;
import me.itstautvydas.uuidswapper.crossplatform.PlatformType;
import me.itstautvydas.uuidswapper.data.FetchedUuidData;
import me.itstautvydas.uuidswapper.database.CacheDatabaseManager;
import me.itstautvydas.uuidswapper.config.Configuration;
import me.itstautvydas.uuidswapper.config.ServiceConfiguration;
import me.itstautvydas.uuidswapper.data.OnlinePlayerData;
import me.itstautvydas.uuidswapper.helper.BiObjectHolder;
import me.itstautvydas.uuidswapper.helper.ObjectHolder;
import me.itstautvydas.uuidswapper.data.ResponseData;
import me.itstautvydas.uuidswapper.crossplatform.CrossPlatformImplementation;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletionException;

@Plugin(id = "uuid-swapper",
        name = "UUIDSwapper",
        version = BuildConstants.VERSION,
        description = "Swap player names or UUID, use online UUIDs for offline mode!",
        url = "https://itstautvydas.me",
        authors = { "ItsTauTvyDas" })
public class UUIDSwapperVelocity {

    private Configuration config;

    private final Path dataDirectory;
    private Path driversDirectory;
    private final Logger logger;
    private final ProxyServer server;
    private final CacheDatabaseManager database;

    private String lastUsedService;
    private long lastUsedServiceAt;

    private final HttpClient client;

    private final Map<UUID, FetchedUuidData> fetchedUuids = new HashMap<>();
    private final Map<UUID, Long> throttlesConnections = new HashMap<>();

    @Inject
    public UUIDSwapperVelocity(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) throws Exception {
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.server = server;
        this.client = HttpClient.newHttpClient();
        reloadConfig();
        this.database = new CacheDatabaseManager(this);
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        this.database.init();
    }

    private void log(Map.Entry<String, Object> entry) {
        logger.info("# {} => {}", entry.getKey(), entry.getValue());
    }

    public void reloadConfig() throws Exception {
        Files.createDirectories(dataDirectory);

        driversDirectory = dataDirectory.resolve("drivers");
        Files.createDirectories(driversDirectory);

        CrossPlatformImplementation.init(
                PlatformType.VELOCITY,
                server,
                logger,
                dataDirectory.resolve("config.toml")
        );
        CrossPlatformImplementation.getVelocity().loadConfiguration();
        config = new Configuration();

        logger.info("Configuration loaded.");
        logger.info("Using online UUIDs => {}", config.areOnlineUUIDsEnabled());

        logger.info("Loaded {} swapped UUIDs.", config.getSwappedUuids().size());
        for (var entry : config.getSwappedUuids().entrySet())
            log(entry);

        logger.info("Loaded {} custom player usernames.", config.getCustomPlayerNames().size());
        for (var entry : config.getCustomPlayerNames().entrySet())
            log(entry);
    }

    public EventTask tryFetchUuid(String username, UUID uniqueUuid, String remoteAddress,
                                  ObjectHolder<PreLoginEvent.PreLoginComponentResult> eventResult,
                                  BiObjectHolder<UUID, FetchedUuidData> newUuid,
                                  Runnable after) {
        var originalUuid = Utils.requireUuid(username, uniqueUuid); // Pre-1.20.1

        return EventTask.async(() -> {
            var services = new ArrayList<String>();
            services.add(config.getServiceName());
            services.addAll(config.getFallbackServices());

            // Make last used (successful) service first
            if (lastUsedService != null) {
                services.remove(0); // Remove use-service because it previously failed
                services.remove(lastUsedService); // Remove because it will duplicate
                services.add(0, lastUsedService);
            }

            var timeoutCounter = 0L;
            var disconnect = true;
            String disconnectMessage = null;
            var placeholders = new HashMap<String, Object>();

            for (int i = 0; i < services.size(); i++) {
                String name = services.get(i);
                ServiceConfiguration service = config.getService(name);

                if (i == 1 && config.getSendErrorMessagesToConsole())
                    logger.warn("Defined service's name in 'use-service' failed, using fallback ones!");

                if (service == null) {
                    if (config.getSendErrorMessagesToConsole())
                        logger.warn("Service called '{}' doesn't exist! Skipping", name);
                    continue;
                }

                if (i >= 1) {
                    // Check if fallback service is expired
                    if (lastUsedServiceAt != 0 && lastUsedServiceAt + config.getFallbackServiceRememberMilliTime() >= System.currentTimeMillis()) {
                        lastUsedService = null;
                        lastUsedServiceAt = 0;
                    } else {
                        lastUsedService = name;
                        lastUsedServiceAt = System.currentTimeMillis();
                    }
                }

                boolean sendMessages = config.getSendSuccessfulMessagesToConsole() || service.isDebugEnabled();
                boolean sendErrorMessages = config.getSendErrorMessagesToConsole() || service.isDebugEnabled();

                disconnectMessage = service.getDefaultDisconnectMessage();
                String prefix = "[" + name + "]:";

                try {
                    placeholders.clear();
                    placeholders.put("username", username);
                    placeholders.put("uuid", originalUuid.toString());

                    String queryString = Utils.replacePlaceholders(Utils.buildDataString(service.getRequestQueryData()), placeholders);
                    if (service.isDebugEnabled())
                        logger.info("{} (Debug) {}'s original UUID - {}", prefix, username, originalUuid);
                    String endpoint = Utils.replacePlaceholders(service.getEndpoint(), placeholders);
                    HttpRequest.Builder builder = HttpRequest.newBuilder();

                    if (!queryString.isEmpty())
                        builder.uri(URI.create(endpoint + "?" + queryString));
                    else
                        builder.uri(URI.create(endpoint));

                    if (service.getMethod().equalsIgnoreCase("POST"))
                        builder.POST(HttpRequest.BodyPublishers.ofString(
                                Utils.replacePlaceholders(Utils.buildDataString(service.getRequestPostData()), placeholders)
                        ));

                    for (var header : service.getRequestHeaders().entrySet()) {
                        builder.setHeader(
                                Utils.replacePlaceholders(header.getKey(), placeholders),
                                Utils.replacePlaceholders(header.getValue().toString(), placeholders)
                        );
                    }

                    long timeout = config.getMaxTimeout() - timeoutCounter;
                    if (timeout <= config.getMinTimeout()) {
                        if (service.isDebugEnabled())
                            logger.warn("{} (Debug) Timed out, current timeout value - {}, min timeout - {}", prefix, timeout, config.getMinTimeout());
                        disconnectMessage = service.getServiceTimedOutDisconnectMessage();
                        break;
                    }
                    builder.timeout(Duration.ofMillis(Math.min(timeout, service.getTimeout())));

                    HttpRequest request = builder.build();

                    long start = System.currentTimeMillis();
                    var result = client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                            .thenApply(response -> new ResponseData(response, null))
                            .exceptionally(ex ->
                                    new ResponseData(
                                            null, ex instanceof CompletionException && ex.getCause() != null
                                            ? ex.getCause() : ex)
                            ).join();
                    if (result.getException() != null) {
                        Utils.addExceptionPlaceholders(result.getException(), placeholders);
                        boolean shouldDisconnect;

                        if (result.getException() instanceof HttpConnectTimeoutException) {
                            disconnectMessage = service.getServiceTimedOutDisconnectMessage();
                            shouldDisconnect = true;
                        } else { // Might handle more exceptions in the future
                            shouldDisconnect = service.shouldDisconnectOnConnectionError();
                            disconnectMessage = service.getConnectionErrorDisconnectMessage();
                        }

                        if (sendErrorMessages)
                            logger.error("{} Connection error ({}), failed to fetch UUID from the service!",
                                    prefix, result.getException().getClass().getName());
                        if (service.isDebugEnabled())
                            logger.error(result.getException().getMessage(), result.getException());

                        if (shouldDisconnect)
                            break;
                        continue;
                    }
                    var response = result.getResponse();
                    long took = System.currentTimeMillis() - start;

                    placeholders.put("http.url", response.uri().toString());
                    placeholders.put("http.status", String.valueOf(response.statusCode()));
                    placeholders.put("took", String.valueOf(took));
                    placeholders.put("max-timeout", String.valueOf(config.getMaxTimeout()));
                    placeholders.put("min-timeout", String.valueOf(config.getMinTimeout()));
                    placeholders.put("timeout-counter", String.valueOf(timeoutCounter));
                    placeholders.put("timeout-left", String.valueOf(timeout));

                    timeoutCounter += took;
                    if (service.isDebugEnabled())
                        logger.info("{} (Debug) Took {}ms ({}/{}ms - added up timeout/max timeout) to fetch data.", prefix, took, timeoutCounter, config.getMaxTimeout());
                    if (response.statusCode() != service.getExpectedStatusCode()) {
                        if (sendErrorMessages)
                            logger.error("{} Returned wrong HTTP status code! Got {}, expected {}.",
                                    prefix, response.statusCode(), service.getExpectedStatusCode());
                        disconnectMessage = service.getBadStatusDisconnectMessage();
                        if (service.shouldDisconnectOnBadStatus())
                            break;
                        continue;
                    }

                    Object responseBody;
                    try {
                        responseBody = JsonParser.parseString(response.body());
                    } catch (Exception ex) {
                        responseBody = response.body();
                        if (service.isDebugEnabled())
                            logger.info("{} (Debug) Body does not have valid JSON, parsing as text => {}",
                                    prefix, responseBody.toString().replaceAll("\\R+", ""));
                    }

                    for (var entry : response.headers().map().entrySet())
                        placeholders.put("http.header.str" + entry.getKey().toLowerCase(), String.join(",", entry.getValue()));
                    for (var entry : response.headers().map().entrySet())
                        placeholders.put("http.header.raw" + entry.getKey().toLowerCase(), entry.getValue());

                    if (responseBody instanceof JsonElement element)
                        placeholders.putAll(Utils.extractJsonPaths("response.", element));
                    else
                        placeholders.put("response", responseBody.toString());

                    var handler = service.executeResponseHandlers(placeholders);
                    if (handler != null) {
                        if (service.isDebugEnabled())
                            logger.info("{} (Debug) Found valid response handler from the configuration, allowed join? => {}, disconnect message => {}",
                                    prefix, handler.isPlayerAllowedToJoin(), handler.getDisconnectMessage());
                        if (handler.isPlayerAllowedToJoin()) {
                            disconnect = false;
                            break;
                        }
                        disconnectMessage = handler.getDisconnectMessage();
                        break;
                    }

                    String uuidString;
                    try {
                        if (responseBody instanceof JsonElement element)
                            uuidString = Utils.getJsonValue(element, service.getPathToUuid()).getAsString();
                        else
                            uuidString = responseBody.toString();
                    } catch (Exception ex) {
                        Utils.addExceptionPlaceholders(ex, placeholders);
                        if (sendErrorMessages)
                            logger.error("{} Failed, invalid JSON path to UUID - {}", prefix, service.getPathToUuid());
                        if (service.isDebugEnabled())
                            logger.error(ex.getMessage(), ex);
                        disconnectMessage = service.getBadUuidDisconnectMessage();
                        if (service.shouldDisconnectOnBadUuidPath())
                            break;
                        continue;
                    }

                    placeholders.put("new-uuid", uuidString);

                    UUID uuid;
                    try {
                        uuid = UUID.fromString(uuidString);
                    } catch (Exception ex) {
                        Utils.addExceptionPlaceholders(ex, placeholders);
                        if (sendErrorMessages)
                            logger.error("{} Failed to convert '{}' to UUID!", prefix, uuidString.replaceAll("\\R+", ""));
                        if (service.isDebugEnabled())
                            logger.error(ex.getMessage(), ex);
                        disconnectMessage = service.getBadUuidDisconnectMessage();
                        if (service.shouldDisconnectOnInvalidUuid())
                            break;
                        continue;
                    }

                    List<GameProfile.Property> properties = null;
                    if (responseBody instanceof JsonElement element) {
                        String path = service.getPathToProperties();
                        if (path == null)
                            path = service.getPathToTextures();
                        try {
                            var propertiesJsonElement = Utils.getJsonValue(element, path);
                            if (propertiesJsonElement.isJsonArray()) {
                                properties = propertiesJsonElement.getAsJsonArray()
                                        .asList()
                                        .stream()
                                        .map(JsonElement::getAsJsonObject)
                                        .map(x -> new GameProfile.Property(
                                                x.get("name").getAsString(),
                                                x.get("value").getAsString(),
                                                x.get("signature").getAsString()
                                        ))
                                        .toList();
                            } else if (propertiesJsonElement.isJsonObject()) {
                                properties = List.of(
                                        new GameProfile.Property(
                                                propertiesJsonElement.getAsJsonObject().get("name").getAsString(),
                                                propertiesJsonElement.getAsJsonObject().get("value").getAsString(),
                                                propertiesJsonElement.getAsJsonObject().get("signature").getAsString()
                                        )
                                );
                            }
                        } catch (Exception ex) {
                            if (sendErrorMessages)
                                logger.error("{} Failed to fetch profile's properties!", prefix);
                            if (service.isDebugEnabled())
                                logger.error(ex.getMessage(), ex);
                        }
                    }

                    if (config.shouldCacheOnlineUuids() && config.getDatabaseConfiguration().isEnabled() && database.isDriverRunning()) {
                        var data = new OnlinePlayerData(originalUuid, uuid, properties, remoteAddress, null, null);
                        newUuid.set(originalUuid, data);
                        database.storeOnlinePlayerCache(data);
                    } else {
                        newUuid.set(originalUuid, new FetchedUuidData(originalUuid, uuid, properties));
                    }

                    if (sendMessages)
                        logger.info("{} UUID successfully fetched for {} => {}", prefix, username, uuid);

                    disconnect = false;
                    break;
                } catch (Exception ex) {
                    Utils.addExceptionPlaceholders(ex, placeholders);
                    if (sendErrorMessages)
                        logger.error("{} Unknown error, failed to fetch UUID from the service!", prefix);
                    if (service.isDebugEnabled())
                        logger.error(ex.getMessage(), ex);
                    disconnectMessage = service.getUnknownErrorDisconnectMessage();
                    if (service.shouldDisconnectOnUnknownError())
                        break;
                    // Continue
                }
            }

            if (disconnect) {
                if (disconnectMessage == null)
                    disconnectMessage = config.getDefaultService().getDefaultDisconnectMessage();
                if (disconnectMessage != null) {
                    disconnectMessage = Utils.replacePlaceholders(disconnectMessage, placeholders);
                    eventResult.set(PreLoginEvent.PreLoginComponentResult.denied(Utils.toComponent(disconnectMessage)));
                    after.run();
                    return;
                }
                eventResult.set(PreLoginEvent.PreLoginComponentResult.denied(Component.translatable("multiplayer.disconnect.generic")));
            }
            after.run();
        });
    }

    @Subscribe
    public EventTask onShutdownEvent(ProxyShutdownEvent event) {
        return EventTask.async(database::shutdown);
    }

    @Subscribe
    public EventTask onPlayerPreLogin(PreLoginEvent event) {
        if (!config.areOnlineUUIDsEnabled())
            return null;
        if (server.getConfiguration().isOnlineMode())
            event.setResult(PreLoginEvent.PreLoginComponentResult.forceOfflineMode());

        if (config.getCheckForOnlineUuid() && event.getUniqueId() != null) {
            var offlineUuid = UuidUtils.generateOfflinePlayerUuid(event.getUsername());
            if (!offlineUuid.equals(event.getUniqueId())) {
                if (config.getSendSuccessfulMessagesToConsole())
                    logger.info("Player {} has online UUID ({}), skipping fetching.", event.getUsername(), event.getUniqueId());
                return null;
            }
        }

        var throttle = config.getServiceConnectionThrottle();
        if (throttle > 0) {
            var throttledWhen = throttlesConnections.get(event.getUniqueId());
            if (throttledWhen != null) {
                var isThrottled = throttledWhen + throttle > System.currentTimeMillis();

                if (isThrottled) {
                    if (!config.isConnectionThrottleDialogEnabled()) {
                        event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                                Utils.toComponent(config.getConnectionThrottleDialogMessage())
                        ));
                    }
                    return null;
                }

                throttlesConnections.entrySet()
                        .removeIf(next -> next.getValue() + throttle <= System.currentTimeMillis());
            }
        }
        return tryFetchUuid(
                event.getUsername(),
                event.getUniqueId(),
                event.getConnection().getRemoteAddress().getAddress().getHostAddress(),
                new ObjectHolder<>(event.getResult(), event::setResult),
                new BiObjectHolder<>(null, null, fetchedUuids::put),
                () -> throttlesConnections.put(event.getUniqueId(), System.currentTimeMillis())
        );
    }

//    @Subscribe
//    public void onPlayerLogin(PlayerConfigurationEvent event) {
//        if (config.isConnectionThrottleDialogEnabled())
//            return;
//        var throttledWhen = throttlesConnections.get(event.getPlayer().getUniqueId());
//        if (throttledWhen != null) {
//            var throttle = config.getServiceConnectionThrottle();
//            long secondsLeft = ((throttledWhen + throttle) - System.currentTimeMillis()) / 1000;
//        }
//    }

    @Subscribe
    public void onGameProfileRequest(GameProfileRequestEvent event) {
        if (config.areOnlineUUIDsEnabled()) {
            var originalUuid = Utils.requireUuid(event.getUsername(), event.getGameProfile().getId());
            var fetched = fetchedUuids.get(originalUuid);
            if (fetched != null) {
                event.setGameProfile(Utils.createProfile(event.getUsername(), fetched.getOnlineUuid(), event.getGameProfile(), fetched.getProperties()));
                if (!config.stillSwapUuids())
                    return;
            }
        }

        var swappedUsernames = config.getCustomPlayerNames();
        var swappedUuids = config.getSwappedUuids();

        var newUsername = Utils.getSwappedValue(swappedUsernames, event.getGameProfile());
        var newUUIDStr = Utils.getSwappedValue(swappedUuids, event.getGameProfile());

        if (newUsername != null || newUUIDStr != null) {
            logger.info("Player's ({} {}) new profile is:", event.getUsername(), event.getGameProfile().getId());
            if (newUsername != null)
                logger.info(" # Username => {}", newUsername);
            if (newUUIDStr != null)
                logger.info(" # Unique ID => {}", newUUIDStr);
            event.setGameProfile(Utils.createProfile(newUsername, newUUIDStr, event.getGameProfile(), null));
        }
    }

    public ProxyServer getServer() {
        return server;
    }

    public Logger getLogger() {
        return logger;
    }

    public Configuration getConfiguration() {
        return config;
    }

    public Path getDataDirectory() {
        return dataDirectory;
    }

    public Path getDriversDirectory() {
        return driversDirectory;
    }
}
