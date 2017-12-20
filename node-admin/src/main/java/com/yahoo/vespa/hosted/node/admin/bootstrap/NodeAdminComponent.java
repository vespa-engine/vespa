// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.bootstrap;

import com.google.inject.Inject;
import com.yahoo.concurrent.classlock.ClassLocking;
import com.yahoo.container.di.componentgraph.Provider;
import com.yahoo.vespa.hosted.dockerapi.Docker;
import com.yahoo.vespa.hosted.dockerapi.metrics.MetricReceiverWrapper;

public class NodeAdminComponent implements Provider<NodeAdminMain> {
    private final NodeAdminMain nodeAdminMain;

    @Inject
    NodeAdminComponent(Docker docker,
                       MetricReceiverWrapper metricReceiver,
                       ClassLocking classLocking) {
        this.nodeAdminMain = new NodeAdminMain(docker, metricReceiver, classLocking);
    }

    @Override
    public NodeAdminMain get() {
        return nodeAdminMain;
    }

    @Override
    public void deconstruct() {
        nodeAdminMain.close();
    }
}
