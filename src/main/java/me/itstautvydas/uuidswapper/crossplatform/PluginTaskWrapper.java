package me.itstautvydas.uuidswapper.crossplatform;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public abstract class PluginTaskWrapper {
    protected final Object handle;

    public abstract void cancel();
}
