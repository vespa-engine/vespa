// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.component.chain;

import com.yahoo.component.AbstractComponent;
import com.yahoo.component.ComponentId;
import com.yahoo.component.chain.dependencies.After;
import com.yahoo.component.chain.dependencies.Before;
import com.yahoo.component.chain.dependencies.Dependencies;
import com.yahoo.component.chain.dependencies.Provides;
import com.yahoo.component.chain.model.Chainable;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Component with dependencies.
 *
 * @author Tony Vaagenes
 */
public abstract class ChainedComponent extends AbstractComponent implements Chainable {

    /**
     * The immutable set of dependencies of this. NOTE: the default is only for unit testing.
     */
    private Dependencies dependencies = getAnnotatedDependencies();

    public ChainedComponent(ComponentId id) {
        super(id);
    }

    protected ChainedComponent() { }

    /**
     * Called by the container to assign the full set of dependencies to this class (configured and declared).
     * This is called once before this is started.
     *
     * @param dependencies The configured dependencies, that this method will merge with annotated dependencies.
     */
    public void initDependencies(Dependencies dependencies) {
        this.dependencies = dependencies.union(getAnnotatedDependencies());
    }

    /**
     * Returns the configured and declared dependencies of this chainedcomponent
     */
    public Dependencies getDependencies() { return dependencies; }

}