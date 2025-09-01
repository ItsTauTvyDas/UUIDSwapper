package me.itstautvydas.uuidswapper.database.driver;

import com.google.gson.JsonObject;
import lombok.Getter;
import me.itstautvydas.uuidswapper.crossplatform.PluginWrapper;
import me.itstautvydas.uuidswapper.data.OnlinePlayerData;
import me.itstautvydas.uuidswapper.data.PlayerData;
import me.itstautvydas.uuidswapper.database.DriverImplementation;

import java.nio.file.Path;
import java.util.UUID;

@Getter
public class JsonImplementation extends DriverImplementation {
    private transient Path databaseFilePath;
    private transient JsonObject data;
    private boolean separateFileForEachPlayer;
    private String fileName;

    @Override
    public boolean init() {
        databaseFilePath = PluginWrapper.getCurrent()
                .getDataDirectory()
                .resolve(getFileName())
                .toAbsolutePath();
        return true;
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
