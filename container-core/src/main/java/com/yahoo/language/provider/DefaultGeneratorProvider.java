// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.provider;

import com.yahoo.component.annotation.Inject;
import com.yahoo.container.di.componentgraph.Provider;
import com.yahoo.language.process.Generator;

/**
 * Provides the default generator implementation if no generator component has been explicitly configured
 * (dependency injection will fall back to providers if no components of the requested type is found).
 *
 * @author lesters
 */
@SuppressWarnings("unused") // Injected
public class DefaultGeneratorProvider implements Provider<Generator> {

    @Inject
    public DefaultGeneratorProvider() { }

    @Override
    public Generator get() { return Generator.throwsOnUse; }

    @Override
    public void deconstruct() {}

}
