// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content.engines;

import com.yahoo.vespa.model.builder.xml.dom.ModelElement;
import com.yahoo.vespa.model.content.ContentSearchCluster;
import com.yahoo.vespa.model.content.StorageGroup;
import com.yahoo.vespa.model.content.StorageNode;
import com.yahoo.vespa.model.content.cluster.ContentCluster;
import com.yahoo.vespa.model.search.SearchNode;

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
        public PersistenceEngine create(StorageNode storageNode, StorageGroup parentGroup, ModelElement storageNodeElement) {
            SearchNode searchNode = search.addSearchNode(storageNode, parentGroup, storageNodeElement);
            return new ProtonProvider(storageNode, searchNode);
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
