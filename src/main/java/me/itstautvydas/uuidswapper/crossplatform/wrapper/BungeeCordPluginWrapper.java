package me.itstautvydas.uuidswapper.crossplatform.wrapper;

import me.itstautvydas.uuidswapper.Utils;
import me.itstautvydas.uuidswapper.config.Configuration;
import me.itstautvydas.uuidswapper.crossplatform.PluginTaskWrapper;
import me.itstautvydas.uuidswapper.crossplatform.shared.JavaLoggerWrapper;
import me.itstautvydas.uuidswapper.loader.UUIDSwapperBungeeCord;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.scheduler.ScheduledTask;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class BungeeCordPluginWrapper extends JavaLoggerWrapper<UUIDSwapperBungeeCord, ProxyServer, CommandSender> {
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
}
