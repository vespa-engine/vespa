// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.provider;

import com.google.inject.Inject;
import com.yahoo.net.HostName;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
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
import com.yahoo.vespa.hosted.node.admin.NodeAdminConfig;
import com.yahoo.vespa.hosted.node.admin.noderepository.NodeRepository;
import com.yahoo.vespa.hosted.node.admin.noderepository.NodeRepositoryImpl;
import com.yahoo.vespa.hosted.node.admin.orchestrator.Orchestrator;
import com.yahoo.vespa.hosted.node.admin.orchestrator.OrchestratorImpl;
import com.yahoo.vespa.hosted.node.admin.util.Environment;
import com.yahoo.vespa.hosted.node.admin.util.SecretAgentScheduleMaker;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Set up node admin for production.
 *
 * @author dybis
 */
public class ComponentsProviderImpl implements ComponentsProvider {
    private static final ContainerName NODE_ADMIN_CONTAINER_NAME = new ContainerName("node-admin");

    private final NodeAdminStateUpdater nodeAdminStateUpdater;
    private final MetricReceiverWrapper metricReceiverWrapper;

    private static final long INITIAL_SCHEDULER_DELAY_MILLIS = 1;
    private static final int NODE_AGENT_SCAN_INTERVAL_MILLIS = 30000;
    private static final int WEB_SERVICE_PORT = Defaults.getDefaults().vespaWebServicePort();
    // We only scan for new nodes within a host every 5 minutes. This is only if new nodes are added or removed
    // which happens rarely. Changes of apps running etc it detected by the NodeAgent.
    private static final int NODE_ADMIN_STATE_INTERVAL_MILLIS = 5 * 60000;

    public ComponentsProviderImpl(Docker docker, MetricReceiverWrapper metricReceiver, Environment environment,
                                  Optional<StorageMaintainer> storageMaintainer) {
        String baseHostName = HostName.getLocalhost();
        Set<String> configServerHosts = environment.getConfigServerHosts();

        Orchestrator orchestrator = new OrchestratorImpl(configServerHosts);
        NodeRepository nodeRepository = new NodeRepositoryImpl(configServerHosts, WEB_SERVICE_PORT, baseHostName);

        final DockerOperations dockerOperations = new DockerOperationsImpl(docker, environment, metricReceiver);
        final Function<String, NodeAgent> nodeAgentFactory =
                (hostName) -> new NodeAgentImpl(hostName, nodeRepository, orchestrator, dockerOperations,
                        storageMaintainer, metricReceiver, environment);
        final AclMaintainer aclMaintainer = new AclMaintainer(dockerOperations, nodeRepository);

        final NodeAdmin nodeAdmin = new NodeAdminImpl(dockerOperations, nodeAgentFactory, storageMaintainer,
                NODE_AGENT_SCAN_INTERVAL_MILLIS, metricReceiver, Optional.of(aclMaintainer));
        nodeAdminStateUpdater = new NodeAdminStateUpdater(nodeRepository, nodeAdmin, INITIAL_SCHEDULER_DELAY_MILLIS,
                NODE_ADMIN_STATE_INTERVAL_MILLIS, orchestrator, baseHostName);

        metricReceiverWrapper = metricReceiver;
    }

    @Inject
    public ComponentsProviderImpl(final NodeAdminConfig config, final Docker docker, final MetricReceiverWrapper metricReceiver) {
        this(
                docker,
                metricReceiver,
                new Environment(),
                config.isRunningLocally() ? Optional.empty() : Optional.of(new StorageMaintainer(new Environment())));

        if (! config.isRunningLocally()) {
            setCorePattern(docker);
            initializeNodeAgentSecretAgent(docker);
        }
    }

    @Override
    public NodeAdminStateUpdater getNodeAdminStateUpdater() {
        return nodeAdminStateUpdater;
    }

    @Override
    public MetricReceiverWrapper getMetricReceiverWrapper() {
        return metricReceiverWrapper;
    }


    private void setCorePattern(Docker docker) {
        final String[] sysctlCorePattern = {"sysctl", "-w", "kernel.core_pattern=/home/y/var/crash/%e.core.%p"};
        docker.executeInContainer(NODE_ADMIN_CONTAINER_NAME, sysctlCorePattern);
    }

    private void initializeNodeAgentSecretAgent(Docker docker) {
        final Path yamasAgentFolder = Paths.get("/etc/yamas-agent/");
        docker.executeInContainer(NODE_ADMIN_CONTAINER_NAME, "sudo", "chmod", "a+w", yamasAgentFolder.toString());

        Path nodeAdminCheckPath = Paths.get("/usr/bin/curl");
        SecretAgentScheduleMaker scheduleMaker = new SecretAgentScheduleMaker("node-admin", 60, nodeAdminCheckPath,
                "localhost:4080/rest/metrics");

        try {
            scheduleMaker.writeTo(yamasAgentFolder);
            docker.executeInContainer(NODE_ADMIN_CONTAINER_NAME, "service", "yamas-agent", "restart");
        } catch (IOException e) {
            throw new RuntimeException("Failed to write secret-agent schedules for node-admin", e);
        }
    }
}
