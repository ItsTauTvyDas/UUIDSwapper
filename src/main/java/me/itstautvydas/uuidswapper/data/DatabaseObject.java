package me.itstautvydas.uuidswapper.data;

import lombok.RequiredArgsConstructor;
import me.itstautvydas.uuidswapper.database.DriverImplementation;

@RequiredArgsConstructor
public abstract class DatabaseObject<T> {
    public final T object;
    public final Exception exception;
    public final DriverImplementation driver;
    protected final Object key;

    public boolean hasError() {
        return exception != null;
    }

    public abstract DatabaseObject<T> printErrorIfAny();
}
