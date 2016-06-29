package com.yahoo.vespa.hosted.node.admin.integrationTests;

import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdmin;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgent;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentImpl;
import com.yahoo.vespa.hosted.node.admin.docker.Docker;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeMechanisms;
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
            new NodeAgentImpl(hostName, nodeRepositoryMock, orchestratorMock, new NodeMechanisms(dockerMock));
    private NodeAdmin nodeAdmin = new NodeAdmin.NodeAdminImpl(dockerMock, nodeAgentFactory);


    @Override
    public NodeAdmin.NodeAdminStateUpdater getNodeAdminStateUpdater() {
        return new NodeAdmin.NodeAdminStateUpdater(nodeRepositoryMock, nodeAdmin, 1, 5, orchestratorMock, "localhost");
    }
}
