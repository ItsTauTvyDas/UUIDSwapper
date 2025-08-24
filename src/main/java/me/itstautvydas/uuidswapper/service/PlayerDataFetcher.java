package me.itstautvydas.uuidswapper.service;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import lombok.Getter;
import lombok.Locked;
import lombok.experimental.UtilityClass;
import me.itstautvydas.uuidswapper.Utils;
import me.itstautvydas.uuidswapper.config.Configuration;
import me.itstautvydas.uuidswapper.crossplatform.PluginWrapper;
import me.itstautvydas.uuidswapper.data.*;
import me.itstautvydas.uuidswapper.enums.FallbackUsage;
import me.itstautvydas.uuidswapper.enums.ResponseHandlerState;
import me.itstautvydas.uuidswapper.helper.BiObjectHolder;
import me.itstautvydas.uuidswapper.helper.ObjectHolder;
import me.itstautvydas.uuidswapper.helper.SimplifiedLogger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

@UtilityClass
public class PlayerDataFetcher {
    private static final Map<UUID, OnlinePlayerData> fetchedPlayerData = new ConcurrentHashMap<>();
    private static final Map<UUID, PlayerData> pretendMap = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> throttledConnections = new ConcurrentHashMap<>();
    private static volatile String lastUsedService;
    private static volatile long lastUsedServiceAt;
    private static final HttpClient client;

    @Getter
    private static volatile boolean busy;

    static {
        client = HttpClient.newHttpClient();
    }

    public static OnlinePlayerData pullPlayerData(UUID originalUniqueId) {
        return fetchedPlayerData.remove(originalUniqueId);
    }

    public static PlayerData pullPretender(UUID originalUniqueId) {
        return pretendMap.remove(originalUniqueId);
    }

    public static PlayerData pretend(
            UUID originalUniqueId,
            String username,
            UUID uniqueId,
            boolean tryFetchProperties,
            SimplifiedLogger logger
    ) {
        Objects.requireNonNull(originalUniqueId);
        Objects.requireNonNull(username);
        Objects.requireNonNull(logger);

        if (tryFetchProperties) {
            // Try fetch to get profile's properties
            var data = getPlayerData(username, originalUniqueId, "", false, false, true, logger).join();
            if (data.containsFirst()) {
                var playerData = new PlayerData(
                        originalUniqueId,
                        username,
                        uniqueId == null ? data.getFirst().getOnlineUuid() : uniqueId
                );
                playerData.setProperties(data.getFirst().getProperties());
                pretendMap.put(originalUniqueId, playerData);
                return playerData;
            }
            return null;
        } else {
            if (uniqueId == null)
                uniqueId = Utils.generateOfflineUniqueId(username);
            var data = new PlayerData(originalUniqueId, username, uniqueId);
            data.setProperties(new ArrayList<>());
            pretendMap.put(originalUniqueId, data);
            return data;
        }
    }

    @Locked
    public static boolean isThrottled(UUID uniqueId) {
        var throttle = PluginWrapper.getCurrent().getConfiguration().getOnlineAuthentication().getServiceConnectionThrottle();
        throttledConnections.entrySet().removeIf(next -> next.getValue() + throttle <= System.currentTimeMillis());
        return throttledConnections.containsKey(uniqueId);
    }

    private static Configuration.ResponseHandlerConfiguration handleResponse(
            ResponseHandlerState state,
            Configuration.ServiceConfiguration service,
            Map<String, Object> placeholders,
            ObjectHolder<Message> result
    ) {
        result.set(null); // Reset
        var handler = service.executeResponseHandlers(state, placeholders);
        if (handler != null) {
            if (service.isDebugEnabled())
                PluginWrapper.getCurrent().logInfo(
                        getPrefix(service.getName()),
                        "DEBUG - Found valid response handler from the configuration, allowed join? => {}, disconnect message => {}",
                        handler.getAllowPlayerToJoin(), handler.getDisconnectMessage());
            if (handler.getAllowPlayerToJoin() != null && !handler.getAllowPlayerToJoin())
                result.set(new Message(handler.getDisconnectMessage(), false));
        }
        return handler;
    }

