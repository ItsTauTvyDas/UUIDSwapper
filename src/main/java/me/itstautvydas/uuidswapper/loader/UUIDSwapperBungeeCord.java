package me.itstautvydas.uuidswapper.loader;

import me.itstautvydas.uuidswapper.enums.PlatformType;
import me.itstautvydas.uuidswapper.multiplatform.MultiPlatform;
import me.itstautvydas.uuidswapper.multiplatform.wrapper.BungeeCordPluginWrapper;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;

public class UUIDSwapperBungeeCord extends Plugin implements Listener {
    @Override
    public void onLoad() {
        MultiPlatform.init(PlatformType.BUNGEE, this, getProxy(), getLogger(), getDataFolder().toPath());
    }

    @Override
    public void onEnable() {
        if (MultiPlatform.onPluginEnable())
            getProxy().getPluginManager().registerListener(this, (BungeeCordPluginWrapper)MultiPlatform.get());
    }

    @Override
    public void onDisable() {
        MultiPlatform.onPluginDisable();
    }
}
