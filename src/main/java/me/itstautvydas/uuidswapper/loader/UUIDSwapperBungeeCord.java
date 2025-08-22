package me.itstautvydas.uuidswapper.loader;

import me.itstautvydas.uuidswapper.crossplatform.PluginWrapper;
import me.itstautvydas.uuidswapper.data.ProfileProperty;
import me.itstautvydas.uuidswapper.enums.PlatformType;
import me.itstautvydas.uuidswapper.helper.BiObjectHolder;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.TranslatableComponent;
import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.event.LoginEvent;
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
        PluginWrapper.getCurrent().onEnable();
        getProxy().getPluginManager().registerListener(this, this);
    }

    @EventHandler
    public void onPlayerPreLoginEvent(LoginEvent event) {
        final var inetAddress = (InetSocketAddress) event.getConnection().getSocketAddress();
        var completableFuture = PluginWrapper.getCurrent().onPlayerLogin(
                event.getConnection().getName(),
                event.getConnection().getUniqueId(),
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

        if (completableFuture != null) {
            event.registerIntent(this);
            ProxyServer.getInstance().getScheduler().runAsync(this, () -> {
                var fetchedData = completableFuture.join();
                if (fetchedData != null && fetchedData.containsFirst())
                    event.getConnection().setUniqueId(fetchedData.getFirst().getOnlineUuid());
                changeGameProfile(event.getConnection());
            });
            event.completeIntent(this);
        } else {
            changeGameProfile(event.getConnection());
        }
    }

    private void changeGameProfile(PendingConnection connection) {
        var holder = new BiObjectHolder<>(connection.getName(), connection.getUniqueId());
        var properties = new ArrayList<ProfileProperty>();
        PluginWrapper.getCurrent().onGameProfileRequest(holder, properties);
        updatePlayer(connection, holder.getFirst(), holder.getSecond(), properties);
    }

    private static Field nameField;
    private static Field uniqueIdField;
    private static Field loginProfileField;
//    private static Field propertiesField;

    private boolean updatePlayer(PendingConnection connection, String newUsername, UUID uniqueId, List<ProfileProperty> properties) {
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

            System.out.println(newUsername);
            System.out.println(uniqueId);
            System.out.println(properties);

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
