package me.itstautvydas.uuidswapper.crossplatform.bungeecord;

import me.itstautvydas.uuidswapper.crossplatform.ConfigurationWrapper;
import me.itstautvydas.uuidswapper.crossplatform.PluginWrapper;
import net.md_5.bungee.config.Configuration;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class BungeeConfigurationWrapper extends ConfigurationWrapper {
    public BungeeConfigurationWrapper(Configuration handle, ConfigurationWrapper defaults) {
        super(handle, defaults);
    }

    @Override
    public boolean isPrimitive(String path) {
        var value = ((Configuration)handle).get(path);
        if (value == null)
            return false;
        return value.getClass().isPrimitive();
    }

    @Override
    public boolean isArray(String path) {
        var value = ((Configuration)handle).get(path);
        if (value == null)
            return false;
        return value instanceof List<?>;
    }

    @Override
    public ConfigurationWrapper getSection(String path, ConfigurationWrapper defaults) {
        var section = ((Configuration)handle).getSection(path);
        if (section == null)
            return null;
        return new BungeeConfigurationWrapper(section, defaults);
    }

    private Constructor<Configuration> configurationConstructor;

    @SuppressWarnings("unchecked")
    @Override
    public List<ConfigurationWrapper> getSections(String path, ConfigurationWrapper defaults) {
        var sectionsList = (List<Map<String, Object>>) ((Configuration)handle).getList(path);
        var sections = new ArrayList<ConfigurationWrapper>();
        try {
            // Very hacky way ngl
            if (configurationConstructor == null) {
                configurationConstructor = Configuration.class.getDeclaredConstructor(Map.class, Configuration.class);
                configurationConstructor.setAccessible(true);
            }
            for (var map : sectionsList)
                sections.add(new BungeeConfigurationWrapper(configurationConstructor.newInstance(map, null), defaults));
        } catch (Exception ex) {
            PluginWrapper.getCurrent().logError("Failed to get sections!", ex);
            return null;
        }
//            sections.add(new BungeeConfigurationWrapper(section.getSection(key), defaults));
        return sections;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <D> D get(String path, D defaultValue) {
        var value = ((Configuration)handle).get(path, defaultValue);
        if (value == null && defaults != null)
            return defaults.get(path, defaultValue);
        if (value instanceof Integer integer)
            return (D) Long.valueOf(integer.longValue());
        return value;
    }


    @Override
    public boolean contains(String path) {
        return ((Configuration)handle).contains(path);
    }

    @Override
    public Map<String, Object> toMap() {
        return ((Configuration)handle)
                .getKeys()
                .stream()
                .collect(Collectors.toMap(key -> key, key -> get(key, null)));
    }

    @Override
    public <T> List<T> getList(String path) {
        if (contains(path))
            return get(path, null);
        return null;
    }
}
