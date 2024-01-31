// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content.engines;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.vespa.config.content.core.StorServerConfig;
import com.yahoo.vespa.model.builder.xml.dom.ModelElement;
import com.yahoo.vespa.model.content.StorageGroup;
import com.yahoo.vespa.model.content.StorageNode;
import com.yahoo.vespa.model.content.cluster.ContentCluster;

public class DummyPersistence extends PersistenceEngine {
    public DummyPersistence(StorageNode parent) {
        super(parent, "provider");
    }

    @Override
    public void getConfig(StorServerConfig.Builder builder) {
        builder.persistence_provider(new StorServerConfig.Persistence_provider.Builder().type(StorServerConfig.Persistence_provider.Type.Enum.DUMMY));
    }

    public static class Factory implements PersistenceFactory {

        @Override
        public PersistenceEngine create(DeployState deployState, StorageNode storageNode, StorageGroup parentGroup, ModelElement storageNodeElement) {
            return new DummyPersistence(storageNode);
        }

        @Override
        public boolean supportRevert() {
            return true;
        }

        @Override
        public boolean enableMultiLevelSplitting() {
            return true;
        }

        @Override
        public ContentCluster.DistributionMode getDefaultDistributionMode() {
            return ContentCluster.DistributionMode.LOOSE;
        }
    }
}
