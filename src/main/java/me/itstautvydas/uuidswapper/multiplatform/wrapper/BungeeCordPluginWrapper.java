package me.itstautvydas.uuidswapper.multiplatform.wrapper;

import me.itstautvydas.uuidswapper.Utils;
import me.itstautvydas.uuidswapper.config.Configuration;
import me.itstautvydas.uuidswapper.data.ProfilePropertyWrapper;
import me.itstautvydas.uuidswapper.helper.BiObjectHolder;
import me.itstautvydas.uuidswapper.helper.ReflectionHelper;
import me.itstautvydas.uuidswapper.multiplatform.PluginTaskWrapper;
import me.itstautvydas.uuidswapper.multiplatform.shared.JavaLoggerWrapper;
import me.itstautvydas.uuidswapper.loader.UUIDSwapperBungeeCord;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.TranslatableComponent;
import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.event.LoginEvent;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PreLoginEvent;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.scheduler.ScheduledTask;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.protocol.data.Property;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class BungeeCordPluginWrapper extends JavaLoggerWrapper<UUIDSwapperBungeeCord, ProxyServer, CommandSender> implements Listener {
    @Override
    public void sendMessage(CommandSender sender, Function<Configuration.CommandMessagesConfiguration, String> message, Map<String, Object> placeholders) {
        sender.sendMessage(TextComponent.fromLegacy(Utils.replacePlaceholders(message.apply(getConfiguration().getCommandMessages()), placeholders)));
    }

    @Override
    public void registerCommand(String commandName) {
        ProxyServer.getInstance().getPluginManager().registerCommand(handle, new Command(commandName) {
            @Override
            public void execute(CommandSender sender, String[] args) {

            }
        });
    }

    @Override
    public boolean isServerOnlineMode() {
        return server.getConfig().isOnlineMode();
    }

    @Override
    public PluginTaskWrapper scheduleTask(Runnable run, @Nullable Long repeatInSeconds, long delayInSeconds) {
        ScheduledTask task;
        if (repeatInSeconds == null)
            task = server.getScheduler().schedule(handle, run, delayInSeconds, TimeUnit.SECONDS);
        else
            task = server.getScheduler().schedule(handle, run, delayInSeconds, repeatInSeconds, TimeUnit.SECONDS);
        return new PluginTaskWrapper(task) {
            @Override
            public void cancel() {
                ((ScheduledTask)handle).cancel();
            }
        };
    }

    @EventHandler
    public void handlePlayerDisconnect(PlayerDisconnectEvent event) {
        handlePlayerDisconnect(event.getPlayer().getName(), event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void handlePlayerHandshake(PreLoginEvent event) {
        tryForceOfflineMode(() -> event.getConnection().setOnlineMode(false), null);
    }

    @EventHandler
    public void handlePlayerPreLogin(LoginEvent event) {
        var completableFuture = handlePlayerLogin(
                event.getConnection().getName(),
                event.getConnection().getUniqueId(),
                null,
                true,
                true,
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

        event.registerIntent(handle);
        ProxyServer.getInstance().getScheduler().runAsync(handle, () -> {
            completableFuture.join();
            var holder = new BiObjectHolder<>(event.getConnection().getName(), event.getConnection().getUniqueId());
            var properties = new ArrayList<ProfilePropertyWrapper>();
            if (handleGameProfileRequest(holder, properties)) {
                try {
                    updatePlayer(event.getConnection(), holder.getFirst(), holder.getSecond(), properties);
                } catch (Exception ex) {
                    event.setCancelled(true);
                    event.setReason(new TextComponent("Internal error: " + ex.getMessage()));
                }
            }
            event.completeIntent(handle);
        });
    }

    private static Property[] convertProperties(List<ProfilePropertyWrapper> properties) {
        if (properties == null)
            return null;

        return properties.stream()
                .map(x -> new Property(x.getName(), x.getValue(), x.getSignature()))
                .toArray(Property[]::new);
    }

    private boolean updatePlayer(PendingConnection connection, String newUsername, UUID uniqueId, List<ProfilePropertyWrapper> properties) {
        try {
            if (properties != null) {
                var loginProfile = ReflectionHelper.getFieldValue(connection, "loginProfile");
                if (loginProfile == null) {
                    var constructor = Class.forName("net.md_5.bungee.connection.LoginResult").getDeclaredConstructor(
                            String.class,
                            String.class,
                            Property[].class
                    );
                    constructor.setAccessible(true);
                    loginProfile = constructor.newInstance(
                            (uniqueId != null ? uniqueId : connection.getUniqueId()).toString().replace("-", ""),
                            newUsername != null ? newUsername : connection.getName(),
                            convertProperties(properties)
                    );
                    ReflectionHelper.setFieldValue(connection, "loginProfile", loginProfile);
                }
            }
            if (newUsername != null)
                ReflectionHelper.setFieldValue(connection, "name", newUsername);

            if (uniqueId != null) {
                // Eh, just modify them all
                ReflectionHelper.setFieldValue(connection, "uniqueId", uniqueId);
                ReflectionHelper.setFieldValueIfCan(connection, "rewriteId", uniqueId);
            }
            return true;
        } catch (Exception ex) {
            logError("Failed to update player's internal data for {0} ({1})", ex, connection.getName(), connection.getUniqueId());
            return false;
        }
    }
}
