package me.itstautvydas.uuidswapper.loader;

import me.itstautvydas.uuidswapper.crossplatform.PluginWrapper;
import me.itstautvydas.uuidswapper.data.ProfilePropertyWrapper;
import me.itstautvydas.uuidswapper.enums.PlatformType;
import me.itstautvydas.uuidswapper.helper.BiObjectHolder;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.TranslatableComponent;
import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.event.LoginEvent;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class UUIDSwapperBungeeCord extends Plugin implements Listener {
    @Override
    public void onLoad() {
        PluginWrapper.init(PlatformType.BUNGEE, this, getProxy(), getLogger(), getDataFolder().toPath());
    }

    @Override
    public void onEnable() {
        if (PluginWrapper.onPluginEnable())
            getProxy().getPluginManager().registerListener(this, this);
    }

    @Override
    public void onDisable() {
        PluginWrapper.onPluginDisable();
    }

    @EventHandler
    public void handlePlayerDisconnect(PlayerDisconnectEvent event) {
        PluginWrapper.getCurrent().onPlayerDisconnect(event.getPlayer().getName(), event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void handlePlayerPreLogin(LoginEvent event) {
        final var inetAddress = (InetSocketAddress) event.getConnection().getSocketAddress();
        var completableFuture = PluginWrapper.getCurrent().onPlayerLogin(
                event.getConnection().getName(),
                event.getConnection().getUniqueId(),
                null,
                inetAddress.getAddress().getHostAddress(),
                true, null,
//                    () -> event.getConnection().setOnlineMode(false),
                (message) -> {
                    if (message.hasMessage()) {
                        event.setCancelled(true);
                        if (message.isTranslatable()) {
                            event.setReason(new TranslatableComponent(message.getMessage()));
                        } else {
                            event.setReason(TextComponent.fromLegacy(message.getMessage()));
                        }
                    }
                }
        );

        event.registerIntent(this);
        ProxyServer.getInstance().getScheduler().runAsync(this, completableFuture::join);
        changeGameProfile(event.getConnection());
        event.completeIntent(this);
    }

    private void changeGameProfile(PendingConnection connection) {
        var holder = new BiObjectHolder<>(connection.getName(), connection.getUniqueId());
        var properties = new ArrayList<ProfilePropertyWrapper>();
        if (PluginWrapper.getCurrent().onGameProfileRequest(holder, properties)) {
            updatePlayer(connection, holder.getFirst(), holder.getSecond(), properties);
        }
    }

    private static Field nameField;
    private static Field uniqueIdField;
    private static Field loginProfileField;
//    private static Field propertiesField;

    private boolean updatePlayer(PendingConnection connection, String newUsername, UUID uniqueId, List<ProfilePropertyWrapper> properties) {
        try {
            if (nameField == null) {
                nameField = connection.getClass().getDeclaredField("name");
                nameField.setAccessible(true);
            }
            if (newUsername != null)
                nameField.set(connection, newUsername);

            if (uniqueIdField == null) {
                uniqueIdField = connection.getClass().getDeclaredField("uniqueId");
                uniqueIdField.setAccessible(true);
            }
            if (uniqueId != null)
                uniqueIdField.set(connection, uniqueId);

            if (loginProfileField == null) {
                loginProfileField = connection.getClass().getDeclaredField("loginProfile");
                loginProfileField.setAccessible(true);
            }

//            var loginProfile = loginProfileField.get(connection);
//
//            if (propertiesField == null) {
//                propertiesField = loginProfile.getClass().getDeclaredField("properties");
//                propertiesField.setAccessible(true);
//            }
//
//            if (properties != null)
//                propertiesField.set(loginProfile, properties.stream()
//                        .map(x -> new Property(x.getName(), x.getValue(), x.getSignature()))
//                        .toArray(Property[]::new));
            return true;
        } catch (Exception ex) {
            PluginWrapper.getCurrent().logError("Failed to update player's internal data for {0} ({1})", ex, connection.getName(), connection.getUniqueId());
            return false;
        }
    }
}
