package me.itstautvydas.uuidswapper.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.ToString;
import me.itstautvydas.uuidswapper.data.ProfilePropertyWrapper;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

@ToString
@Deprecated(forRemoval = true)
public class DecodedPlayerProperty {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    protected ProfilePropertyWrapper original;
    protected JsonObject parsedJson;

    private String skinUrl, capeUrl;

    private DecodedPlayerProperty() {}

    public static DecodedPlayerProperty decode(ProfilePropertyWrapper property) {
        return new DecodedPlayerProperty() {
            {
                original = property;
                var decodedValue = new String(Base64.getDecoder().decode(original.getValue().getBytes()), StandardCharsets.UTF_8);
                parsedJson = JsonParser.parseString(decodedValue).getAsJsonObject();
            }
        };
    }

    public static DecodedPlayerProperty createUnsignedTexturesProperty(String username, UUID uniqueId) {
        return new DecodedPlayerProperty() {
            {
                parsedJson = new JsonObject();
                parsedJson.addProperty("timestamp", System.currentTimeMillis());
                parsedJson.addProperty("profileId", uniqueId.toString().replace("-", ""));
                parsedJson.addProperty("profileName", username);
                var textures = new JsonObject();
                parsedJson.add("textures", textures);
                original = new ProfilePropertyWrapper("textures", null, null);
            }
        };
    }

    public DecodedPlayerProperty setCapeUrl(@Nullable String capeUrl, boolean removeIfNull) {
        this.capeUrl = (capeUrl == null && removeIfNull) ? null : capeUrl;
        return this;
    }

    public DecodedPlayerProperty setSkinUrl(@Nullable String skinUrl, boolean removeIfNull) {
        this.skinUrl = (skinUrl == null && removeIfNull) ? null : skinUrl;
        return this;
    }

    public DecodedPlayerProperty match(@Nullable String username, @Nullable UUID uniqueId) {
        if (username != null)
            parsedJson.addProperty("profileName", username);
        if (uniqueId != null)
            parsedJson.addProperty("profileId", uniqueId.toString().replace("-", ""));
        return this;
    }

    public DecodedPlayerProperty removeSignature() {
        original.setSignature(null);
        parsedJson.addProperty("signatureRequired", false);
//        parsedJson.addProperty("timestamp", System.currentTimeMillis());
        return this;
    }

    public ProfilePropertyWrapper encode() {
        var textures = parsedJson.getAsJsonObject("textures");
        if (skinUrl != null) {
            var skin = new JsonObject();
            skin.addProperty("url", skinUrl);
            textures.add("SKIN", skin);
        }
        if (capeUrl != null) {
            var cape = new JsonObject();
            cape.addProperty("url", capeUrl);
            textures.add("CAPE", cape);
        }
        original.setValue(Base64.getEncoder().encodeToString(GSON.toJson(parsedJson).getBytes()));
        System.out.println(parsedJson);
        return original;
    }
}
