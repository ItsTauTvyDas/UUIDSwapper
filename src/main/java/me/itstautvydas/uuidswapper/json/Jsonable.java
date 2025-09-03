package me.itstautvydas.uuidswapper.json;

import com.google.gson.JsonObject;
import me.itstautvydas.uuidswapper.Utils;

public interface Jsonable {
    default JsonObject toJson() {
        return Utils.DEFAULT_GSON.toJsonTree(this).getAsJsonObject();
    }
}
