// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.integrationTests;

import com.yahoo.metrics.simple.MetricReceiver;
import com.yahoo.vespa.hosted.dockerapi.metrics.MetricReceiverWrapper;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdmin;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdminImpl;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdminStateUpdater;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgent;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentImpl;
import com.yahoo.vespa.hosted.dockerapi.Docker;
import com.yahoo.vespa.hosted.node.admin.docker.DockerOperationsImpl;
import com.yahoo.vespa.hosted.node.admin.provider.ComponentsProvider;
import com.yahoo.vespa.hosted.node.admin.util.Environment;

import java.util.function.Function;

/**
 * For setting up test with mocks.
 *
 * @author dybis
 */
public class ComponentsProviderWithMocks implements ComponentsProvider {
    static final CallOrderVerifier callOrder = new CallOrderVerifier();
    static final NodeRepoMock nodeRepositoryMock = new NodeRepoMock(callOrder);
    static final StorageMaintainerMock maintenanceSchedulerMock = new StorageMaintainerMock(callOrder);
    static final OrchestratorMock orchestratorMock = new OrchestratorMock(callOrder);
    static final Docker dockerMock = new DockerMock(callOrder);

    private Environment environment = new Environment();
    private final Function<String, NodeAgent> nodeAgentFactory = (hostName) -> new NodeAgentImpl(hostName,
            nodeRepositoryMock, orchestratorMock, new DockerOperationsImpl(dockerMock, environment), maintenanceSchedulerMock);
    private NodeAdmin nodeAdmin = new NodeAdminImpl(dockerMock, nodeAgentFactory, maintenanceSchedulerMock, 100,
            new MetricReceiverWrapper(MetricReceiver.nullImplementation));


    @Override
    public NodeAdminStateUpdater getNodeAdminStateUpdater() {
        return new NodeAdminStateUpdater(nodeRepositoryMock, nodeAdmin, 1, 5, orchestratorMock, "localhost");
    }

    @Override
    public MetricReceiverWrapper getMetricReceiverWrapper() {
        return null;
    }
}
