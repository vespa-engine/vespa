package com.yahoo.vespa.model.container;

import com.yahoo.config.FileReference;
import com.yahoo.config.application.api.ComponentInfo;
import com.yahoo.config.model.api.container.ContainerServiceType;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.vespa.model.container.component.Component;
import com.yahoo.vespa.model.container.jersey.RestApi;
import com.yahoo.vespa.model.utils.FileSender;

/**
 * @author gjoranv
 */
public final class ContainerImpl extends Container {

    public ContainerImpl(AbstractConfigProducer parent, String name, int index, boolean isHostedVespa) {
        super(parent, name, index, isHostedVespa);
    }

    public ContainerImpl(AbstractConfigProducer parent, String name, boolean retired, int index, boolean isHostedVespa) {
        super(parent, name, retired, index, isHostedVespa);
    }

    @Override
    protected ContainerServiceType myServiceType() {
        if (parent instanceof ContainerCluster) {
            ContainerCluster cluster = (ContainerCluster)parent;
            // TODO: The 'qrserver' name is retained for legacy reasons (e.g. system tests and log parsing).
            if (cluster.getSearch() != null && cluster.getDocproc() == null && cluster.getDocumentApi() == null) {
                return ContainerServiceType.QRSERVER;
            }
        }
        return ContainerServiceType.CONTAINER;
    }

}
