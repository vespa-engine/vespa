// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.search;

import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.vespa.config.search.DispatchConfig;
import com.yahoo.vespa.config.search.DispatchNodesConfig;
import com.yahoo.vespa.model.container.component.Component;
import com.yahoo.vespa.model.container.PlatformBundles;
import com.yahoo.vespa.model.search.IndexedSearchCluster;

/**
 * Represents a dispatcher component - an instance of a dispatcher in a search container cluster
 * knows how to communicate with one indexed search cluster and owns the connections to it.
 *
 * @author bratseth
 */
public class DispatcherComponent extends Component<TreeConfigProducer<?>, ComponentModel> implements
        DispatchConfig.Producer,
        DispatchNodesConfig.Producer
{

    private final IndexedSearchCluster indexedSearchCluster;

    public DispatcherComponent(IndexedSearchCluster indexedSearchCluster) {
        super(toComponentModel(indexedSearchCluster.getClusterName()));
        this.indexedSearchCluster = indexedSearchCluster;
    }

    private static ComponentModel toComponentModel(String clusterName) {
        String dispatcherComponentId = "dispatcher." + clusterName; // used by ClusterSearcher
        return new ComponentModel(dispatcherComponentId,
                                  com.yahoo.search.dispatch.Dispatcher.class.getName(),
                                  PlatformBundles.SEARCH_AND_DOCPROC_BUNDLE);
    }

    @Override
    public void getConfig(DispatchConfig.Builder builder) {
        indexedSearchCluster.getConfig(builder);
    }

    @Override
    public void getConfig(DispatchNodesConfig.Builder builder) {
        indexedSearchCluster.getConfig(builder);
    }

}
