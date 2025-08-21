package me.itstautvydas.uuidswapper.loader;

import com.google.inject.Inject;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.util.UuidUtils;
import me.itstautvydas.BuildConstants;
import me.itstautvydas.uuidswapper.Utils;
import me.itstautvydas.uuidswapper.crossplatform.PlatformType;
import me.itstautvydas.uuidswapper.crossplatform.PluginWrapper;
import me.itstautvydas.uuidswapper.service.PlayerDataFetcher;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.nio.file.Path;

@Plugin(id = "uuid-swapper",
        name = "UUIDSwapper",
        version = BuildConstants.VERSION,
        description = "Swap player names or UUID, use online UUIDs for offline mode!",
        url = "https://itstautvydas.me",
        authors = { "ItsTauTvyDas" })
public class UUIDSwapperVelocity {

    @Inject
    public UUIDSwapperVelocity(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        PluginWrapper.init(
                PlatformType.VELOCITY,
                this,
                server,
                logger,
                dataDirectory.resolve("config.toml")
        );
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        PluginWrapper.getCurrent().onEnable();
    }

    @Subscribe
    public EventTask onShutdownEvent(ProxyShutdownEvent event) {
        return EventTask.async(PluginWrapper.getCurrent()::onDisable);
    }

    @Subscribe
    public EventTask onPlayerPreLogin(PreLoginEvent event) {
        final var config = PluginWrapper.getCurrent().getConfiguration();

        if (!config.areOnlineUuidsEnabled())
            return null;

        if (PluginWrapper.getCurrent().isServerOnlineMode())
            event.setResult(PreLoginEvent.PreLoginComponentResult.forceOfflineMode());

        if (config.getCheckForOnlineUuid() && event.getUniqueId() != null) {
            var offlineUuid = UuidUtils.generateOfflinePlayerUuid(event.getUsername());
            if (!offlineUuid.equals(event.getUniqueId())) {
                if (config.getSendSuccessfulMessagesToConsole())
                    PluginWrapper.getCurrent().logInfo("Player {} has online UUID ({}), skipping fetching.", event.getUsername(), event.getUniqueId());
                return null;
            }
        }

        if (PlayerDataFetcher.isThrottled(event.getUniqueId())) {
            if (!config.isConnectionThrottleDialogEnabled()) {
                event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                        Utils.toComponent(config.getConnectionThrottleDialogMessage())
                ));
            }
            return null;
        }

        return EventTask.async(() -> PlayerDataFetcher.getPlayerData(
                    event.getUsername(),
                    event.getUniqueId(),
                    event.getConnection().getRemoteAddress().getAddress().getHostAddress()
            ).thenAccept(fetchedData -> {
                if (fetchedData.getSecond().hasMessage()) {
                    if (fetchedData.getSecond().isTranslatable())
                        event.setResult(PreLoginEvent.PreLoginComponentResult
                                .denied(Component.translatable(fetchedData.getSecond().getMessage())));
                    else
                        event.setResult(PreLoginEvent.PreLoginComponentResult
                                .denied(Utils.toComponent(fetchedData.getSecond().getMessage())));
                }
            }).join());
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

//    @Subscribe
//    public void onGameProfileRequest(GameProfileRequestEvent event) {
//        if (config.areOnlineUuidsEnabled()) {
//            var originalUuid = Utils.requireUuid(event.getUsername(), event.getGameProfile().getId());
//            var fetched = fetchedUuids.get(originalUuid);
//            if (fetched != null) {
//                event.setGameProfile(Utils.createProfile(event.getUsername(), fetched.getOnlineUuid(), event.getGameProfile(), fetched.getProperties()));
//                if (!config.stillSwapUuids())
//                    return;
//            }
//        }
//
//        var swappedUsernames = config.getCustomPlayerNames();
//        var swappedUuids = config.getSwappedUuids();
//
//        var newUsername = Utils.getSwappedValue(swappedUsernames, event.getGameProfile());
//        var newUUIDStr = Utils.getSwappedValue(swappedUuids, event.getGameProfile());
//
//        if (newUsername != null || newUUIDStr != null) {
//            logger.info("Player's ({} {}) new profile is:", event.getUsername(), event.getGameProfile().getId());
//            if (newUsername != null)
//                logger.info(" # Username => {}", newUsername);
//            if (newUUIDStr != null)
//                logger.info(" # Unique ID => {}", newUUIDStr);
//            event.setGameProfile(Utils.createProfile(newUsername, newUUIDStr, event.getGameProfile(), null));
//        }
//    }
}
