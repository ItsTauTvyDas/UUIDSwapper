package me.itstautvydas.uuidswapper.cache;

import java.net.InetSocketAddress;
import java.util.UUID;

public interface DriverImplementation {
    void initConnection() throws Exception;
    void clearConnection();
    boolean isConnectionClosed();

    void createOnlineUuidCacheTable(boolean useCreatedAt, boolean useUpdatedAt);
    void createRandomizedPlayerDataTable();

    void storeOnlinePlayerCache(PlayerCache player);
    PlayerCache getOnlinePlayerCache(InetSocketAddress address);
    PlayerCache getOnlinePlayerCache(UUID uuid);

    void storeRandomPlayerCache(RandomCache player);
    RandomCache getRandomPlayerCache(UUID uuid);
}
