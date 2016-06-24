package com.yahoo.vespa.hosted.node.admin.integrationTests;

import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.hosted.node.admin.*;
import com.yahoo.vespa.hosted.node.admin.docker.Docker;
import com.yahoo.vespa.hosted.node.admin.provider.ComponentsProvider;

import java.util.function.Function;

/**
 * For setting up test with mocks.
 *
 * @author dybis
 */
public class ComponentsProviderWithMocks implements ComponentsProvider {
    public NodeRepoMock nodeRepositoryMock = new NodeRepoMock();
    private OrchestratorMock orchestratorMock = new OrchestratorMock();
    private Docker dockerMock = new DockerMock();

    private final Function<HostName, NodeAgent> nodeAgentFactory = (hostName) ->
            new NodeAgentImpl(hostName, dockerMock, nodeRepositoryMock, orchestratorMock);
    private NodeAdmin nodeAdmin = new NodeAdminImpl(dockerMock, nodeAgentFactory);


    @Override
    public NodeAdminStateUpdater getNodeAdminStateUpdater() {
        return new NodeAdminStateUpdater(nodeRepositoryMock, nodeAdmin, 1, 5, orchestratorMock, "localhost");
    }
}
