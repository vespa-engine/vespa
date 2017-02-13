// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.integrationTests;

import com.yahoo.metrics.simple.MetricReceiver;
import com.yahoo.vespa.hosted.dockerapi.Container;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.dockerapi.DockerImage;
import com.yahoo.vespa.hosted.dockerapi.metrics.MetricReceiverWrapper;
import com.yahoo.vespa.hosted.node.admin.docker.DockerOperations;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdmin;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdminImpl;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdminStateUpdater;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgent;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentImpl;
import com.yahoo.vespa.hosted.dockerapi.Docker;
import com.yahoo.vespa.hosted.node.admin.docker.DockerOperationsImpl;
import com.yahoo.vespa.hosted.node.admin.provider.ComponentsProvider;
import com.yahoo.vespa.hosted.node.admin.util.Environment;

import java.util.Optional;
import java.util.function.Function;

/**
 * For setting up test with mocks.
 *
 * @author dybis
 */
public class ComponentsProviderWithMocks implements ComponentsProvider {
    static final CallOrderVerifier callOrderVerifier = new CallOrderVerifier();
    static final NodeRepoMock nodeRepositoryMock = new NodeRepoMock(callOrderVerifier);
    static final OrchestratorMock orchestratorMock = new OrchestratorMock(callOrderVerifier);
    static final Docker dockerMock = new DockerMock(callOrderVerifier);

    private final Environment environment = new Environment.Builder().build();
    private final MetricReceiverWrapper mr = new MetricReceiverWrapper(MetricReceiver.nullImplementation);
    private final DockerOperations dockerOperations = new DockerOperationsImpl(dockerMock, environment, mr);
    private final Container container = new Container("host123.name.yahoo.com", new DockerImage("image-123"),
            new ContainerName("host123"), Container.State.RUNNING, 1);
    private final Function<String, NodeAgent> nodeAgentFactory =
            (hostName) -> new NodeAgentImpl(hostName, nodeRepositoryMock, orchestratorMock,
                    dockerOperations, Optional.empty(), mr, environment, Optional.of(container));
    private NodeAdmin nodeAdmin = new NodeAdminImpl(dockerOperations, nodeAgentFactory, Optional.empty(), 100, mr, Optional.empty());


    @Override
    public NodeAdminStateUpdater getNodeAdminStateUpdater() {
        return new NodeAdminStateUpdater(nodeRepositoryMock, nodeAdmin, 1, 5, orchestratorMock, "localhost");
    }

    @Override
    public MetricReceiverWrapper getMetricReceiverWrapper() {
        return null;
    }
}
