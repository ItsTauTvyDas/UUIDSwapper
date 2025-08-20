package me.itstautvydas.uuidswapper.config;

import com.moandjiezana.toml.Toml;
import me.itstautvydas.uuidswapper.Utils;
import me.itstautvydas.uuidswapper.crossplatform.ConfigurationWrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ServiceConfiguration {

    private final ConfigurationWrapper config;
    private final List<ResponseHandler> handlers = new ArrayList<>();

    public ServiceConfiguration(ConfigurationWrapper config) {
        this.config = config;

        var handlers = config.getSections("online-uuids.services.response-handlers");
        if (handlers != null) {
            for (var handler : handlers)
                this.handlers.add(new ResponseHandler(handler));
        }
    }

    public List<ResponseHandler> getResponseHandlers() {
        return handlers;
    }

    public ResponseHandler executeResponseHandlers(Map<String, Object> placeholders) {
        for (var handler : getResponseHandlers()) {
            if (handler.testConditions(placeholders))
                return handler;
        }
        return null;
    }

    public String getName() {
        return config.getString("name");
    }

    public String getEndpoint() {
        return config.getString("endpoint");
    }

    public Long getTimeout() {
        return config.getLong("timeout", 3000L);
    }

    public boolean doesAllowCaching() {
        return config.getBoolean("allow-caching", true);
    }

    public String getPathToUuid() {
        return config.getString("json-path-to-uuid", "");
    }

    public String getPathToTextures() {
        return config.getString("json-path-to-textures");
    }

    public String getPathToProperties() {
        return config.getString("json-path-to-properties");
    }

    public String getDefaultDisconnectMessage() {
        return Utils.toMessage(config, "default-disconnect-message");
    }

    public String getConnectionErrorDisconnectMessage() {
        return Utils.toMessage(config, "connection-error-disconnect-message");
    }

    public String getServiceTimedOutDisconnectMessage() {
        return Utils.toMessage(config, "service-timeout-disconnect-message");
    }

    public String getBadUuidDisconnectMessage() {
        return Utils.toMessage(config, "bad-uuid-disconnect-message");
    }

    public String getUnknownErrorDisconnectMessage() {
        return Utils.toMessage(config, "unknown-error-disconnect-message");
    }

    public String getBadStatusDisconnectMessage() {
        return Utils.toMessage(config, "service-bad-status-disconnect-message");
    }

    public boolean isDebugEnabled() {
        return config.getBoolean("debug", false);
    }

    public Map<String, Object> getRequestHeaders() {
        return config.getMap("headers", true);
    }

    public Map<String, Object> getRequestPostData() {
        return config.getMap("post-data", true);
    }

    public Map<String, Object> getRequestQueryData() {
        return config.getMap("query-data", true);
    }

    public long getExpectedStatusCode() {
        return config.getLong("expected-status-code", 200L);
    }

    public String getMethod() {
        return config.getString("method", "GET");
    }

    public boolean shouldDisconnectOnConnectionError() {
        return !config.getBoolean("use-fallbacks.on-connection-error", true);
    }

    public boolean shouldDisconnectOnInvalidUuid() {
        return !config.getBoolean("use-fallbacks.on-invalid-uuid", true);
    }

    public boolean shouldDisconnectOnBadUuidPath() {
        return !config.getBoolean("use-fallbacks.on-bad-uuid-path", true);
    }

    public boolean shouldDisconnectOnUnknownError() {
        return !config.getBoolean("use-fallbacks.on-unknown-error", true);
    }

    public boolean shouldDisconnectOnBadStatus() {
        return !config.getBoolean("use-fallbacks.on-bad-status", true);
    }
}
