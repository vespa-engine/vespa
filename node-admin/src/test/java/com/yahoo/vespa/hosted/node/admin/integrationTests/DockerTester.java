package com.yahoo.vespa.hosted.node.admin.integrationTests;

import com.yahoo.metrics.simple.MetricReceiver;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.dockerapi.Docker;
import com.yahoo.vespa.hosted.dockerapi.metrics.MetricReceiverWrapper;
import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;
import com.yahoo.vespa.hosted.node.admin.docker.DockerOperationsImpl;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdmin;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdminImpl;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdminStateUpdater;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgent;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentImpl;
import com.yahoo.vespa.hosted.node.admin.util.Environment;
import com.yahoo.vespa.hosted.node.maintenance.Maintainer;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.function.Function;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author musum
 */
// Need to deconstruct updater
public class DockerTester implements AutoCloseable {

    private final NodeRepoMock nodeRepositoryMock;
    private CallOrderVerifier callOrderVerifier;
    private Docker dockerMock;
    private NodeAdminStateUpdater updater;
    private final NodeAdmin nodeAdmin;


    public DockerTester() {
        callOrderVerifier = new CallOrderVerifier();
        StorageMaintainerMock maintenanceSchedulerMock = new StorageMaintainerMock(callOrderVerifier);
        OrchestratorMock orchestratorMock = new OrchestratorMock(callOrderVerifier);
        nodeRepositoryMock = new NodeRepoMock(callOrderVerifier);
        dockerMock = new DockerMock(callOrderVerifier);

        Environment environment = mock(Environment.class);
        when(environment.getConfigServerHosts()).thenReturn(Collections.emptySet());
        try {
            when(environment.getInetAddressForHost(any(String.class))).thenReturn(InetAddress.getByName("1.1.1.1"));
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }

        MetricReceiverWrapper mr = new MetricReceiverWrapper(MetricReceiver.nullImplementation);
        Function<String, NodeAgent> nodeAgentFactory = (hostName) -> {
            final Maintainer maintainer = new Maintainer();
            return new NodeAgentImpl(hostName, nodeRepositoryMock, orchestratorMock,
                                     new DockerOperationsImpl(dockerMock, environment, maintainer),
                                     maintenanceSchedulerMock, mr, environment, maintainer);
        };
        nodeAdmin = new NodeAdminImpl(dockerMock, nodeAgentFactory, maintenanceSchedulerMock, 100, mr);
        updater = new NodeAdminStateUpdater(nodeRepositoryMock, nodeAdmin, 1, 1, orchestratorMock, "basehostname");
    }

    public void addContainerNodeSpec(ContainerNodeSpec containerNodeSpec) {
        nodeRepositoryMock.addContainerNodeSpec(containerNodeSpec);
    }

    public NodeAdmin getNodeAdmin() {
        return nodeAdmin;
    }

    public CallOrderVerifier getCallOrderVerifier() {
        return callOrderVerifier;
    }

    public void deleteContainer(ContainerName containerName) {
        dockerMock.deleteContainer(containerName);
    }

    @Override
    public void close() {
        updater.deconstruct();
    }
}
