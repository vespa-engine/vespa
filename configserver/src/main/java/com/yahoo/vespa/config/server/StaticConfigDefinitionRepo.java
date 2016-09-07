// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.google.inject.Inject;
import com.yahoo.config.model.api.ConfigDefinitionRepo;
import com.yahoo.vespa.config.ConfigDefinitionKey;
import com.yahoo.vespa.config.buildergen.ConfigDefinition;

import java.util.Collections;
import java.util.Map;

/**
 * A global pool of all config definitions that this server knows about. These objects can be shared
 * by all tenants, as they are not modified.
 *
 * @author lulf
 * @since 5.10
 */
public class StaticConfigDefinitionRepo implements ConfigDefinitionRepo {

    private final ConfigDefinitionRepo repo;

    // Only useful in tests that dont need full blown repo.
    public StaticConfigDefinitionRepo() {
        this.repo = new ConfigDefinitionRepo() {
            @Override
            public Map<ConfigDefinitionKey, ConfigDefinition> getConfigDefinitions() {
                return Collections.emptyMap();
            }
        };
    }

    @Inject
    public StaticConfigDefinitionRepo(ConfigServerDB serverDB) {
        this.repo = new com.yahoo.config.model.application.provider.StaticConfigDefinitionRepo(serverDB.serverdefs());
    }

    @Override
    public Map<ConfigDefinitionKey, ConfigDefinition> getConfigDefinitions() {
        return repo.getConfigDefinitions();
    }
}
