// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.search;

import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.vespa.config.search.DispatchConfig;
import com.yahoo.vespa.model.container.component.Component;
import com.yahoo.vespa.model.container.xml.BundleMapper;
import com.yahoo.vespa.model.search.IndexedSearchCluster;

/**
 * Represents a dispatcher component - an instance of a dispatcher in a search container cluster
 * knows how to communicate with one indexed search cluster and owns the connections to it.
 *
 * @author bratseth
 */
public class DispatcherComponent extends Component<DispatcherComponent, ComponentModel>
        implements DispatchConfig.Producer {

    private final IndexedSearchCluster indexedSearchCluster;

    public DispatcherComponent(IndexedSearchCluster indexedSearchCluster) {
        super(toComponentModel(indexedSearchCluster));
        this.indexedSearchCluster = indexedSearchCluster;
    }

    private static ComponentModel toComponentModel(IndexedSearchCluster indexedSearchCluster) {
        String dispatcherComponentId = "dispatcher." + indexedSearchCluster.getClusterName(); // used by ClusterSearcher
        return new ComponentModel(dispatcherComponentId,
                                  com.yahoo.search.dispatch.Dispatcher.class.getName(),
                                  BundleMapper.searchAndDocprocBundle,
                                  null);
    }

    @Override
    public void getConfig(DispatchConfig.Builder builder) {
        indexedSearchCluster.getConfig(builder);
    }

}
