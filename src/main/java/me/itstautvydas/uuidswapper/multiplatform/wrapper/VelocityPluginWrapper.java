package me.itstautvydas.uuidswapper.multiplatform.wrapper;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.player.GameProfileRequestEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.api.util.GameProfile;
import me.itstautvydas.uuidswapper.Utils;
import me.itstautvydas.uuidswapper.config.Configuration;
import me.itstautvydas.uuidswapper.data.ProfilePropertyWrapper;
import me.itstautvydas.uuidswapper.helper.BiObjectHolder;
import me.itstautvydas.uuidswapper.multiplatform.PluginTaskWrapper;
import me.itstautvydas.uuidswapper.multiplatform.MultiPlatform;
import me.itstautvydas.uuidswapper.loader.UUIDSwapperVelocity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.event.Level;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class VelocityPluginWrapper extends MultiPlatform<UUIDSwapperVelocity, Logger, ProxyServer, CommandContext<CommandSource>> {
    @Override
    public void sendMessage(CommandContext<CommandSource> sender, Function<Configuration.CommandMessagesConfiguration, String> message, Map<String, Object> placeholders) {
        sender.getSource().sendMessage(LegacyComponentSerializer
                .legacy('&')
                .deserialize(Utils.replacePlaceholders(message.apply(getConfiguration().getCommandMessages()), placeholders)));
    }

    @Override
    public void registerCommand(String commandName) {
        var commandManager = ((VelocityPluginWrapper) MultiPlatform.get()).getServer().getCommandManager();
        var command = BrigadierCommand.literalArgumentBuilder(commandName)
                .requires(source -> source.hasPermission(Utils.COMMAND_PERMISSION))
                .executes(ctx -> {
                    onNoArgsCommand(ctx);
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierCommand.literalArgumentBuilder("reload")
                        .requires(source -> source.hasPermission(Utils.RELOAD_COMMAND_PERMISSION))
                        .executes(ctx -> {
                            onReloadCommand(ctx);
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();

        commandManager.register(commandManager.metaBuilder(commandName)
                .plugin(handle)
                .build(), new BrigadierCommand(command));
    }

    @Override
    public boolean isServerOnlineMode() {
        return server.getConfiguration().isOnlineMode();
    }

    @Override
    public void logInfo(String prefix, String message, Object... args) {
        logger.info(Utils.toLoggerMessage(prefix, message, args));
    }

    @Override
    public void logWarning(String prefix, String message, Throwable exception, Object... args) {
        logger.atLevel(Level.WARN)
                .setMessage(exception == null ? null : exception.getMessage())
                .setCause(exception)
                .log(Utils.toLoggerMessage(prefix, message, args));
    }

    @Override
    public void logError(String prefix, String message, Throwable exception, Object... args) {
        logger.atLevel(Level.ERROR)
                .setMessage(exception == null ? null : exception.getMessage())
                .setCause(exception)
                .log(Utils.toLoggerMessage(prefix, message, args));
    }

    @Override
    public PluginTaskWrapper scheduleTask(Runnable run, @Nullable Long repeatInSeconds, long delayInSeconds) {
        var builder = server.getScheduler().buildTask(handle, run);
        if (repeatInSeconds != null)
            builder.repeat(repeatInSeconds, TimeUnit.SECONDS);
        builder.delay(delayInSeconds, TimeUnit.SECONDS);
        return new PluginTaskWrapper(builder.schedule()) {
            @Override
            public void cancel() {
                ((ScheduledTask)handle).cancel();
            }
        };
    }

    @Subscribe
    public void handlePlayerDisconnect(DisconnectEvent event) {
        handlePlayerDisconnect(event.getPlayer().getUsername(), event.getPlayer().getUniqueId());
    }

    @Subscribe
    public EventTask handlePlayerPreLogin(PreLoginEvent event) {
        return EventTask.async(() -> handlePlayerLogin(
                event.getUsername(),
                event.getUniqueId(),
                null,
                true,
                (Runnable) () -> event.setResult(PreLoginEvent.PreLoginComponentResult.forceOfflineMode()),
                (message) -> {
                    if (message.hasMessage()) {
                        if (message.isTranslatable()) {
                            event.setResult(PreLoginEvent.PreLoginComponentResult
                                    .denied(Component.translatable(message.getMessage())));
                        } else {
                            if (getConfiguration().getPaper().isUseMiniMessages())
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
        var properties = new ArrayList<ProfilePropertyWrapper>();
        if (handleGameProfileRequest(holder, properties)) {
            event.setGameProfile(new GameProfile(
                    holder.getSecond(),
                    holder.getFirst(),
                    properties.isEmpty() ? event.getGameProfile().getProperties() : properties.stream()
                            .map(x -> new GameProfile.Property(x.getName(), x.getValue(), x.getSignature()))
                            .toList()));
        }
    }
}
