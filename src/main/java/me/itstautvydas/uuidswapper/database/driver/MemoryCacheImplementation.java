package me.itstautvydas.uuidswapper.database.driver;

import me.itstautvydas.uuidswapper.data.OnlinePlayerData;
import me.itstautvydas.uuidswapper.data.PlayerData;
import me.itstautvydas.uuidswapper.database.DriverImplementation;
import me.itstautvydas.uuidswapper.processor.ReadMeCallSuperClass;
import me.itstautvydas.uuidswapper.processor.ReadMeDescription;
import me.itstautvydas.uuidswapper.processor.ReadMeTitle;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@SuppressWarnings("unused")
@ReadMeTitle(value = "(Database Driver) Memory Cache Implementation", order = -996)
@ReadMeDescription("Memory-based driver to use for caching player data. No configuration needed for this.")
@ReadMeCallSuperClass()
public class MemoryCacheImplementation extends DriverImplementation {
    private final List<OnlinePlayerData> onlineOnlinePlayerData = new ArrayList<>();
    private final List<PlayerData> randomPlayerCache = new ArrayList<>();

    @Override
    public boolean init() {
        return true;
    }

    @Override
    public boolean clearConnection() {
        return true;
    }

    @Override
    public boolean isConnectionClosed() {
        return false;
    }

    @Override
    public void createOnlineUuidCacheTable() {
    }

    @Override
    public void createRandomizedPlayerDataTable() {
    }

    @Override
    public void storeOnlinePlayerCache(OnlinePlayerData player) {
        onlineOnlinePlayerData.add(player);
    }

    @Override
    public OnlinePlayerData getOnlinePlayerCache(UUID uuid) {
        return onlineOnlinePlayerData.stream()
                .filter(player -> player.getOriginalUniqueId().equals(uuid))
                .findFirst()
                .orElse(null);
    }

    @Override
    public void storeRandomPlayerCache(PlayerData player) {
        randomPlayerCache.add(player);
    }

    @Override
    public PlayerData getRandomPlayerCache(UUID uuid) {
        return randomPlayerCache.stream()
                .filter(player -> player.getOriginalUniqueId().equals(uuid))
                .findFirst()
                .orElse(null);
    }
}
