// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.provider;

import com.google.inject.Inject;
import com.yahoo.concurrent.classlock.ClassLocking;
import com.yahoo.container.di.componentgraph.Provider;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.hosted.dockerapi.Docker;
import com.yahoo.vespa.hosted.dockerapi.metrics.MetricReceiverWrapper;
import com.yahoo.vespa.hosted.node.admin.component.ConfigServerInfo;
import com.yahoo.vespa.hosted.node.admin.component.DockerAdminComponent;
import com.yahoo.vespa.hosted.node.admin.config.ConfigServerConfig;
import com.yahoo.vespa.hosted.node.admin.configserver.ConfigServerClients;
import com.yahoo.vespa.hosted.node.admin.configserver.RealConfigServerClients;

public class NodeAdminProvider implements Provider<NodeAdminStateUpdater> {
    private final DockerAdminComponent dockerAdmin;

    @Inject
    public NodeAdminProvider(ConfigServerConfig configServerConfig,
                             Docker docker,
                             MetricReceiverWrapper metricReceiver,
                             ClassLocking classLocking) {
        ConfigServerClients clients = new RealConfigServerClients(
                new ConfigServerInfo(configServerConfig),
                Defaults.getDefaults().vespaHostname());

        dockerAdmin = new DockerAdminComponent(configServerConfig,
                docker,
                metricReceiver,
                classLocking,
                clients);
        dockerAdmin.enable();
    }

    @Override
    public NodeAdminStateUpdater get() {
        return dockerAdmin.getNodeAdminStateUpdater();
    }

    @Override
    public void deconstruct() {
        dockerAdmin.disable();
    }
}
