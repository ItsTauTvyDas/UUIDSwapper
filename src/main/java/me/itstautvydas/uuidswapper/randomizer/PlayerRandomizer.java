package me.itstautvydas.uuidswapper.randomizer;

import lombok.RequiredArgsConstructor;
import me.itstautvydas.uuidswapper.config.Configuration;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

@RequiredArgsConstructor
public class PlayerRandomizer {
    private final Configuration.RandomizerConfiguration configuration;
    private final Map<UUID, UUID> generatedUniqueIds = new HashMap<>();
    private final Map<UUID, String> generatedUsernames = new HashMap<>();
    private final Random random = new Random();

    public void removeGeneratedPlayer(UUID originalUniqueId) {
        generatedUniqueIds.remove(originalUniqueId);
        generatedUsernames.remove(originalUniqueId);
    }

    public void removeGeneratedPlayer(String randomUsername, UUID randomUniqueId) {
        generatedUsernames.entrySet().removeIf(entry -> entry.getValue().equals(randomUsername));
        generatedUniqueIds.entrySet().removeIf(entry -> entry.getValue().equals(randomUniqueId));
    }

    public String getGeneratedUsername(UUID uniqueId) {
        return generatedUsernames.get(uniqueId);
    }

    public UUID getGeneratedUniqueId(UUID uniqueId) {
        return generatedUniqueIds.get(uniqueId);
    }

    public UUID nextUniqueId(UUID uniqueId) {
        UUID id;
        do {
            id = UUID.randomUUID();
        } while (generatedUniqueIds.containsValue(id));
        generatedUniqueIds.put(uniqueId, id);
        return id;
    }

    public String nextUsername(UUID uniqueId) throws IllegalArgumentException, IllegalStateException {
        var from = configuration.getUsernameSettings().getFromLength();
        var to = configuration.getUsernameSettings().getToLength();
        if (from <= 0 || to <= 0)
            throw new IllegalArgumentException("Username length must be positive.");
        if (from > to) {
            var tmp = from;
            from = to;
            to = tmp;
        }
        if (isUsernameSpaceExhausted(from, to))
            throw new IllegalStateException("All unique usernames have been generated.");
        String username;
        do {
            int length = randomLength(from, to);
            username = randomUsername(length);
        } while (generatedUsernames.containsValue(username));
        generatedUsernames.put(uniqueId, username);
        return username;
    }

    private int randomLength(int from, int to) {
        if (from == to) return from;
        return from + random.nextInt(to - from + 1);
    }

    private String randomUsername(int length) {
        var builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int idx = random.nextInt(configuration.getUsernameSettings().getCharacters().length());
            builder.append(configuration.getUsernameSettings().getCharacters().charAt(idx));
        }
        return builder.toString();
    }

    private boolean isUsernameSpaceExhausted(int from, int to) {
        var base = BigInteger.valueOf(configuration.getUsernameSettings().getCharacters().length());
        var total = BigInteger.ZERO;
        for (int k = from; k <= to; k++)
            total = total.add(base.pow(k));
        return BigInteger.valueOf(generatedUsernames.size()).compareTo(total) >= 0;
    }
}
