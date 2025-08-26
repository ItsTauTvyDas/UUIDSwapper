package me.itstautvydas.uuidswapper.crossplatform.wrapper;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.itstautvydas.uuidswapper.Utils;
import me.itstautvydas.uuidswapper.config.Configuration;
import me.itstautvydas.uuidswapper.crossplatform.PluginTaskWrapper;
import me.itstautvydas.uuidswapper.crossplatform.shared.JavaLoggerWrapper;
import me.itstautvydas.uuidswapper.loader.UUIDSwapperPaper;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@SuppressWarnings("unused")
public class PaperPluginWrapper extends JavaLoggerWrapper<UUIDSwapperPaper, Server, CommandContext<CommandSourceStack>> {
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

    @Override
    public void registerCommand(String commandName) {
        handle.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, commands -> {
            commands.registrar().register(Commands.literal(commandName)
                            .requires(source -> source.getSender().hasPermission(Utils.COMMAND_PERMISSION))
                            .executes(ctx -> {
                                onNoArgsCommand(ctx);
                                return Command.SINGLE_SUCCESS;
                            })
                            .then(Commands.literal("reload")
                                    .requires(source -> source.getSender().hasPermission(Utils.RELOAD_COMMAND_PERMISSION))
                                    .executes(ctx -> {
                                        onReloadCommand(ctx);
                                        return Command.SINGLE_SUCCESS;
                                    }))
                            .then(Commands.literal("pretend")
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
                                                            })))))
                    .build());
        });
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
}
