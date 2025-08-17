package me.itstautvydas.uuidswapper.config;

import com.google.common.collect.ImmutableMap;
import com.moandjiezana.toml.Toml;
import com.velocitypowered.api.proxy.ProxyServer;

import java.util.*;

public class Configuration {
    private static final Map<String, Object> EMPTY_MAP = ImmutableMap.of();

    private Toml config;
    private Toml apiDefaultsConfig;
    private final Map<String, ApiConfiguration> apis = new HashMap<>();
    private ApiConfiguration defaultApiConfig;
    private final ProxyServer server;

    public Configuration(Toml config, ProxyServer server) {
        this.server = server;
        reload(config);
    }

    public void reload(Toml mainConfig) {
        this.config = mainConfig;
        this.apiDefaultsConfig = mainConfig.getTable("online-uuids.api-defaults");
        this.defaultApiConfig = new ApiConfiguration(this.apiDefaultsConfig);

        var list = mainConfig.getTables("online-uuids.api");
        apis.clear();
        if (list != null) {
            for (var api : list)
                apis.put(api.getString("name"), new ApiConfiguration(api));
        }
    }

    public ApiConfiguration getDefaultApi() {
        return defaultApiConfig;
    }

    public boolean areOnlineUuidEnabled() {
        return !server.getConfiguration().isOnlineMode() && config.getBoolean("online-uuids.enabled", false);
    }

    private boolean isPlayerExceptional(String value, boolean isUsername) {
        if (!config.getBoolean("online-uuids.exceptions.enabled"))
            return true;
        var reversed = config.getBoolean("online-uuids.exceptions.reversed");
        var list = config.getList("online-uuids.exceptions.list");
        if (isUsername)
            value = "u:" + value;
        return reversed != list.contains(value);
    }

    private boolean isPlayerExceptional(String username) {
        return isPlayerExceptional(username, true);
    }

    private boolean isPlayerExceptional(UUID uuid) {
        return isPlayerExceptional(uuid.toString(), false);
    }

    public boolean stillSwapUuids() {
        return config.getBoolean("online-uuids.swap-uuids", true);
    }

    public boolean isForcedOfflineModeEnabled() {
        return server.getConfiguration().isOnlineMode() && config.getBoolean("forced-offline-mode.enabled", false);
    }

    public boolean isForcedOfflineModeSetByDefault() {
        return config.getBoolean("forced-offline-mode.forced-by-default", true);
    }

    public List<String> getForcedOfflineModeExceptions() {
        return config.getList("forced-offline-mode.exceptions", new ArrayList<>());
    }

    public Map<String, Object> getSwappedUuids() {
        var table = config.getTable("swapped-uuids");
        if (table == null)
            return EMPTY_MAP;
        return table.toMap();
    }

    public Map<String, Object> getCustomPlayerNames() {
        var table = config.getTable("custom-player-names");
        if (table == null)
            return EMPTY_MAP;
        return table.toMap();
    }

    public boolean isFilteringEnabled() {
        return config.getBoolean("online-uuids.exceptions.enabled", false);
    }

    public boolean isFilteringReversed() {
        return config.getBoolean("online-uuids.exceptions.reversed", false);
    }

    public String getApiName() {
        return config.getString("online-uuids.use-api");
    }

    public List<String> getApiFallbacks() {
        return config.getList("online-uuids.fallback-apis", new ArrayList<>());
    }

    public ApiConfiguration getApi(String name) {
        return apis.get(name);
    }

    public long getMaxTimeout() {
        return config.getLong("max-timeout", 6000L);
    }

    public long getMinTimeout() {
        return config.getLong("min-timeout", 1000L);
    }

    public long getFallbackApiRememberTime() {
        return config.getLong("fallback-api-remember-time", 21600L);
    }

    public boolean getCheckDependingOnIPAddress() {
        return config.getBoolean("online-uuids.username-changes.check-depending-on-ip-address", false);
    }

    public class ApiConfiguration {
        public class ResponseHandler {
            private final Toml config;

            public ResponseHandler(Toml config) {
                this.config = config;
            }

            public boolean isPlayerAllowedToJoin() {
                return config.getBoolean("allow-player-to-join", false);
            }

            public String getDisconnectMessage() {
                return config.getString("disconnect-message", ApiConfiguration.this.getDefaultDisconnectMessage());
            }

            public String getConditionsMode() {
                return config.getString("conditions-mode", "AND");
            }

