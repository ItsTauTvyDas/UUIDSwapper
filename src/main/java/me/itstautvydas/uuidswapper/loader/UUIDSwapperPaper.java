package me.itstautvydas.uuidswapper.loader;

import me.itstautvydas.uuidswapper.enums.PlatformType;
import me.itstautvydas.uuidswapper.multiplatform.MultiPlatform;
import me.itstautvydas.uuidswapper.multiplatform.wrapper.PaperPluginWrapper;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class UUIDSwapperPaper extends JavaPlugin {
    @Override
    public void onLoad() {
        MultiPlatform.init(PlatformType.PAPER.verifyFolia(), this, Bukkit.getServer(), getLogger(), getDataPath());
    }

    @Override
    public void onEnable() {
        if (MultiPlatform.onPluginEnable()) {
            Bukkit.getPluginManager().registerEvents((PaperPluginWrapper)MultiPlatform.get(), this);
        } else {
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        MultiPlatform.onPluginDisable();
    }
}
