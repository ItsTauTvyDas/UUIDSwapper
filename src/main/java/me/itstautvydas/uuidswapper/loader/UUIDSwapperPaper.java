package me.itstautvydas.uuidswapper.loader;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.itstautvydas.uuidswapper.Utils;
import me.itstautvydas.uuidswapper.crossplatform.PluginWrapper;
import me.itstautvydas.uuidswapper.data.ProfileProperty;
import me.itstautvydas.uuidswapper.enums.PlatformType;
import me.itstautvydas.uuidswapper.helper.BiObjectHolder;
import me.itstautvydas.uuidswapper.service.PlayerDataFetcher;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;

public class UUIDSwapperPaper extends JavaPlugin implements Listener {
    @Override
    public void onLoad() {
        PluginWrapper.init(PlatformType.PAPER, this, Bukkit.getServer(), getLogger(), getDataPath());
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
    public void onPlayerJoin(AsyncPlayerPreLoginEvent event) {
        var pretendData = PlayerDataFetcher.pullPretender(event.getUniqueId());
        if (pretendData != null) {
            var profile = Bukkit.createProfile(pretendData.getUniqueId(), pretendData.getUsername());
            if (pretendData.getProperties() != null) {
                profile.setProperties(pretendData.getProperties().stream()
                        .map(x -> new com.destroystokyo.paper.profile.ProfileProperty(
                                x.getName(), x.getValue(), x.getSignature()
                        )).toList());
            }
            event.setPlayerProfile(profile);
            return;
        }

        var completableFuture = PluginWrapper.getCurrent().onPlayerLogin(
                event.getName(),
                event.getUniqueId(),
                event.getAddress().getHostAddress(),
                true,
                null,
                (message) -> {
                    if (message.hasMessage()) {
                        event.setLoginResult(AsyncPlayerPreLoginEvent.Result.KICK_OTHER);
                        if (message.isTranslatable()) {
                            event.kickMessage(Component.translatable(message.getMessage()));
                        } else {
                            event.kickMessage(Utils.toComponent(message.getMessage()));
                        }
                    }
                }
        );

        if (completableFuture != null)
            completableFuture.join();

        var holder = new BiObjectHolder<>(event.getName(), event.getUniqueId());
        var properties = new ArrayList<ProfileProperty>();
        PluginWrapper.getCurrent().onGameProfileRequest(holder, properties);
        var profile = Bukkit.createProfile(holder.getSecond(), holder.getFirst());
        profile.setProperties(properties.stream()
                .map(x -> new com.destroystokyo.paper.profile.ProfileProperty(
                        x.getName(), x.getValue(), x.getSignature()
                )).toList());
        event.setPlayerProfile(profile);
    }
}
