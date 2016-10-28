package com.yahoo.vespa.hosted.node.admin.integrationTests;

import com.yahoo.metrics.simple.MetricReceiver;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.dockerapi.Docker;
import com.yahoo.vespa.hosted.dockerapi.DockerImage;
import com.yahoo.vespa.hosted.dockerapi.metrics.MetricReceiverWrapper;
import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;
import com.yahoo.vespa.hosted.node.admin.docker.DockerOperations;
import com.yahoo.vespa.hosted.node.admin.docker.DockerOperationsImpl;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdmin;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdminImpl;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdminStateUpdater;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgent;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentImpl;
import com.yahoo.vespa.hosted.node.admin.util.Environment;
import com.yahoo.vespa.hosted.node.admin.util.InetAddressResolver;
import com.yahoo.vespa.hosted.node.maintenance.Maintainer;
import com.yahoo.vespa.hosted.provision.Node;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Optional;
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
    private final NodeAdminStateUpdater updater;
    private final NodeAdmin nodeAdmin;
    private final OrchestratorMock orchestratorMock;


    public DockerTester() {
        callOrderVerifier = new CallOrderVerifier();
        StorageMaintainerMock maintenanceSchedulerMock = new StorageMaintainerMock(callOrderVerifier);
        orchestratorMock = new OrchestratorMock(callOrderVerifier);
        nodeRepositoryMock = new NodeRepoMock(callOrderVerifier);
        dockerMock = new DockerMock(callOrderVerifier);

        InetAddressResolver inetAddressResolver = mock(InetAddressResolver.class);
        try {
            when(inetAddressResolver.getInetAddressForHost(any(String.class))).thenReturn(InetAddress.getByName("1.1.1.1"));
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }

        Environment environment = new Environment(Collections.emptySet(),
                                                  "dev",
                                                  "us-east-1",
                                                  "parent.host.name.yahoo.com",
                                                  inetAddressResolver);

        MetricReceiverWrapper mr = new MetricReceiverWrapper(MetricReceiver.nullImplementation);
        final Maintainer maintainer = new Maintainer();
        final DockerOperations dockerOperations = new DockerOperationsImpl(dockerMock, environment, maintainer, mr);
        Function<String, NodeAgent> nodeAgentFactory = (hostName) -> new NodeAgentImpl(hostName, nodeRepositoryMock,
                orchestratorMock, dockerOperations, maintenanceSchedulerMock, mr, environment, maintainer);
        nodeAdmin = new NodeAdminImpl(dockerOperations, nodeAgentFactory, maintenanceSchedulerMock, 100, mr);
        updater = new NodeAdminStateUpdater(nodeRepositoryMock, nodeAdmin, 1, 1, orchestratorMock, "basehostname");
    }

    public void addContainerNodeSpec(ContainerNodeSpec containerNodeSpec) {
        nodeRepositoryMock.addContainerNodeSpec(containerNodeSpec);
    }

    public Optional<ContainerNodeSpec> getContainerNodeSpec(String hostname) throws IOException {
        return nodeRepositoryMock.getContainerNodeSpec(hostname);
    }

    public int getNumberOfContainerSpecs() {
        return nodeRepositoryMock.getNumberOfContainerSpecs();
    }

    public void updateContainerNodeSpec(final String hostname,
                            final Optional<DockerImage> wantedDockerImage,
                            final ContainerName containerName,
                            final Node.State nodeState,
                            final Optional<Long> wantedRestartGeneration,
                            final Optional<Long> currentRestartGeneration,
                            final Optional<Double> minCpuCores,
                            final Optional<Double> minMainMemoryAvailableGb,
                            final Optional<Double> minDiskAvailableGb) {

        nodeRepositoryMock.updateContainerNodeSpec(hostname,
                wantedDockerImage,
                containerName,
                nodeState,
                wantedRestartGeneration,
                currentRestartGeneration,
                minCpuCores,
                minMainMemoryAvailableGb,
                minDiskAvailableGb);
    }

    public void updateContainerNodeSpec(ContainerNodeSpec containerNodeSpec) {
        nodeRepositoryMock.updateContainerNodeSpec(containerNodeSpec);
    }

    public void clearContainerNodeSpecs() {
        nodeRepositoryMock.clearContainerNodeSpecs();
    }

    public NodeAdmin getNodeAdmin() {
        return nodeAdmin;
    }

    public OrchestratorMock getOrchestratorMock() {
        return orchestratorMock;
    }

    public NodeAdminStateUpdater getNodeAdminStateUpdater() {
        return updater;
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
