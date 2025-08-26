package me.itstautvydas.uuidswapper.helper;

import lombok.ToString;

import java.util.function.BiConsumer;

@ToString
public class BiObjectHolder<T, D> {
    private T object;
    private D object2;
    private BiConsumer<T, D> biconsumer;

    public BiObjectHolder(T object, D object2) {
        this.object = object;
        this.object2 = object2;
    }

    public BiObjectHolder(T object, D object2, BiConsumer<T, D> biconsumer) {
        this.object = object;
        this.object2 = object2;
        this.biconsumer = biconsumer;
    }

    public void setFirst(T object) {
        this.object = object;
        if (biconsumer != null)
            biconsumer.accept(object, object2);
    }

    public void setSecond(D object2) {
        this.object2 = object2;
        if (biconsumer != null)
            biconsumer.accept(object, object2);
    }

    public void set(T object, D object2) {
        this.object = object;
        this.object2 = object2;
        if (biconsumer != null)
            biconsumer.accept(object, object2);
    }

    public T getFirst() {
        return object;
    }

    public D getSecond() {
        return object2;
    }

    public boolean containsFirst() {
        return object != null;
    }

    public boolean containsSecond() {
        return object2 != null;
    }
}
