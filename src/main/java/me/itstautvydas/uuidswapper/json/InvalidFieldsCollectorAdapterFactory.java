package me.itstautvydas.uuidswapper.json;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.internal.bind.ReflectiveTypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import me.itstautvydas.uuidswapper.crossplatform.PluginWrapper;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// https://github.com/google/gson/issues/188 (keeping original comments)
// IT HAS BEEN 10 YEARS COME ON GOOGLE DO SOMETHING
@SuppressWarnings({"rawtypes", "unchecked"})
public class InvalidFieldsCollectorAdapterFactory implements TypeAdapterFactory {

    public static final Map<Gson, List<Object>> INVALID_FIELDS = new HashMap<>();

    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
        // If the type adapter is a reflective type adapter, we want to modify the implementation using reflection. The
        // trick is to replace the Map object used to lookup the property name. Instead of returning null if the
        // property is not found, we throw a Json exception to terminate the deserialization.
        TypeAdapter<T> delegate = gson.getDelegateAdapter(this, type);

        // Check if the type adapter is a reflective, cause this solution only work for reflection.
        if (delegate instanceof ReflectiveTypeAdapterFactory.Adapter delegateAdapter) {
            try {
                // Get reference to the existing boundFields.
                Field f = findField(delegateAdapter.getClass(), "boundFields");
                f.setAccessible(true);
                Map boundFields = (Map) f.get(delegate);

                // Then replace it with our implementation throwing exception if the value is null.
                boundFields = new LinkedHashMap(boundFields) {
                    @Override
                    public Object get(Object key) {
                        Object value = super.get(key);
                        if (value == null)
                            INVALID_FIELDS.get(gson).add(key);
                        return value;

                    }
                };
                // Finally, push our custom map back using reflection.
                f.set(delegate, boundFields);
            } catch (Exception e) {
                // Should never happen if the implementation doesn't change.
//                throw new IllegalStateException(e);
                PluginWrapper.getCurrent().logInfo("(Ignore) Failed to collect invalid configuration fields - " + e.getMessage());
            }
        }
        return delegate;
    }

    private static Field findField(@NotNull Class<?> startingClass, String fieldName) throws NoSuchFieldException {
        for (Class<?> c = startingClass; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                // OK: continue with superclasses
            }
        }
        throw new NoSuchFieldException(fieldName + " starting from " + startingClass.getName());
    }
}