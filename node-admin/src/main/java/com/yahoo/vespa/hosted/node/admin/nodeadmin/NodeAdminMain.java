// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeadmin;

import com.yahoo.concurrent.classlock.ClassLocking;
import com.yahoo.net.HostName;
import com.yahoo.system.ProcessExecuter;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.hosted.dockerapi.Docker;
import com.yahoo.vespa.hosted.dockerapi.metrics.MetricReceiverWrapper;
import com.yahoo.vespa.hosted.node.admin.docker.DockerOperations;
import com.yahoo.vespa.hosted.node.admin.docker.DockerOperationsImpl;
import com.yahoo.vespa.hosted.node.admin.maintenance.StorageMaintainer;
import com.yahoo.vespa.hosted.node.admin.maintenance.acl.AclMaintainer;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgent;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentImpl;
import com.yahoo.vespa.hosted.node.admin.noderepository.NodeRepository;
import com.yahoo.vespa.hosted.node.admin.noderepository.NodeRepositoryImpl;
import com.yahoo.vespa.hosted.node.admin.orchestrator.Orchestrator;
import com.yahoo.vespa.hosted.node.admin.orchestrator.OrchestratorImpl;
import com.yahoo.vespa.hosted.node.admin.util.ConfigServerHttpRequestExecutor;
import com.yahoo.vespa.hosted.node.admin.util.Environment;

import java.io.File;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;

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
    private static final Duration NODE_AGENT_SCAN_INTERVAL = Duration.ofSeconds(30);
    private static final Duration NODE_ADMIN_CONVERGE_STATE_INTERVAL = Duration.ofSeconds(30);

    private final Docker docker;
    private final MetricReceiverWrapper metricReceiver;
    private final ClassLocking classLocking;

    private Optional<NodeAdminStateUpdater> nodeAdminStateUpdater = Optional.empty();

    public NodeAdminMain(Docker docker, MetricReceiverWrapper metricReceiver, ClassLocking classLocking) {
        this.docker = docker;
        this.metricReceiver = metricReceiver;
        this.classLocking = classLocking;
    }

    @Override
    public void close() {
        nodeAdminStateUpdater.ifPresent(NodeAdminStateUpdater::stop);
    }

    public NodeAdminStateUpdater getNodeAdminStateUpdater() {
        return nodeAdminStateUpdater.get();
    }

    public void start() {
        String staticConfigPath = Defaults.getDefaults().underVespaHome("conf/node-admin.json");
        NodeAdminConfig config = NodeAdminConfig.fromFile(new File(staticConfigPath));

        switch (config.mode) {
            case tenant:
                setupTenantHostNodeAdmin();
                break;
            case config_server_host:
                setupConfigServerHostNodeAdmin();
                break;
            default:
                throw new IllegalStateException(
                        "Unknown bootstrap mode: " + config.mode.name());
        }
    }

    private void setupTenantHostNodeAdmin() {
        nodeAdminStateUpdater = Optional.of(createNodeAdminStateUpdater());
        nodeAdminStateUpdater.get().start();
    }

    private NodeAdminStateUpdater createNodeAdminStateUpdater() {
        Clock clock = Clock.systemUTC();
        String dockerHostHostName = HostName.getLocalhost();
        ProcessExecuter processExecuter = new ProcessExecuter();
        Environment environment = new Environment();

        ConfigServerHttpRequestExecutor requestExecutor = ConfigServerHttpRequestExecutor.create(environment.getConfigServerUris());
        NodeRepository nodeRepository = new NodeRepositoryImpl(requestExecutor);
        Orchestrator orchestrator = new OrchestratorImpl(requestExecutor);

        docker.start();
        DockerOperations dockerOperations = new DockerOperationsImpl(docker, environment, processExecuter);

        StorageMaintainer storageMaintainer = new StorageMaintainer(dockerOperations, processExecuter, metricReceiver, environment, clock);
        AclMaintainer aclMaintainer = new AclMaintainer(dockerOperations, nodeRepository, dockerHostHostName);

        Function<String, NodeAgent> nodeAgentFactory =
                (hostName) -> new NodeAgentImpl(hostName, nodeRepository, orchestrator, dockerOperations,
                        storageMaintainer, aclMaintainer, environment, clock, NODE_AGENT_SCAN_INTERVAL);
        NodeAdmin nodeAdmin = new NodeAdminImpl(dockerOperations, nodeAgentFactory, storageMaintainer, aclMaintainer,
                metricReceiver, clock);

        return new NodeAdminStateUpdater(nodeRepository, orchestrator, storageMaintainer, nodeAdmin,
                dockerHostHostName, clock, NODE_ADMIN_CONVERGE_STATE_INTERVAL, classLocking);
    }

    private void setupConfigServerHostNodeAdmin() {
        // TODO:
        //  - install and start docker daemon
        //  - Read config that specifies which containers to start how
        //  - use thin static backends for node repo, orchestrator, and others
        //  - Start core node admin.
    }
}
