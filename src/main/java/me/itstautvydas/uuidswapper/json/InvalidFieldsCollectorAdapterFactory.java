package me.itstautvydas.uuidswapper.json;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.internal.bind.ReflectiveTypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

// https://github.com/google/gson/issues/188 (modified)
// IT HAS BEEN 10 YEARS COME ON GOOGLE DO SOMETHING

@SuppressWarnings({"rawtypes", "unchecked"})
public class InvalidFieldsCollectorAdapterFactory implements TypeAdapterFactory {

    private static final ThreadLocal<JsonReader> CURRENT_READER = new ThreadLocal<>();

    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
        TypeAdapter<T> delegate = gson.getDelegateAdapter(this, type);

        if (delegate instanceof ReflectiveTypeAdapterFactory.Adapter delegateAdapter) {
            try {
                Field f = findField(delegateAdapter.getClass(), "boundFields");
                f.setAccessible(true);
                f.set(delegate, new LinkedHashMap((Map) f.get(delegate)) {
                    @Override
                    public Object get(Object key) {
                        Object value = super.get(key);
                        System.out.println(key + " - " + value + " at " + CURRENT_READER.get().getPath());
                        if (value == null)
                            ConfigurationErrorCollector.collect(
                                    gson,
                                    ConfigurationErrorCollector.INVALID_PROPERTY,
                                    key,
                                    CURRENT_READER.get(),
                                    false
                            );
                        return value;
                    }
                });
            } catch (Exception e) {
                // Do nothing
            }
        }
        return new TypeAdapter<>() {
            @Override
            public void write(JsonWriter out, T value) throws IOException {
                delegate.write(out, value);
            }

            @Override
            public T read(JsonReader in) throws IOException {
                CURRENT_READER.set(in);
                try {
                    return delegate.read(in);
                } finally {
                    CURRENT_READER.remove();
                }
            }
        };
    }

    private static Field findField(@NotNull Class<?> startingClass, String fieldName) throws NoSuchFieldException {
        for (Class<?> c = startingClass; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
            }
        }
        throw new NoSuchFieldException(fieldName + " starting from " + startingClass.getName());
    }
}