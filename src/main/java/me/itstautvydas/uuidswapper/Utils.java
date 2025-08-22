package me.itstautvydas.uuidswapper;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.experimental.UtilityClass;
import me.itstautvydas.uuidswapper.crossplatform.PluginWrapper;
import me.itstautvydas.uuidswapper.enums.PlatformType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@UtilityClass
public class Utils {
    private static final Pattern LOG_MESSAGE_BRACKETS = Pattern.compile("\\{}");

    public static String getSwappedValue(Map<String, String> map, String username, UUID uniqueId) {
        var value = map.get("u:" + username);
        if (value == null)
            value = map.get(uniqueId.toString());
        return value;
    }

    public static String buildDataString(Map<String, String> map) {
        if (map.isEmpty())
            return "";
        return map.entrySet().stream()
                .map(entry -> {
                    String key = URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8);
                    String value = URLEncoder.encode(String.valueOf(entry.getValue()), StandardCharsets.UTF_8);
                    return key + "=" + value;
                }).collect(Collectors.joining("&"));
    }

    public static JsonElement getJsonValue(JsonElement element, String path) {
        if (element == null || element.isJsonNull())
            return null;
        if (path == null || path.isEmpty())
            return element;
        String[] parts = path.split("\\.");
        for (String part : parts) {
            if (part.matches(".+\\[\\d+]")) { // Array indexes - key[0]
                String key = part.substring(0, part.indexOf('['));
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

    public static Map<String, String> extractJsonPaths(String prefix, JsonElement data) {
        Map<String, String> result = new HashMap<>();
        collectJsonPaths(prefix, "", data, result);
        return result;
    }

    private static void collectJsonPaths(String prefix, String currentPath, JsonElement node, Map<String, String> result) {
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
            result.put(prefix + currentPath, node == null ? null : node.toString());
        }
    }

    public static String replacePlaceholders(String string, Map<String, Object> placeholders, String prefix, String suffix) {
        if (prefix == null) prefix = "";
        if (suffix == null) suffix = "";
        for (var entry : placeholders.entrySet()) {
            var value = entry.getValue();
            if (value == null)
                value = "null";
            string = string.replace(prefix + entry.getKey() + suffix, value.toString());
        }
        return string;
    }

    public static String replacePlaceholders(String string, Map<String, Object> placeholders) {
        return replacePlaceholders(string, placeholders, "{", "}");
    }

    public static void addExceptionPlaceholders(Throwable ex, Map<String, Object> placeholders) {
        placeholders.put("error.class", ex.getClass().getName());
        placeholders.put("error.message", ex.getMessage());
        placeholders.put("error.class-name", ex.getClass().getSimpleName());
    }

    public static Component toComponent(String string) {
        return MiniMessage.miniMessage().deserialize(string);
    }

    public static UUID requireUuid(String username, UUID uuid) {
        if (uuid == null)
            return PluginWrapper.getCurrent().generateOfflinePlayerUuid(username);
        return uuid;
    }

    public static String fixLogMessageFormat(String message, PlatformType type) {
        if (type == PlatformType.BUNGEE) {
            var counter = new AtomicInteger(0);
            var matcher = LOG_MESSAGE_BRACKETS.matcher(message);
            return matcher.replaceAll(match -> "{" + counter.getAndIncrement() + "}");
        }
        return message;
    }
}
