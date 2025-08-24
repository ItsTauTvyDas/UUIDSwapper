package me.itstautvydas.uuidswapper.json;

import com.google.common.collect.Sets;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.internal.LinkedTreeMap;

import java.lang.reflect.Type;

@SuppressWarnings({"unchecked", "rawtypes"})
public class SortedJsonSerializer implements JsonSerializer<LinkedTreeMap> {
    @Override
    public JsonElement serialize(LinkedTreeMap foo, Type type, JsonSerializationContext context) {
        var object = new JsonObject();
        var sorted = Sets.newTreeSet(foo.keySet());
        for (Object key : sorted)
            object.add((String) key, context.serialize(foo.get(key)));
        return object;
    }
}