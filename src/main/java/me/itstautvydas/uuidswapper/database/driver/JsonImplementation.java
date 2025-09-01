package me.itstautvydas.uuidswapper.database.driver;

import com.google.gson.JsonObject;
import me.itstautvydas.uuidswapper.crossplatform.PluginWrapper;
import me.itstautvydas.uuidswapper.data.OnlinePlayerData;
import me.itstautvydas.uuidswapper.data.PlayerData;
import me.itstautvydas.uuidswapper.database.DriverImplementation;
import me.itstautvydas.uuidswapper.processor.ReadMeCallSuperClass;
import me.itstautvydas.uuidswapper.processor.ReadMeDescription;
import me.itstautvydas.uuidswapper.processor.ReadMeTitle;

import java.nio.file.Path;
import java.util.UUID;

@SuppressWarnings("unused")
@ReadMeTitle(value = "(Database Driver) JSON Implementation", order = -996)
@ReadMeDescription("JSON file-based driver to use for caching player data.")
@ReadMeCallSuperClass()
public class JsonImplementation extends DriverImplementation {
    private transient Path databaseFilePath;
    private transient JsonObject data;
    @ReadMeDescription("Should driver make a directory to stone each player in their own JSON file")
    private boolean split;
    @ReadMeDescription("Should JSON be **always** loaded")
    private boolean keepLoaded;
    @ReadMeDescription("Should JSON be saved as beautified")
    private boolean beautifyJson;
    @ReadMeDescription("File to store JSON. If `split` is enabled and no {original-uuid} is defined, the UUID will be prefixed with a dot after it")
    private String fileName;

    @Override
    public boolean init() {
        databaseFilePath = PluginWrapper.getCurrent()
                .getDataDirectory()
                .resolve(fileName)
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
