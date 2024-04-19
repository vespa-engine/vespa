// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.federation.sourceref;

import com.yahoo.component.ComponentSpecification;
import com.yahoo.processing.request.Properties;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Maps a source reference to search chain invocation specs.
 *
 * @author Tony Vaagenes
 */
public class SourceRefResolver {

    private final SearchChainResolver searchChainResolver;
    private final Map<String, List<String>> schema2Clusters;

    public SourceRefResolver(SearchChainResolver searchChainResolver, Map<String, List<String>> schema2Clusters) {
        this.searchChainResolver = searchChainResolver;
        this.schema2Clusters = schema2Clusters;
    }

    public List<ResolveResult> resolve(ComponentSpecification sourceRef, Properties sourceToProviderMap) {
        ResolveResult searchChainResolveResult = searchChainResolver.resolve(sourceRef, sourceToProviderMap);
        if (searchChainResolveResult.invocationSpec() == null) {
            return resolveClustersWithDocument(sourceRef, sourceToProviderMap);
        }
        return List.of(searchChainResolveResult);
    }

    private List<ResolveResult> resolveClustersWithDocument(ComponentSpecification sourceRef, Properties sourceToProviderMap) {

        if (hasOnlyName(sourceRef)) {
            List<ResolveResult> clusterSearchChains = new ArrayList<>();

            List<String> clusters = schema2Clusters.getOrDefault(sourceRef.getName(), List.of());
            for (String cluster : clusters) {
                clusterSearchChains.add(resolveClusterSearchChain(cluster, sourceRef, sourceToProviderMap));
            }

            if ( ! clusterSearchChains.isEmpty())
                return clusterSearchChains;
        }
        return List.of(new ResolveResult(createForMissingSourceRef(sourceRef)));
    }

    static String createForMissingSourceRef(ComponentSpecification source) {
        return "Could not resolve source ref '" + source + "'.";
    }

    private ResolveResult resolveClusterSearchChain(String cluster,
                                                    ComponentSpecification sourceRef,
                                                    Properties sourceToProviderMap) {
        var resolveResult = searchChainResolver.resolve(new ComponentSpecification(cluster), sourceToProviderMap);
        if (resolveResult.invocationSpec() == null) {
            return new ResolveResult("Failed to resolve cluster search chain '" + cluster +
                                     "' when using source ref '" + sourceRef + "' as a document name.");
        }
        return resolveResult;
    }

    private boolean hasOnlyName(ComponentSpecification sourceSpec) {
        return new ComponentSpecification(sourceSpec.getName()).equals(sourceSpec);
    }

}
