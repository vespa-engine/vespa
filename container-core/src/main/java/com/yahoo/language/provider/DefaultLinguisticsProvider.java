// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.provider;

import ai.vespa.opennlp.OpenNlpConfig;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.yahoo.component.annotation.Inject;
import com.yahoo.container.di.componentgraph.Provider;
import com.yahoo.language.Linguistics;
import com.yahoo.language.opennlp.OpenNlpLinguistics;

/**
 * Provides the default linguistics implementation if no linguistics component has been explicitly configured
 * (dependency injection will fallback to providers if no components of the requested type is found).
 *
 * @author bratseth
 */
@SuppressWarnings("unused") // Injected
public class DefaultLinguisticsProvider implements Provider<Linguistics> {

    // Use lazy initialization to avoid expensive (memory-wise) instantiation
    private final Supplier<Linguistics> linguisticsSupplier;

    @Inject
    public DefaultLinguisticsProvider(OpenNlpConfig config) {
        linguisticsSupplier = Suppliers.memoize(() -> createOpenNlpLinguistics(config));
    }

    @Override
    public Linguistics get() { return linguisticsSupplier.get(); }

    @Override
    public void deconstruct() {}

    private static OpenNlpLinguistics createOpenNlpLinguistics(OpenNlpConfig config) {
        return new OpenNlpLinguistics(config);
    }

}
