// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.search.searchchain;

import com.yahoo.component.chain.model.ChainSpecification;
import com.yahoo.search.searchchain.model.federation.FederationOptions;
import com.yahoo.vespa.model.container.component.ConfigProducerGroup;

import java.util.Arrays;
import java.util.Collection;

/**
 * Base config producer for search chains that communicate with backends.
 *
 * @author Tony Vaagenes
 */
public class Provider extends GenericTarget {

    private ConfigProducerGroup<Source> sources;

    public Provider(ChainSpecification specWithoutInnerSearchers, FederationOptions federationOptions) {
        super(specWithoutInnerSearchers, federationOptions);
        sources = new ConfigProducerGroup<>(this, "source");
    }

    public void addSource(Source source) {
        sources.addComponent(source.getComponentId(), source);
    }

    public Collection<Source> getSources() {
        return sources.getComponents();
    }

    @Override
    protected boolean useByDefault() {
        return sources.getComponents().isEmpty();
    }

    public Collection<? extends GenericTarget> defaultFederationTargets() {
        if (sources.getComponents().isEmpty()) {
            return Arrays.asList(this);
        } else {
            return sources.getComponents();
        }
    }
}
