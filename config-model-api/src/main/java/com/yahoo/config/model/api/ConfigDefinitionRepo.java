// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

import com.yahoo.vespa.config.ConfigDefinitionKey;
import com.yahoo.vespa.config.buildergen.ConfigDefinition;

import java.util.Map;

/**
 * A config definition repository.
 *
 * @author Ulf Lillengen
 */
public interface ConfigDefinitionRepo {

    /**
     * Retrieve a map with all config definitions in this repo.
     */
    Map<ConfigDefinitionKey, ConfigDefinition> getConfigDefinitions();

    /**
     * Gets a config definition from repo or null if not found
     */
    ConfigDefinition get(ConfigDefinitionKey key);

}
