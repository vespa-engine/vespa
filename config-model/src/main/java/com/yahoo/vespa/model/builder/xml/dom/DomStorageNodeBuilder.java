// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.vespa.model.content.StorageNode;
import com.yahoo.vespa.model.content.storagecluster.StorageCluster;
import org.w3c.dom.Element;

import static java.util.logging.Level.WARNING;

/**
 * Builder for a storage node
 *
 * @author hmusum
 */
public class DomStorageNodeBuilder extends VespaDomBuilder.DomConfigProducerBuilder<StorageNode, StorageNode> {

    @Override
    protected StorageNode doBuild(DeployState deployState, TreeConfigProducer<StorageNode> ancestor, Element producerSpec) {
        ModelElement e = new ModelElement(producerSpec);
        Double capacity = e.doubleAttribute("capacity");
        if (capacity != null)
            deployState.getDeployLogger().logApplicationPackage(WARNING, "'capacity' is deprecated, see https://docs.vespa.ai/en/reference/services-content#node");
        return new StorageNode(deployState.getProperties(), (StorageCluster)ancestor, capacity, e.integerAttribute("distribution-key"), false);
    }


}
