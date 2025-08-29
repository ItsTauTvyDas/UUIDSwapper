package me.itstautvydas.uuidswapper.service;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import me.itstautvydas.uuidswapper.Utils;
import me.itstautvydas.uuidswapper.config.Configuration;
import me.itstautvydas.uuidswapper.crossplatform.PluginWrapper;
import me.itstautvydas.uuidswapper.data.*;
import me.itstautvydas.uuidswapper.enums.FallbackUsage;
import me.itstautvydas.uuidswapper.enums.ServiceStateEvent;
import me.itstautvydas.uuidswapper.exception.BreakContinuationException;
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
import java.util.concurrent.TimeUnit;

public class PlayerDataFetcher {
    private static final Map<UUID, OnlinePlayerData> fetchedPlayerDataMap = new ConcurrentHashMap<>();
    private static final Map<UUID, PlayerData> pretendMap = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> throttledConnections = new ConcurrentHashMap<>();
    private static final HttpClient client;

    private static volatile String lastUsedService;
    private static volatile long lastUsedServiceAt;

    static {
        client = HttpClient.newHttpClient();
    }

    private final String username;
    private final UUID uniqueId;
    private final String remoteAddress;
    private final SimplifiedLogger logger;
    private final boolean cacheFetchedData;
    private final boolean cacheDatabase;
    private final boolean forceErrorMessages;
    private String disconnectMessage;
    private boolean disconnect = true;
    private boolean sendMessages;
    private boolean sendErrorMessages;
    private boolean sendDebugMessages;
    private OnlinePlayerData fetchedPlayerData;
    private long totalExecutionTime;
    private final Map<String, Object> placeholders = new HashMap<>();
    private final Configuration.OnlineAuthenticationConfiguration config;
    private Configuration.ServiceConfiguration service;
    private String servicePrefix;
    private boolean applyProperties = true;
    private boolean requireProperties;

    public PlayerDataFetcher(String username, UUID uniqueId, String remoteAddress, SimplifiedLogger logger,
                             boolean cacheFetchedData, boolean cacheDatabase, boolean forceErrorMessages) {
        this.config = PluginWrapper.getCurrent().getConfiguration().getOnlineAuthentication();
        this.username = username;
        this.uniqueId = Objects.requireNonNullElse(uniqueId, Utils.offlineUniqueIdIfNull(username, uniqueId));
        this.remoteAddress = remoteAddress;
        this.logger = Objects.requireNonNullElse(logger, PluginWrapper.getCurrent());
        this.cacheFetchedData = cacheFetchedData;
        this.cacheDatabase = cacheDatabase;
        this.forceErrorMessages = forceErrorMessages;
        updateMessages();
    }

    public static void setPlayerProperties(UUID originalUniqueId, List<ProfilePropertyWrapper> properties) {
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
            var data = fetchPlayerData(username, originalUniqueId, "", false, false, true, logger).join();
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
        if (name != null)
            return "PlayerDataFetcher/" + name;
        return "PlayerDataFetcher";
    }

    public static void clearCache() {
        lastUsedServiceAt = 0;
        lastUsedService = null;
    }

