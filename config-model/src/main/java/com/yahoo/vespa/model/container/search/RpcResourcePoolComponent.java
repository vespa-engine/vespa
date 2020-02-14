// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.search;

import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.vespa.config.search.DispatchConfig;
import com.yahoo.vespa.model.container.component.Component;
import com.yahoo.vespa.model.container.xml.BundleMapper;
import com.yahoo.vespa.model.search.IndexedSearchCluster;

public class RpcResourcePoolComponent extends Component<RpcResourcePoolComponent, ComponentModel>
        implements DispatchConfig.Producer {
    private final IndexedSearchCluster indexedSearchCluster;

    public RpcResourcePoolComponent(IndexedSearchCluster indexedSearchCluster) {
        super(toComponentModel(indexedSearchCluster));
        this.indexedSearchCluster = indexedSearchCluster;
    }

    @Override
    public void getConfig(DispatchConfig.Builder builder) {
        indexedSearchCluster.getConfig(builder);
    }

    private static ComponentModel toComponentModel(IndexedSearchCluster indexedSearchCluster) {
        String componentId = "rpcresourcepool." + indexedSearchCluster.getClusterName(); // used by Dispatcher
        return new ComponentModel(componentId,
                "com.yahoo.search.dispatch.rpc.RpcResourcePool",
                BundleMapper.searchAndDocprocBundle,
                null);
    }
}
