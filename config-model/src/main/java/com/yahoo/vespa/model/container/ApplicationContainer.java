// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container;

import com.yahoo.config.model.api.container.ContainerServiceType;
import com.yahoo.config.model.producer.AbstractConfigProducer;

/**
 * A container that is typically used by container clusters set up from the user application.
 *
 * @author gjoranv
 */
public final class ApplicationContainer extends Container {

    public ApplicationContainer(AbstractConfigProducer parent, String name, int index, boolean isHostedVespa) {
        super(parent, name, index, isHostedVespa);
    }

    public ApplicationContainer(AbstractConfigProducer parent, String name, boolean retired, int index, boolean isHostedVespa) {
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
