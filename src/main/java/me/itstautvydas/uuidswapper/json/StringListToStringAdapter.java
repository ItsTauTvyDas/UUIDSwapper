package me.itstautvydas.uuidswapper.json;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.List;

public class StringListToStringAdapter extends TypeAdapter<String> {
    @Override
    public void write(JsonWriter out, String value) throws IOException {
        out.value(value);
    }

    @SuppressWarnings("unchecked")
    @Override
    public String read(JsonReader in) {
        var element = JsonParser.parseReader(in);

        if (element.isJsonArray()) {
            var list = (List<String>) new Gson().fromJson(element, List.class);
            return String.join("\n", list);
        }
        if (element.isJsonNull())
            return null;
        return element.getAsString();
    }
}
