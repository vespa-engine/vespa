// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content.engines;

import com.yahoo.vespa.config.content.core.StorServerConfig;
import com.yahoo.vespa.model.builder.xml.dom.ModelElement;
import com.yahoo.vespa.model.content.StorageGroup;
import com.yahoo.vespa.model.content.StorageNode;
import com.yahoo.vespa.model.content.cluster.ContentCluster;
import com.yahoo.vespa.model.search.SearchNode;

public class RPCEngine extends PersistenceEngine {

    private SearchNode searchNode;
    public RPCEngine(StorageNode parent) {
        super(parent, "provider");
    }

    public RPCEngine(StorageNode parent, SearchNode searchNode) {
        super(parent, "provider");
        this.searchNode = searchNode;
    }

    @Override
    public void getConfig(StorServerConfig.Builder builder) {
        StorServerConfig.Persistence_provider.Builder provider =
                new StorServerConfig.Persistence_provider.Builder();
        provider.type(StorServerConfig.Persistence_provider.Type.Enum.RPC);

        if (searchNode != null) {
            provider.rpc(new StorServerConfig.Persistence_provider.Rpc.Builder().connectspec("tcp/localhost:" + searchNode.getPersistenceProviderRpcPort()));
        }

        builder.persistence_provider(provider);
    }

    public static class Factory implements PersistenceFactory {
        @Override
        public PersistenceEngine create(StorageNode storageNode, StorageGroup parentGroup, ModelElement storageNodeElement) {
            return new RPCEngine(storageNode);
        }

        @Override
        public boolean supportRevert() {
            return false;
        }

        @Override
        public boolean enableMultiLevelSplitting() {
            return false;
        }

        @Override
        public ContentCluster.DistributionMode getDefaultDistributionMode() {
            return ContentCluster.DistributionMode.LOOSE;
        }
    }
}
