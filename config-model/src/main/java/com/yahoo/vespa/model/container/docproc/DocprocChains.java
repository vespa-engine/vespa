// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.docproc;

import com.yahoo.component.ComponentId;
import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.container.jdisc.config.SessionConfig;
import com.yahoo.docproc.jdisc.observability.DocprocsStatusExtension;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.PlatformBundles;
import com.yahoo.vespa.model.container.component.Component;
import com.yahoo.vespa.model.container.component.SimpleComponent;
import com.yahoo.vespa.model.container.component.SystemBindingPattern;
import com.yahoo.vespa.model.container.component.chain.Chains;
import com.yahoo.vespa.model.container.component.chain.ProcessingHandler;

/**
 * @author Einar M R Rosenvinge
 */
public class DocprocChains extends Chains<DocprocChain> {

    public static final String DOCUMENT_TYPE_MANAGER_CLASS = "com.yahoo.document.DocumentTypeManager";

    private final ProcessingHandler<DocprocChains> docprocHandler;

    public DocprocChains(TreeConfigProducer<? super Chains> parent, String subId) {
        super(parent, subId);
        docprocHandler = new ProcessingHandler<>(
                this,
                BundleInstantiationSpecification.fromSearchAndDocproc("com.yahoo.docproc.jdisc.DocumentProcessingHandler"));
        addComponent(docprocHandler);
        addComponent(new SimpleComponent(
                new ComponentModel(DocprocsStatusExtension.class.getName(), null, PlatformBundles.SEARCH_AND_DOCPROC_BUNDLE)));

        if (! (getParent() instanceof ApplicationContainerCluster)) {
            // All application containers already have a DocumentTypeManager,
            // but this could also belong to e.g. a cluster controller.
            addComponent(new SimpleComponent(DOCUMENT_TYPE_MANAGER_CLASS));
        }
    }

    private void addComponent(Component<?, ?> component) {
        if (!(getParent() instanceof ContainerCluster<?>)) {
            return;
        }
        ((ContainerCluster<?>) getParent()).addComponent(component);
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
