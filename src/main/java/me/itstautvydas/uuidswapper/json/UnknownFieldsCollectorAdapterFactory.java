package me.itstautvydas.uuidswapper.json;

import com.google.gson.*;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

// IT HAS BEEN 10 YEARS COME ON GOOGLE DO SOMETHING
// https://itstautvydas.me/catgirls/nekoha-shizuku/yunowork.webp

public class UnknownFieldsCollectorAdapterFactory implements TypeAdapterFactory {

    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
        var raw = type.getRawType();

        var p = raw.getPackage();
        var pkg = (p == null) ? "" : p.getName();

        if (!pkg.startsWith("me.itstautvydas")
                || raw.isPrimitive()
                || raw.isEnum()
                || raw.isArray()
                || Map.class.isAssignableFrom(raw)
                || Collection.class.isAssignableFrom(raw)
                || raw.getName().startsWith("java.")
                || raw.getName().startsWith("javax.")
                || raw.getName().startsWith("jdk.")) {
            return null;
        }

        var delegate = gson.getDelegateAdapter(this, type);
        var validFields = collectValidFieldNames(raw);

        return new TypeAdapter<>() {
            @Override
            public void write(JsonWriter out, T value) throws IOException {
                delegate.write(out, value);
            }

            @Override
            public T read(JsonReader in) throws IOException {
                var peek = in.peek();
                if (peek == JsonToken.NULL) {
                    in.nextNull();
                    return null;
                }

                if (peek != JsonToken.BEGIN_OBJECT)
                    return delegate.read(in);

                var obj = new JsonObject();

                in.beginObject();
                while (in.hasNext()) {
                    String name = in.nextName();
                    JsonElement value = com.google.gson.internal.Streams.parse(in);

                    if (!validFields.contains(name))
                        ConfigurationErrorCollector.collect(
                                gson, ConfigurationErrorCollector.UNKNOWN_PROPERTY, name, in, false
                        );
                    obj.add(name, value);
                }
                in.endObject();

                return delegate.fromJsonTree(obj);
            }
        };
    }

    private static Set<String> collectValidFieldNames(Class<?> rawClass) {
        var names = new HashSet<String>();
        while (rawClass != null && rawClass != Object.class) {
            for (Field field : rawClass.getDeclaredFields()) {
                int mod = field.getModifiers();
                if (Modifier.isStatic(mod) || Modifier.isTransient(mod)) continue;

                var serializedName = field.getAnnotation(SerializedName.class);
                if (serializedName != null) {
                    names.add(serializedName.value());
                    names.addAll(Arrays.asList(serializedName.alternate()));
                } else {
                    String javaName = field.getName();
                    names.add(javaName);
                    names.add(toKebabCase(javaName));
                }
            }
            rawClass = rawClass.getSuperclass();
        }
        return names;
    }

    private static String toKebabCase(String s) {
        var builder = new StringBuilder(s.length() + 4);
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (Character.isUpperCase(ch))
                builder.append('-').append(Character.toLowerCase(ch));
            else
                builder.append(ch);
        }
        return builder.toString();
    }
}