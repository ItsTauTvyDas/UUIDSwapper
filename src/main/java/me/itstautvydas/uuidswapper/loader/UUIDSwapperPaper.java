package me.itstautvydas.uuidswapper.loader;

import me.itstautvydas.uuidswapper.crossplatform.PluginWrapper;
import me.itstautvydas.uuidswapper.data.ProfileProperty;
import me.itstautvydas.uuidswapper.enums.PlatformType;
import me.itstautvydas.uuidswapper.helper.BiObjectHolder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;

public class UUIDSwapperPaper extends JavaPlugin implements Listener {
    @Override
    public void onLoad() {
        PluginWrapper.init(PlatformType.PAPER.verifyFolia(), this, Bukkit.getServer(), getLogger(), getDataPath());
    }

    @Override
    public void onEnable() {
        PluginWrapper.getCurrent().onEnable();
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        PluginWrapper.getCurrent().onDisable();
    }

    @EventHandler
    public void handlePlayerDisconnect(PlayerQuitEvent event) {
        PluginWrapper.getCurrent().onPlayerDisconnect(event.getPlayer().getName(), event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void handlePlayerJoin(AsyncPlayerPreLoginEvent event) {
        PluginWrapper.getCurrent().onPlayerLogin(
                event.getName(),
                event.getUniqueId(),
                event.getPlayerProfile().getProperties()
                        .stream()
                        .map(x -> new ProfileProperty(
                                x.getName(), x.getValue(), x.getSignature()
                        ))
                        .toList(),
                event.getAddress().getHostAddress(),
                true,
                null,
                (message) -> {
                    if (message.hasMessage()) {
                        event.setLoginResult(AsyncPlayerPreLoginEvent.Result.KICK_OTHER);
                        if (message.isTranslatable()) {
                            event.kickMessage(Component.translatable(message.getMessage()));
                        } else {
                            if (PluginWrapper.getCurrent().getConfiguration().getPaper().isUseMiniMessages())
                                event.kickMessage(MiniMessage.miniMessage().deserialize(message.getMessage()));
                            else
                                event.kickMessage(LegacyComponentSerializer.legacy('&').deserialize(message.getMessage()));
                        }
                    }
                }
        ).join();

        var holder = new BiObjectHolder<>(event.getName(), event.getUniqueId());
        var properties = new ArrayList<ProfileProperty>();
        if (PluginWrapper.getCurrent().onGameProfileRequest(holder, properties)) {
            var profile = Bukkit.createProfile(holder.getSecond(), holder.getFirst());
            profile.setProperties(properties.stream()
                    .map(x -> new com.destroystokyo.paper.profile.ProfileProperty(
                            x.getName(), x.getValue(), x.getSignature()
                    )).toList());
            event.setPlayerProfile(profile);
        }
    }
}
