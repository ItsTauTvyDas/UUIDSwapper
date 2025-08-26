package me.itstautvydas.uuidswapper.service;

import com.google.common.collect.Sets;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
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

public class PlayerDataFetcher {
    private static final Map<UUID, OnlinePlayerData> fetchedPlayerDataMap = new ConcurrentHashMap<>();
    private static final Set<Fetcher> workers = Sets.newConcurrentHashSet();
    private static final Map<UUID, PlayerData> pretendMap = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> throttledConnections = new ConcurrentHashMap<>();
    private static final HttpClient client;
    private static volatile String lastUsedService;
    private static volatile long lastUsedServiceAt;

    static {
        client = HttpClient.newHttpClient();
    }

    public static boolean isBusy() {
        return !workers.isEmpty();
    }

    public static void setPlayerProperties(UUID originalUniqueId, List<ProfileProperty> properties) {
        var fetched = fetchedPlayerDataMap.get(originalUniqueId);
        if (fetched != null) {
            fetched.setProperties(properties);
            fetched.setUpdatedAt(System.currentTimeMillis());
            return;
        }
        fetchedPlayerDataMap.put(originalUniqueId, new OnlinePlayerData(originalUniqueId, null, properties, null));
    }

    public static OnlinePlayerData pullPlayerData(UUID originalUniqueId) {
        return fetchedPlayerDataMap.remove(originalUniqueId);
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
                        uniqueId == null ? data.getFirst().getOnlineUniqueId() : uniqueId
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

    public static boolean isThrottled(UUID uniqueId, ObjectHolder<Long> timeLeft) {
        var throttle = PluginWrapper.getCurrent().getConfiguration().getOnlineAuthentication().getServiceConnectionThrottle();
        throttledConnections.entrySet().removeIf(next -> next.getValue() + throttle <= System.currentTimeMillis());
        if (timeLeft != null) {
            var when = throttledConnections.get(uniqueId);
            if (when == null) return false;
            timeLeft.set((when + throttle - System.currentTimeMillis()) / 1000);
            return true;
        }
        return throttledConnections.containsKey(uniqueId);
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
        final var fetcher = new Fetcher(username, uniqueId, remoteAddress, logger, cacheFetchedData, cacheDatabase, forceErrorMessages);
        return CompletableFuture.supplyAsync(() -> {
            workers.add(fetcher);
            fetcher.updateMessages(null);
            var services = new ArrayList<>(fetcher.config.getFallbackServices());
            services.add(0, fetcher.config.getServiceName());

            // Make last used (successful) service first
            if (lastUsedService != null) {
                services.remove(0); // Remove use-service because it previously failed
                services.remove(lastUsedService); // Remove because it will duplicate
                services.add(0, lastUsedService);
            }

            for (int i = 0; i < services.size(); i++) {
                String name = services.get(i);
                var service = fetcher.config.getService(name);

                if (i == 1 && fetcher.sendErrorMessages)
                    fetcher.logger.logWarning("Defined service's name in 'use-service' failed, using fallback ones!", null);

                if (service == null) {
                    if (fetcher.sendErrorMessages)
                        fetcher.logger.logWarning("Service '%s' doesn't exist! Skipping.", null, name);
                    continue;
                }

                if (service.getJsonPathToUuid() == null && service.getJsonPathToProperties() == null) {
                    if (fetcher.sendErrorMessages)
                        fetcher.logger.logWarning("Service '%s' doesn't have JSON path to unique ID not properties! Skipping.", null, name);
                    continue;
                }

                if (i >= 1) {
                    var rememberTime = fetcher.config.getFallbackServiceRememberTime() * 1000;
                    // Check if cached fallback service is expired
                    if (lastUsedServiceAt != 0 && lastUsedServiceAt + rememberTime >= System.currentTimeMillis()) {
                        lastUsedService = null;
                        lastUsedServiceAt = 0;
                    } else {
                        lastUsedService = name;
                        lastUsedServiceAt = System.currentTimeMillis();
                    }
                }

                if (!fetcher.fetchService(service))
                    break;
            }

            return fetcher.getOutput();
        }).whenComplete((result, ex) -> workers.remove(fetcher));
    }

    // Made a class because of how many variables I need to share...
    private static class Fetcher {
        final String username;
        final UUID uniqueId;
        final String remoteAddress;
        final SimplifiedLogger logger;
        final boolean cacheFetchedData;
        final boolean cacheDatabase;
        final boolean forceErrorMessages;
        final ObjectHolder<Message> messageHolder = new ObjectHolder<>(null);
        String disconnectMessage;
        boolean disconnect = true;
        boolean sendMessages;
        boolean sendErrorMessages;
        boolean sendDebugMessages;
        OnlinePlayerData fetchedPlayerData;
        long timeoutCounter;
        Map<String, Object> placeholders = new HashMap<>();
        Configuration.OnlineAuthenticationConfiguration config;

        public Fetcher(String username, UUID uniqueId, String remoteAddress, SimplifiedLogger logger,
                       boolean cacheFetchedData, boolean cacheDatabase, boolean forceErrorMessages) {
            this.config = PluginWrapper
                    .getCurrent()
                    .getConfiguration()
                    .getOnlineAuthentication();

            this.username = username;
            this.uniqueId = Objects.requireNonNullElse(uniqueId, Utils.offlineUniqueIdIfNull(username, uniqueId));
            this.remoteAddress = remoteAddress;
            this.logger = Objects.requireNonNullElse(logger, PluginWrapper.getCurrent());
            this.cacheFetchedData = cacheFetchedData;
            this.cacheDatabase = cacheDatabase;
            this.forceErrorMessages = forceErrorMessages;
        }

        void updateMessages(Configuration.ServiceConfiguration service) {
            sendDebugMessages = service != null && service.isDebugEnabled();
            sendMessages = config.isSendMessagesToConsole() || sendDebugMessages || forceErrorMessages;
            sendErrorMessages = config.isSendErrorMessagesToConsole() || sendDebugMessages || forceErrorMessages;
            if (service != null)
                disconnectMessage = service.getDefaultDisconnectMessage();
        }

        HttpRequest buildRequest(Configuration.ServiceConfiguration service, String prefix) {
            String queryString = Utils.replacePlaceholders(Utils.buildDataString(service.getQueryData()), placeholders);
            String endpoint = Utils.replacePlaceholders(service.getEndpoint(), placeholders);

            if (!queryString.isBlank())
                endpoint += "?" + queryString;

            if (sendDebugMessages)
                logger.logInfo(prefix, "(Debug) Preparing request to %s", endpoint);

            HttpRequest.Builder builder = HttpRequest.newBuilder();
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
                if (service.isDebugEnabled())
                    logger.logWarning(prefix, "(Debug) Timed out, current timeout value - %s, min timeout - %s", null, timeout, config.getMinTimeout());
                return null;
            }

            builder.timeout(Duration.ofMillis(Math.min(timeout, service.getTimeout())));
            return builder.build();
        }

        Configuration.ResponseHandlerConfiguration handleResponse(
                ResponseHandlerState state,
                Configuration.ServiceConfiguration service
        ) {
            messageHolder.set(null); // Reset
            var handler = service.executeResponseHandlers(state, placeholders);
            if (handler != null) {
                if (service.isDebugEnabled())
                    PluginWrapper.getCurrent().logInfo(
                            getPrefix(service.getName()),
                            "(Debug) Found valid response handler from the configuration, allowed join? => %s, disconnect message => %s",
                            handler.getAllowPlayerToJoin(), handler.getDisconnectMessage());
                if (handler.getAllowPlayerToJoin() != null && !handler.getAllowPlayerToJoin())
                    messageHolder.set(new Message(handler.getDisconnectMessage(), false));
            }
            return handler;
        }

        ResponseData sendRequest(HttpRequest request) {
            return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .handle((response, ex) ->
                            new ResponseData(
                                    response, ex instanceof CompletionException && ex.getCause() != null
                                    ? ex.getCause() : ex)).join();
        }

        List<ProfileProperty> fetchProperties(Configuration.ServiceConfiguration service, Object serviceResponseBody) {
            List<ProfileProperty> properties = null;
            var propertiesServices = new ArrayList<String>();
            if (service.canRetrieveProperties())
                propertiesServices.add(null);
            if (service.getRequestServicesForProperties() != null)
                propertiesServices.addAll(service.getRequestServicesForProperties());

            for (var propertyServiceName : propertiesServices) {
                var propertyService = config.getService(propertyServiceName);
                String prefix = getPrefix(propertyServiceName + "#properties");
                Object responseBody;
                if (propertyServiceName == null) {
                    if (serviceResponseBody == null)
                        continue;
                    responseBody = serviceResponseBody;
                    serviceResponseBody = null; // In case somehow getRequestServiceForProperties() has a null inside
                } else {
                    var start = System.currentTimeMillis();
                    var request = buildRequest(propertyService, prefix);
                    if (request == null) {
                        if (sendErrorMessages)
                            logger.logError(prefix,
                                    "Not enough time-out for sending this request! reached timeout %sms, min timeout %sms",
                                    null, timeoutCounter, config.getMinTimeout());
                        break;
                    }
                    var result = sendRequest(request);
                    var took = System.currentTimeMillis() - start;

                    timeoutCounter += took;

                    if (result.getException() != null) {
                        Utils.addExceptionPlaceholders(result.getException(), placeholders);
                        if (sendErrorMessages)
                            logger.logError(prefix, "Connection error (%s), failed to fetch properties from the service!",
                                    null, result.getException().getClass().getName());
                        if (sendDebugMessages)
                            logger.logError(result.getException().getMessage(), result.getException());
                        continue;
                    }

                    try {
                        responseBody = JsonParser.parseString(result.getResponse().body());
                    } catch (Exception ex) {
                        if (sendErrorMessages)
                            logger.logError(prefix, "Failed to parse JSON from properties service!",
                                    ex);
                        continue;
                    }
                }

                if (responseBody instanceof JsonElement element) {
                    String path = (propertyService == null ? service : propertyService).getJsonPathToProperties();
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
                            } else {
                                if (sendErrorMessages)
                                    logger.logError(prefix, "Invalid JSON", null);
                            }
                        } catch (Exception ex) {
                            if (sendErrorMessages)
                                logger.logError(prefix, "Failed to fetch profile's properties!", null);
                            if (sendDebugMessages)
                                logger.logError("Failed to fetch profile's properties!", ex);
                        }
                    } else {
                        if (sendErrorMessages)
                            logger.logError(prefix, "JSON path to UUID is not defined!", null);
                    }
                }
            }

            if (properties == null && sendErrorMessages)
                    logger.logWarning(getPrefix(service.getName()), "Failed to retrieve properties (even if fallbacks were used)", null);

            return properties;
        }

        boolean fetchService(Configuration.ServiceConfiguration service) {
            placeholders.clear();
            placeholders.put("username", username);
            placeholders.put("uuid", uniqueId.toString());

            updateMessages(service);
            Configuration.ResponseHandlerConfiguration handler;

            var prefix = getPrefix(service.getName());
            var database = PluginWrapper.getCurrent().getDatabase();

            if (service.isForProperties()) {
                if (sendMessages)
                    logger.logWarning("Can't use '%s' service as it's only used for getting properties!", null, service.getName());
                return true;
            }

            if (!service.isForUniqueId()) {
                if (sendMessages)
                    logger.logWarning("Can't use '%s' service as it doesn't have JSON path to the player's unique id!", null, service.getName());
                return true;
            }

            try {
                if (sendDebugMessages)
                    logger.logInfo(prefix, "(Debug) Player %s original UUID - %s", username, uniqueId);

                HttpRequest request = buildRequest(service, prefix);
                if (request == null) {
                    disconnectMessage = service.getServiceTimeoutDisconnectMessage();
                    return false;
                }

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
                        logger.logError(prefix, "Connection error (%s), failed to fetch UUID from the service!",
                                null, result.getException().getClass().getName());
                    if (sendDebugMessages)
                        logger.logError(result.getException().getMessage(), result.getException());

                    return !shouldDisconnect;
                }
                var response = result.getResponse();
                long took = System.currentTimeMillis() - start;

                placeholders.put("http.url", response.uri().toString());
                placeholders.put("http.status", String.valueOf(response.statusCode()));
                placeholders.put("took", String.valueOf(took));
                placeholders.put("max-timeout", String.valueOf(config.getMaxTimeout()));
                placeholders.put("min-timeout", String.valueOf(config.getMinTimeout()));
                placeholders.put("timeout-counter", String.valueOf(timeoutCounter));
                placeholders.put("timeout-left", String.valueOf(timeoutCounter));

                timeoutCounter += took;
                if (sendDebugMessages)
                    logger.logInfo(prefix, "(Debug) Took %sms (%s/%sms - added up timeout/max timeout) to fetch data.", took, timeoutCounter, config.getMaxTimeout());
                if (!service.isIgnoreStatusCode() && response.statusCode() != service.getExpectStatusCode()) {
                    if (sendErrorMessages)
                        logger.logError(prefix, "Returned wrong HTTP status code! Got %s, expected %s.",
                                null, response.statusCode(), service.getExpectStatusCode());
                    disconnectMessage = service.getServiceBadStatusDisconnectMessage();
                    return service.getUseFallbacks().contains(FallbackUsage.ON_BAD_STATUS);
                }

                Object responseBody;
                try {
                    responseBody = JsonParser.parseString(response.body());
                } catch (Exception ex) {
                    responseBody = response.body();
                    if (sendDebugMessages)
                        logger.logInfo(prefix, "(Debug) Body does not have valid JSON, parsing as text => %s",
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

                handler = handleResponse(ResponseHandlerState.AFTER_REQUEST, service);
                if (handler != null) {
                    if (handler.getAllowPlayerToJoin()) {
                        disconnect = false;
                        return false;
                    }
                    if (messageHolder.containsValue())
                        return false;
                }

                UUID fetchedUniqueId = null;
                if (service.getJsonPathToUuid() != null) {
                    String fetchedUniqueIdString = null;
                    try {
                        if (responseBody instanceof JsonElement element)
                            fetchedUniqueIdString = Utils.getJsonValue(element, service.getJsonPathToUuid()).getAsString();
                        else
                            fetchedUniqueIdString = responseBody.toString();
                    } catch (Exception ex) {
                        Utils.addExceptionPlaceholders(ex, placeholders);
                        if (sendErrorMessages)
                            logger.logError(prefix, "Failed, invalid JSON path to unique ID - %s", null, service.getJsonPathToUuid());
                        if (sendDebugMessages)
                            logger.logError(ex.getMessage(), ex);
                    }


                    handler = handleResponse(ResponseHandlerState.AFTER_UUID, service);
                    if (handler != null) {
                        if (handler.getAllowPlayerToJoin()) {
                            disconnect = false;
                            return false;
                        }
                        if (messageHolder.containsValue())
                            return false;
                    }

                    if (fetchedUniqueIdString == null) {
                        disconnectMessage = service.getBadUuidDisconnectMessage();
                        return service.getUseFallbacks().contains(FallbackUsage.ON_BAD_UUID_PATH);
                    }

                    placeholders.put("new-uuid", fetchedUniqueIdString);

                    try {
                        fetchedUniqueId = Utils.toUniqueId(fetchedUniqueIdString);
                    } catch (Exception ex) {
                        Utils.addExceptionPlaceholders(ex, placeholders);
                        if (sendErrorMessages)
                            logger.logError(prefix, "Failed to convert '%s' to UUID!", null, fetchedUniqueIdString.replaceAll("\\R+", ""));
                        if (sendDebugMessages)
                            logger.logError(ex.getMessage(), ex);
                        disconnectMessage = service.getBadUuidDisconnectMessage();
                        return service.getUseFallbacks().contains(FallbackUsage.ON_INVALID_UUID);
                    }
                }

                List<ProfileProperty> properties = fetchProperties(service, responseBody);
                if (sendMessages && properties != null)
                    logger.logInfo(prefix, "Properties successfully fetched for %s", username);

                if (config.getCaching().isEnabled() && database.getConfiguration().isEnabled() && database.isDriverRunning()) {
                    fetchedPlayerData = new OnlinePlayerData(uniqueId, fetchedUniqueId, properties, remoteAddress);
                    if (cacheDatabase)
                        database.storeOnlinePlayerCache(fetchedPlayerData);
                } else {
                    fetchedPlayerData = new OnlinePlayerData(uniqueId, fetchedUniqueId, properties, null);
                }

                if (sendMessages && fetchedUniqueId != null)
                    logger.logInfo(prefix, "Unique ID successfully fetched for %s => %s (took %s/%sms)", username, fetchedUniqueId, took, timeoutCounter);

                if (cacheFetchedData)
                    fetchedPlayerDataMap.put(uniqueId, fetchedPlayerData);
                disconnect = false;
                return false;
            } catch (Exception ex) {
                Utils.addExceptionPlaceholders(ex, placeholders);
                if (sendErrorMessages)
                    logger.logError(prefix, "Unknown error, failed to fetch unique ID from the service!", ex);
                if (sendDebugMessages)
                    logger.logError(ex.getMessage(), ex);
                disconnectMessage = service.getUnknownErrorDisconnectMessage();
                if (!service.getUseFallbacks().contains(FallbackUsage.ON_UNKNOWN_ERROR))
                    return false;
            }

            return true;
        }

        public BiObjectHolder<OnlinePlayerData, Message> getOutput() {
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
            return new BiObjectHolder<>(fetchedPlayerData, message);
        }
    }
}
