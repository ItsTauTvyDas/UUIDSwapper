package me.itstautvydas.uuidswapper.database;

public interface TableBasedDriver {
    void createOnlineUuidCacheTable() throws Exception;
    void createRandomizedPlayerDataTable() throws Exception;
}
