package me.itstautvydas.uuidswapper.json;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import lombok.Getter;
import me.itstautvydas.uuidswapper.exception.ConfigurationException;

import java.util.*;
import java.util.function.Consumer;

public class ConfigurationErrorCollector extends HashSet<String> {
    public static final String INVALID_ENUM = "Invalid enum value";
    public static final String MISSING_PROPERTY = "Missing required property";
    public static final String INVALID_PROPERTY = "Invalid property";
    public static final String ERROR_MESSAGE = "Failed to correctly parse configuration!";

    private static final Map<Gson, ConfigurationErrorCollector> map = new HashMap<>();

    public static void collect(Gson gson, String message, boolean severeError) {
        if (!map.containsKey(gson))
            map.put(gson, new ConfigurationErrorCollector());
        var list = map.get(gson);
        if (severeError)
            list.severe = true;
        list.add(message);
    }

    public static void collect(Gson gson, String message, Object value, JsonReader reader, boolean severeError) {
        collect(gson, String.format("%s %s at %s", message, value, reader.getPath()), severeError);
    }

    public static void throwIfAny(Gson gson, boolean remove) {
        var list = remove ? map.remove(gson) : map.get(gson);
        if (list != null && !list.isEmpty())
            throw new ConfigurationException(ERROR_MESSAGE);
    }

    public static void print(Gson gson, Consumer<String> printAcceptor) {
        var list = map.get(gson);
        if (list != null)
            for (var message : list)
                printAcceptor.accept(message);
    }

    public static boolean isSevere(Gson gson) {
        var list = map.get(gson);
        if (list != null)
            return list.severe;
        return false;
    }

    public static Set<String> clear(Gson gson) {
        return map.remove(gson);
    }

    private boolean severe;
}
