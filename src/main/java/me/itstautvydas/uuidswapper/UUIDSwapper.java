package me.itstautvydas.uuidswapper;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.inject.Inject;
import com.moandjiezana.toml.Toml;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.player.GameProfileRequestEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.util.UuidUtils;
import me.itstautvydas.BuildConstants;
import me.itstautvydas.uuidswapper.config.Configuration;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Plugin(id = "uuid-swapper",
        name = "UUIDSwapper",
        version = BuildConstants.VERSION,
        description = "Swap player names or UUID, use online UUIDs for offline mode!",
        url = "https://itstautvydas.me",
        authors = {"ItsTauTvyDas"})
public class UUIDSwapper {

    private Configuration config;
    private final Path dataDirectory;
    private final Logger logger;
    private final ProxyServer server;

    private String lastAPIUsed;
    private long lastAPIUsedWhen;

    private final HttpClient client;

    private final Map<UUID, UUID> fetchedUuids = new HashMap<>();

    @Inject
    public UUIDSwapper(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) throws IOException {
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.server = server;
        this.client = HttpClient.newHttpClient();
        reloadConfig();
    }

    private void log(Map.Entry<String, Object> entry) {
        logger.info("# {} => {}", entry.getKey(), entry.getValue());
    }

    public void reloadConfig() throws IOException {
        if (Files.notExists(dataDirectory))
            Files.createDirectories(dataDirectory);

        var configFile = dataDirectory.resolve("config.toml");
        if (Files.notExists(configFile)) {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream("config.toml")) {
                if (in != null) {
                    logger.info("Copying new configuration...");
                    Files.copy(in, configFile);
                }
            }
        }

        var toml = new Toml().read(configFile.toFile());
        config = new Configuration(toml, server);

