package me.itstautvydas.uuidswapper.crossplatform;

import com.moandjiezana.toml.Toml;
import lombok.Getter;
import lombok.Setter;
import me.itstautvydas.uuidswapper.Utils;
import net.md_5.bungee.config.Configuration;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Getter
public abstract class ConfigurationWrapper {
    protected Object handle;
    protected ConfigurationWrapper defaults;

    public ConfigurationWrapper(Object handle, ConfigurationWrapper defaults) {
        this.handle = handle;
        this.defaults = defaults;
    }

    public boolean getBoolean(String path, boolean defaultValue) {
        return get(path, defaultValue);
    }

    public boolean getBoolean(String path) {
        return get(path, false);
    }

    public String getString(String path, String defaultValue) {
        return get(path, defaultValue);
    }

    public String getString(String path) {
        return get(path, null);
    }

    public long getLong(String path, long defaultValue) {
        return get(path, defaultValue);
    }

    public long getLong(String path) {
        return get(path, 0L);
    }

    public double getDouble(String path, double defaultValue) {
        return get(path, defaultValue);
    }

    public double getDouble(String path) {
        return get(path, 0.0);
    }

    public <T> List<T> getList(String path, List<T> defaultValue) {
        return get(path, defaultValue);
    }

    public <T> List<T> getList(String path) {
        return get(path, null);
    }

    public ConfigurationWrapper getSection(String path) {
        return getSection(path, null);
    }

    public List<ConfigurationWrapper> getSections(String path) {
        return getSections(path, null);
    }

    public Map<String, Object> getMap(String path, boolean emptyIfNull) {
        var table = getSection(path);
        if (table == null && emptyIfNull)
            return Utils.EMPTY_MAP;
        return table == null ? null : table.toMap();
    }

    public abstract boolean isPrimitive(String path);
    public abstract boolean isArray(String path);

    public abstract ConfigurationWrapper getSection(String path, ConfigurationWrapper defaults);
    public abstract List<ConfigurationWrapper> getSections(String path, ConfigurationWrapper defaults);
    public abstract <D> D get(String path, D defaultValue);
    public abstract boolean contains(String path);
    public abstract Map<String, Object> toMap();

    public static class BungeeConfigurationWrapper extends ConfigurationWrapper {
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

        @Override
        public List<ConfigurationWrapper> getSections(String path, ConfigurationWrapper defaults) {
//            return handle.getTables(path).stream().map(BungeeConfiguration::new).collect(Collectors.toList());
            return null;
        }

        @Override
        public <D> D get(String path, D defaultValue) {
            var value = ((Configuration)handle).get(path, defaultValue);
            if (value == null && defaults != null)
                return defaults.get(path, defaultValue);
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

    public static class VelocityConfigurationWrapper extends ConfigurationWrapper {
        public VelocityConfigurationWrapper(Toml handle, ConfigurationWrapper defaults) {
            super(handle, defaults);
        }

        @Override
        public boolean isPrimitive(String path) {
            return ((Toml)handle).containsPrimitive(path);
        }

        @Override
        public boolean isArray(String path) {
            return ((Toml)handle).containsTableArray(path);
        }

        @Override
        public ConfigurationWrapper getSection(String path, ConfigurationWrapper defaults) {
            var section = ((Toml)handle).getTable(path);
            if (section == null)
                return null;
            return new VelocityConfigurationWrapper(section, defaults);
        }

        @Override
        public List<ConfigurationWrapper> getSections(String path, ConfigurationWrapper defaults) {
            var list = Utils.getTablesWithDefaults(path, (Toml)handle, defaults == null ? null : (Toml)defaults.getHandle());
            if (list != null)
                    return list.stream()
                            .map(x -> new VelocityConfigurationWrapper(x, defaults))
                            .collect(Collectors.toList());
            return null;
        }

        @Override
        public <D> D get(String path, D defaultValue) {
            return Utils.getRawTomlObject(((Toml)handle), path, defaultValue);
        }

        @Override
        public boolean contains(String path) {
            return ((Toml)handle).contains(path);
        }

        @Override
        public Map<String, Object> toMap() {
            return ((Toml)handle).toMap();
        }
    }
}