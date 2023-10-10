// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.federation.sourceref;


import com.google.common.base.Joiner;
import com.yahoo.component.ComponentId;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.component.chain.model.ComponentAdaptor;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.processing.request.Properties;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;


public class SourcesTarget extends Target {

    private ComponentRegistry<ComponentAdaptor<SearchChainInvocationSpec>> providerSources = new ComponentRegistry<>() {};

    private SearchChainInvocationSpec defaultProviderSource;

    public SourcesTarget(ComponentId sourceId) {
        super(sourceId);
    }

    @Override
    public SearchChainInvocationSpec responsibleSearchChain(Properties queryProperties) throws UnresolvedSearchChainException {
        ComponentSpecification providerSpecification = providerSpecificationForSource(queryProperties);
        if (providerSpecification == null) {
            return defaultProviderSource;
        } else {
            return lookupProviderSource(providerSpecification);
        }
    }

    @Override
    public String searchRefDescription() {
        StringBuilder builder = new StringBuilder(sourceId().stringValue());
        builder.append("[provider = ").
                append(Joiner.on(", ").join(allProviderIdsStringValue())).
                append("]");
        return builder.toString();
    }

    private SortedSet<String> allProviderIdsStringValue() {
        SortedSet<String> result = new TreeSet<>();
        for (ComponentAdaptor<SearchChainInvocationSpec> providerSource : providerSources.allComponents()) {
            result.add(providerSource.getId().stringValue());
        }
        return result;
    }

    private SearchChainInvocationSpec lookupProviderSource(ComponentSpecification providerSpecification)
            throws UnresolvedSearchChainException {
        ComponentAdaptor<SearchChainInvocationSpec> providerSource = providerSources.getComponent(providerSpecification);

        if (providerSource == null)
            throw UnresolvedProviderException.createForMissingProvider(sourceId(), providerSpecification);

        return providerSource.model;
    }

    public void freeze() {
        if (defaultProviderSource == null)
            throw new RuntimeException("Null default provider source for source " + sourceId() + ".");

        providerSources.freeze();
    }

    public void addSource(ComponentId providerId, SearchChainInvocationSpec searchChainInvocationSpec,
                          boolean isDefaultProviderForSource) {
        providerSources.register(providerId, new ComponentAdaptor<>(providerId, searchChainInvocationSpec));

        if (isDefaultProviderForSource) {
            setDefaultProviderSource(searchChainInvocationSpec);
        }
    }

    private void setDefaultProviderSource(SearchChainInvocationSpec searchChainInvocationSpec) {
        if (defaultProviderSource != null)
            throw new RuntimeException("Tried to set two default providers for source " + sourceId() + ".");

        defaultProviderSource = searchChainInvocationSpec;
    }

    ComponentId sourceId() {
        return localId;
    }


    /**
     * Looks up source.(sourceId).provider in the query properties.
     * @return null if the default provider should be used
     */
    private ComponentSpecification providerSpecificationForSource(Properties queryProperties) {
        String spec = queryProperties.getString("source." + sourceId().stringValue() + ".provider");
        return ComponentSpecification.fromString(spec);
    }

    public SearchChainInvocationSpec defaultProviderSource() {
        return defaultProviderSource;
    }

    public List<SearchChainInvocationSpec> allProviderSources() {
        List<SearchChainInvocationSpec> allProviderSources = new ArrayList<>();
        for (ComponentAdaptor<SearchChainInvocationSpec> component : providerSources.allComponents()) {
            allProviderSources.add(component.model);
        }
        return allProviderSources;
    }
}
