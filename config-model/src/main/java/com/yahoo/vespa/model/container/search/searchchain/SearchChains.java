// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.search.searchchain;

import com.yahoo.collections.CollectionUtil;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.vespa.model.container.component.chain.Chains;
import com.yahoo.vespa.model.search.SearchCluster;
import com.yahoo.vespa.model.container.search.searchchain.defaultsearchchains.LocalClustersCreator;
import com.yahoo.vespa.model.container.search.searchchain.defaultsearchchains.VespaSearchChainsCreator;

import java.util.Collection;
import java.util.Map;

/**
 * Root config producer of the whole search chains model (contains searchchains and searchers).
 *
 * @author Tony Vaagenes
 */
public class SearchChains extends Chains<SearchChain> {

    private final SourceGroupRegistry sourceGroups = new SourceGroupRegistry();

    public SearchChains(AbstractConfigProducer<?> parent, String subId) {
        super(parent, subId);
    }

    public void initialize(Map<String, ? extends SearchCluster> searchClustersByName) {
        LocalClustersCreator.addDefaultLocalProviders(this, searchClustersByName.keySet());
        VespaSearchChainsCreator.addVespaSearchChains(this);

        validateSourceGroups(); // must be done before initializing searchers since they are used by FederationSearchers
        initializeComponents(searchClustersByName);
    }

    private void initializeComponents(Map<String, ? extends SearchCluster> searchClustersByName) {
        setSearchClusterForLocalProvider(searchClustersByName);
        initializeComponents();
    }

    private void setSearchClusterForLocalProvider(Map<String, ? extends SearchCluster> clusterIndexByName) {
        for (LocalProvider provider : localProviders()) {
            SearchCluster cluster = clusterIndexByName.get(provider.getClusterName());
            if (cluster == null)
                throw new IllegalArgumentException("No searchable content cluster with id '" + provider.getClusterName() + "'");
            provider.setSearchCluster(cluster);
        }
    }

    private void validateSourceGroups() {
        for (SourceGroup sourceGroup : sourceGroups.groups()) {
            sourceGroup.validate();

            if (getChainGroup().getComponentMap().containsKey(sourceGroup.getComponentId())) {
                throw new IllegalArgumentException("Id '" + sourceGroup.getComponentId() +
                                                   "' is used both for a source and another search chain/provider");
            }
        }
    }

    @Override
    public void validate() throws Exception {
        validateSourceGroups();
        super.validate();
    }

    SourceGroupRegistry allSourceGroups() {
        return sourceGroups;
    }

    public Collection<LocalProvider> localProviders() {
        return CollectionUtil.filter(allChains().allComponents(), LocalProvider.class);
    }

    /*
     * If searchChain is a provider, its sources must already have been attached.
     */
    @Override
    public void add(SearchChain searchChain) {
        assert !(searchChain instanceof Source);

        super.add(searchChain);

        if (searchChain instanceof Provider) {
            sourceGroups.addSources((Provider)searchChain);
        }
    }

    @Override
    public ComponentRegistry<SearchChain> allChains() {
        ComponentRegistry<SearchChain> allChains = new ComponentRegistry<>();
        for (SearchChain chain : getChainGroup().getComponents()) {
            allChains.register(chain.getId(), chain);
            if (chain instanceof Provider)
                addSources(allChains, (Provider)chain);
        }
        allChains.freeze();
        return allChains;
    }

    private void addSources(ComponentRegistry<SearchChain> chains, Provider provider) {
         for (Source source : provider.getSources()) {
             chains.register(source.getId(), source);
         }
     }

}
