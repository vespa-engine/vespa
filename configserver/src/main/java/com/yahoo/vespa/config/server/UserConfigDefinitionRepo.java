// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;


import com.yahoo.config.model.api.ConfigDefinitionRepo;
import com.yahoo.vespa.config.ConfigDefinitionKey;
import com.yahoo.vespa.config.buildergen.ConfigDefinition;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author hmusum
 */
public class UserConfigDefinitionRepo implements ConfigDefinitionRepo {

    private final Map<ConfigDefinitionKey, ConfigDefinition> defs = new LinkedHashMap<>();


    public void add(ConfigDefinitionKey key, ConfigDefinition configDefinition) {
        defs.put(key, configDefinition);
    }

    @Override
    public Map<ConfigDefinitionKey, ConfigDefinition> getConfigDefinitions() {
        return defs;
    }

    @Override
    public ConfigDefinition get(ConfigDefinitionKey key) {
        return defs.get(key);
    }
}
