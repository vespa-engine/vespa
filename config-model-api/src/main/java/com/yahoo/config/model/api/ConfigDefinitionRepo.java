// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

import com.yahoo.vespa.config.ConfigDefinitionKey;
import com.yahoo.vespa.config.buildergen.ConfigDefinition;

import java.util.Map;

/**
 * Represents a repository of config definitions.
 *
 * @author lulf
 * @since 5.10
 */
public interface ConfigDefinitionRepo {

    /**
     * Retrieve a map with all config definitions in this repo.
     */
    Map<ConfigDefinitionKey, ConfigDefinition> getConfigDefinitions();

}
