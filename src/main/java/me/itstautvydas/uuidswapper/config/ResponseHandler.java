package me.itstautvydas.uuidswapper.config;

import com.moandjiezana.toml.Toml;
import me.itstautvydas.uuidswapper.Utils;
import me.itstautvydas.uuidswapper.crossplatform.ConfigurationWrapper;
import me.itstautvydas.uuidswapper.enums.ResponseHandlerState;

import java.util.Map;

public class ResponseHandler {
    private final ConfigurationWrapper config;

    public ResponseHandler(ConfigurationWrapper config) {
        this.config = config;
    }

    public boolean isPlayerAllowedToJoin() {
        return config.getBoolean("allow-player-to-join", false);
    }

    public ResponseHandlerState getExecuteTime() {
        return ResponseHandlerState.fromString(config.getString("when"), ResponseHandlerState.BEFORE_UUID);
    }

    public String getDisconnectMessage() {
        return Utils.toMessage(config, "disconnect-message");
    }

    public String getConditionsMode() {
        return config.getString("conditions-mode", "AND");
    }

    public boolean ignoreConditionsCase() {
        return config.getBoolean("ignore-conditions-case", false);
    }

    public Boolean testConditions(Map<String, Object> placeholders) {
        var table = config.getSection("conditions");
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
