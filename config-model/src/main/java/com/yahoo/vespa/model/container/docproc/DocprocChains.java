// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.docproc;

import com.yahoo.component.ComponentId;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.container.jdisc.config.SessionConfig;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.component.Component;
import com.yahoo.vespa.model.container.component.SystemBindingPattern;
import com.yahoo.vespa.model.container.component.chain.Chains;
import com.yahoo.vespa.model.container.component.chain.ProcessingHandler;

/**
 * @author Einar M R Rosenvinge
 */
public class DocprocChains extends Chains<DocprocChain> {
    private final ProcessingHandler<DocprocChains> docprocHandler;

    public DocprocChains(AbstractConfigProducer parent, String subId) {
        super(parent, subId);
        docprocHandler = new ProcessingHandler<>(this, "com.yahoo.docproc.jdisc.DocumentProcessingHandler");
        addComponent(docprocHandler);
    }

    private void addComponent(Component component) {
        if (!(getParent() instanceof ContainerCluster)) {
            return;
        }
        ((ContainerCluster) getParent()).addComponent(component);
    }


    public void addServersAndClientsForChains() {
        if (getParent() instanceof ApplicationContainerCluster) {
            for (DocprocChain chain: getChainGroup().getComponents())
                addServerAndClientForChain((ApplicationContainerCluster) getParent(), chain);
        }
    }

    private void addServerAndClientForChain(ApplicationContainerCluster cluster, DocprocChain docprocChain) {
        docprocHandler.addServerBindings(SystemBindingPattern.fromPattern("mbus://*/" + docprocChain.getSessionName()));

        cluster.addMbusServer(ComponentId.fromString(docprocChain.getSessionName()));

        MbusClient client = new MbusClient(docprocChain.getSessionName(), SessionConfig.Type.INTERMEDIATE);
        client.addClientBindings(SystemBindingPattern.fromPattern("mbus://*/" + client.getSessionName()));
        addComponent(client);
    }
}
