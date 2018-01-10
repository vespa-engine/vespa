// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.provider;

import com.google.inject.Inject;
import com.yahoo.concurrent.classlock.ClassLocking;
import com.yahoo.container.di.componentgraph.Provider;
import com.yahoo.vespa.hosted.dockerapi.Docker;
import com.yahoo.vespa.hosted.dockerapi.metrics.MetricReceiverWrapper;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdminMain;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdminStateUpdater;

public class NodeAdminProvider implements Provider<NodeAdminStateUpdater> {
    private final NodeAdminMain nodeAdminMain;

    @Inject
    public NodeAdminProvider(Docker docker,
                             MetricReceiverWrapper metricReceiver,
                             ClassLocking classLocking) {
        nodeAdminMain = new NodeAdminMain(docker, metricReceiver, classLocking);
        nodeAdminMain.start();
    }

    @Override
    public NodeAdminStateUpdater get() {
        return nodeAdminMain.getNodeAdminStateUpdater();
    }

    @Override
    public void deconstruct() {
        nodeAdminMain.close();
    }
}
