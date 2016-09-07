// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.provider;

import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.hosted.node.admin.maintenance.MaintenanceScheduler;
import com.yahoo.vespa.hosted.node.admin.maintenance.MaintenanceSchedulerImpl;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdmin;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdminImpl;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdminStateUpdater;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgent;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentImpl;
import com.yahoo.vespa.hosted.dockerapi.Docker;
import com.yahoo.vespa.hosted.node.admin.docker.DockerOperationsImpl;
import com.yahoo.vespa.hosted.node.admin.noderepository.NodeRepository;
import com.yahoo.vespa.hosted.node.admin.noderepository.NodeRepositoryImpl;
import com.yahoo.vespa.hosted.node.admin.orchestrator.Orchestrator;
import com.yahoo.vespa.hosted.node.admin.orchestrator.OrchestratorImpl;
import com.yahoo.vespa.hosted.node.admin.util.Environment;

import java.util.Set;
import java.util.function.Function;

/**
 * Set up node admin for production.
 *
 * @author dybis
 */
public class ComponentsProviderImpl implements ComponentsProvider {

    private final Docker docker;
    private final NodeAdminStateUpdater nodeAdminStateUpdater;

    private static final long INITIAL_SCHEDULER_DELAY_MILLIS = 1;
    private static final int NODE_AGENT_SCAN_INTERVAL_MILLIS = 60000;
    private static final int HARDCODED_NODEREPOSITORY_PORT = 19071;
    private static final String ENV_HOSTNAME = "HOSTNAME";
    // We only scan for new nodes within a host every 5 minutes. This is only if new nodes are added or removed
    // which happens rarely. Changes of apps running etc it detected by the NodeAgent.
    private static final int NODE_ADMIN_STATE_INTERVAL_MILLIS = 5 * 60000;
    public ComponentsProviderImpl(final Docker docker) {
        this.docker = docker;
        String baseHostName = java.util.Optional.ofNullable(System.getenv(ENV_HOSTNAME))
                .orElseThrow(() -> new IllegalStateException("Environment variable " + ENV_HOSTNAME + " unset"));

        Environment environment = new Environment();
        Set<HostName> configServerHosts = environment.getConfigServerHosts();

        Orchestrator orchestrator = new OrchestratorImpl(configServerHosts);
        NodeRepository nodeRepository = new NodeRepositoryImpl(configServerHosts, HARDCODED_NODEREPOSITORY_PORT, baseHostName);
        MaintenanceScheduler maintenanceScheduler = new MaintenanceSchedulerImpl();

        final Function<HostName, NodeAgent> nodeAgentFactory = (hostName) -> new NodeAgentImpl(hostName, nodeRepository,
                orchestrator, new DockerOperationsImpl(docker, environment), maintenanceScheduler);
        final NodeAdmin nodeAdmin = new NodeAdminImpl(docker, nodeAgentFactory, maintenanceScheduler, NODE_AGENT_SCAN_INTERVAL_MILLIS);
        nodeAdminStateUpdater = new NodeAdminStateUpdater(
                nodeRepository, nodeAdmin, INITIAL_SCHEDULER_DELAY_MILLIS, NODE_ADMIN_STATE_INTERVAL_MILLIS, orchestrator, baseHostName);
    }

    @Override
    public NodeAdminStateUpdater getNodeAdminStateUpdater() {
        return nodeAdminStateUpdater;
    }
}
