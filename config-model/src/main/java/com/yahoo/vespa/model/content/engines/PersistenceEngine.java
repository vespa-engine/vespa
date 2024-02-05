// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

        ContentCluster.DistributionMode getDefaultDistributionMode();

    }

}
