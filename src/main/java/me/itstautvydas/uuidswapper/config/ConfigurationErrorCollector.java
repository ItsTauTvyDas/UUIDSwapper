package me.itstautvydas.uuidswapper.config;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import me.itstautvydas.uuidswapper.exception.ConfigurationException;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class ConfigurationErrorCollector extends LinkedHashSet<String> {
    public static final String INVALID_ENUM = "Invalid enum value";
    public static final String MISSING_PROPERTY = "Missing required property";
    public static final String UNKNOWN_PROPERTY = "Invalid property";
    public static final String IMPLEMENTATION_ERROR = "Resolved implementation class does not extend DriverImplementation";
    public static final String UNKNOWN_DRIVER = "Unknown driver class %s, resolved";
    public static final String DRIVER_MISSING_CLASS = "Mising class field";
    public static final String ERROR_MESSAGE = "Failed to correctly parse the configuration!";

    private static final Map<Gson, ConfigurationErrorCollector> map = new HashMap<>();

    public static class ErrorCollector {
        private final ConfigurationErrorCollector list = new ConfigurationErrorCollector();
        private final List<String> keys = new ArrayList<>(); // Stupid work-around ngl
        private final Gson gson;

        private ErrorCollector(Gson gson) {
            this.gson = gson;
            if (!map.containsKey(gson))
                map.put(gson, new ConfigurationErrorCollector());
        }

        public boolean collect(String message, boolean severeError) {
            if (severeError)
                list.severe = true;
            return list.add(message);
        }

        public void collect(String message, String key, JsonReader reader, boolean severeError) {
            if (collect(String.format("%s %s at %s", message, key, reader.getPath()), severeError))
                keys.add(key);
        }

        public void push(Predicate<String> filter) {
            var listToPush = list;
            if (filter != null) {
                listToPush = new ConfigurationErrorCollector();
                var it = list.iterator();
                for (int i = 0; it.hasNext(); i++) {
                    var value = it.next();
                    var key = keys.get(i);
                    if (filter.test(key))
                        listToPush.add(value);
                }
            }
            map.get(gson).addAll(listToPush);
        }
    }

    public static ErrorCollector startCollecting(Gson gson) {
        return new ErrorCollector(gson);
    }

    public static void collect(Gson gson, String message, boolean severeError) {
        if (!map.containsKey(gson))
            map.put(gson, new ConfigurationErrorCollector());
        var list = map.get(gson);
        if (severeError)
            list.severe = true;
        list.add(message);
    }

    public static void collect(Gson gson, String message, String key, JsonReader reader, boolean severeError) {
        collect(gson, String.format("%s %s at %s", message, key, reader.getPath()), severeError);
    }

    public static void collect(Gson gson, String message, JsonReader reader, boolean severeError) {
        collect(gson, String.format("%s at %s", message, reader.getPath()), severeError);
    }

    public static void throwIfAnySevereErrors(Gson gson) {
        var list = map.get(gson);
        if (list != null && !list.isEmpty()) {
            if (list.severe)
                throw new ConfigurationException(ERROR_MESSAGE);
            map.remove(gson);
        }
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

    private boolean severe;
}
