package me.itstautvydas.uuidswapper;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.Getter;
import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;

import java.io.PrintWriter;
import java.io.Writer;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@UtilityClass
public class Utils {
    private final String PLACEHOLDER_PREFIX = "{";
    private final String PLACEHOLDER_SUFFIX = "}";

    public final String COMMAND_PERMISSION = "uuidswapper.command";
    public final String RELOAD_COMMAND_PERMISSION = "uuidswapper.command.reload";
    public final String DEBUG_COMMAND_PERMISSION = "uuidswapper.command.debug";
    public final String PRETEND_COMMAND_PERMISSION = "uuidswapper.command.pretend";

    public final String GENERIC_DISCONNECT_MESSAGE = "multiplayer.disconnect.generic";

    public boolean isRunningFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public String getSwappedValue(Map<String, String> map, String username, UUID uniqueId) {
        var value = map.get("u:" + username);
        if (value == null)
            value = map.get(uniqueId.toString());
        return value;
    }

    public String buildDataString(Map<String, String> map) {
        if (map.isEmpty())
            return "";
        return map.entrySet().stream()
                .map(entry -> {
                    String key = URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8);
                    String value = URLEncoder.encode(String.valueOf(entry.getValue()), StandardCharsets.UTF_8);
                    return key + "=" + value;
                }).collect(Collectors.joining("&"));
    }

    public JsonElement getJsonValue(JsonElement element, String path) {
        if (element == null || element.isJsonNull())
            return null;
        if (path == null || path.isEmpty())
            return element;
        var parts = path.split("\\.");
        for (var part : parts) {
            if (part.matches(".+\\[\\d+]")) { // Array indexes - key[0]
                var key = part.substring(0, part.indexOf('['));
                int index = Integer.parseInt(part.substring(part.indexOf('[') + 1, part.indexOf(']')));
                element = element.getAsJsonObject().get(key);
                if (element == null || !element.isJsonArray())
                    return null;
                element = element.getAsJsonArray().get(index);
            } else if (part.matches("\\[\\d+]")) { // Direct array indexes - [0]
                int index = Integer.parseInt(part.substring(1, part.indexOf(']')));
                if (!element.isJsonArray())
                    return null;
                element = element.getAsJsonArray().get(index);
            } else { // Normal objects
                element = element.getAsJsonObject().get(part);
            }
        }
        return element;
    }

    public Map<String, String> extractJsonPaths(String prefix, JsonElement data) {
        Map<String, String> result = new HashMap<>();
        collectJsonPaths(prefix, "", data, result);
        return result;
    }

    private void collectJsonPaths(String prefix, String currentPath, JsonElement node, Map<String, String> result) {
        if (node instanceof JsonObject json) {
            for (var entry : json.entrySet()) {
                String newPath = currentPath.isEmpty() ? entry.getKey() : currentPath + "." + entry.getKey();
                collectJsonPaths(prefix, newPath, entry.getValue(), result);
            }
        } else if (node instanceof JsonArray json) {
            for (int i = 0; i < json.size(); i++) {
                String newPath = currentPath + "[" + i + "]";
                collectJsonPaths(prefix, newPath, json.get(i), result);
            }
        } else {
            String value ;
            if (node != null && node.isJsonPrimitive())
                value = node.getAsString();
            else if (node == null)
                value = null;
            else
                value = node.toString();
            result.put(prefix + currentPath, value);
        }
    }

    public String replacePlaceholders(String string, Map<String, Object> placeholders) {
        if (placeholders == null)
            return string;
        for (var entry : placeholders.entrySet()) {
            var value = entry.getValue();
            if (value == null)
                value = "null";
            string = string.replace(PLACEHOLDER_PREFIX + entry.getKey() + PLACEHOLDER_SUFFIX, value.toString());
        }
        return string;
    }

    public void addExceptionPlaceholders(Throwable ex, Map<String, Object> placeholders) {
        placeholders.put("error.class", ex.getClass().getName());
        placeholders.put("error.message", ex.getMessage());
        placeholders.put("error.class-name", ex.getClass().getSimpleName());
    }

    public UUID offlineUniqueIdIfNull(String username, UUID uuid) {
        if (uuid == null)
            return Utils.generateOfflineUniqueId(username);
        return uuid;
    }

    public void printException(Throwable exception, Consumer<String> onMessageLine) {
        if (exception == null) return;
        exception.printStackTrace(new PrintWriter(new Writer() {
            @Override
            public void write(char @NotNull []cbuf, int off, int len) {
                var str = new String(cbuf, off, len).trim();
                if (str.isBlank())
                    return;
                onMessageLine.accept(str);
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        }));
    }

    public UUID generateOfflineUniqueId(String username) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
    }

    public String toLoggerMessage(String prefix, String message, Object[] args) {
        message = String.format(message, args);
        if (prefix == null)
            return message;
        return "[" + prefix + "]: " + message;
    }

    public UUID toUniqueId(String uniqueId) {
        if (!uniqueId.contains("-")) {
            return UUID.fromString(
                    uniqueId.substring(0, 8) + "-" +
                            uniqueId.substring(8, 12) + "-" +
                            uniqueId.substring(12, 16) + "-" +
                            uniqueId.substring(16, 20) + "-" +
                            uniqueId.substring(20)
            );
        }
        return UUID.fromString(uniqueId);
    }

    // https://stackoverflow.com/questions/34092373/merge-extend-json-objects-using-gson-in-java
//    public static void merge(JsonObject defaultConfiguration, JsonObject currentConfiguration) {
//        for (Map.Entry<String, JsonElement> rightEntry : defaultConfiguration.entrySet()) {
//            String rightKey = rightEntry.getKey();
//            JsonElement rightVal = rightEntry.getValue();
//            if (currentConfiguration.has(rightKey)) {
//                // conflict
//                JsonElement leftVal = currentConfiguration.get(rightKey);
//                if (leftVal.isJsonArray() && rightVal.isJsonArray()) {
//                    JsonArray leftArr = leftVal.getAsJsonArray();
//                    JsonArray rightArr = rightVal.getAsJsonArray();
//                    // concat the arrays -- there cannot be a conflict in an array, it's just a collection of stuff
//                    for (int i = 0; i < rightArr.size(); i++)
//                        leftArr.add(rightArr.get(i));
//                } else if (leftVal.isJsonObject() && rightVal.isJsonObject()) {
//                    // recursive merging
//                    merge(rightVal.getAsJsonObject(), leftVal.getAsJsonObject());
//                } else {// not both arrays or objects, normal merge with conflict resolution
//                    if (leftVal.isJsonNull() && !rightVal.isJsonNull()) {
//                        currentConfiguration.add(rightKey, rightVal);
//                    }
//                }
//            } else { // no conflict, add to the object
//                currentConfiguration.add(rightKey, rightVal);
//            }
//        }
//    }
}
