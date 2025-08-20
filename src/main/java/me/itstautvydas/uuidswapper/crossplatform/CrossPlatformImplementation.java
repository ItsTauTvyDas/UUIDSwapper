package me.itstautvydas.uuidswapper.crossplatform;

import com.moandjiezana.toml.Toml;
import com.velocitypowered.api.proxy.ProxyServer;
import lombok.Getter;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.event.Level;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

@Getter
public abstract class CrossPlatformImplementation<L, S> {
    private static CrossPlatformImplementation<?, ?> CURRENT;
    private static CrossPlatformImplementation<Logger, ProxyServer> VELOCITY;
    private static CrossPlatformImplementation<java.util.logging.Logger, net.md_5.bungee.api.ProxyServer> BUNGEE;

    @SuppressWarnings("unchecked")
    public static <T, L> void init(PlatformType type, T serverObject, L loggerObject, Path configurationPath) {
        if (CURRENT != null)
            throw new RuntimeException("Cross-platform implementation is already done!");
        CrossPlatformImplementation<L, T> implementation = switch (type) {
            case VELOCITY -> (CrossPlatformImplementation<L, T>) getVelocity();
            case BUNGEE -> (CrossPlatformImplementation<L, T>) getBungeeCord();
        };
        if (implementation == null)
            throw new RuntimeException("Failed to initiate cross-platform implementation!");
        implementation.server = serverObject;
        implementation.logger = loggerObject;
        implementation.configurationPath = configurationPath;
        implementation.logInfo("Initiated " + type.name + " implementation.");
        CURRENT = implementation;
    }

    public static CrossPlatformImplementation<?, ?> getCurrent() {
        return CURRENT;
    }

    public static CrossPlatformImplementation<java.util.logging.Logger, net.md_5.bungee.api.ProxyServer> getBungeeCord() {
        if (BUNGEE != null)
            return BUNGEE;
        BUNGEE = new CrossPlatformImplementation<>() {
            @Override
            public void loadConfiguration() throws Exception {
                super.loadConfiguration();
                var handle = ConfigurationProvider.getProvider(YamlConfiguration.class).load(configurationPath.toFile());
                config = new ConfigurationWrapper.BungeeConfigurationWrapper(handle, null);
            }

            @Override
            public boolean isServerOnlineMode() {
                return server.getConfig().isOnlineMode();
            }

            @Override
            public void logInfo(String prefix, String message, Object... args) {
                if (prefix != null)
                    message = prefix + message;
                logger.log(java.util.logging.Level.INFO, message, args);
            }

            @Override
            public void logWarning(String prefix, String message, Throwable exception, Object... args) {
                if (prefix != null)
                    message = prefix + message;
                logger.log(java.util.logging.Level.WARNING, message, args);
                printException(exception, false);
            }

            @Override
            public void logError(String prefix, String message, Throwable exception, Object... args) {
                if (prefix != null)
                    message = prefix + message;
                logger.log(java.util.logging.Level.SEVERE, message, args);
                printException(exception, true);
            }

            private void printException(Throwable exception, boolean isError) {
                if (exception == null) return;
                exception.printStackTrace(new PrintWriter(new Writer() {
                    @Override
                    public void write(char @NotNull []cbuf, int off, int len) {
                        if (isError)
                            logger.severe(new String(cbuf, off, len));
                        else
                            logger.warning(new String(cbuf, off, len));
                    }

                    @Override
                    public void flush() {}

                    @Override
                    public void close() {}
                }));
            }
        };
        return BUNGEE;
    }

    public static CrossPlatformImplementation<Logger, ProxyServer> getVelocity() {
        if (VELOCITY != null)
            return VELOCITY;
        VELOCITY = new CrossPlatformImplementation<>() {
            @Override
            public void loadConfiguration() throws Exception {
                super.loadConfiguration();
                var handle = new Toml().read(configurationPath.toFile());
                config = new ConfigurationWrapper.VelocityConfigurationWrapper(handle, null);
            }

            @Override
            public boolean isServerOnlineMode() {
                return server.getConfiguration().isOnlineMode();
            }

            @Override
            public void logInfo(String prefix, String message, Object... args) {
                if (prefix != null)
                    message = prefix + message;
                logger.info(message, args);
            }

            @Override
            public void logWarning(String prefix, String message, Throwable exception, Object... args) {
                if (prefix != null)
                    message = prefix + message;
                logger.atLevel(Level.WARN)
                        .setMessage(exception == null ? null : exception.getMessage())
                        .setCause(exception)
                        .log(message, args);
            }

            @Override
            public void logError(String prefix, String message, Throwable exception, Object... args) {
                if (prefix != null)
                    message = prefix + message;
                logger.atLevel(Level.ERROR)
                        .setMessage(exception == null ? null : exception.getMessage())
                        .setCause(exception)
                        .log(message, args);
            }
        };
        return VELOCITY;
    }

    protected PlatformType platformType;
    protected ConfigurationWrapper config;
    protected L logger;
    protected S server;

    public Path configurationPath;

    public void loadConfiguration() throws Exception {
        if (Files.notExists(configurationPath)) {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(getResourceConfigurationName())) {
                if (in != null) {
                    logInfo("Copying configuration file...");
                    Files.copy(in, configurationPath);
                }
            }
        }
    }

    public String getResourceConfigurationName() {
        return switch (platformType) {
            case BUNGEE -> "config-bungee.yml";
            case VELOCITY -> "config-velocity.toml";
        };
    }

    public void logInfo(String message, Object ...args) {
        logInfo(null, message, args);
    }

    public void logWarning(String message, Throwable exception, Object ...args) {
        logWarning(null, message, exception, args);
    }

    public void logError(String message, Throwable exception, Object ...args) {
        logError(null, message, exception, args);
    }

    public abstract boolean isServerOnlineMode();
    public abstract void logInfo(String prefix, String message, Object ...args);
    public abstract void logWarning(String prefix, String message, Throwable exception, Object ...args);
    public abstract void logError(String prefix, String message, Throwable exception, Object ...args);
}
