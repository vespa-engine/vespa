// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.producer;

import com.yahoo.vespa.config.ConfigDefinitionKey;
import com.yahoo.vespa.config.ConfigPayload;
import com.yahoo.vespa.config.ConfigPayloadBuilder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A UserConfigRepo is a repository for user configs, typically for a particular config producer. The repo encapsulates
 * how the user configs are stored, and defines the methods to retrieve user configs and merge the repo with others.
 *
 * @author Ulf Lilleengen
 */
public class UserConfigRepo {
    private final Map<ConfigDefinitionKey, ConfigPayloadBuilder> userConfigsMap;

    public UserConfigRepo() {
        this.userConfigsMap = new LinkedHashMap<>();
    }

    @Override
    public UserConfigRepo clone() {
        return new UserConfigRepo(copyBuilders(userConfigsMap));
    }

    /**
     * Must copy the builder, because the merge method on {@link TreeConfigProducer} might override the row's builders otherwise
     */
    private Map<ConfigDefinitionKey, ConfigPayloadBuilder> copyBuilders(Map<ConfigDefinitionKey, ConfigPayloadBuilder> source) {
        Map<ConfigDefinitionKey, ConfigPayloadBuilder> ret = new LinkedHashMap<>();
        for (Map.Entry<ConfigDefinitionKey, ConfigPayloadBuilder> e : source.entrySet()) {
            ConfigDefinitionKey key = e.getKey();
            ConfigPayloadBuilder sourceVal = e.getValue();
            ConfigPayloadBuilder destVal = new ConfigPayloadBuilder(ConfigPayload.fromBuilder(sourceVal));
            ret.put(key, destVal);
        }
        return ret;
    }

    public UserConfigRepo(Map<ConfigDefinitionKey, ConfigPayloadBuilder> map) {
        this.userConfigsMap = map;
    }

    public ConfigPayloadBuilder get(ConfigDefinitionKey key) {
        return userConfigsMap.get(key);
    }

    public void merge(UserConfigRepo newRepo) {
        for (Map.Entry<ConfigDefinitionKey, ConfigPayloadBuilder> entry : newRepo.userConfigsMap.entrySet()) {
            if (entry.getValue() == null) continue;

            ConfigDefinitionKey key = entry.getKey();
            if (userConfigsMap.containsKey(key)) {
                ConfigPayloadBuilder lhsBuilder = userConfigsMap.get(key);
                ConfigPayloadBuilder rhsBuilder = entry.getValue();
                lhsBuilder.override(rhsBuilder);
            } else {
                userConfigsMap.put(key, entry.getValue());
            }
        }
    }

    public boolean isEmpty() {
        return userConfigsMap.isEmpty();
    }

    public int size() {
        return userConfigsMap.size();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (ConfigDefinitionKey key : userConfigsMap.keySet()) {
            sb.append(key.toString());
        }
        return sb.toString();
    }

    /**
     * The keys of all the configs contained in this.
     * @return a set of ConfigDefinitionsKey
     */
    public Set<ConfigDefinitionKey> configsProduced() {
        return userConfigsMap.keySet();
    }

}