            public boolean ignoreConditionsCase() {
                return config.getBoolean("ignore-conditions-case", false);
            }

            public Boolean testConditions(Map<String, Object> placeholders) {
                var table = config.getTable("conditions");
                if (table == null)
                    return null;
                var conditions = table.toMap();
                Boolean result = null;
                for (var entry : conditions.entrySet()) {
                    var value = placeholders.get(entry.getKey());
                    if (value == null || entry.getValue() == null)
                        continue;
                    boolean conditionResult;
                    if (ignoreConditionsCase())
                        conditionResult = entry.getValue().toString().equalsIgnoreCase(value.toString());
                    else
                        conditionResult = entry.getValue().equals(value);
                    if (result == null)
                        result = conditionResult;
                    else
                        if (getConditionsMode().equalsIgnoreCase("and"))
                            result = result && conditionResult;
                        else
                            result = result || conditionResult;
                }
                return result;
            }
        }

        private final Toml config;
        private final List<ResponseHandler> handlers = new ArrayList<>();

        public ApiConfiguration(Toml config) {
            this.config = config;

            var handlers = config.getTables("online-uuids.api.response-handlers");
            if (handlers != null) {
                for (var handler : handlers)
                    this.handlers.add(new ResponseHandler(handler));
            }
        }

        public List<ResponseHandler> getResponseHandlers() {
            return handlers;
        }

        public boolean canPlayerJoin(Map<String, Object> placeholders) {
            for (var handler : handlers) {
                var result = handler.testConditions(placeholders);
                if (result)
                    return true;
            }
            return false;
        }

        public String getName() {
            return config.getString("name");
        }

        public String getEndpoint() {
            return getApiString("endpoint");
        }

        public Long getTimeout() {
            return getApiLong("timeout", 3000L);
        }

        public String getPathToUuid() {
            return getApiString("json-path-to-uuid");
        }

        public String getDefaultDisconnectMessage() {
            return getApiString("default-disconnect-message");
        }

        public String getServiceDownDisconnectMessage() {
            return getApiString("api-down-disconnect-message");
        }

        public String getServiceTimedOutDisconnectMessage() {
            return getApiString("api-timeout-disconnect-message");
        }

        public String getUuidIsBadDisconnectMessage() {
            return getApiString("bad-uuid-disconnect-message");
        }

        public String getUnknownErrorDisconnectMessage() {
            return getApiString("unknown-error-disconnect-message");
        }

        public boolean isDebugEnabled() {
            return getApiBoolean("debug", false);
        }

        public Map<String, Object> getRequestHeaders() {
            return getApiMap("headers");
        }

        public Map<String, Object> getRequestPostData() {
            return getApiMap("post-data");
        }

        public Map<String, Object> getRequestQueryData() {
            return getApiMap("query-data");
        }

        public long getExpectedStatusCode() {
            return getApiLong("expected-status-code", 200L);
        }

        public String getMethod() {
            var method = getApiString("method");
            if (method == null)
                return "GET";
            return method;
        }

        public boolean shouldDisconnectOnServiceDown() {
            return !getApiBoolean("use-fallbacks.on-api-down", true);
        }

        public boolean shouldDisconnectOnInvalidUuid() {
            return !getApiBoolean("use-fallbacks.on-invalid-uuid", true);
        }

        public boolean shouldDisconnectOnBadUuidPath() {
            return !getApiBoolean("use-fallbacks.on-bad-uuid-path", true);
        }

        public boolean shouldDisconnectOnUnknownError() {
            return !getApiBoolean("use-fallbacks.on-unknown-error", true);
        }

        private String getApiString(String path) {
            return config.getString(path, apiDefaultsConfig == null ? null : apiDefaultsConfig.getString(path));
        }

        private boolean getApiBoolean(String path, boolean defaultValue) {
            return config.getBoolean(path, apiDefaultsConfig == null ? defaultValue : apiDefaultsConfig.getBoolean(path, defaultValue));
        }

        private Long getApiLong(String path, Long defaultValue) {
            return config.getLong(path, apiDefaultsConfig == null ? defaultValue : apiDefaultsConfig.getLong(path, defaultValue));
        }

        private Map<String, Object> getApiMap(String path) {
            var table = config.getTable(path);
            if (table == null && apiDefaultsConfig != null)
                table = apiDefaultsConfig.getTable(path);
            return table == null ? EMPTY_MAP : table.toMap();
        }
    }
}
