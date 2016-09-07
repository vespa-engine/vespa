// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.yahoo.cloud.config.LbServicesConfig;
import com.yahoo.config.SimpletypesConfig;
import com.yahoo.config.model.api.ConfigDefinitionRepo;
import com.yahoo.vespa.config.ConfigDefinitionKey;
import com.yahoo.vespa.config.buildergen.ConfigDefinition;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author lulf
 * @since 5.
 */
public class TestConfigDefinitionRepo implements ConfigDefinitionRepo {
    private final Map<ConfigDefinitionKey, ConfigDefinition> repo = new LinkedHashMap<>();
    public TestConfigDefinitionRepo() {
        repo.put(new ConfigDefinitionKey(SimpletypesConfig.CONFIG_DEF_NAME, SimpletypesConfig.CONFIG_DEF_NAMESPACE),
                 new ConfigDefinition(SimpletypesConfig.CONFIG_DEF_NAME, SimpletypesConfig.CONFIG_DEF_SCHEMA));
        repo.put(new ConfigDefinitionKey(LbServicesConfig.CONFIG_DEF_NAME, LbServicesConfig.CONFIG_DEF_NAMESPACE),
                 new ConfigDefinition(LbServicesConfig.CONFIG_DEF_NAME, LbServicesConfig.CONFIG_DEF_SCHEMA));
    }

    @Override
    public Map<ConfigDefinitionKey, ConfigDefinition> getConfigDefinitions() {
        return repo;
    }
}