    @NotNull
    public static CompletableFuture<BiObjectHolder<OnlinePlayerData, Message>> fetchPlayerData(
            @NotNull String username,
            @Nullable UUID uniqueId,
            @NotNull String remoteAddress,
            boolean cacheFetchedData,
            boolean cacheDatabase,
            boolean forceErrorMessages,
            @Nullable SimplifiedLogger logger
    ) {
        final var fetcher = new PlayerDataFetcher(username, uniqueId, remoteAddress, logger,
                cacheFetchedData, cacheDatabase, forceErrorMessages);

        return CompletableFuture.supplyAsync(() -> {
            var services = new ArrayList<>(fetcher.config.getFallbackServices());
            services.add(0, fetcher.config.getServiceName());

            // Make last used (successful) service first
            if (lastUsedService != null) {
                var rememberTime = fetcher.config.getFallbackServiceRememberTime();
                if (rememberTime != -1 && lastUsedServiceAt + rememberTime >= System.currentTimeMillis()) {
                    lastUsedService = null;
                    lastUsedServiceAt = 0;
                } else {
                    services.remove(0); // Remove use-service because it previously failed
                    services.remove(lastUsedService); // Remove because it will duplicate
                    services.add(0, lastUsedService);
                }
            }

            var prefix = getPrefix(null);

            if (fetcher.sendMessages)
                fetcher.logger.logInfo(prefix, "Player %s original unique ID is %s", username, uniqueId);

            int ignored = 0;
            for (int i = 0; i < services.size(); i++) {
                String name = services.get(i);
                fetcher.setService(name);

                if (i - ignored == 1 && fetcher.sendErrorMessages)
                    fetcher.logger.logWarning(prefix, "Defined service's name in 'use-service' failed, using fallback ones!", null);

                if (fetcher.service == null) {
                    if (fetcher.sendMessages)
                        fetcher.logger.logWarning(fetcher.servicePrefix, "I do not exist in this universe, I shall vanish...", null, name);
                    continue;
                }

                if (!fetcher.service.isEnabled()) {
                    if (fetcher.sendDebugMessages)
                        fetcher.logger.logInfo(fetcher.servicePrefix, "(Debug) Service is disabled, skipping.");
                    ignored++;
                    continue;
                }

                if (fetcher.service.getJsonPathToUuid() == null && fetcher.service.getJsonPathToProperties() == null) {
                    if (fetcher.sendErrorMessages)
                        fetcher.logger.logWarning(prefix, "Service '%s' doesn't have JSON path to unique ID not properties! Skipping.", null, name);
                    ignored++;
                    continue;
                }

                try {
                    if (!fetcher.service.canSendRequest()) {
                        if (fetcher.sendErrorMessages)
                            fetcher.logger.logWarning(fetcher.servicePrefix, "Service got rate-limited!", null, name);
                        if (!fetcher.disconnectCheckFallback(fetcher.service.getRateLimitedDisconnectMessage(), FallbackUsage.ON_SERVICE_RATE_LIMITED))
                            break;
                    }
                } catch (BreakContinuationException ex) {
                    break;
                }

                try {
                    if (!fetcher.fetchService())
                        break;
                } catch (BreakContinuationException ex) {
                    if (fetcher.sendMessages)
                        fetcher.logger.logInfo(prefix, "(Debug) Response handler did not allow service to finish: %s", ex.getMessage());
                    break;
                }
            }

            if (fetcher.sendMessages)
                fetcher.logger.logInfo(prefix, "Took %s/%sms to fetch data.", fetcher.totalExecutionTime, fetcher.config.getMaxTimeout());

            if (fetcher.config.getServiceConnectionThrottle() > 0)
                throttledConnections.put(uniqueId, System.currentTimeMillis());

            return fetcher.getOutput();
        });
    }

    public void setService(String name) {
        service = config.getService(name);
        if (service == null)
            return;
        servicePrefix = getPrefix(name);
        updateMessages();
        disconnectNoThrow(null);
        requireProperties = service.isRequireProperties();
    }

    public boolean isFallback() {
        return config.getFallbackServices().contains(service.getName());
    }

    private void disconnectNoThrow(String message) {
        disconnect = true;
        disconnectMessage = message;
    }

    private void disconnect(String message) throws BreakContinuationException {
        disconnectNoThrow(message);
        throw new BreakContinuationException("Disconnect - " + message);
    }

    // false - break
    // true - continue
    private boolean disconnectCheckFallback(String message, FallbackUsage fallbackUsage) throws BreakContinuationException {
        var useFallback = service.getUseFallbacks().contains(fallbackUsage);
        // TODO
        if (fallbackUsage != FallbackUsage.ON_SUB_SERVICE_RATE_LIMITED) {
            disconnectNoThrow(message);
            if (useFallback)
                handleResponse(ServiceStateEvent.PRE_FALLBACK_USE);
        }
        return useFallback;
    }

    private void updateMessages() {
        sendDebugMessages = service != null && service.isDebugEnabled();
        sendMessages = config.isSendMessagesToConsole() || sendDebugMessages || forceErrorMessages;
        sendErrorMessages = config.isSendErrorMessagesToConsole() || sendDebugMessages || forceErrorMessages;
    }

    private void updateTotalExecutionTime() {
        placeholders.put("total-execution-time", totalExecutionTime);
        placeholders.put("total-took", totalExecutionTime);
        placeholders.put("total-execution-time-left", config.getMaxTimeout() - totalExecutionTime);
        placeholders.put("total-took-left", config.getMaxTimeout() - totalExecutionTime);
    }

    private HttpRequest buildRequest(Configuration.ServiceConfiguration service, String prefix) {
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

        for (var header : service.getHeaders().entrySet())
            builder.setHeader(
                    Utils.replacePlaceholders(header.getKey(), placeholders),
                    Utils.replacePlaceholders(header.getValue(), placeholders)
            );

        var timeout = config.getMaxTimeout() - totalExecutionTime;
        if (timeout <= config.getMinTimeout()) {
            if (sendErrorMessages)
                logger.logWarning(prefix, "Not enough timed out, time-out left - %s, min timeout - %s", null, timeout, config.getMinTimeout());
            return null;
        }

        builder.timeout(Duration.ofMillis(Math.min(timeout, service.getTimeout())));
        return builder.build();
    }

