// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.search;

import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.vespa.config.search.DispatchConfig;
import com.yahoo.vespa.config.search.DispatchNodesConfig;
import com.yahoo.vespa.model.container.PlatformBundles;
import com.yahoo.vespa.model.container.component.Component;
import com.yahoo.vespa.model.search.IndexedSearchCluster;

/**
 * Hold config for a dispatcher component, used to let the configurer have
 * all configs and dispatchers injected, and match them.
 *
 * @author jonmv
 */
public class DispatchNodesConfigHolderComponent extends Component<TreeConfigProducer<?>, ComponentModel> implements
        DispatchNodesConfig.Producer
{

    private final IndexedSearchCluster indexedSearchCluster;

    public DispatchNodesConfigHolderComponent(IndexedSearchCluster indexedSearchCluster) {
        super(toComponentModel(indexedSearchCluster.getClusterName()));
        this.indexedSearchCluster = indexedSearchCluster;
    }

    private static ComponentModel toComponentModel(String clusterName) {
        String dispatcherComponentId = "dispatcher-config." + clusterName; // used by DispatchConfigurer
        return new ComponentModel(dispatcherComponentId,
                                  com.yahoo.search.dispatch.DispatchNodesConfigHolder.class.getName(),
                                  PlatformBundles.SEARCH_AND_DOCPROC_BUNDLE);
    }

    @Override
    public void getConfig(DispatchNodesConfig.Builder builder) {
        indexedSearchCluster.getConfig(builder);
    }

}
