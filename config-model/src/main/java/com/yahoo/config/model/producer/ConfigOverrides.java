// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.producer;

import com.yahoo.vespa.config.ConfigDefinitionKey;
import com.yahoo.vespa.config.ConfigPayloadBuilder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * ConfigOverrides is a repository for config overrides, typically for a particular config producer. It
 * encapsulates how the config overrides are stored, and defines the methods to retrieve config overrides and merge
 * them with others.
 *
 * @author Ulf Lilleengen
 * @author hmusum
 */
public class ConfigOverrides {

    private final Map<ConfigDefinitionKey, ConfigPayloadBuilder> configOverrides;

    public ConfigOverrides() {
        this.configOverrides = new LinkedHashMap<>();
    }

    public ConfigOverrides(Map<ConfigDefinitionKey, ConfigPayloadBuilder> configOverrides) {
        this.configOverrides = configOverrides;
    }

    public ConfigPayloadBuilder get(ConfigDefinitionKey key) {
        return configOverrides.get(key);
    }

    public void merge(ConfigOverrides newOverrides) {
        for (Map.Entry<ConfigDefinitionKey, ConfigPayloadBuilder> entry : newOverrides.configOverrides.entrySet()) {
            if (entry.getValue() == null) continue;

            ConfigDefinitionKey key = entry.getKey();
            if (configOverrides.containsKey(key)) {
                ConfigPayloadBuilder lhsBuilder = configOverrides.get(key);
                ConfigPayloadBuilder rhsBuilder = entry.getValue();
                lhsBuilder.override(rhsBuilder);
            } else {
                configOverrides.put(key, entry.getValue());
            }
        }
    }

    public boolean isEmpty() {
        return configOverrides.isEmpty();
    }

    public int size() {
        return configOverrides.size();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (ConfigDefinitionKey key : configOverrides.keySet()) {
            sb.append(key.toString());
        }
        return sb.toString();
    }

    /**
     * The keys of all the configs contained in this.
     * @return a set of ConfigDefinitionsKey
     */
    public Set<ConfigDefinitionKey> configsProduced() {
        return configOverrides.keySet();
    }

}
