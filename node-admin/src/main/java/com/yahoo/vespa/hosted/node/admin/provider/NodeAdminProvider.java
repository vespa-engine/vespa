// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.provider;

import com.google.inject.Inject;
import com.yahoo.concurrent.lock.Lock;
import com.yahoo.concurrent.lock.Locking;
import com.yahoo.container.di.componentgraph.Provider;
import com.yahoo.log.LogLevel;
import com.yahoo.net.HostName;

import com.yahoo.system.ProcessExecuter;
import com.yahoo.vespa.hosted.dockerapi.Docker;
import com.yahoo.vespa.hosted.dockerapi.metrics.MetricReceiverWrapper;
import com.yahoo.vespa.hosted.node.admin.docker.DockerOperations;
import com.yahoo.vespa.hosted.node.admin.maintenance.acl.AclMaintainer;
import com.yahoo.vespa.hosted.node.admin.maintenance.StorageMaintainer;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdmin;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdminImpl;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdminStateUpdater;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgent;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentImpl;
import com.yahoo.vespa.hosted.node.admin.docker.DockerOperationsImpl;
import com.yahoo.vespa.hosted.node.admin.noderepository.NodeRepository;
import com.yahoo.vespa.hosted.node.admin.noderepository.NodeRepositoryImpl;
import com.yahoo.vespa.hosted.node.admin.orchestrator.Orchestrator;
import com.yahoo.vespa.hosted.node.admin.orchestrator.OrchestratorImpl;
import com.yahoo.vespa.hosted.node.admin.util.ConfigServerHttpRequestExecutor;
import com.yahoo.vespa.hosted.node.admin.util.Environment;

import java.time.Clock;
import java.time.Duration;
import java.util.function.Function;
import java.util.logging.Logger;

import static com.yahoo.vespa.defaults.Defaults.getDefaults;

/**
 * Set up node admin for production.
 *
 * @author dybis
 */
public class NodeAdminProvider implements Provider<NodeAdminStateUpdater> {
    private static final int WEB_SERVICE_PORT = getDefaults().vespaWebServicePort();
    private static final Duration NODE_AGENT_SCAN_INTERVAL = Duration.ofSeconds(30);
    private static final Duration NODE_ADMIN_CONVERGE_STATE_INTERVAL = Duration.ofSeconds(30);

    private final Logger log = Logger.getLogger(NodeAdminProvider.class.getName());
    private final NodeAdminStateUpdater nodeAdminStateUpdater;
    private final Lock classLock;

    @Inject
    public NodeAdminProvider(Docker docker, MetricReceiverWrapper metricReceiver, Locking locking) {
        log.log(LogLevel.INFO, objectToString() + ": Creating object, acquiring lock...");
        classLock = locking.lock(this.getClass());
        log.log(LogLevel.INFO, objectToString() + ": Lock acquired");

        Clock clock = Clock.systemUTC();
        String dockerHostHostName = HostName.getLocalhost();
        ProcessExecuter processExecuter = new ProcessExecuter();
        Environment environment = new Environment();

        ConfigServerHttpRequestExecutor requestExecutor = ConfigServerHttpRequestExecutor.create(environment.getConfigServerHosts());
        NodeRepository nodeRepository = new NodeRepositoryImpl(requestExecutor, WEB_SERVICE_PORT);
        Orchestrator orchestrator = new OrchestratorImpl(requestExecutor, WEB_SERVICE_PORT);
        DockerOperations dockerOperations = new DockerOperationsImpl(docker, environment, processExecuter);

        StorageMaintainer storageMaintainer = new StorageMaintainer(docker, processExecuter, metricReceiver, environment, clock);
        AclMaintainer aclMaintainer = new AclMaintainer(dockerOperations, nodeRepository, dockerHostHostName);

        Function<String, NodeAgent> nodeAgentFactory =
                (hostName) -> new NodeAgentImpl(hostName, nodeRepository, orchestrator, dockerOperations,
                        storageMaintainer, aclMaintainer, environment, clock, NODE_AGENT_SCAN_INTERVAL);
        NodeAdmin nodeAdmin = new NodeAdminImpl(dockerOperations, nodeAgentFactory, storageMaintainer, aclMaintainer,
                metricReceiver, clock);

        nodeAdminStateUpdater = new NodeAdminStateUpdater(nodeRepository, orchestrator, storageMaintainer, nodeAdmin,
                dockerHostHostName, clock, NODE_ADMIN_CONVERGE_STATE_INTERVAL);
        nodeAdminStateUpdater.start();
    }

    @Override
    public NodeAdminStateUpdater get() {
        return nodeAdminStateUpdater;
    }

    @Override
    public void deconstruct() {
        log.log(LogLevel.INFO, objectToString() + ": Stop called");

        nodeAdminStateUpdater.stop();
        log.log(LogLevel.INFO, objectToString() + ": Stop complete");

        classLock.close();
        log.log(LogLevel.INFO, objectToString() + ": Lock released");
    }

    private String objectToString() {
        return this.getClass().getSimpleName() + "@" + Integer.toString(System.identityHashCode(this));
    }
}
