// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content.engines;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.vespa.config.content.core.StorServerConfig;
import com.yahoo.config.model.producer.AnyConfigProducer;
import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.vespa.model.builder.xml.dom.ModelElement;
import com.yahoo.vespa.model.content.StorageGroup;
import com.yahoo.vespa.model.content.StorageNode;
import com.yahoo.vespa.model.content.cluster.ContentCluster;

public abstract class PersistenceEngine extends TreeConfigProducer<AnyConfigProducer> implements StorServerConfig.Producer {

    public PersistenceEngine(TreeConfigProducer<? super PersistenceEngine>  parent, String name) {
        super(parent, name);
    }

    /**
     * Creates a config producer for the engines provider at a given node.
     */
    public interface PersistenceFactory {

        PersistenceEngine create(DeployState deployState, StorageNode storageNode, StorageGroup parentGroup, ModelElement storageNodeElement);

        /**
         * If a write request succeeds on some nodes and fails on others, causing request to
         * fail to client, the content layer will revert the operation where it succeeded if
         * reverts are supported. (Typically require backend to keep multiple entries of the
         * same document identifier persisted at the same time)
         */
        boolean supportRevert();

        /**
         * Multi level splitting can increase split performance a lot where documents have been
         * co-localized, for backends where retrieving document identifiers contained in bucket
         * is cheap. Backends where split is cheaper than fetching document identifiers will
         * not want to enable multi level splitting.
         */
        boolean enableMultiLevelSplitting();

        ContentCluster.DistributionMode getDefaultDistributionMode();

    }

}
