package me.itstautvydas.uuidswapper.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.itstautvydas.uuidswapper.data.ProfilePropertyWrapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DecodedPlayerPropertyTest {
    private static JsonObject decodeValueToJson(ProfilePropertyWrapper property) {
        var json = new String(Base64.getDecoder().decode(property.getValue()), StandardCharsets.UTF_8);
        return JsonParser.parseString(json).getAsJsonObject();
    }

    @Test
    void createUnsignedTexturesPropertyAndEncodeWithSkinAndCape() {
        var decoded = DecodedPlayerProperty.createUnsignedTexturesProperty("Alex", UUID.randomUUID());
        decoded.setSkinUrl("https://example.com/skin.png", false);
        decoded.setCapeUrl("https://example.com/cape.png", false);

        ProfilePropertyWrapper property = decoded.encode();
        assertEquals("textures", property.getName());
        assertNull(property.getSignature(), "Signature should be null");

        var json = decodeValueToJson(property);
        assertTrue(json.has("timestamp"));

        JsonObject textures = json.getAsJsonObject("textures");
        assertNotNull(textures);

        assertEquals("https://example.com/skin.png", textures.getAsJsonObject("SKIN").get("url").getAsString());
        assertEquals("https://example.com/cape.png", textures.getAsJsonObject("CAPE").get("url").getAsString());
    }

    @Test
    void decodeThenRemoveSignatureUpdatesTimestampAndDropsSignatureRequired() {
        long originalTimestamp = -1L;
        JsonObject jsonProperty = new JsonObject();
        jsonProperty.addProperty("timestamp", originalTimestamp);
        jsonProperty.addProperty("profileId", "<uuid>");
        jsonProperty.addProperty("profileName", "Steve");
        jsonProperty.addProperty("signatureRequired", true);
        jsonProperty.add("textures", new JsonObject());

        String encoded = Base64.getEncoder().encodeToString(jsonProperty.toString().getBytes(StandardCharsets.UTF_8));
        var property = new ProfilePropertyWrapper("textures", encoded, "<signature>");
        DecodedPlayerProperty.decode(property).removeSignature().encode();

        assertNull(property.getSignature(), "Should not have signature");

        JsonObject out = decodeValueToJson(property);
        assertFalse(out.has("signatureRequired"), "Should not have signatureRequired");
        assertTrue(out.get("timestamp").getAsLong() != originalTimestamp, "Timestamp should be updated");
    }

    @Test
    void matchChangesProfileNameAndId() {
        var original = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
        var decoded = DecodedPlayerProperty.createUnsignedTexturesProperty("Steve", original);

        var updated = UUID.fromString("00000000-0000-0000-0000-000000000000");
        decoded.match("Alex", updated);

        var encoded = decoded.encode();
        JsonObject root = decodeValueToJson(encoded);

        assertEquals("Alex", root.get("profileName").getAsString());
        assertEquals(updated.toString().replace("-", ""), root.get("profileId").getAsString());
    }
}