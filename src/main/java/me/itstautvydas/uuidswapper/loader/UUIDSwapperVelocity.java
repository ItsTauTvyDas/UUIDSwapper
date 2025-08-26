package me.itstautvydas.uuidswapper.loader;

import com.google.inject.Inject;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.player.GameProfileRequestEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.util.GameProfile;
import me.itstautvydas.BuildConstants;
import me.itstautvydas.uuidswapper.crossplatform.PluginWrapper;
import me.itstautvydas.uuidswapper.data.ProfileProperty;
import me.itstautvydas.uuidswapper.enums.PlatformType;
import me.itstautvydas.uuidswapper.helper.BiObjectHolder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.ArrayList;

@Plugin(id = "uuid-swapper",
        name = BuildConstants.NAME,
        version = BuildConstants.VERSION,
        description = BuildConstants.DESCRIPTION,
        url = BuildConstants.WEBSITE,
        authors = { "ItsTauTvyDas" })
public class UUIDSwapperVelocity {
    @Inject
    public UUIDSwapperVelocity(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        PluginWrapper.init(PlatformType.VELOCITY, this, server, logger, dataDirectory);
    }

    @Subscribe
    public void handleProxyInitialization(ProxyInitializeEvent event) {
        PluginWrapper.getCurrent().onEnable();
    }

    @Subscribe
    public EventTask handleProxyShutdown(ProxyShutdownEvent event) {
        return EventTask.async(PluginWrapper.getCurrent()::onDisable);
    }

    @Subscribe
    public void handlePlayerDisconnect(DisconnectEvent event) {
        PluginWrapper.getCurrent().onPlayerDisconnect(event.getPlayer().getUsername(), event.getPlayer().getUniqueId());
    }

    @Subscribe
    public EventTask handlePlayerPreLogin(PreLoginEvent event) {
        return EventTask.async(() -> PluginWrapper.getCurrent().onPlayerLogin(
                event.getUsername(),
                event.getUniqueId(),
                null,
                event.getConnection().getRemoteAddress().getAddress().getHostAddress(),
                true,
                () -> event.setResult(PreLoginEvent.PreLoginComponentResult.forceOfflineMode()),
                (message) -> {
                    if (message.hasMessage()) {
                        if (message.isTranslatable()) {
                            event.setResult(PreLoginEvent.PreLoginComponentResult
                                    .denied(Component.translatable(message.getMessage())));
                        } else {
                            if (PluginWrapper.getCurrent().getConfiguration().getPaper().isUseMiniMessages())
                                event.setResult(PreLoginEvent.PreLoginComponentResult
                                        .denied(MiniMessage.miniMessage().deserialize(message.getMessage())));
                            else
                                event.setResult(PreLoginEvent.PreLoginComponentResult
                                        .denied(LegacyComponentSerializer.legacy('&').deserialize(message.getMessage())));
                        }
                    }
                }
        ).join());
    }

    @Subscribe
    public void handleGameProfileRequest(GameProfileRequestEvent event) {
        var holder = new BiObjectHolder<>(event.getUsername(), event.getGameProfile().getId());
        var properties = new ArrayList<ProfileProperty>();
        if (PluginWrapper.getCurrent().onGameProfileRequest(holder, properties)) {
            event.setGameProfile(new GameProfile(
                    holder.getSecond(),
                    holder.getFirst(),
                    properties.isEmpty() ? event.getGameProfile().getProperties() : properties.stream()
                            .map(x -> new GameProfile.Property(x.getName(), x.getValue(), x.getSignature()))
                            .toList()));
        }
    }
}
