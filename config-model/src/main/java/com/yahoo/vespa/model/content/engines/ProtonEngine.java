// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content.engines;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.vespa.model.builder.xml.dom.ModelElement;
import com.yahoo.vespa.model.content.ContentSearchCluster;
import com.yahoo.vespa.model.content.StorageGroup;
import com.yahoo.vespa.model.content.StorageNode;
import com.yahoo.vespa.model.content.cluster.ContentCluster;

/**
 * Initializes the engines engine on each storage node. May include creating other
 * nodes.
 */
public class ProtonEngine {
    public static class Factory implements PersistenceEngine.PersistenceFactory {
        ContentSearchCluster search;

        public Factory(ContentSearchCluster search) {
            this.search = search;
        }

        @Override
        public PersistenceEngine create(DeployState deployState, StorageNode storageNode, StorageGroup parentGroup, ModelElement storageNodeElement) {
            search.addSearchNode(deployState, storageNode, parentGroup, storageNodeElement);
            return new ProtonProvider(storageNode);
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
