package me.itstautvydas.uuidswapper.json;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import me.itstautvydas.uuidswapper.annotation.RequiredProperty;

import java.io.IOException;
import java.util.HashSet;

public class RequiredPropertyAdapterFactory implements TypeAdapterFactory {
    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
        final var delegate = gson.getDelegateAdapter(this, type);

        return new TypeAdapter<>() {
            @Override
            public void write(JsonWriter out, T value) throws IOException {
                delegate.write(out, value);
            }

            @Override
            public T read(JsonReader in) throws IOException {
                var obj = delegate.read(in);
                if (obj != null)
                    validateRequiredProperties(obj);
                return obj;
            }

            private void validateRequiredProperties(T obj) {
                var missingFields = new HashSet<String>();
                for (var field : obj.getClass().getDeclaredFields()) {
                    if (field.isAnnotationPresent(RequiredProperty.class)) {
                        field.setAccessible(true);
                        try {
                            var value = field.get(obj);
                            if (value == null)
                                missingFields.add(field.getName());
                        } catch (IllegalAccessException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
                if (!missingFields.isEmpty())
                    throw new JsonParseException("Missing required properties: " + String.join(", ", missingFields));
            }
        };
    }
}
