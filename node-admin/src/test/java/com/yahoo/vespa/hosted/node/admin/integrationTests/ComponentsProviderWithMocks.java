// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.integrationTests;

import com.yahoo.metrics.simple.MetricReceiver;
import com.yahoo.vespa.hosted.dockerapi.metrics.MetricReceiverWrapper;
import com.yahoo.vespa.hosted.node.admin.docker.DockerOperations;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdmin;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdminImpl;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdminStateUpdater;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgent;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentImpl;
import com.yahoo.vespa.hosted.node.admin.noderepository.NodeRepository;
import com.yahoo.vespa.hosted.node.admin.orchestrator.Orchestrator;
import com.yahoo.vespa.hosted.node.admin.provider.ComponentsProvider;
import com.yahoo.vespa.hosted.node.admin.util.Environment;

import java.time.Clock;
import java.util.Optional;
import java.util.function.Function;

import static org.mockito.Mockito.mock;

/**
 * For setting up test with mocks.
 *
 * @author dybis
 */
public class ComponentsProviderWithMocks implements ComponentsProvider {
    static final NodeRepository nodeRepositoryMock = mock(NodeRepository.class);
    static final Orchestrator orchestratorMock = mock(Orchestrator.class);
    static final DockerOperations dockerOperationsMock = mock(DockerOperations.class);

    private final Environment environment = new Environment.Builder().build();
    private final MetricReceiverWrapper mr = new MetricReceiverWrapper(MetricReceiver.nullImplementation);
    private final Function<String, NodeAgent> nodeAgentFactory =
            (hostName) -> new NodeAgentImpl(hostName, nodeRepositoryMock, orchestratorMock,
                    dockerOperationsMock, Optional.empty(), mr, environment, Clock.systemUTC(), Optional.empty());
    private final NodeAdmin nodeAdmin = new NodeAdminImpl(dockerOperationsMock, nodeAgentFactory, Optional.empty(), 100, mr, Optional.empty(), Clock.systemUTC());
    private final NodeAdminStateUpdater nodeAdminStateUpdater = new NodeAdminStateUpdater(nodeRepositoryMock, nodeAdmin, Clock.systemUTC(), orchestratorMock, "localhost.test.yahoo.com");

    public ComponentsProviderWithMocks() {
        nodeAdminStateUpdater.start(10);
    }

    @Override
    public NodeAdminStateUpdater getNodeAdminStateUpdater() {
        return nodeAdminStateUpdater;
    }

    @Override
    public MetricReceiverWrapper getMetricReceiverWrapper() {
        return null;
    }
}