    private static String getPrefix(String name) {
        return "PlayerDataFetcher/" + name;
    }

    @NotNull
    public static CompletableFuture<BiObjectHolder<OnlinePlayerData, Message>> getPlayerData(
            @NotNull String username,
            @Nullable UUID uniqueId,
            @NotNull String remoteAddress,
            boolean cacheFetchedData,
            boolean cacheDatabase,
            boolean forceErrorMessages,
            @Nullable SimplifiedLogger logger
    ) {
        busy = true;
        final var plugin = PluginWrapper.getCurrent();
        final var config = plugin.getConfiguration().getOnlineAuthentication();
        final var database = plugin.getDatabase();
        final var finalLogger = logger == null ? plugin : logger;

        final var originalUuid = uniqueId == null ? Utils.requireUuid(username, uniqueId) : uniqueId;
        return CompletableFuture.supplyAsync(() -> {
            OnlinePlayerData fetchedPlayerData = null;
            var messageHolder = new ObjectHolder<Message>(null);

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

            Configuration.ResponseHandlerConfiguration handler;

            for (int i = 0; i < services.size(); i++) {
                String name = services.get(i);
                Configuration.ServiceConfiguration service = config.getService(name);

                if (i == 1 && config.isSendErrorMessagesToConsole())
                    finalLogger.logWarning("Defined service's name in 'use-service' failed, using fallback ones!", null);

                if (service == null) {
                    if (config.isSendErrorMessagesToConsole())
                        finalLogger.logWarning("Service called '{}' doesn't exist! Skipping", null, name);
                    continue;
                }

                if (i >= 1) {
                    var rememberTime = config.getFallbackServiceRememberTime() * 1000;
                    // Check if fallback service is expired
                    if (lastUsedServiceAt != 0 && lastUsedServiceAt + rememberTime >= System.currentTimeMillis()) {
                        lastUsedService = null;
                        lastUsedServiceAt = 0;
                    } else {
                        lastUsedService = name;
                        lastUsedServiceAt = System.currentTimeMillis();
                    }
                }

                boolean sendDebugMessages = service.isDebugEnabled();
                boolean sendMessages = config.isSendMessagesToConsole()
                        || sendDebugMessages;
                boolean sendErrorMessages = config.isSendErrorMessagesToConsole()
                        || sendDebugMessages || forceErrorMessages;

                disconnectMessage = service.getDefaultDisconnectMessage();
                String prefix = getPrefix(name);

                try {
                    placeholders.clear();
                    placeholders.put("username", username);
                    placeholders.put("uuid", originalUuid.toString());

                    String queryString = Utils.replacePlaceholders(Utils.buildDataString(service.getQueryData()), placeholders);
                    if (sendDebugMessages)
                        finalLogger.logInfo(prefix, "DEBUG - {}'s original UUID - {}", username, originalUuid);
                    String endpoint = Utils.replacePlaceholders(service.getEndpoint(), placeholders);
                    HttpRequest.Builder builder = HttpRequest.newBuilder();

                    if (!queryString.isEmpty())
                        builder.uri(URI.create(endpoint + "?" + queryString));
                    else
                        builder.uri(URI.create(endpoint));

                    if (service.getRequestMethod().equalsIgnoreCase("POST"))
                        builder.POST(HttpRequest.BodyPublishers.ofString(
                                Utils.replacePlaceholders(Utils.buildDataString(service.getPostData()), placeholders)
                        ));

                    for (var header : service.getHeaders().entrySet()) {
                        builder.setHeader(
                                Utils.replacePlaceholders(header.getKey(), placeholders),
                                Utils.replacePlaceholders(header.getValue(), placeholders)
                        );
                    }

                    var timeout = config.getMaxTimeout() - timeoutCounter;
                    if (timeout <= config.getMinTimeout()) {
                        if (sendDebugMessages)
                            finalLogger.logWarning(prefix, "DEBUG - Timed out, current timeout value - {}, min timeout - {}", null, timeout, config.getMinTimeout());
                        disconnectMessage = service.getServiceTimeoutDisconnectMessage();
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
                            disconnectMessage = service.getServiceTimeoutDisconnectMessage();
                            shouldDisconnect = true;
                        } else { // Might handle more exceptions in the future
                            shouldDisconnect = !service.getUseFallbacks().contains(FallbackUsage.ON_CONNECTION_ERROR);
                            disconnectMessage = service.getConnectionErrorDisconnectMessage();
                        }

                        if (sendErrorMessages)
                            finalLogger.logError(prefix, "Connection error ({}), failed to fetch UUID from the service!",
                                    null, result.getException().getClass().getName());
                        if (sendDebugMessages)
                            finalLogger.logError(result.getException().getMessage(), result.getException());

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
                    if (sendDebugMessages)
                        finalLogger.logInfo(prefix, "DEBUG - Took {}ms ({}/{}ms - added up timeout/max timeout) to fetch data.", took, timeoutCounter, config.getMaxTimeout());
                    if (!service.isIgnoreStatusCode() && response.statusCode() != service.getExpectStatusCode()) {
                        if (sendErrorMessages)
                            finalLogger.logError(prefix, "Returned wrong HTTP status code! Got {}, expected {}.",
                                    null, response.statusCode(), service.getExpectStatusCode());
                        disconnectMessage = service.getServiceBadStatusDisconnectMessage();
                        if (!service.getUseFallbacks().contains(FallbackUsage.ON_BAD_STATUS))
                            break;
                        continue;
                    }

                    Object responseBody;
                    try {
                        responseBody = JsonParser.parseString(response.body());
                    } catch (Exception ex) {
                        responseBody = response.body();
                        if (sendDebugMessages)
                            finalLogger.logInfo(prefix, "DEBUG - Body does not have valid JSON, parsing as text => {}",
                                    responseBody.toString().replaceAll("\\R+", ""));
                    }

                    for (var entry : response.headers().map().entrySet())
                        placeholders.put("http.header.str" + entry.getKey().toLowerCase(), String.join(",", entry.getValue()));
                    for (var entry : response.headers().map().entrySet())
                        placeholders.put("http.header.raw" + entry.getKey().toLowerCase(), entry.getValue());

                    if (responseBody instanceof JsonElement element)
                        placeholders.putAll(Utils.extractJsonPaths("response.", element));
                    else
                        placeholders.put("response", responseBody.toString());

                    handler = handleResponse(ResponseHandlerState.AFTER_REQUEST, service, placeholders, messageHolder);
                    if (handler != null) {
                        if (handler.getAllowPlayerToJoin()) {
                            disconnect = false;
                            break;
                        }
                        if (messageHolder.containsValue())
                            break;
                    }

                    String uniqueIdString;
                    try {
                        if (responseBody instanceof JsonElement element)
                            uniqueIdString = Utils.getJsonValue(element, service.getJsonPathToUuid()).getAsString();
                        else
                            uniqueIdString = responseBody.toString();
                    } catch (Exception ex) {
                        Utils.addExceptionPlaceholders(ex, placeholders);
                        if (sendErrorMessages)
                            finalLogger.logError(prefix, "Failed, invalid JSON path to UUID - {}", null, service.getJsonPathToUuid());
                        if (sendDebugMessages)
                            finalLogger.logError(ex.getMessage(), ex);
                        uniqueIdString = null;
                    }

                    handler = handleResponse(ResponseHandlerState.AFTER_UUID, service, placeholders, messageHolder);
                    if (handler != null) {
                        if (handler.getAllowPlayerToJoin()) {
                            disconnect = false;
                            break;
                        }
                        if (messageHolder.containsValue())
                            break;
                    }

                    if (uniqueIdString == null) {
                        disconnectMessage = service.getBadUuidDisconnectMessage();
                        if (!service.getUseFallbacks().contains(FallbackUsage.ON_BAD_UUID_PATH))
                            break;
                        continue;
                    }

                    placeholders.put("new-uuid", uniqueIdString);

                    UUID fetchedUniqueId;
                    try {
                        fetchedUniqueId = UUID.fromString(uniqueIdString);
                    } catch (Exception ex) {
                        Utils.addExceptionPlaceholders(ex, placeholders);
                        if (sendErrorMessages)
                            finalLogger.logError(prefix, "Failed to convert '{}' to UUID!", null, uniqueIdString.replaceAll("\\R+", ""));
                        if (sendDebugMessages)
                            finalLogger.logError(ex.getMessage(), ex);
                        disconnectMessage = service.getBadUuidDisconnectMessage();
                        if (!service.getUseFallbacks().contains(FallbackUsage.ON_INVALID_UUID))
                            break;
                        continue;
                    }

                    List<ProfileProperty> properties = null;
                    if (responseBody instanceof JsonElement element) {
                        String path = service.getJsonPathToProperties();
                        if (path == null)
                            path = service.getJsonPathToTextures();
                        if (path != null) {
                            try {
                                var propertiesJsonElement = Utils.getJsonValue(element, path);
                                if (propertiesJsonElement.isJsonArray()) {
                                    properties = propertiesJsonElement.getAsJsonArray()
                                            .asList()
                                            .stream()
                                            .map(JsonElement::getAsJsonObject)
                                            .map(x -> new ProfileProperty(
                                                    x.get("name").getAsString(),
                                                    x.get("value").getAsString(),
                                                    x.get("signature").getAsString()
                                            ))
                                            .toList();
                                } else if (propertiesJsonElement.isJsonObject()) {
                                    properties = List.of(
                                            new ProfileProperty(
                                                    propertiesJsonElement.getAsJsonObject().get("name").getAsString(),
                                                    propertiesJsonElement.getAsJsonObject().get("value").getAsString(),
                                                    propertiesJsonElement.getAsJsonObject().get("signature").getAsString()
                                            )
                                    );
                                }
                            } catch (Exception ex) {
                                if (sendErrorMessages)
                                    finalLogger.logError(prefix, "Failed to fetch profile's properties!", null);
                                if (sendDebugMessages)
                                    finalLogger.logError(ex.getMessage(), ex);
                            }
                        }
                    }

                    if (config.getCaching().isEnabled() && database.getConfiguration().isEnabled() && database.isDriverRunning()) {
                        fetchedPlayerData = new OnlinePlayerData(originalUuid, fetchedUniqueId, properties, remoteAddress, System.currentTimeMillis(), null);
                        if (cacheDatabase)
                            database.storeOnlinePlayerCache(fetchedPlayerData);
                    } else {
                        fetchedPlayerData = new OnlinePlayerData(originalUuid, fetchedUniqueId, properties, null, System.currentTimeMillis(), null);
                    }

                    if (sendMessages)
                        finalLogger.logInfo(prefix, "UUID successfully fetched for {} => {}", username, fetchedUniqueId);

                    if (cacheFetchedData)
                        PlayerDataFetcher.fetchedPlayerData.put(originalUuid, fetchedPlayerData);
                    disconnect = false;
                    break;
                } catch (Exception ex) {
                    Utils.addExceptionPlaceholders(ex, placeholders);
                    if (sendErrorMessages)
                        finalLogger.logError(prefix, "Unknown error, failed to fetch UUID from the service!", ex);
                    if (sendDebugMessages)
                        finalLogger.logError(ex.getMessage(), ex);
                    disconnectMessage = service.getUnknownErrorDisconnectMessage();
                    if (!service.getUseFallbacks().contains(FallbackUsage.ON_UNKNOWN_ERROR))
                        break;
                }
            }

            if (messageHolder.containsValue()) {
                disconnect = true;
                disconnectMessage = messageHolder.get().getMessage();
            }

            Message message = null;
            if (disconnect) {
                if (disconnectMessage == null)
                    disconnectMessage = config.getServiceDefaults().getDefaultDisconnectMessage();
                if (disconnectMessage != null) {
                    message = new Message(Utils.replacePlaceholders(disconnectMessage, placeholders), false);
                } else
                    message = new Message("multiplayer.disconnect.generic", true);
            }
            if (config.getServiceConnectionThrottle() > 0)
                throttledConnections.put(uniqueId, System.currentTimeMillis());
            busy = false;
            return new BiObjectHolder<>(fetchedPlayerData, message);
        });
    }
}
