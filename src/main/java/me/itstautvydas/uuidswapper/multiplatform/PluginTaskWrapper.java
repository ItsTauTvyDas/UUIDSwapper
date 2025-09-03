package me.itstautvydas.uuidswapper.multiplatform;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public abstract class PluginTaskWrapper {
    protected final Object handle;

    public abstract void cancel();
}
