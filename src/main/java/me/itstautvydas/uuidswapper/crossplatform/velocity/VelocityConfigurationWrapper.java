package me.itstautvydas.uuidswapper.crossplatform.velocity;

import com.moandjiezana.toml.Toml;
import me.itstautvydas.uuidswapper.Utils;
import me.itstautvydas.uuidswapper.crossplatform.ConfigurationWrapper;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class VelocityConfigurationWrapper extends ConfigurationWrapper {
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
