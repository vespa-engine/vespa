// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription;

import com.yahoo.config.ConfigInstance;
import com.yahoo.vespa.config.ConfigKey;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Config source as a programmatically built set of {@link com.yahoo.config.ConfigInstance}s
 *
 * @author Vegard Havdal
 */
public class ConfigSet implements ConfigSource {
    private final Map<ConfigKey<?>, ConfigInstance.Builder> configs = new ConcurrentHashMap<>();

    /**
     * Inserts a new builder in this set. If an existing entry exists, it is overwritten.
     *
     * @param configId The config id for this builder.
     * @param builder The builder that will produce config for the particular config id.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void addBuilder(String configId, ConfigInstance.Builder builder) {
        Class<?> configClass = builder.getClass().getDeclaringClass();
        ConfigKey<?> key = new ConfigKey(configClass, configId);
        configs.put(key, builder);
    }

    /**
     * Returns a Builder matching the given key, or null if no match
     *
     * @param key a config key to get a Builder for
     * @return a ConfigInstance
     */
    public ConfigInstance.Builder get(ConfigKey<?> key) {
        return configs.get(key);
    }

    /**
     * Returns true if this set contains a config instance matching the given key
     *
     * @param key a config key
     * @return a ConfigInstance
     */
    public boolean contains(ConfigKey<?> key) {
        return configs.containsKey(key);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<ConfigKey<?>, ConfigInstance.Builder> entry : configs.entrySet()) {
            sb.append(entry.getKey()).append("=>").append(entry.getValue());
        }
        return sb.toString();
    }

}
