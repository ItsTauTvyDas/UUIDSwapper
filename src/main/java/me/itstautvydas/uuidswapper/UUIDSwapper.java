package me.itstautvydas.uuidswapper;

import com.google.inject.Inject;
import com.moandjiezana.toml.Toml;
import com.moandjiezana.toml.TomlWriter;
import com.velocitypowered.api.event.player.GameProfileRequestEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.util.GameProfile;
import me.itstautvydas.BuildConstants;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

@Plugin(id = "uuid-swapper",
        name = "UUIDSwapper",
        version = BuildConstants.VERSION,
        description = "",
        url ="https://itstautvydas.me",
        authors ={"ItsTauTvyDas"})
public class UUIDSwapper {

    private final Configuration config;
    private final Logger logger;

    @Inject
    public UUIDSwapper(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) throws IOException {
        this.logger = logger;

        if (Files.notExists(dataDirectory))
            Files.createDirectories(dataDirectory);

        Path configFile = dataDirectory.resolve("config.toml");
        if (Files.notExists(configFile)) {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream("config.toml")) {
                if (in != null) {
                    logger.info("Copying new configuration...");
                    Files.copy(in, configFile);
                }
            }
        }

        var toml = new Toml().read(configFile.toFile());
        config = toml.to(Configuration.class);

        config.swappedUuids = toml.getTable("swapped-uuids").toMap();
        config.customPlayerNames = toml.getTable("custom-player-names").toMap();

        logger.info("Configuration loaded.");
        logger.info("Loaded {} swapped UUIDs.", config.swappedUuids.size());
        for (var entry : config.swappedUuids.entrySet()) {
            logger.info("# {} => {}", entry.getKey(), entry.getValue());
        }
        logger.info("Loaded {} custom player usernames.", config.customPlayerNames.size());
        for (var entry : config.customPlayerNames.entrySet()) {
            logger.info("# {} => {}", entry.getKey(), entry.getValue());
        }
    }

    public GameProfile createProfile(String username, String uuid, GameProfile profile) {
        if (username == null)
            username = profile.getName();
        return new GameProfile(uuid == null ? profile.getId() : UUID.fromString(uuid), username, profile.getProperties());
    }

    public String getSwappedValue(Map<String, Object> map, GameProfile profile) {
        var value = map.get("\"u:" + profile.getName() + "\"");
        if (value == null)
            value = map.get("\"" + profile.getId().toString() + "\"");
        if (value != null)
            return value.toString();
        return null;
    }

    @Subscribe
    public void onGameProfileRequest(GameProfileRequestEvent event) {
        var swappedUsernames = config.customPlayerNames;
        var swappedUuids = config.swappedUuids;

        String newUsername = getSwappedValue(swappedUsernames, event.getGameProfile());
        String newUUIDStr = getSwappedValue(swappedUuids, event.getGameProfile());

        if (newUsername != null || newUUIDStr != null) {
            logger.info("Player's ({} {}) new profile is:", event.getUsername(), event.getGameProfile().getId());
            if (newUsername != null)
                logger.info(" # Username => {}", newUsername);
            if (newUUIDStr != null)
                logger.info(" # Unique ID => {}", newUUIDStr);
            var profile = createProfile(newUsername, newUUIDStr, event.getGameProfile());
            event.setGameProfile(profile);
        }
    }
}
