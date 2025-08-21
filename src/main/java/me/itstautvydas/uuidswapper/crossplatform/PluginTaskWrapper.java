package me.itstautvydas.uuidswapper.crossplatform;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public abstract class PluginTaskWrapper<T> {
    protected final T handle;

    public abstract void cancel();
}