    private void handleResponse(ServiceStateEvent event) throws BreakContinuationException {
        var handler = service.executeResponseHandlers(event, placeholders);
        if (handler != null) {
            if (sendDebugMessages)
                PluginWrapper.getCurrent().logInfo(
                        servicePrefix,
                        "(Debug) Found valid response handler from the configuration ",
                        handler.resultToString()
                );

            if (handler.getMessageToConsole() != null)
                switch (handler.getConsoleMessageType()) {
                    case INFO -> logger.logInfo(handler.getMessageToConsole());
                    case WARNING -> logger.logWarning(handler.getMessageToConsole(), null);
                    case ERROR -> logger.logError(handler.getMessageToConsole(), null);
                }

            applyProperties = handler.isApplyProperties();
            if (handler.getRequireProperties() != null)
                requireProperties = handler.getRequireProperties();

            if (handler.getAllowPlayerToJoin() != null) {
                if (handler.getAllowPlayerToJoin()) {
                    disconnect = false;
                } else {
                    disconnectNoThrow(handler.getDisconnectMessage());
                }
                throw new BreakContinuationException("getAllowPlayerToJoin was not null, disconnect message if any - " + handler.getDisconnectMessage());
            }
        }
    }

    private ResponseData sendRequest(HttpRequest request, ObjectHolder<Long> timeTook) {
        Objects.requireNonNull(request);
        var current = System.nanoTime();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .handle((response, ex) -> {
                    timeTook.set(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - current));
                    return new ResponseData(
                            response, ex instanceof CompletionException && ex.getCause() != null
                            ? ex.getCause() : ex);
                }).join();
    }

    private List<ProfilePropertyWrapper> fetchProperties(Object serviceResponseBody) throws BreakContinuationException {
        Objects.requireNonNull(service);

        List<ProfilePropertyWrapper> properties = null;
        var propertiesServices = new ArrayList<String>();
        if (service.canRetrieveProperties())
            propertiesServices.add(null);
        if (service.getRequestServicesForProperties() != null)
            propertiesServices.addAll(service.getRequestServicesForProperties());

        String prefix = null;
        long fetchTook = -1;
        for (var propertyServiceName : propertiesServices) {
            var propertyService = config.getService(propertyServiceName);
            Object responseBody;
            if (propertyServiceName == null) {
                if (serviceResponseBody == null)
                    continue;
                prefix = servicePrefix;
                responseBody = serviceResponseBody;
                serviceResponseBody = null; // In case somehow getRequestServiceForProperties() has a null inside
            } else {
                prefix = getPrefix(propertyServiceName + "#properties");
                if (!propertyService.canSendRequest()) {
                    if (sendMessages)
                        logger.logWarning(prefix, "Service got rate-limited!", null);
                    if (!disconnectCheckFallback(propertyService.getRateLimitedDisconnectMessage(),
                            FallbackUsage.ON_SUB_SERVICE_RATE_LIMITED))
                        break;
                }
                var request = buildRequest(propertyService, prefix);
                if (request == null) {
                    if (sendErrorMessages)
                        logger.logError(prefix,
                                "Not enough time-out for sending this request! reached timeout %sms, min timeout %sms",
                                null, totalExecutionTime, config.getMinTimeout());
                    if (!disconnectCheckFallback(propertyService.getTimeoutDisconnectMessage(),
                            FallbackUsage.ON_SUB_SERVICE_TIMEOUT))
                        break;
                }
                var took = new ObjectHolder<Long>(null);
                var result = sendRequest(request, took);
                fetchTook = took.get();

                placeholders.put("sub-execution-time", fetchTook);
                placeholders.put("sub-took-time", fetchTook);

                if (sendDebugMessages)
                    logger.logInfo(prefix, "(Debug) Took %sms to fetch data.", fetchTook);

                totalExecutionTime += fetchTook;
                updateTotalExecutionTime();

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
                                    .map(x -> Utils.DEFAULT_GSON.fromJson(
                                            x.getAsJsonObject(),
                                            ProfilePropertyWrapper.class
                                    ))
                                    .toList();
                            break;
                        } else if (propertiesJsonElement.isJsonObject()) {
                            properties = List.of(Utils.DEFAULT_GSON.fromJson(
                                    propertiesJsonElement.getAsJsonObject(),
                                    ProfilePropertyWrapper.class
                            ));
                            break;
                        } else {
                            if (sendErrorMessages)
                                logger.logError(prefix, "Invalid JSON", null);
                        }
                    } catch (Exception ex) {
                        if (sendErrorMessages)
                            logger.logError(prefix, "Failed to get profile's properties! (Invalid properties?)", sendDebugMessages ? ex : null);
                    }
                } else {
                    if (sendErrorMessages)
                        logger.logError(prefix, "JSON path to properties is not defined!", null);
                }
            }
        }

        if (properties == null && sendErrorMessages)
            logger.logWarning(servicePrefix, "Failed to retrieve properties (even if fallbacks were used)", null);
        if (properties != null && sendMessages)
            logger.logInfo(prefix, "Properties successfully fetched for %s (took %s/%sms)", username, fetchTook, config.getMaxTimeout());
        return properties;
    }

    public boolean fetchService() throws BreakContinuationException {
        Objects.requireNonNull(service);
        var database = PluginWrapper.getCurrent().getDatabase();
        placeholders.clear();

        placeholders.putAll(service.getCustomPlaceholders());
        placeholders.put("username", username);
        placeholders.put("uuid", uniqueId.toString());
        placeholders.put("database-running", database.isDriverRunning());
        placeholders.put("service-name", service.getName());
        placeholders.put("max-timeout", config.getMaxTimeout());
        placeholders.put("min-timeout", config.getMinTimeout());

        try {
            HttpRequest request = buildRequest(service, servicePrefix);
            if (request == null)
                return disconnectCheckFallback(service.getTimeoutDisconnectMessage(), FallbackUsage.ON_SERVICE_TIMEOUT);
//                disconnect(service.getTimeoutDisconnectMessage());

            handleResponse(ServiceStateEvent.PRE_REQUEST);

            var took = new ObjectHolder<Long>(null);
            var result = sendRequest(request, took);
            if (result.getException() != null) {
                Utils.addExceptionPlaceholders(result.getException(), placeholders);
                if (sendErrorMessages)
                    logger.logError(servicePrefix, "Connection error (%s), failed to fetch data from the service!",
                            null, result.getException().getClass().getName());
                if (sendDebugMessages)
                    logger.logError(result.getException().getMessage(), result.getException());

                if (result.getException() instanceof HttpConnectTimeoutException)
                    return disconnectCheckFallback(service.getTimeoutDisconnectMessage(), FallbackUsage.ON_SERVICE_TIMEOUT);
                // Might handle more exceptions in the future
                return disconnectCheckFallback(service.getConnectionErrorDisconnectMessage(), FallbackUsage.ON_CONNECTION_ERROR);
            }
            var response = result.getResponse();

            totalExecutionTime += took.get();

            placeholders.put("http.url", response.uri().toString());
            placeholders.put("http.status", response.statusCode());
            placeholders.put("execution-time", took.get());
            placeholders.put("took", took.get());
            updateTotalExecutionTime();

            handleResponse(ServiceStateEvent.POST_REQUEST);

            if (sendDebugMessages)
                logger.logInfo(servicePrefix, "(Debug) Took %sms to fetch data.", took.get());

            if (service.getExpectStatusCode() != null && response.statusCode() != service.getExpectStatusCode()) {
                if (sendErrorMessages)
                    logger.logError(servicePrefix, "Returned wrong HTTP status code! Got %s, expected %s.",
                            null, response.statusCode(), service.getExpectStatusCode());
                var message = service.getBadStatusDisconnectMessage();
                var customMessage = service.getCustomStatusCodeDisconnectMessages().get(String.valueOf(response.statusCode()));
                if (customMessage != null)
                    message = customMessage.toString();
                return disconnectCheckFallback(message, FallbackUsage.ON_BAD_STATUS);
            }

            for (var entry : response.headers().map().entrySet())
                placeholders.put("http.header.str" + entry.getKey().toLowerCase(), String.join(",", entry.getValue()));
            for (var entry : response.headers().map().entrySet())
                placeholders.put("http.header.raw" + entry.getKey().toLowerCase(), entry.getValue());

            Object responseBody;
            try {
                responseBody = JsonParser.parseString(response.body());
            } catch (Exception ex) {
                responseBody = response.body();
                if (sendDebugMessages)
                    logger.logInfo(servicePrefix, "(Debug) Body does not have valid JSON, parsing as text => %s",
                            responseBody.toString().replaceAll("\\R+", ""));
            }

            if (responseBody instanceof JsonElement element)
                placeholders.putAll(Utils.extractJsonPaths("response.", element));
            else
                placeholders.put("response", responseBody.toString());

            UUID rewriteUniqueId = null;
            if (service.getJsonPathToUuid() != null) {
                String fetchedUniqueId = null;
                try {
                    if (responseBody instanceof JsonElement element)
                        fetchedUniqueId = Utils.getJsonValue(element, service.getJsonPathToUuid()).getAsString();
                    else
                        fetchedUniqueId = responseBody.toString();
                } catch (Exception ex) {
                    Utils.addExceptionPlaceholders(ex, placeholders);
                    if (sendErrorMessages)
                        logger.logError(servicePrefix, "Failed, invalid JSON path to unique ID - %s", null, service.getJsonPathToUuid());
                    if (sendDebugMessages)
                        logger.logError(ex.getMessage(), ex);
                }

                if (fetchedUniqueId == null)
                    return disconnectCheckFallback(service.getBadUniqueIdDisconnectMessage(), FallbackUsage.ON_BAD_UUID_PATH);

                try {
                    rewriteUniqueId = Utils.toUniqueId(fetchedUniqueId);

                    placeholders.put("fetched-uuid", rewriteUniqueId.toString());
                    placeholders.put("fetched-dashless-uuid", rewriteUniqueId.toString().replace("-", ""));

                    handleResponse(ServiceStateEvent.FETCHED_UUID);
                } catch (IllegalArgumentException ex) {
                    Utils.addExceptionPlaceholders(ex, placeholders);
                    if (sendErrorMessages)
                        logger.logError(servicePrefix, "Failed to convert '%s' to UUID!", null, fetchedUniqueId.replaceAll("\\R+", ""));
                    if (sendDebugMessages)
                        logger.logError(ex.getMessage(), ex);
                    return disconnectCheckFallback(service.getBadUniqueIdDisconnectMessage(), FallbackUsage.ON_INVALID_UUID);
                }
            }

            handleResponse(ServiceStateEvent.PRE_PROPERTIES_FETCH);

            List<ProfilePropertyWrapper> properties = null;
            if (applyProperties || requireProperties) {
                properties = fetchProperties(responseBody);
                if (properties != null)
                    handleResponse(ServiceStateEvent.FETCHED_PROPERTIES);
                else if (requireProperties)
                    return disconnectCheckFallback(service.getBadPropertiesDisconnectMessage(), FallbackUsage.ON_BAD_PROPERTIES);
            }

            boolean cache = rewriteUniqueId == null;
            if (cache)
                rewriteUniqueId = uniqueId;

            if (cache && config.getCaching().isEnabled() && database.getConfiguration().isEnabled() && database.isDriverRunning()) {
                fetchedPlayerData = new OnlinePlayerData(uniqueId, rewriteUniqueId, properties, remoteAddress);
                if (cacheDatabase)
                    database.storeOnlinePlayerCache(fetchedPlayerData);
            } else {
                fetchedPlayerData = new OnlinePlayerData(uniqueId, rewriteUniqueId, properties, null);
            }

            if (sendMessages && !rewriteUniqueId.equals(uniqueId))
                logger.logInfo(servicePrefix, "Unique ID successfully fetched for %s => %s (took %s/%sms)", username, rewriteUniqueId, took, totalExecutionTime);

            if (cacheFetchedData)
                fetchedPlayerDataMap.put(uniqueId, fetchedPlayerData);
            disconnect = false;
            handleResponse(ServiceStateEvent.PLAYER_DATA_FETCHED);
            return false;
        } catch (Exception ex) {
            Utils.addExceptionPlaceholders(ex, placeholders);
            if (sendErrorMessages)
                logger.logError(servicePrefix, "Unknown error, failed to fetch unique ID from the service!", ex);
            if (sendDebugMessages)
                logger.logError(ex.getMessage(), ex);
            return disconnectCheckFallback(service.getUnknownErrorDisconnectMessage(), FallbackUsage.ON_UNKNOWN_ERROR);
        }
    }

    public BiObjectHolder<OnlinePlayerData, Message> getOutput() {
        Message message = null;
        if (disconnect) {
            if (disconnectMessage == null)
                disconnectMessage = config.getServiceDefaults().getDefaultDisconnectMessage();
            if (disconnectMessage != null) {
                message = new Message(disconnectMessage, false).replacePlaceholders(placeholders);
            } else
                message = new Message(Utils.GENERIC_DISCONNECT_MESSAGE_ID, true);
        } else {
            if (isFallback() && lastUsedService == null && config.getFallbackServiceRememberTime() != 0) {
                lastUsedService = service.getName();
                lastUsedServiceAt = System.currentTimeMillis();
            }
        }
        return new BiObjectHolder<>(fetchedPlayerData, message);
    }
}
