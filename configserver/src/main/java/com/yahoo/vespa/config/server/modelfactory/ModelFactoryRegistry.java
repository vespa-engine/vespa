// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.modelfactory;

import com.yahoo.component.annotation.Inject;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.config.model.api.ModelFactory;
import com.yahoo.component.Version;
import com.yahoo.vespa.config.server.http.UnknownVespaVersionException;

import java.util.*;

/**
 * A registry of model factories. Allows querying for a specific version of a {@link ModelFactory} or
 * simply returning all of them. Keeps track of the latest {@link Version} supported.
 *
 * @author Ulf Lilleengen
 */
public class ModelFactoryRegistry {

    private final Map<Version, ModelFactory> factories = new HashMap<>();

    @Inject
    public ModelFactoryRegistry(ComponentRegistry<ModelFactory> factories) {
        this(factories.allComponents());
    }

    public ModelFactoryRegistry(List<ModelFactory> modelFactories) {
        if (modelFactories.isEmpty()) {
            throw new IllegalArgumentException("No ModelFactory instances registered, cannot build config models");
        }
        for (ModelFactory factory : modelFactories) {
            factories.put(factory.version(), factory);
        }
    }

    public Set<Version> allVersions() { return factories.keySet(); }

    /**
     * Returns the factory for the given version
     *
     * @throws UnknownVespaVersionException if there is no factory for this version
     */
    public ModelFactory getFactory(Version version) {
        if ( ! factories.containsKey(version))
            throw new UnknownVespaVersionException("Unknown Vespa version '" + version +
                                                   "', cannot build config model for this version, known versions: " + allVersions());
        return factories.get(version);
    }

    /**
     * Return all factories that can build a model.
     *
     * @return An immutable collection of {@link com.yahoo.config.model.api.ModelFactory} instances.
     */
    public Collection<ModelFactory> getFactories() {
        return Collections.unmodifiableCollection(factories.values());
    }

}
