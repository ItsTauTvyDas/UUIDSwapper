package me.itstautvydas.uuidswapper.loader;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Continuation;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.player.GameProfileRequestEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.util.GameProfile;
import me.itstautvydas.BuildConstants;
import me.itstautvydas.uuidswapper.Utils;
import me.itstautvydas.uuidswapper.crossplatform.PluginWrapper;
import me.itstautvydas.uuidswapper.data.ProfileProperty;
import me.itstautvydas.uuidswapper.enums.PlatformType;
import me.itstautvydas.uuidswapper.helper.BiObjectHolder;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.ArrayList;

@Plugin(id = "uuid-swapper",
        name = "UUIDSwapper",
        version = BuildConstants.VERSION,
        description = "Swap player names or UUID, use online UUIDs for offline mode!",
        url = "https://itstautvydas.me",
        authors = { "ItsTauTvyDas" })
public class UUIDSwapperVelocity {

    @Inject
    public UUIDSwapperVelocity(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        PluginWrapper.init(PlatformType.VELOCITY, this, server, logger, dataDirectory);
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
    public void onPlayerPreLogin(PreLoginEvent event, Continuation continuation) {
        PluginWrapper.getCurrent().onPlayerLogin(
                event.getUsername(),
                event.getUniqueId(),
                event.getConnection().getRemoteAddress().getAddress().getHostAddress(),
                true,
                () -> event.setResult(PreLoginEvent.PreLoginComponentResult.forceOfflineMode()),
                (message) -> {
                    if (message.hasMessage()) {
                        if (message.isTranslatable()) {
                            event.setResult(PreLoginEvent.PreLoginComponentResult
                                    .denied(Component.translatable(message.getMessage())));
                        } else {
                            event.setResult(PreLoginEvent.PreLoginComponentResult
                                    .denied(Utils.toComponent(message.getMessage())));
                        }
                    }
                }
        ).join();
        continuation.resume();
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
        var holder = new BiObjectHolder<>(event.getUsername(), event.getGameProfile().getId());
        var properties = new ArrayList<ProfileProperty>();
        PluginWrapper.getCurrent().onGameProfileRequest(holder, properties);
        event.setGameProfile(new GameProfile(
                holder.getSecond(),
                holder.getFirst(),
                properties.isEmpty() ? event.getGameProfile().getProperties() : properties.stream()
                        .map(x -> new GameProfile.Property(x.getName(), x.getValue(), x.getSignature()))
                        .toList()
        ));
    }
}
