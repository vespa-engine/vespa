// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.federation.sourceref;

import com.yahoo.component.ComponentSpecification;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.processing.request.Properties;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Maps a source reference to search chain invocation specs.
 *
 * @author Tony Vaagenes
 */
public class SourceRefResolver {

    private final SearchChainResolver searchChainResolver;

    public SourceRefResolver(SearchChainResolver searchChainResolver) {
        this.searchChainResolver = searchChainResolver;
    }

    public Set<SearchChainInvocationSpec> resolve(ComponentSpecification sourceRef,
                                                  Properties sourceToProviderMap,
                                                  IndexFacts indexFacts) throws UnresolvedSearchChainException {
        try {
            return new LinkedHashSet<>(List.of(searchChainResolver.resolve(sourceRef, sourceToProviderMap)));
        } catch (UnresolvedSourceRefException e) {
            return resolveClustersWithDocument(sourceRef, sourceToProviderMap, indexFacts);
        }
    }

    private Set<SearchChainInvocationSpec> resolveClustersWithDocument(ComponentSpecification sourceRef,
                                                                       Properties sourceToProviderMap,
                                                                       IndexFacts indexFacts)
            throws UnresolvedSearchChainException {

        if (hasOnlyName(sourceRef)) {
            Set<SearchChainInvocationSpec> clusterSearchChains = new LinkedHashSet<>();

            List<String> clusters = indexFacts.clustersHavingSearchDefinition(sourceRef.getName());
            for (String cluster : clusters) {
                clusterSearchChains.add(resolveClusterSearchChain(cluster, sourceRef, sourceToProviderMap));
            }

            if ( ! clusterSearchChains.isEmpty())
                return clusterSearchChains;
        }

        throw UnresolvedSourceRefException.createForMissingSourceRef(sourceRef);

    }

    private SearchChainInvocationSpec resolveClusterSearchChain(String cluster,
                                                                ComponentSpecification sourceRef,
                                                                Properties sourceToProviderMap)
            throws UnresolvedSearchChainException {
        try {
            return searchChainResolver.resolve(new ComponentSpecification(cluster), sourceToProviderMap);
        }
        catch (UnresolvedSearchChainException e) {
            throw new UnresolvedSearchChainException("Failed to resolve cluster search chain '" + cluster +
                                                     "' when using source ref '" + sourceRef +
                                                     "' as a document name.");
        }
    }

    private boolean hasOnlyName(ComponentSpecification sourceSpec) {
        return new ComponentSpecification(sourceSpec.getName()).equals(sourceSpec);
    }

}
