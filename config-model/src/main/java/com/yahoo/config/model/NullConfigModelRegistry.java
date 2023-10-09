// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model;

import com.yahoo.config.model.builder.xml.ConfigModelBuilder;
import com.yahoo.config.model.builder.xml.ConfigModelId;

import java.util.Collection;

/**
 * A config model class registry that only forwards to the chained registry.
 *
 * @author bratseth
 */
public class NullConfigModelRegistry extends ConfigModelRegistry {

    @Override
    public Collection<ConfigModelBuilder> resolve(ConfigModelId id) {
        return chained().resolve(id);
    }

}
