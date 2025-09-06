package me.itstautvydas.uuidswapper.json;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import me.itstautvydas.uuidswapper.config.ConfigurationErrorCollector;
import me.itstautvydas.uuidswapper.database.DriverImplementation;

import java.io.IOException;

public class DriverPolymorphicAdapterFactory implements TypeAdapterFactory {
    public static final String CLASS_INDICATOR_FIELD_NAME = "class";
    private static final String DEFAULT_PACKAGE = "me.itstautvydas.uuidswapper.database.driver";

    @Override
    @SuppressWarnings("unchecked")
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
        if (type.getRawType() != DriverImplementation.class)
            return null;

        TypeAdapter<JsonElement> elementAdapter = gson.getAdapter(JsonElement.class);

        return (TypeAdapter<T>) new TypeAdapter<DriverImplementation>() {
            @Override
            public void write(JsonWriter out, DriverImplementation value) throws IOException {
                TypeAdapter<DriverImplementation> delegate = gson.getDelegateAdapter(
                        DriverPolymorphicAdapterFactory.this,
                        TypeToken.get(DriverImplementation.class)
                );
                delegate.write(out, value);
            }

            @Override
            public DriverImplementation read(JsonReader in) throws IOException {
                JsonObject obj = elementAdapter.read(in).getAsJsonObject();

                JsonElement discEl = obj.get(CLASS_INDICATOR_FIELD_NAME);
                if (discEl == null || discEl.isJsonNull()) {
                    ConfigurationErrorCollector.collect(gson, ConfigurationErrorCollector.DRIVER_MISSING_CLASS, in, false);
                    return null;
                }
                String raw = discEl.getAsString().trim();
                String resolved = raw.contains(".") ? raw : DEFAULT_PACKAGE + "." + raw;

                Class<?> clazz;
                try {
                    clazz = Class.forName(resolved);
                } catch (ClassNotFoundException e) {
                    ConfigurationErrorCollector.collect(gson, ConfigurationErrorCollector.UNKNOWN_DRIVER.formatted(raw), resolved, in, false);
                    return null;
                }

                if (!DriverImplementation.class.isAssignableFrom(clazz)) {
                    ConfigurationErrorCollector.collect(gson, ConfigurationErrorCollector.IMPLEMENTATION_ERROR, resolved, in, false);
                    return null;
                }

                TypeAdapter<?> delegate = gson.getDelegateAdapter(
                        DriverPolymorphicAdapterFactory.this,
                        TypeToken.get((Class<?>) clazz)
                );
                return (DriverImplementation) delegate.fromJsonTree(obj);
            }
        };
    }
}
