package me.itstautvydas.uuidswapper.loader;

import me.itstautvydas.uuidswapper.crossplatform.CrossPlatformImplementation;
import me.itstautvydas.uuidswapper.crossplatform.PlatformType;
import net.md_5.bungee.api.event.LoginEvent;
import net.md_5.bungee.api.event.PreLoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;

public class UUIDSwapperBungeeCord extends Plugin implements Listener {
    @Override
    public void onEnable() {
        if (!getDataFolder().exists())
            getDataFolder().mkdir();
        CrossPlatformImplementation.init(
                PlatformType.BUNGEE,
                getProxy(),
                getLogger(),
                getDataFolder().toPath().resolve("config.yml")
        );
        getProxy().getPluginManager().registerListener(this, this);
    }

    @EventHandler
    public void onPlayerPreLoginEvent(PreLoginEvent event) {

    }

    @EventHandler
    public void onPlayerLoginEvent(LoginEvent event) {

    }
}
