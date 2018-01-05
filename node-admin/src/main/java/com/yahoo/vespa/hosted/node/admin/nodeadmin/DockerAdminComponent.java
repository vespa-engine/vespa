// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeadmin;

import com.yahoo.concurrent.classlock.ClassLocking;
import com.yahoo.net.HostName;
import com.yahoo.system.ProcessExecuter;
import com.yahoo.vespa.hosted.dockerapi.Docker;
import com.yahoo.vespa.hosted.dockerapi.metrics.MetricReceiverWrapper;
import com.yahoo.vespa.hosted.node.admin.component.AdminComponent;
import com.yahoo.vespa.hosted.node.admin.docker.DockerOperations;
import com.yahoo.vespa.hosted.node.admin.docker.DockerOperationsImpl;
import com.yahoo.vespa.hosted.node.admin.maintenance.StorageMaintainer;
import com.yahoo.vespa.hosted.node.admin.maintenance.acl.AclMaintainer;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgent;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentImpl;
import com.yahoo.vespa.hosted.node.admin.noderepository.NodeRepository;
import com.yahoo.vespa.hosted.node.admin.orchestrator.Orchestrator;
import com.yahoo.vespa.hosted.node.admin.util.Environment;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;

/**
 * Component that manages Docker containers based on some node repository.
 */
public class DockerAdminComponent implements AdminComponent {
    private static final Duration NODE_AGENT_SCAN_INTERVAL = Duration.ofSeconds(30);
    private static final Duration NODE_ADMIN_CONVERGE_STATE_INTERVAL = Duration.ofSeconds(30);

    private final Environment environment;
    private final NodeRepository nodeRepository;
    private final Orchestrator orchestrator;
    private final Docker docker;
    private final MetricReceiverWrapper metricReceiver;
    private final ClassLocking classLocking;
    private Optional<NodeAdminStateUpdater> nodeAdminStateUpdater = Optional.empty();

    public DockerAdminComponent(Environment environment,
                                NodeRepository nodeRepository,
                                Orchestrator orchestrator,
                                Docker docker,
                                MetricReceiverWrapper metricReceiver,
                                ClassLocking classLocking) {
        this.environment = environment;
        this.nodeRepository = nodeRepository;
        this.orchestrator = orchestrator;
        this.docker = docker;
        this.metricReceiver = metricReceiver;
        this.classLocking = classLocking;
    }

    @Override
    public void enable() {
        if (nodeAdminStateUpdater.isPresent()) {
            return;
        }

        Clock clock = Clock.systemUTC();
        String dockerHostHostName = HostName.getLocalhost();
        ProcessExecuter processExecuter = new ProcessExecuter();

        docker.start();
        DockerOperations dockerOperations = new DockerOperationsImpl(
                docker,
                environment,
                processExecuter);

        StorageMaintainer storageMaintainer = new StorageMaintainer(
                dockerOperations,
                processExecuter,
                metricReceiver,
                environment,
                clock);

        AclMaintainer aclMaintainer = new AclMaintainer(
                dockerOperations,
                nodeRepository,
                dockerHostHostName);

        Function<String, NodeAgent> nodeAgentFactory = (hostName) -> new NodeAgentImpl(
                hostName,
                nodeRepository,
                orchestrator,
                dockerOperations,
                storageMaintainer,
                aclMaintainer,
                environment,
                clock,
                NODE_AGENT_SCAN_INTERVAL);

        NodeAdmin nodeAdmin = new NodeAdminImpl(
                dockerOperations,
                nodeAgentFactory,
                storageMaintainer,
                aclMaintainer,
                metricReceiver,
                clock);

        nodeAdminStateUpdater = Optional.of(new NodeAdminStateUpdater(
                nodeRepository,
                orchestrator,
                storageMaintainer,
                nodeAdmin,
                dockerHostHostName,
                clock,
                NODE_ADMIN_CONVERGE_STATE_INTERVAL,
                classLocking));

        nodeAdminStateUpdater.get().start();
    }

    @Override
    public void disable() {
        if (!nodeAdminStateUpdater.isPresent()) {
            return;
        }

        nodeAdminStateUpdater.get().stop();
        nodeAdminStateUpdater = Optional.empty();
        // TODO: Also stop docker
    }

    public NodeAdminStateUpdater getNodeAdminStateUpdater() {
        return nodeAdminStateUpdater.get();
    }
}
