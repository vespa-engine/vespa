// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.provider;

import com.yahoo.component.annotation.Inject;
import com.yahoo.container.di.componentgraph.Provider;
import com.yahoo.language.process.Embedder;

/**
 * Provides the default embedder implementation if no embedder component has been explicitly configured
 * (dependency injection will fallback to providers if no components of the requested type is found).
 *
 * @author bratseth
 */
@SuppressWarnings("unused") // Injected
public class DefaultEmbedderProvider implements Provider<Embedder> {

    @Inject
    public DefaultEmbedderProvider() { }

    @Override
    public Embedder get() { return Embedder.throwsOnUse; }

    @Override
    public void deconstruct() {}

}
