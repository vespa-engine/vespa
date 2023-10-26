// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model;

import com.yahoo.config.model.builder.xml.ConfigModelBuilder;
import com.yahoo.config.model.builder.xml.ConfigModelId;

import java.util.Collection;
import java.util.Collections;

/**
 * A resolver of implementations of named config models.
 * Registries may be chained in a chain of command.
 *
 * @author bratseth
 */
public abstract class ConfigModelRegistry {

    private final ConfigModelRegistry chained;

    public ConfigModelRegistry() {
        this(new EmptyTerminalRegistry());
    }

    /** Creates a config model class registry which forwards unresolved requests to the argument instance */
    public ConfigModelRegistry(ConfigModelRegistry chained) {
        this.chained=chained;
    }

    /**
     * Returns the builders this id resolves to both in this and any chained registry.
     *
     * @return the resolved config model builders, or an empty list (never null) if none
     */
    public abstract Collection<ConfigModelBuilder> resolve(ConfigModelId id);

    public ConfigModelRegistry chained() { return chained; }

    /** An empty registry which does not support chaining */
    private static class EmptyTerminalRegistry extends ConfigModelRegistry {

        public EmptyTerminalRegistry() {
            super(null);
        }

        @Override
        public Collection<ConfigModelBuilder> resolve(ConfigModelId id) {
            return Collections.emptyList();
        }
    }

}
