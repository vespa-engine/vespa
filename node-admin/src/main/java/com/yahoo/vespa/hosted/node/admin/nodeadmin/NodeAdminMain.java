// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeadmin;

import com.yahoo.concurrent.classlock.ClassLocking;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.hosted.dockerapi.Docker;
import com.yahoo.vespa.hosted.dockerapi.metrics.MetricReceiverWrapper;

import java.io.File;
import java.util.Optional;

/**
 * NodeAdminMain is the main component of the node admin JDisc application:
 *  - It will read config and check its environment to figure out its responsibilities
 *  - It will "start" (only) the necessary components.
 *  - Other components MUST NOT try to start (typically in constructor) since the features
 *    they provide is NOT WANTED and possibly destructive, and/or the environment may be
 *    incompatible. For instance, trying to contact the Docker daemon too early will
 *    be fatal: the node admin may not have installed and started the docker daemon.
 */
public class NodeAdminMain implements AutoCloseable {
    private final Docker docker;
    private final MetricReceiverWrapper metricReceiver;
    private final ClassLocking classLocking;

    private Optional<DockerAdminComponent> dockerAdmin = Optional.empty();

    public NodeAdminMain(Docker docker,
                         MetricReceiverWrapper metricReceiver,
                         ClassLocking classLocking) {
        this.docker = docker;
        this.metricReceiver = metricReceiver;
        this.classLocking = classLocking;
    }

    @Override
    public void close() {
        dockerAdmin.ifPresent(DockerAdminComponent::disable);
    }

    public NodeAdminStateUpdater getNodeAdminStateUpdater() {
        return dockerAdmin.get().getNodeAdminStateUpdater();
    }

    public void start() {
        String staticConfigPath = Defaults.getDefaults().underVespaHome("conf/node-admin.json");
        NodeAdminConfig config = NodeAdminConfig.fromFile(new File(staticConfigPath));

        switch (config.mode) {
            case aws_tenant:
            case tenant:
                dockerAdmin = Optional.of(new DockerAdminComponent(
                        config,
                        docker,
                        metricReceiver,
                        classLocking));
                dockerAdmin.get().enable();
                return;
            case config_server_host:
                // TODO:
                //  - install and start docker daemon
                //  - Read config that specifies which containers to start how
                //  - use thin static backends for node repo and orchestrator
                //  - Start node admin state updater.
                return;
        }

        throw new IllegalStateException("Unknown bootstrap mode: " + config.mode.name());
    }
}
