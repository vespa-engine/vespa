// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.integrationTests;

import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdmin;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdminImpl;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdminStateUpdater;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgent;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentImpl;
import com.yahoo.vespa.hosted.node.admin.docker.Docker;
import com.yahoo.vespa.hosted.node.admin.nodeagent.DockerOperations;
import com.yahoo.vespa.hosted.node.admin.provider.ComponentsProvider;

import java.util.function.Function;

/**
 * For setting up test with mocks.
 *
 * @author dybis
 */
public class ComponentsProviderWithMocks implements ComponentsProvider {
    public NodeRepoMock nodeRepositoryMock = new NodeRepoMock();
    private MaintenanceSchedulerMock maintenanceSchedulerMock = new MaintenanceSchedulerMock();
    private OrchestratorMock orchestratorMock = new OrchestratorMock();
    private Docker dockerMock = new DockerMock();

    private final Function<HostName, NodeAgent> nodeAgentFactory = (hostName) ->
            new NodeAgentImpl(hostName, nodeRepositoryMock, orchestratorMock, new DockerOperations(dockerMock), maintenanceSchedulerMock);
    private NodeAdmin nodeAdmin = new NodeAdminImpl(dockerMock, nodeAgentFactory, maintenanceSchedulerMock, 100);


    @Override
    public NodeAdminStateUpdater getNodeAdminStateUpdater() {
        return new NodeAdminStateUpdater(nodeRepositoryMock, nodeAdmin, 1, 5, orchestratorMock, "localhost");
    }
}
