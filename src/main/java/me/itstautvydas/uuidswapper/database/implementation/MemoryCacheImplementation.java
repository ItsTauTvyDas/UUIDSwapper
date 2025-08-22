package me.itstautvydas.uuidswapper.database.implementation;

import me.itstautvydas.uuidswapper.data.OnlinePlayerData;
import me.itstautvydas.uuidswapper.data.RandomPlayerData;
import me.itstautvydas.uuidswapper.database.DriverImplementation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MemoryCacheImplementation extends DriverImplementation {
    private final List<OnlinePlayerData> onlineOnlinePlayerData = new ArrayList<>();
    private final List<RandomPlayerData> randomPlayerCache = new ArrayList<>();

    @Override
    public void init() {
    }

    @Override
    public void clearConnection() {
    }

    @Override
    public boolean isConnectionClosed() {
        return false;
    }

    @Override
    public void createOnlineUuidCacheTable(boolean useCreatedAt, boolean useUpdatedAt) {
    }

    @Override
    public void createRandomizedPlayerDataTable() {
    }

    @Override
    public void storeOnlinePlayerCache(OnlinePlayerData player) {
        onlineOnlinePlayerData.add(player);
    }

    @Override
    public OnlinePlayerData getOnlinePlayerCache(String address) {
        return onlineOnlinePlayerData.stream()
                .filter(player -> player.getAddress().equals(address))
                .findFirst()
                .orElse(null);
    }

    @Override
    public OnlinePlayerData getOnlinePlayerCache(UUID uuid) {
        return onlineOnlinePlayerData.stream()
                .filter(player -> player.getOriginalUuid().equals(uuid))
                .findFirst()
                .orElse(null);
    }

    @Override
    public void storeRandomPlayerCache(RandomPlayerData player) {
        randomPlayerCache.add(player);
    }

    @Override
    public RandomPlayerData getRandomPlayerCache(UUID uuid) {
        return randomPlayerCache.stream()
                .filter(player -> player.getOriginalUuid().equals(uuid))
                .findFirst()
                .orElse(null);
    }
}
