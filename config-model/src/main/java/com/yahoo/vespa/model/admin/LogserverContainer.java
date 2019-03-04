// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin;

import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.vespa.model.container.Container;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.ContainerServiceType;
import com.yahoo.vespa.model.container.component.Handler;

/**
 * Container that should be running on same host as the logserver. Sets up a handler for getting logs from logserver.
 * Only in use in hosted Vespa.
 */
public class LogserverContainer extends Container {

    public LogserverContainer(AbstractConfigProducer parent) {
        super(parent, "" + 0, 0, true);
        // Add base handlers and the log handler
        ContainerCluster logServerCluster = (ContainerCluster) parent;
        logServerCluster.addMetricStateHandler();
        logServerCluster.addApplicationStatusHandler();
        logServerCluster.addDefaultRootHandler();
        logServerCluster.addVipHandler();
        addLogHandler(logServerCluster);
    }

    // TODO: Change service type to 'logserver-container'
    @Override
    public ContainerServiceType myServiceType() {
        return ContainerServiceType.CONTAINER;
    }

    private void addLogHandler(ContainerCluster cluster) {
        Handler<?> logHandler = Handler.fromClassName("com.yahoo.container.handler.LogHandler");
        logHandler.addServerBindings("http://*/logs", "https://*/logs");
        cluster.addComponent(logHandler);
    }

}
