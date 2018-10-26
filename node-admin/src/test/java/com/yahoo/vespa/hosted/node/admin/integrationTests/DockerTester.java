// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.integrationTests;

import com.yahoo.collections.Pair;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.NodeType;
import com.yahoo.metrics.simple.MetricReceiver;
import com.yahoo.system.ProcessExecuter;
import com.yahoo.vespa.hosted.dockerapi.Docker;
import com.yahoo.vespa.hosted.dockerapi.metrics.MetricReceiverWrapper;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeSpec;
import com.yahoo.vespa.hosted.node.admin.configserver.orchestrator.Orchestrator;
import com.yahoo.vespa.hosted.node.admin.docker.DockerOperations;
import com.yahoo.vespa.hosted.node.admin.docker.DockerOperationsImpl;
import com.yahoo.vespa.hosted.node.admin.maintenance.StorageMaintainer;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdminImpl;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdminStateUpdaterImpl;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgent;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContextImpl;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentImpl;
import com.yahoo.vespa.hosted.node.admin.task.util.network.IPAddressesMock;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.test.file.TestFileSystem;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.function.Function;

import static com.yahoo.vespa.hosted.node.admin.task.util.file.IOExceptionUtil.uncheck;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.when;

/**
 * @author musum
 */
// Need to deconstruct nodeAdminStateUpdater
public class DockerTester implements AutoCloseable {
    private static final Duration NODE_AGENT_SCAN_INTERVAL = Duration.ofMillis(100);
    private static final Duration NODE_ADMIN_CONVERGE_STATE_INTERVAL = Duration.ofMillis(10);
    private static final Path PATH_TO_VESPA_HOME = Paths.get("/opt/vespa");
    static final String NODE_PROGRAM = PATH_TO_VESPA_HOME.resolve("bin/vespa-nodectl").toString();
    static final HostName HOST_HOSTNAME = HostName.from("host.test.yahoo.com");

    final Docker docker = spy(new DockerMock());
    final NodeRepoMock nodeRepository = spy(new NodeRepoMock());
    final Orchestrator orchestrator = mock(Orchestrator.class);
    final StorageMaintainer storageMaintainer = mock(StorageMaintainer.class);
    final InOrder inOrder = Mockito.inOrder(docker, nodeRepository, orchestrator, storageMaintainer);

    final NodeAdminStateUpdaterImpl nodeAdminStateUpdater;
    final NodeAdminImpl nodeAdmin;


    DockerTester() {
        when(storageMaintainer.getDiskUsageFor(any())).thenReturn(Optional.empty());

        IPAddressesMock ipAddresses = new IPAddressesMock();
        ipAddresses.addAddress(HOST_HOSTNAME.value(), "1.1.1.1");
        ipAddresses.addAddress(HOST_HOSTNAME.value(), "f000::");
        for (int i = 1; i < 4; i++) ipAddresses.addAddress("host" + i + ".test.yahoo.com", "f000::" + i);

        ProcessExecuter processExecuter = mock(ProcessExecuter.class);
        uncheck(() -> when(processExecuter.exec(any(String[].class))).thenReturn(new Pair<>(0, "")));

        NodeSpec hostSpec = new NodeSpec.Builder()
                .hostname(HOST_HOSTNAME.value())
                .state(Node.State.active)
                .nodeType(NodeType.host)
                .flavor("default")
                .wantedRestartGeneration(1L)
                .currentRestartGeneration(1L)
                .build();
        nodeRepository.updateNodeRepositoryNode(hostSpec);

        Clock clock = Clock.systemUTC();
        FileSystem fileSystem = TestFileSystem.create();
        DockerOperations dockerOperations = new DockerOperationsImpl(docker, processExecuter, node -> "", Collections.emptyList(), ipAddresses);

        MetricReceiverWrapper mr = new MetricReceiverWrapper(MetricReceiver.nullImplementation);
        Function<String, NodeAgent> nodeAgentFactory = (hostName) -> new NodeAgentImpl(
                new NodeAgentContextImpl.Builder(hostName).fileSystem(fileSystem).build(), nodeRepository,
                orchestrator, dockerOperations, storageMaintainer, clock, NODE_AGENT_SCAN_INTERVAL, Optional.empty(), Optional.empty());
        nodeAdmin = new NodeAdminImpl(nodeAgentFactory, Optional.empty(), mr, Clock.systemUTC());
        nodeAdminStateUpdater = new NodeAdminStateUpdaterImpl(nodeRepository, orchestrator,
                nodeAdmin, HOST_HOSTNAME, clock, NODE_ADMIN_CONVERGE_STATE_INTERVAL);
        nodeAdminStateUpdater.start();
    }

    /** Adds a node to node-repository mock that is running on this host */
    void addChildNodeRepositoryNode(NodeSpec nodeSpec) {
        nodeRepository.updateNodeRepositoryNode(new NodeSpec.Builder(nodeSpec)
                .parentHostname(HOST_HOSTNAME.value())
                .build());
    }

    @Override
    public void close() {
        nodeAdminStateUpdater.stop();
    }

    public <T> T inOrder(T t) {
        return inOrder.verify(t, timeout(1000));
    }
}
