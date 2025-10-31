// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.vespa.model.content.Distributor;
import com.yahoo.vespa.model.content.DistributorCluster;
import com.yahoo.vespa.model.content.engines.PersistenceEngine;
import org.w3c.dom.Element;

/**
 * Builder for a distributor node
 *
 * @author hmusum
 */
public class DomDistributorBuilder extends VespaDomBuilder.DomConfigProducerBuilder<Distributor, Distributor> {

    PersistenceEngine persistenceProvider;

    public DomDistributorBuilder(PersistenceEngine persistenceProvider) {
        this.persistenceProvider = persistenceProvider;
    }

    @Override
    protected Distributor doBuild(DeployState deployState, TreeConfigProducer<Distributor> ancestor, Element producerSpec) {
        return new Distributor(deployState.getProperties(),
                               (DistributorCluster) ancestor,
                               new ModelElement(producerSpec).integerAttribute("distribution-key"),
                               persistenceProvider);
    }

}
