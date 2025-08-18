package me.itstautvydas.uuidswapper.helper;

import java.util.function.Consumer;

public class ObjectHolder<T> {
    private T object;
    private Consumer<T> consumer;

    public ObjectHolder(T object) {
        this.object = object;
    }

    public ObjectHolder(T object, Consumer<T> consumer) {
        this.object = object;
        this.consumer = consumer;
    }

    public void set(T object) {
        this.object = object;
        if (consumer != null)
            consumer.accept(object);
    }

    public T get() {
        return object;
    }
}
