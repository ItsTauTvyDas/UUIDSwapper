package me.itstautvydas.uuidswapper.config;

import com.google.common.collect.ImmutableMap;
import com.moandjiezana.toml.Toml;

import java.util.*;

public class Configuration {
    private static final Map<String, Object> EMPTY_MAP = ImmutableMap.of();

    private Toml config;
    private Toml apiDefaultsConfig;
    private final Map<String, APIConfiguration> apis = new HashMap<>();

    public Configuration(Toml config) {
        reload(config);
    }

    public void reload(Toml mainConfig) {
        this.config = mainConfig;
        this.apiDefaultsConfig = mainConfig.getTable("online-uuids.api-defaults");

        var list = mainConfig.getTables("online-uuids.api");
        apis.clear();
        if (list != null) {
            for (var api : list)
                apis.put(api.getString("name"), new APIConfiguration(api));
        }
    }

    public boolean areOnlineUUIDsEnabled() {
        return config.getBoolean("online-uuids.enabled", false);
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

    public boolean swapUUIDs() {
        return config.getBoolean("online-uuids.swap-uuids", true);
    }

    public Map<String, Object> getSwappedUUIDs() {
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

    public String getAPIName() {
        return config.getString("online-uuids.use-api");
    }

    public List<String> getAPIFallbacks() {
        return config.getList("online-uuids.fallback-apis", new ArrayList<>());
    }

    public APIConfiguration getAPI(String name) {
        return apis.get(name);
    }

    public long getMaxTimeout() {
        return config.getLong("max-timeout", 6000L);
    }

    public long getFallbackAPIRememberTime() {
        return config.getLong("fallback-api-remember-time", 21600L);
    }

    public boolean getCheckDependingOnIPAddress() {
        return config.getBoolean("online-uuids.username-changes.check-depending-on-ip-address", false);
    }

    public class APIConfiguration {
        public class ResponseHandler {
            private final Toml config;

            public ResponseHandler(Toml config) {
                this.config = config;
            }

            public boolean isPlayerAllowedToJoin() {
                return config.getBoolean("allow-player-to-join", false);
            }

            public String getDisconnectMessage() {
                return config.getString("disconnect-message", APIConfiguration.this.getDefaultDisconnectMessage());
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
                        result = switch (getConditionsMode()) {
                            case "AND" -> result && conditionResult;
                            case "OR" -> result || conditionResult;
                            default -> result;
                        };
                }
                return result;
            }
        }

        private final Toml config;
        private final List<ResponseHandler> handlers = new ArrayList<>();

        public APIConfiguration(Toml config) {
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
            return getAPIString("endpoint");
        }

        public Long getTimeout() {
            return getAPILong("timeout");
        }

        public String getPathToUUID() {
            return getAPIString("json-path-to-uuid");
        }

        public String getDefaultDisconnectMessage() {
            return getAPIString("default-disconnect-message");
        }

        public String getServiceDownDisconnectMessage() {
            return getAPIString("api-down-disconnect-message");
        }

        public String getServiceTimedOutDisconnectMessage() {
            return getAPIString("api-timeout-disconnect-message");
        }

        public boolean isDebugEnabled() {
            return getAPIBoolean("debug", false);
        }

        public Map<String, Object> getRequestHeaders() {
            return getAPIMap("headers");
        }

        public Map<String, Object> getRequestPostData() {
            return getAPIMap("post-data");
        }

        public Map<String, Object> getRequestQueryData() {
            return getAPIMap("query-data");
        }

        private String getAPIString(String path) {
            return config.getString(path, apiDefaultsConfig == null ? null : apiDefaultsConfig.getString(path));
        }

        private boolean getAPIBoolean(String path, boolean defaultValue) {
            return config.getBoolean(path, apiDefaultsConfig == null ? defaultValue : apiDefaultsConfig.getBoolean(path, defaultValue));
        }

        private Long getAPILong(String path) {
            return config.getLong(path, apiDefaultsConfig == null ? null : apiDefaultsConfig.getLong(path));
        }

        private Map<String, Object> getAPIMap(String path) {
            var table = config.getTable(path);
            if (table == null && apiDefaultsConfig != null)
                table = apiDefaultsConfig.getTable(path);
            return table == null ? EMPTY_MAP : table.toMap();
        }
    }
}
