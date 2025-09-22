package me.itstautvydas.uuidswapper.database.driver;

import me.itstautvydas.uuidswapper.data.OnlinePlayerData;
import me.itstautvydas.uuidswapper.data.PlayerData;
import me.itstautvydas.uuidswapper.database.DriverImplementation;
import me.itstautvydas.uuidswapper.processor.ReadMeCallSuperClass;
import me.itstautvydas.uuidswapper.processor.ReadMeDescription;
import me.itstautvydas.uuidswapper.processor.ReadMeTitle;

import java.util.*;

@ReadMeTitle(value = "(Database Driver) Memory Cache Implementation", order = -996)
@ReadMeDescription("Memory-based driver to use for caching player data, **be aware that data does not persist between restarts!**")
@ReadMeCallSuperClass()
public class MemoryCacheImplementation extends DriverImplementation {
    private final Set<OnlinePlayerData> onlineOnlinePlayerData = new HashSet<>();
    private final Set<PlayerData> randomPlayerCache = new HashSet<>();

    @Override
    public boolean init() {
        return true;
    }

    @Override
    public void clean() {
        onlineOnlinePlayerData.clear();
        randomPlayerCache.clear();
    }

    @Override
    public boolean isRunning() {
        return true;
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
    public List<OnlinePlayerData> getOnlinePlayersCache() {
        return List.copyOf(onlineOnlinePlayerData);
    }

    @Override
    public List<PlayerData> getRandomPlayersCache() {
        return List.copyOf(randomPlayerCache);
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
