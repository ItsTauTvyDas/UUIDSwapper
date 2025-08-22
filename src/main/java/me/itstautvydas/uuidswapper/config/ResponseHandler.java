package me.itstautvydas.uuidswapper.config;

import me.itstautvydas.uuidswapper.Utils;
import me.itstautvydas.uuidswapper.crossplatform.ConfigurationWrapper;
import me.itstautvydas.uuidswapper.enums.ResponseHandlerState;

import java.util.Map;

public class ResponseHandler {
    private final ConfigurationWrapper config;

    public ResponseHandler(ConfigurationWrapper config) {
        this.config = config;
    }

    public Boolean isPlayerAllowedToJoin() {
        if (!config.contains("forcefully-allow-player-to-join"))
            return null;
        return config.getBoolean("forcefully-allow-player-to-join");
    }

    public ResponseHandlerState getExecuteState() {
        return ResponseHandlerState.fromString(config.getString("state"), ResponseHandlerState.AFTER_UUID);
    }

    public Boolean shouldUseFallback() {
        if (!config.contains("use-fallback"))
            return null;
        return config.getBoolean("use-fallback");
    }

    public boolean shouldApplyProperties() {
        return config.getBoolean("apply-properties", true);
    }

    public String getDisconnectMessage() {
        return Utils.toMessage(config, "disconnect-message");
    }

    public long getOrder() {
        return config.getLong("order", 9999L);
    }

    public String getConditionsMode() {
        return config.getString("conditions-mode", "AND");
    }

    public boolean shouldIgnoreConditionsCase() {
        return config.getBoolean("ignore-conditions-case", false);
    }

    public boolean testConditions(Map<String, Object> placeholders) {
        var table = config.getSection("conditions");
        if (table == null)
            return false;
        var conditions = table.toMap();
        if (conditions.isEmpty())
            return true;
        Boolean result = null;
        for (var entry : conditions.entrySet()) {
            var key = Utils.escapeQuotes(entry.getKey());
            var conditionValue = entry.getValue();
            if (conditionValue == null)
                conditionValue = "[null]";
            boolean conditionResult;
            if (key.startsWith("::")) {
                if (entry.getValue() instanceof Boolean bool)
                    conditionResult = placeholders.containsKey(key.substring(2)) == bool;
                else
                    conditionResult = false;
            } else {
                var value = placeholders.get(key);
                if (value == null)
                    value = "[null]";

                if (value instanceof String)
                    value = Utils.escapeQuotes(value.toString());

                if (shouldIgnoreConditionsCase())
                    conditionResult = conditionValue.toString().equalsIgnoreCase(value.toString());
                else
                    conditionResult = conditionValue.equals(value);
            }
            if (result == null) {
                result = conditionResult;
            } else {
                if (getConditionsMode().equals("AND"))
                    result = result && conditionResult;
                else
                    result = result || conditionResult;
            }
        }
        return result != null && result;
    }
}
