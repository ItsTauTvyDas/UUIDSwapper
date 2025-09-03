package me.itstautvydas.uuidswapper.loader;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import me.itstautvydas.BuildConstants;
import me.itstautvydas.uuidswapper.enums.PlatformType;
import me.itstautvydas.uuidswapper.multiplatform.MultiPlatform;
import me.itstautvydas.uuidswapper.multiplatform.wrapper.VelocityPluginWrapper;
import org.slf4j.Logger;

import java.nio.file.Path;

@Plugin(id = "uuid-swapper",
        name = BuildConstants.NAME,
        version = BuildConstants.VERSION,
        description = BuildConstants.DESCRIPTION,
        url = BuildConstants.WEBSITE,
        authors = { "ItsTauTvyDas" })
public class UUIDSwapperVelocity {
    @Inject
    public UUIDSwapperVelocity(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        MultiPlatform.init(PlatformType.VELOCITY, this, server, logger, dataDirectory);
    }

    @Subscribe
    public void handleProxyInitialization(ProxyInitializeEvent event) {
        if (MultiPlatform.onPluginEnable()) {
            var velocity = (VelocityPluginWrapper)MultiPlatform.get();
            velocity.getServer().getEventManager().register(this, velocity);
        }
    }

    @Subscribe
    public void handleProxyShutdown(ProxyShutdownEvent event) {
        MultiPlatform.onPluginDisable();
    }
}
