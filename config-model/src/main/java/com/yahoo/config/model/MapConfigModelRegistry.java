// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model;

import com.google.inject.Inject;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.config.model.builder.xml.ConfigModelBuilder;
import com.yahoo.config.model.builder.xml.ConfigModelId;
import com.yahoo.log.LogLevel;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

import java.util.*;
import java.util.logging.Logger;

/**
 * @author lulf
 * @since 5.1
 */
public class MapConfigModelRegistry extends ConfigModelRegistry {

    private static final Logger log = Logger.getLogger(MapConfigModelRegistry.class.getPackage().getName());
    private final List<ConfigModelBuilder> builders;

    /**
     * Constructs a registry of config models, where the components are injected.
     *
     * @param registry a component registry
     */
    @Inject
    public MapConfigModelRegistry(ComponentRegistry<? extends ConfigModelBuilder> registry) {
        this(registry.allComponents());
    }

    /**
     * Constructs a registry of config models.
     *
     * @param builderCollection A collection of builders used to populate the registry.
     */
    public MapConfigModelRegistry(Collection<? extends ConfigModelBuilder> builderCollection) {
        super();
        builders = new ArrayList<>(builderCollection);
    }

    @Override
    public Collection<ConfigModelBuilder> resolve(ConfigModelId id) {
        Set<ConfigModelBuilder> matchingBuilders = new HashSet<>(chained().resolve(id));
        for (ConfigModelBuilder builder : builders)
            if (builder.handlesElements().contains(id))
                matchingBuilders.add(builder);
        return matchingBuilders;
    }

    /**
     * Create a registry from a variable argument list of builders.
     *
     * @param builders A variable argument list of builders to use in this map
     * @return a ConfigModelRegistry instance.
     */
    @SafeVarargs
    @SuppressWarnings("varargs")
    public static ConfigModelRegistry createFromList(ConfigModelBuilder<? extends ConfigModel> ... builders) {
        return new MapConfigModelRegistry(Arrays.asList(builders));
    }

}
