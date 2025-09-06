package me.itstautvydas.uuidswapper.multiplatform.wrapper;

import com.destroystokyo.paper.profile.ProfileProperty;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.itstautvydas.uuidswapper.Utils;
import me.itstautvydas.uuidswapper.config.Configuration;
import me.itstautvydas.uuidswapper.data.ProfilePropertyWrapper;
import me.itstautvydas.uuidswapper.helper.BiObjectHolder;
import me.itstautvydas.uuidswapper.multiplatform.MultiPlatform;
import me.itstautvydas.uuidswapper.multiplatform.PluginTaskWrapper;
import me.itstautvydas.uuidswapper.multiplatform.shared.JavaLoggerWrapper;
import me.itstautvydas.uuidswapper.loader.UUIDSwapperPaper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

public class PaperPluginWrapper extends JavaLoggerWrapper<UUIDSwapperPaper, Server, CommandContext<CommandSourceStack>> implements Listener {
    @Override
    public void sendMessage(CommandContext<CommandSourceStack> ctx, Function<Configuration.CommandMessagesConfiguration, String> message, Map<String, Object> placeholders) {
        ctx.getSource().getSender().sendMessage(LegacyComponentSerializer
                .legacy('&')
                .deserialize(Utils.replacePlaceholders(message.apply(getConfiguration().getCommandMessages()), placeholders)));
    }

    private static <T> T getOptionalArgument(CommandContext<?> ctx, String name, Class<T> type, T defaultValue) {
        try {
            return ctx.getArgument(name, type);
        } catch (IllegalArgumentException ex) {
            return defaultValue;
        }
    }

    private void pretend(CommandContext<CommandSourceStack> ctx) {
        if (ctx.getSource().getSender() instanceof Player player)
            onPretendCommand(
                    ctx,
                    player.getUniqueId(),
                    getOptionalArgument(ctx, "uniqueId", UUID.class, null),
                    ctx.getArgument("username", String.class),
                    getOptionalArgument(ctx, "fetchProperties", boolean.class, false),
                    run -> Bukkit.getScheduler().runTaskAsynchronously(handle, run));
    }

    private LiteralArgumentBuilder<CommandSourceStack> debug(DebugCommandCacheType type) {
        return Commands.literal(type.toString()).executes(ctx -> {
            ctx.getSource().getSender().sendMessage(
                    LegacyComponentSerializer
                            .legacyAmpersand()
                            .deserialize(getDebugMessage(type))
            );
            return Command.SINGLE_SUCCESS;
        });
    }

    @Override
    public void registerCommand(String commandName) {
        handle.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, commands ->
                commands.registrar().register(Commands.literal(commandName)
                        .requires(source -> source.getSender().hasPermission(Utils.COMMAND_PERMISSION))
                        .executes(ctx -> {
                            onNoArgsCommand(ctx);
                            return Command.SINGLE_SUCCESS;
                        }).then(Commands.literal("reload")
                                .requires(source -> source.getSender().hasPermission(Utils.RELOAD_COMMAND_PERMISSION))
                                .executes(ctx -> {
                                    onReloadCommand(ctx);
                                    return Command.SINGLE_SUCCESS;
                                })
                        ).then(Commands.literal("debug")
                                .requires(source -> source.getSender().hasPermission(Utils.DEBUG_COMMAND_PERMISSION))
                                .then(debug(DebugCommandCacheType.PLAYER_DATA_FETCHER_FETCHED))
                                .then(debug(DebugCommandCacheType.PLAYER_DATA_FETCHER_PRETEND))
                                .then(debug(DebugCommandCacheType.PLAYER_DATA_FETCHER_THROTTLED))
                                .then(debug(DebugCommandCacheType.PLAYER_DATA_FETCHER_LAST_SERVICE))
                                .then(debug(DebugCommandCacheType.DATABASE_FETCHED_PLAYERS))
                                .then(debug(DebugCommandCacheType.DATABASE_RANDOM_PLAYERS))
                        ).then(Commands.literal("pretend")
                                .requires(source -> source.getSender().hasPermission(Utils.PRETEND_COMMAND_PERMISSION))
                                .then(Commands.argument("username", StringArgumentType.word())
                                        .executes(ctx -> {
                                            pretend(ctx);
                                            return Command.SINGLE_SUCCESS;
                                        })
                                        .then(Commands.argument("fetchProperties", BoolArgumentType.bool())
                                                .executes(ctx -> {
                                                    pretend(ctx);
                                                    return Command.SINGLE_SUCCESS;
                                                })
                                                .then(Commands.argument("uniqueId", ArgumentTypes.uuid())
                                                        .executes(ctx -> {
                                                            pretend(ctx);
                                                            return Command.SINGLE_SUCCESS;
                                                        }))))
                        )
                .build()));
    }

    @Override
    public boolean isServerOnlineMode() {
        return server.getOnlineMode();
    }

    @Override
    public PluginTaskWrapper scheduleTask(Runnable run, @Nullable Long repeatInSeconds, long delayInSeconds) {
        BukkitTask task;
        if (repeatInSeconds == null)
            task = server.getScheduler().runTaskLater(handle, run, delayInSeconds * 20);
        else
            task = server.getScheduler().runTaskTimer(handle, run, delayInSeconds * 20, repeatInSeconds * 20);

        return new PluginTaskWrapper(task) {
            @Override
            public void cancel() {
                ((BukkitTask)handle).cancel();
            }
        };
    }

    @Override
    public PluginTaskWrapper scheduleTaskAsync(Runnable run) {
        return new PluginTaskWrapper(server.getScheduler().runTaskAsynchronously(handle, run)) {
            @Override
            public void cancel() {
                ((BukkitTask)handle).cancel();
            }
        };
    }

    @EventHandler
    public void handlePlayerDisconnect(PlayerQuitEvent event) {
        MultiPlatform.get().handlePlayerDisconnect(event.getPlayer().getName(), event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void handlePlayerJoin(AsyncPlayerPreLoginEvent event) {
        MultiPlatform.get().handlePlayerLogin(
                event.getName(),
                event.getUniqueId(),
                event.getPlayerProfile().getProperties()
                        .stream()
                        .map(x -> new ProfilePropertyWrapper(
                                x.getName(), x.getValue(), x.getSignature()
                        ))
                        .toList(),
                true,
                null,
                (message) -> {
                    if (message.hasMessage()) {
                        event.setLoginResult(AsyncPlayerPreLoginEvent.Result.KICK_OTHER);
                        if (message.isTranslatable()) {
                            event.kickMessage(Component.translatable(message.getMessage()));
                        } else {
                            if (MultiPlatform.get().getConfiguration().getPaper().isUseMiniMessages())
                                event.kickMessage(MiniMessage.miniMessage().deserialize(message.getMessage()));
                            else
                                event.kickMessage(LegacyComponentSerializer.legacy('&').deserialize(message.getMessage()));
                        }
                    }
                }
        ).join();

        var holder = new BiObjectHolder<>(event.getName(), event.getUniqueId());
        var properties = new ArrayList<ProfilePropertyWrapper>();
        if (MultiPlatform.get().handleGameProfileRequest(holder, properties)) {
            var profile = Bukkit.createProfile(holder.getSecond(), holder.getFirst());
            profile.setProperties(properties.stream()
                    .map(x -> new ProfileProperty(
                            x.getName(), x.getValue(), x.getSignature()
                    )).toList());
            event.setPlayerProfile(profile);
        }
    }
}
