package me.itstautvydas.uuidswapper.database.implementation;

import com.google.gson.JsonObject;
import me.itstautvydas.uuidswapper.crossplatform.PluginWrapper;
import me.itstautvydas.uuidswapper.data.OnlinePlayerData;
import me.itstautvydas.uuidswapper.data.PlayerData;
import me.itstautvydas.uuidswapper.database.DriverImplementation;

import java.nio.file.Path;
import java.util.UUID;

public class JsonImplementation extends DriverImplementation {
    private Path databaseFilePath;
    private JsonObject data;

    @Override
    public void init() throws Exception {
        databaseFilePath = PluginWrapper.getCurrent()
                .getDataDirectory()
                .resolve(getConfiguration().getFileName())
                .toAbsolutePath();
    }

    @Override
    public boolean clearConnection() throws Exception {
        return false;
    }

    @Override
    public boolean isConnectionClosed() throws Exception {
        return false;
    }

    @Override
    public void createOnlineUuidCacheTable() throws Exception {

    }

    @Override
    public void createRandomizedPlayerDataTable() throws Exception {

    }

    @Override
    public void storeOnlinePlayerCache(OnlinePlayerData player) throws Exception {

    }

    @Override
    public OnlinePlayerData getOnlinePlayerCache(String address) throws Exception {
        return null;
    }

    @Override
    public OnlinePlayerData getOnlinePlayerCache(UUID uuid) throws Exception {
        return null;
    }

    @Override
    public void storeRandomPlayerCache(PlayerData player) throws Exception {

    }

    @Override
    public PlayerData getRandomPlayerCache(UUID uuid) throws Exception {
        return null;
    }


}
