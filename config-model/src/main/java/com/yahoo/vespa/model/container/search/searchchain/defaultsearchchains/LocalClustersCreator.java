// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.search.searchchain.defaultsearchchains;

import com.yahoo.component.ComponentId;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.component.chain.Phase;
import com.yahoo.component.chain.model.ChainSpecification;
import com.yahoo.search.searchchain.model.federation.FederationOptions;
import com.yahoo.search.searchchain.model.federation.LocalProviderSpec;
import com.yahoo.vespa.model.container.search.searchchain.LocalProvider;
import com.yahoo.vespa.model.container.search.searchchain.SearchChains;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Adds default search chains for all local clusters not mentioned explicitly
 * @author Tony Vaagenes
 */
public class LocalClustersCreator {

    static ChainSpecification emptySearchChainSpecification(String componentName) {
        return new ChainSpecification(new ComponentId(componentName),
                                      VespaSearchChainsCreator.inheritsVespaPhases(), //TODO: refactor
                                      List.of(),
                                      Set.of());
    }

    static LocalProvider createDefaultLocalProvider(String clusterName) {
        return new LocalProvider(emptySearchChainSpecification(clusterName),
                                 new FederationOptions(),
                                 new LocalProviderSpec(clusterName));
    }

    static Set<String> presentClusters(SearchChains searchChains) {
        Set<String> presentClusters = new LinkedHashSet<>();
        for (LocalProvider provider : searchChains.localProviders()) {
            presentClusters.add(provider.getClusterName());
        }
        return presentClusters;
    }

    public static void addDefaultLocalProviders(SearchChains searchChains, Set<String> clusterNames) {
        Set<String> missingClusters = new LinkedHashSet<>(clusterNames);
        missingClusters.removeAll(presentClusters(searchChains));

        for (String clusterName : missingClusters) {
            searchChains.add(createDefaultLocalProvider(clusterName));
        }
    }

}
