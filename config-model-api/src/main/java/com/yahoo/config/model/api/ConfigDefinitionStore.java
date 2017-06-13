// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

import com.yahoo.vespa.config.ConfigDefinition;
import com.yahoo.vespa.config.ConfigDefinitionKey;

/**
 * @author lulf
 * @since 5.1
 */
public interface ConfigDefinitionStore {
    ConfigDefinition getConfigDefinition(ConfigDefinitionKey defKey);
}
