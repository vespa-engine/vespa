// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
import com.yahoo.vespa.hosted.node.admin.noderepository.NodeRepository;
import com.yahoo.vespa.hosted.node.admin.noderepository.NodeRepositoryImpl;
import com.yahoo.vespa.hosted.node.admin.orchestrator.Orchestrator;
import com.yahoo.vespa.hosted.node.admin.orchestrator.OrchestratorImpl;
import com.yahoo.vespa.hosted.node.admin.util.ConfigServerHttpRequestExecutor;
import com.yahoo.vespa.hosted.node.admin.util.Environment;
import com.yahoo.vespa.hosted.node.admin.util.SecretAgentScheduleMaker;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
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

    private static final int NODE_AGENT_SCAN_INTERVAL_MILLIS = 30000;
    private static final int WEB_SERVICE_PORT = Defaults.getDefaults().vespaWebServicePort();

    // Converge towards desired node admin state every 30 seconds
    private static final int NODE_ADMIN_CONVERGE_STATE_INTERVAL_MILLIS = 30000;

    public ComponentsProviderImpl(Docker docker, MetricReceiverWrapper metricReceiver, Environment environment) {
        String baseHostName = HostName.getLocalhost();
        Set<String> configServerHosts = environment.getConfigServerHosts();
        if (configServerHosts.isEmpty()) {
            throw new IllegalStateException("Environment setting for config servers missing or empty.");
        }

        Clock clock = Clock.systemUTC();
        ConfigServerHttpRequestExecutor requestExecutor = ConfigServerHttpRequestExecutor.create(configServerHosts);
        Orchestrator orchestrator = new OrchestratorImpl(requestExecutor);
        NodeRepository nodeRepository = new NodeRepositoryImpl(requestExecutor, WEB_SERVICE_PORT, baseHostName);
        DockerOperations dockerOperations = new DockerOperationsImpl(docker, environment);

        Optional<StorageMaintainer> storageMaintainer = environment.isRunningLocally() ?
                Optional.empty() : Optional.of(new StorageMaintainer(docker, metricReceiver, environment, clock));
        Optional<AclMaintainer> aclMaintainer = environment.isRunningLocally() ?
                Optional.empty() : Optional.of(new AclMaintainer(dockerOperations, nodeRepository, baseHostName));

        Function<String, NodeAgent> nodeAgentFactory =
                (hostName) -> new NodeAgentImpl(hostName, nodeRepository, orchestrator, dockerOperations,
                        storageMaintainer, environment, clock, aclMaintainer);
        NodeAdmin nodeAdmin = new NodeAdminImpl(dockerOperations, nodeAgentFactory, storageMaintainer,
                NODE_AGENT_SCAN_INTERVAL_MILLIS, metricReceiver, aclMaintainer, clock);
        nodeAdminStateUpdater = new NodeAdminStateUpdater(nodeRepository, nodeAdmin, clock, orchestrator, baseHostName);
        nodeAdminStateUpdater.start(NODE_ADMIN_CONVERGE_STATE_INTERVAL_MILLIS);

        metricReceiverWrapper = metricReceiver;

        if (! environment.isRunningLocally()) {
            setCorePattern(docker);
            initializeNodeAgentSecretAgent(docker);
        }
    }

    @Inject
    public ComponentsProviderImpl(final Docker docker, final MetricReceiverWrapper metricReceiver) {
        this(docker, metricReceiver, new Environment());
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
        docker.executeInContainerAsRoot(NODE_ADMIN_CONTAINER_NAME, sysctlCorePattern);
    }

    private void initializeNodeAgentSecretAgent(Docker docker) {
        final Path yamasAgentFolder = Paths.get("/etc/yamas-agent/");
        docker.executeInContainerAsRoot(NODE_ADMIN_CONTAINER_NAME, "chmod", "a+w", yamasAgentFolder.toString());

        Path nodeAdminCheckPath = Paths.get("/usr/bin/curl");
        SecretAgentScheduleMaker scheduleMaker = new SecretAgentScheduleMaker("node-admin", 60, nodeAdminCheckPath,
                "localhost:4080/rest/metrics");

        try {
            scheduleMaker.writeTo(yamasAgentFolder);
            docker.executeInContainerAsRoot(NODE_ADMIN_CONTAINER_NAME, "service", "yamas-agent", "restart");
        } catch (IOException e) {
            throw new RuntimeException("Failed to write secret-agent schedules for node-admin", e);
        }
    }
}
