// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.deploy;

import com.yahoo.vespa.config.ConfigDefinition;
import com.yahoo.vespa.config.ConfigDefinitionKey;

import java.util.Optional;

/**
 * @author lulf
 * @since 5.1
 */
public interface ConfigDefinitionStore {

    /**
     * Returns a config definition, or empty if the config definition is not found.
     */
    Optional<ConfigDefinition> getConfigDefinition(ConfigDefinitionKey defKey);

}