        logger.info("Configuration loaded.");
        if (toml.getBoolean("online-uuids.enabled", false) && server.getConfiguration().isOnlineMode())
            logger.warn("You are trying to use online UUIDs while being in online mode! This will be disabled.");
        logger.info("Using online UUIDs => {}", config.areOnlineUuidEnabled());
        logger.info("Loaded {} swapped UUIDs.", config.getSwappedUuids().size());
        for (var entry : config.getSwappedUuids().entrySet())
            log(entry);
        logger.info("Loaded {} custom player usernames.", config.getCustomPlayerNames().size());
        for (var entry : config.getCustomPlayerNames().entrySet())
            log(entry);
    }

    @Subscribe
    public EventTask onPlayerPreLogin(PreLoginEvent event) {
        if (server.getConfiguration().isOnlineMode()) {
            if (!config.isForcedOfflineModeEnabled())
                return null;
            if (config.isForcedOfflineModeSetByDefault() && !Utils.containsPlayer(config.getForcedOfflineModeExceptions(), event.getUsername(), event.getUniqueId()))
                event.setResult(PreLoginEvent.PreLoginComponentResult.forceOfflineMode());
            else if (!config.isForcedOfflineModeSetByDefault() && Utils.containsPlayer(config.getForcedOfflineModeExceptions(), event.getUsername(), event.getUniqueId()))
                event.setResult(PreLoginEvent.PreLoginComponentResult.forceOfflineMode());
            return null;
        } else {
            UUID originalUUID = event.getUniqueId() == null ?
                    UuidUtils.generateOfflinePlayerUuid(event.getUsername()) : event.getUniqueId();  // Pre-1.20.1
            if (!config.areOnlineUuidEnabled())
                return null;
            return EventTask.async(() -> {
                long timeoutCounter = 0;
                long fallbackAPIRememberTime = config.getFallbackApiRememberTime() * 1000;

                var apis = new ArrayList<String>();
                apis.add(config.getApiName());
                apis.addAll(config.getApiFallbacks());

                // Make last used (successful) API first
                if (lastAPIUsed != null) {
                    apis.remove(0); // Remove use-api because it previously failed
                    apis.remove(lastAPIUsed); // Remove because it will duplicate
                    apis.add(0, lastAPIUsed);
                }

                boolean disconnect = true;
                String disconnectMessage = null;

                for (int i = 0; i < apis.size(); i++) {
                    String name = apis.get(i);
                    Configuration.ApiConfiguration api = config.getApi(name);

                    if (i == 1) {
                        logger.warn("Defined API in 'use-api' failed, using fallback ones!");
                        if (lastAPIUsedWhen != 0 && lastAPIUsedWhen + fallbackAPIRememberTime >= System.currentTimeMillis()) {
                            lastAPIUsed = null;
                            lastAPIUsedWhen = 0;
                        } else {
                            lastAPIUsed = name;
                            lastAPIUsedWhen = System.currentTimeMillis();
                        }
                    }

                    if (api == null) {
                        logger.warn("API called '{}' does not exist! Skipping", name);
                        return;
                    }

                    disconnectMessage = api.getDefaultDisconnectMessage();
                    String prefix = "[" + name + "]:";

                    try {
                        String queryString = Utils.buildDataString(api.getRequestQueryData());
                        Map<String, Object> placeholders = new HashMap<>();
                        placeholders.put("username", event.getUsername());
                        placeholders.put("uuid", originalUUID.toString());
                        if (api.isDebugEnabled())
                            logger.info("{} {}'s original UUID - {}", prefix, event.getUsername(), originalUUID);
                        String endpoint = Utils.replacePlaceholders(api.getEndpoint(), placeholders);
                        HttpRequest.Builder builder = HttpRequest.newBuilder();
                        if (!queryString.isEmpty())
                            builder.uri(URI.create(endpoint + "?" + queryString));
                        else
                            builder.uri(URI.create(endpoint));
                        if (api.getMethod().equalsIgnoreCase("POST"))
                            builder.POST(HttpRequest.BodyPublishers.ofString(Utils.buildDataString(api.getRequestPostData())));
                        for (var header : api.getRequestHeaders().entrySet())
                            builder.setHeader(header.getKey(), header.getValue().toString());
                        long timeout = config.getMaxTimeout() - timeoutCounter;
                        if (timeout <= config.getMinTimeout()) {
                            if (api.isDebugEnabled())
                                logger.warn("{} Timed out, current timeout value - {}, min timeout - {}", prefix, timeout, config.getMinTimeout());
                            disconnectMessage = api.getServiceTimedOutDisconnectMessage();
                            break;
                        }
                        builder.timeout(Duration.ofMillis(Math.min(timeout, api.getTimeout())));
                        HttpRequest request = builder.build();
                        long start = System.currentTimeMillis();
                        var result = client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).join();
                        long took = System.currentTimeMillis() - start;
                        timeoutCounter += took;
                        if (api.isDebugEnabled())
                            logger.info("{} Took {}ms ({}/{} - added up timeout/max timeout) to fetch data.", prefix, took, timeoutCounter, config.getMaxTimeout());
                        if (result.statusCode() != api.getExpectedStatusCode()) {
                            if (api.shouldDisconnectOnServiceDown()) {
                                disconnectMessage = api.getServiceDownDisconnectMessage();
                                break;
                            }
                            continue;
                        }
                        Object responseBody;
                        try {
                            responseBody = JsonParser.parseString(result.body());
                        } catch (Exception ex) {
                            responseBody = result.body();
                            if (api.isDebugEnabled()) {
                                logger.info("{} Body does not have valid JSON, parsing as text => {}", prefix, responseBody);
                            }
                        }
                        placeholders.put("http.url", result.uri().toString());
                        placeholders.put("http.code", Integer.toString(result.statusCode()));
                        for (var entry : result.headers().map().entrySet())
                            placeholders.put("http.header.str" + entry.getKey().toLowerCase(), String.join(",", entry.getValue()));
                        for (var entry : result.headers().map().entrySet())
                            placeholders.put("http.header.raw" + entry.getKey().toLowerCase(), entry.getValue());
                        if (responseBody instanceof JsonElement element)
                            placeholders.putAll(Utils.extractJsonPaths("response.", element));
                        else
                            placeholders.put("response", responseBody.toString());
                        String uuidString;
                        try {
                            if (responseBody instanceof JsonElement element)
                                uuidString = Utils.getJsonValue(element, api.getPathToUuid());
                            else
                                uuidString = responseBody.toString();
                        } catch (Exception ex) {
                            logger.error("{} Failed, invalid JSON path to UUID - {}", prefix, api.getPathToUuid());
                            if (api.isDebugEnabled()) logger.error(ex.getMessage(), ex);
                            if (api.shouldDisconnectOnBadUuidPath()) {
                                disconnectMessage = api.getUuidIsBadDisconnectMessage();
                                break;
                            }
                            continue;
                        }
                        placeholders.put("new-uuid", uuidString);
                        UUID uuid;
                        try {
                            uuid = UUID.fromString(uuidString);
                        } catch (Exception ex) {
                            logger.error("{} Failed to convert '{}' to UUID!", prefix, uuidString.replaceAll("\\R+", ""));
                            if (api.isDebugEnabled()) logger.error(ex.getMessage(), ex);
                            if (api.shouldDisconnectOnInvalidUuid()) {
                                disconnectMessage = api.getUuidIsBadDisconnectMessage();
                                break;
                            }
                            continue;
                        }
                        fetchedUuids.put(originalUUID, uuid);
                        logger.info("{} UUID successfully fetched for {} => {}", prefix, event.getUsername(), uuid);
                        disconnect = false;
                        break;
                    } catch (Exception ex) {
                        logger.error("{} Unknown error, failed to fetch UUID from the API!", prefix);
                        if (api.isDebugEnabled())
                            logger.error(ex.getMessage(), ex);
                        if (api.shouldDisconnectOnUnknownError()) {
                            disconnectMessage = api.getUnknownErrorDisconnectMessage();
                            break;
                        }
                        // Continue
                    }
                }

                if (disconnect) {
                    if (disconnectMessage == null)
                        disconnectMessage = config.getDefaultApi().getDefaultDisconnectMessage();
                    if (disconnectMessage != null) {
                        event.setResult(PreLoginEvent.PreLoginComponentResult.denied(Utils.toComponent(disconnectMessage)));
                        return;
                    }
                    event.setResult(PreLoginEvent.PreLoginComponentResult.denied(Component.translatable("multiplayer.disconnect.generic")));
                }
            });
        }
    }

    @Subscribe
    public void onGameProfileRequest(GameProfileRequestEvent event) {
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
            var profile = Utils.createProfile(newUsername, newUUIDStr, event.getGameProfile());
            event.setGameProfile(profile);
        }
    }
}
