package me.itstautvydas.uuidswapper.helper;

import lombok.experimental.UtilityClass;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

@UtilityClass
public class ReflectionHelper {
    public Map<Class<?>, Map<String, Field>> cachedFields = new HashMap<>();

    private Field getOrCache(Object object, String fieldName) throws NoSuchFieldException {
        if (object == null)
            return null;
        var type = object.getClass();
        var classMap = cachedFields.getOrDefault(type, new HashMap<>());
        if (!cachedFields.containsKey(type))
            cachedFields.put(type, classMap);
        var field = classMap.get(fieldName);
        if (field == null) {
            field = object.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            classMap.put(fieldName, field);
        }
        return field;
    }

    public boolean setFieldValueIfCan(Object object, String fieldName, Object value) {
        try {
            setFieldValue(object, fieldName, value);
            return true;
        } catch (NoSuchFieldException | IllegalAccessException ex) {
            return false;
        }
    }

    public Object getFieldValue(Object object, String fieldName) throws NoSuchFieldException, IllegalAccessException {
        var field = getOrCache(object, fieldName);
        return field.get(object);
    }

    public void setFieldValue(Object object, String fieldName, Object value) throws NoSuchFieldException, IllegalAccessException {
        var field = getOrCache(object, fieldName);
        field.set(object, value);
    }
}
