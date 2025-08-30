package me.itstautvydas.uuidswapper.json;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.HashSet;

// IT HAS BEEN 10 YEARS COME ON GOOGLE DO SOMETHING

public class InvalidFieldsCollectorAdapterFactory implements TypeAdapterFactory {

    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
        var delegate = gson.getDelegateAdapter(this, type);

        var validFields = new HashSet<>();
        for (Field f : type.getRawType().getDeclaredFields())
            validFields.add(f.getName());

        return new TypeAdapter<>() {
            @Override
            public void write(JsonWriter out, T value) throws IOException {
                delegate.write(out, value);
            }

            @Override
            public T read(JsonReader in) throws IOException {
                JsonToken peek = in.peek();
                if (peek == JsonToken.NULL) {
                    in.nextNull();
                    return null;
                }

                if (peek != JsonToken.BEGIN_OBJECT)
                    return delegate.read(in);

                JsonObject obj = new JsonObject();
                in.beginObject();
                while (in.hasNext()) {
                    String name = in.nextName();
                    JsonElement value = com.google.gson.internal.Streams.parse(in);

                    if (!validFields.contains(name))
                        ConfigurationErrorCollector.collect(gson, ConfigurationErrorCollector.UNKNOWN_PROPERTY, name, in, false);
                    obj.add(name, value);
                }
                in.endObject();

                return delegate.fromJsonTree(obj);
            }
        };
    }
}