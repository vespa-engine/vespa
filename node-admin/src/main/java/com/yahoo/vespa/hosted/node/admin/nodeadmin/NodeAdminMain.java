// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeadmin;

import com.yahoo.concurrent.classlock.ClassLocking;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.hosted.dockerapi.Docker;
import com.yahoo.vespa.hosted.dockerapi.metrics.MetricReceiverWrapper;
import com.yahoo.vespa.hosted.node.admin.noderepository.NodeRepository;
import com.yahoo.vespa.hosted.node.admin.noderepository.NodeRepositoryImpl;
import com.yahoo.vespa.hosted.node.admin.orchestrator.Orchestrator;
import com.yahoo.vespa.hosted.node.admin.orchestrator.OrchestratorImpl;
import com.yahoo.vespa.hosted.node.admin.util.ConfigServerHttpRequestExecutor;
import com.yahoo.vespa.hosted.node.admin.util.Environment;

import java.io.File;

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
    private final DockerAdminComponent dockerAdmin;

    public NodeAdminMain(Docker docker,
                         MetricReceiverWrapper metricReceiver,
                         ClassLocking classLocking) {
        Environment environment = new Environment();
        ConfigServerHttpRequestExecutor requestExecutor =
                ConfigServerHttpRequestExecutor.create(environment.getConfigServerUris());

        NodeRepository nodeRepository = new NodeRepositoryImpl(requestExecutor);
        Orchestrator orchestrator = new OrchestratorImpl(requestExecutor);

        dockerAdmin = new DockerAdminComponent(
                environment,
                nodeRepository,
                orchestrator,
                docker,
                metricReceiver,
                classLocking);
    }

    @Override
    public void close() {
        dockerAdmin.disable();
    }

    public NodeAdminStateUpdater getNodeAdminStateUpdater() {
        return dockerAdmin.getNodeAdminStateUpdater();
    }

    public void start() {
        String staticConfigPath = Defaults.getDefaults().underVespaHome("conf/node-admin.json");
        NodeAdminConfig config = NodeAdminConfig.fromFile(new File(staticConfigPath));

        switch (config.mode) {
            case tenant:
                dockerAdmin.enable();
                break;
            case config_server_host:
                // TODO:
                //  - install and start docker daemon
                //  - Read config that specifies which containers to start how
                //  - use thin static backends for node repo and orchestrator
                //  - Start node admin state updater.
                break;
            default:
                throw new IllegalStateException(
                        "Unknown bootstrap mode: " + config.mode.name());
        }
    }
}
