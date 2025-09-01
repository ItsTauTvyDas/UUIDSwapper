package me.itstautvydas.uuidswapper.database;

import lombok.Getter;

@Getter
public abstract class CacheableConnectionDriverImplementation extends DriverImplementation {
    private long timeout;
    private long keepOpenTime;
    private long timerRepeatTime;

    public boolean shouldConnectionBeCached() {
        return keepOpenTime > 0;
    }

    public boolean shouldConnectionBeAlwaysKept() {
        return timerRepeatTime <= -1;
    }
}
