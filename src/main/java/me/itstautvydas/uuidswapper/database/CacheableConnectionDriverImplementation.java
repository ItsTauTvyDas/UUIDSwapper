package me.itstautvydas.uuidswapper.database;

import lombok.Getter;
import me.itstautvydas.uuidswapper.processor.ReadMeCallSuperClass;
import me.itstautvydas.uuidswapper.processor.ReadMeDescription;

@Getter
@ReadMeCallSuperClass()
public abstract class CacheableConnectionDriverImplementation extends DriverImplementation {
    @ReadMeDescription("Driver's connection timeout")
    private long timeout;
    @ReadMeDescription("For how much time in seconds driver's connection should be open")
    private long keepOpenTime;
    @ReadMeDescription("Internal timer's update frequency in seconds for checking keep-open-time")
    private long timerRepeatTime;

    public boolean shouldConnectionBeCached() {
        return keepOpenTime > 0;
    }

    public boolean shouldConnectionBeAlwaysKept() {
        return timerRepeatTime <= -1;
    }
}
