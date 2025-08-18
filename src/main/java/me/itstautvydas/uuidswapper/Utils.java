package me.itstautvydas.uuidswapper;

import com.google.common.collect.ImmutableMap;
import com.google.gson.*;
import com.moandjiezana.toml.Toml;
import com.velocitypowered.api.util.GameProfile;
import com.velocitypowered.api.util.UuidUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class Utils {
    public static final Map<String, Object> EMPTY_MAP = ImmutableMap.of();

    private Utils() {}

    public static GameProfile createProfile(String username, UUID uuid, GameProfile profile) {
        if (username == null)
            username = profile.getName();
        return new GameProfile(uuid == null ? profile.getId() : uuid, username, profile.getProperties());
    }

    public static GameProfile createProfile(String username, String uuid, GameProfile profile) {
        if (username == null)
            username = profile.getName();
        return new GameProfile(uuid == null ? profile.getId() : UUID.fromString(uuid), username, profile.getProperties());
    }

    public static boolean containsPlayer(List<String> list, String username, UUID id) {
        if (list.contains("\"u:" + username + "\""))
            return true;
        return list.contains("\"" + id.toString() + "\"");
    }

    public static String getSwappedValue(Map<String, Object> map, GameProfile profile) {
        var value = map.get("\"u:" + profile.getName() + "\"");
        if (value == null)
            value = map.get("\"" + profile.getId().toString() + "\"");
        if (value != null)
            return value.toString();
        return null;
    }

    public static String buildDataString(Map<String, Object> map) {
        if (map.isEmpty())
            return "";
        return map.entrySet().stream()
                .map(entry -> {
                    String key = URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8);
                    String value = URLEncoder.encode(String.valueOf(entry.getValue()), StandardCharsets.UTF_8);
                    return key + "=" + value;
                }).collect(Collectors.joining("&"));
    }

    public static String getJsonValue(JsonElement element, String path) {
        if (element == null || element.isJsonNull())
            return null;
        if (path == null || path.isEmpty())
            return element.isJsonPrimitive() ? element.getAsString() : element.toString();
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
        return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
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
            result.put(prefix + currentPath, node == null ? "[null]" : node.toString());
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
            return UuidUtils.generateOfflinePlayerUuid(username);
        return uuid;
    }

    public static String toMessage(Toml config, String path) {
        if (config.containsPrimitive(path))
            return config.getString(path);
        else if (config.containsTableArray(path)) {
            List<String> list = config.getList(path);
            if (list == null)
                return null;
            return String.join("\n", list);
        }
        return null;
    }

    // Unused
//    @SuppressWarnings("unchecked")
//    public static Toml getTableWithDefaults(String pathToTable, Toml toml, Toml defaultToml) {
//        Objects.requireNonNull(pathToTable);
//        Objects.requireNonNull(toml);
//        Objects.requireNonNull(defaultToml);
//
//        try {
//            var method = toml.getClass().getDeclaredMethod("get", String.class);
//            method.setAccessible(true);
//            var map = (Map<String, Object>) method.invoke(toml, pathToTable);
//
//            var constructor = toml.getClass().getDeclaredConstructor(Toml.class, Map.class);
//            constructor.setAccessible(true);
//
//            return map != null ? constructor.newInstance(defaultToml, map) : null;
//        } catch (Exception ex) {
//            return toml.getTable(pathToTable);
//        }
//    }

    @SuppressWarnings("unchecked")
    public static List<Toml> getTablesWithDefaults(String pathToTable, Toml toml, Toml defaultToml) {
        Objects.requireNonNull(pathToTable);
        Objects.requireNonNull(toml);
        Objects.requireNonNull(defaultToml);

        try {
            var method = toml.getClass().getDeclaredMethod("get", String.class);
            method.setAccessible(true);
            var tableArray = (List<Map<String, Object>>) method.invoke(toml, pathToTable);

            var constructor = toml.getClass().getDeclaredConstructor(Toml.class, Map.class);
            constructor.setAccessible(true);

            if (tableArray == null)
                return null;

            var tables = new ArrayList<Toml>();
            for (Map<String, Object> table : tableArray)
                tables.add(constructor.newInstance(defaultToml, table));

            return tables;
        } catch (Exception ex) {
            ex.printStackTrace();
            return toml.getTables(pathToTable);
        }
    }

    public static Map<String, Object> getTableOrEmpty(Toml toml, String path) {
        var table = toml.getTable(path);
        return table == null ? Utils.EMPTY_MAP : table.toMap();
    }
}
