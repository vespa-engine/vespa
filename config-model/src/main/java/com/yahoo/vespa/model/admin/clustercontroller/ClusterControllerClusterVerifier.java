// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin.clustercontroller;

import com.yahoo.component.ComponentSpecification;
import com.yahoo.container.handler.ThreadpoolConfig;
import com.yahoo.vespa.model.container.Container;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.ContainerClusterVerifier;
import com.yahoo.vespa.model.container.component.Component;
import com.yahoo.vespa.model.container.component.SimpleComponent;

import java.util.Collections;
import java.util.Set;

/**
 * @author baldersheim
 * Verifies that all containers added are ClusterControllerContainers and that filters away Linguistics components.
 */
public class ClusterControllerClusterVerifier implements ContainerClusterVerifier {
    static final Set<ComponentSpecification> unwantedComponents = Collections.singleton(new SimpleComponent(ContainerCluster.DEFAULT_LINGUISTICS_PROVIDER).getClassId());
    @Override
    public boolean acceptComponent(Component component) {
        return ! unwantedComponents.contains(component.getClassId());
    }

    @Override
    public boolean acceptContainer(Container container) {
        return container instanceof ClusterControllerContainer;
    }

    @Override
    public void getConfig(ThreadpoolConfig.Builder builder) {
        builder.maxthreads(10);
    }
}
