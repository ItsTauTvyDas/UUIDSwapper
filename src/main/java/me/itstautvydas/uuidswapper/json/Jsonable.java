package me.itstautvydas.uuidswapper.json;

import com.google.gson.JsonObject;
import me.itstautvydas.uuidswapper.Utils;

public interface Jsonable {
    default JsonObject toJson() {
        return Utils.DEFAULT_GSON.toJsonTree(this).getAsJsonObject();
    }

    default String toJsonString(boolean pretty) {
        if (pretty)
            return Utils.DEFAULT_PRETTY_GSON.toJson(this);
        return Utils.DEFAULT_GSON.toJson(this);
    }
}
