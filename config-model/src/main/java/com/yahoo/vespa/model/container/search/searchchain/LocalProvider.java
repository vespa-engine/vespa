// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.search.searchchain;

import com.yahoo.component.ComponentId;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.component.chain.model.ChainSpecification;
import com.yahoo.component.chain.model.ChainedComponentModel;
import com.yahoo.prelude.fastsearch.DocumentdbInfoConfig;
import com.yahoo.prelude.cluster.QrMonitorConfig;
import com.yahoo.vespa.config.search.RankProfilesConfig;
import com.yahoo.vespa.config.search.AttributesConfig;
import com.yahoo.search.config.ClusterConfig;
import com.yahoo.search.searchchain.model.federation.FederationOptions;
import com.yahoo.search.searchchain.model.federation.LocalProviderSpec;
import com.yahoo.vespa.model.search.AbstractSearchCluster;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Config producer for search chain responsible for sending queries to a local cluster.
 *
 * @author Tony Vaagenes
 */
public class LocalProvider extends Provider implements
        DocumentdbInfoConfig.Producer,
        ClusterConfig.Producer,
        AttributesConfig.Producer,
        QrMonitorConfig.Producer,
        RankProfilesConfig.Producer {

    private final LocalProviderSpec providerSpec;
    private volatile AbstractSearchCluster searchCluster;


    @Override
    public void getConfig(ClusterConfig.Builder builder) {
        assert (searchCluster != null) : "Null search cluster!";
        builder.clusterId(searchCluster.getClusterIndex());
        builder.clusterName(searchCluster.getClusterName());

        if (searchCluster.getVisibilityDelay() != null)
            builder.cacheTimeout(convertVisibilityDelay(searchCluster.getVisibilityDelay()));
    }

    @Override
    public void getConfig(RankProfilesConfig.Builder builder) {
        searchCluster.getConfig(builder);
    }

    @Override
    public void getConfig(AttributesConfig.Builder builder) {
        searchCluster.getConfig(builder);
    }

    @Override
    public void getConfig(QrMonitorConfig.Builder builder) {
        int requestTimeout = federationOptions().getTimeoutInMilliseconds();
        if (requestTimeout != -1) {
            builder.requesttimeout(requestTimeout);
        }
    }

    private void addProviderSearchers() {
        for (ChainedComponentModel searcherModel : LocalProviderSpec.searcherModels) {
            addInnerComponent(new Searcher<>(searcherModel));
        }
    }

    @Override
    public ChainSpecification getChainSpecification() {
        ChainSpecification spec =
                super.getChainSpecification();
        return new ChainSpecification(spec.componentId, spec.inheritance, spec.phases(),
                disableStemmingIfStreaming(spec.componentReferences));
    }

    //TODO: ugly, restructure this
    private Set<ComponentSpecification> disableStemmingIfStreaming(Set<ComponentSpecification> searcherReferences) {
        if (!searchCluster.isStreaming()) {
            return searcherReferences;
        } else {
            Set<ComponentSpecification> filteredSearcherReferences = new LinkedHashSet<>(searcherReferences);
            filteredSearcherReferences.remove(
                    toGlobalComponentId(
                            new ComponentId("com.yahoo.prelude.querytransform.StemmingSearcher")).
                            toSpecification());
            return filteredSearcherReferences;
        }
    }

    private ComponentId toGlobalComponentId(ComponentId searcherId) {
        return searcherId.nestInNamespace(getComponentId());
    }

    public String getClusterName() {
        return providerSpec.clusterName;
    }

    void setSearchCluster(AbstractSearchCluster searchCluster) {
        this.searchCluster = searchCluster;
    }

    public LocalProvider(ChainSpecification specWithoutInnerSearchers,
                         FederationOptions federationOptions,
                         LocalProviderSpec providerSpec) {
        super(specWithoutInnerSearchers, federationOptions);
        addProviderSearchers();
        this.providerSpec = providerSpec;
    }

    @Override
    public List<String> getDocumentTypes() {
        List<String> documentTypes = new ArrayList<>();

        for (AbstractSearchCluster.SchemaSpec spec : searchCluster.getLocalSDS()) {
            documentTypes.add(spec.getSearchDefinition().getSearch().getDocument().getName());
        }

        return documentTypes;
    }

    @Override
    public FederationOptions federationOptions() {
        Double queryTimeoutInSeconds = searchCluster.getQueryTimeout();

        return queryTimeoutInSeconds == null ?
                super.federationOptions() :
                super.federationOptions().inherit(
                        new FederationOptions().setTimeoutInMilliseconds((int) (queryTimeoutInSeconds * 1000)));
    }

    @Override
    public void getConfig(DocumentdbInfoConfig.Builder builder) {
        searchCluster.getConfig(builder);
    }

    // The semantics of visibility delay in search is deactivating caches if the
    // delay is less than 1.0, in qrs the cache is deactivated if the delay is 0
    // (or less). 1.0 seems a little arbitrary, so just doing the conversion
    // here instead of having two totally independent implementations having to
    // follow each other down in the modules.
    private static Double convertVisibilityDelay(Double visibilityDelay) {
        return (visibilityDelay < 1.0d) ? 0.0d : visibilityDelay;
    }

}
