// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.integrationTests;

import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.hosted.dockerapi.ContainerEngine;
import com.yahoo.vespa.hosted.dockerapi.RegistryCredentials;
import com.yahoo.vespa.hosted.dockerapi.metrics.Metrics;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeSpec;
import com.yahoo.vespa.hosted.node.admin.configserver.orchestrator.Orchestrator;
import com.yahoo.vespa.hosted.node.admin.docker.ContainerOperations;
import com.yahoo.vespa.hosted.node.admin.docker.ContainerOperationsImpl;
import com.yahoo.vespa.hosted.node.admin.maintenance.StorageMaintainer;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdminImpl;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdminStateUpdater;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContextFactory;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContextImpl;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentFactory;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentImpl;
import com.yahoo.vespa.hosted.node.admin.task.util.network.IPAddressesMock;
import com.yahoo.vespa.hosted.node.admin.task.util.process.TerminalImpl;
import com.yahoo.vespa.hosted.node.admin.task.util.process.TestChildProcess2;
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
import java.util.logging.Logger;

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
    private static final Logger log = Logger.getLogger(DockerTester.class.getName());
    private static final Duration INTERVAL = Duration.ofMillis(10);
    private static final Path PATH_TO_VESPA_HOME = Paths.get("/opt/vespa");
    static final String NODE_PROGRAM = PATH_TO_VESPA_HOME.resolve("bin/vespa-nodectl").toString();
    static final HostName HOST_HOSTNAME = HostName.from("host.test.yahoo.com");

    private final Thread loopThread;

    final ContainerEngine containerEngine = spy(new ContainerEngineMock());
    final NodeRepoMock nodeRepository = spy(new NodeRepoMock());
    final Orchestrator orchestrator = mock(Orchestrator.class);
    final StorageMaintainer storageMaintainer = mock(StorageMaintainer.class);
    final InOrder inOrder = Mockito.inOrder(containerEngine, nodeRepository, orchestrator, storageMaintainer);
    final InMemoryFlagSource flagSource = new InMemoryFlagSource();

    final NodeAdminStateUpdater nodeAdminStateUpdater;
    final NodeAdminImpl nodeAdmin;

    private boolean terminated = false;
    private volatile NodeAdminStateUpdater.State wantedState = NodeAdminStateUpdater.State.RESUMED;


    DockerTester() {
        when(storageMaintainer.diskUsageFor(any())).thenReturn(Optional.empty());

        IPAddressesMock ipAddresses = new IPAddressesMock();
        ipAddresses.addAddress(HOST_HOSTNAME.value(), "1.1.1.1");
        ipAddresses.addAddress(HOST_HOSTNAME.value(), "f000::");
        for (int i = 1; i < 4; i++) ipAddresses.addAddress("host" + i + ".test.yahoo.com", "f000::" + i);

        TerminalImpl terminal = new TerminalImpl(command -> new TestChildProcess2(0, ""));

        NodeSpec hostSpec = NodeSpec.Builder.testSpec(HOST_HOSTNAME.value()).type(NodeType.host).build();
        nodeRepository.updateNodeRepositoryNode(hostSpec);

        Clock clock = Clock.systemUTC();
        Metrics metrics = new Metrics();
        FileSystem fileSystem = TestFileSystem.create();
        ContainerOperations containerOperations = new ContainerOperationsImpl(containerEngine, terminal, ipAddresses, fileSystem);

        NodeAgentFactory nodeAgentFactory = (contextSupplier, nodeContext) -> new NodeAgentImpl(
                contextSupplier, nodeRepository, orchestrator, containerOperations, () -> RegistryCredentials.none,
                storageMaintainer, flagSource,
                Collections.emptyList(), Optional.empty(), Optional.empty(), clock, Duration.ofSeconds(-1));
        nodeAdmin = new NodeAdminImpl(nodeAgentFactory, metrics, clock, Duration.ofMillis(10), Duration.ZERO);
        NodeAgentContextFactory nodeAgentContextFactory = (nodeSpec, acl) ->
                new NodeAgentContextImpl.Builder(nodeSpec).acl(acl).fileSystem(fileSystem).build();
        nodeAdminStateUpdater = new NodeAdminStateUpdater(nodeAgentContextFactory, nodeRepository, orchestrator,
                nodeAdmin, HOST_HOSTNAME, clock);

        loopThread = new Thread(() -> {
            nodeAdminStateUpdater.start();

            while (! terminated) {
                try {
                    nodeAdminStateUpdater.converge(wantedState);
                } catch (RuntimeException e) {
                    log.info(e.getMessage());
                }
                try {
                    Thread.sleep(INTERVAL.toMillis());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        loopThread.start();
    }

    /** Adds a node to node-repository mock that is running on this host */
    void addChildNodeRepositoryNode(NodeSpec nodeSpec) {
        nodeRepository.updateNodeRepositoryNode(new NodeSpec.Builder(nodeSpec)
                .parentHostname(HOST_HOSTNAME.value())
                .build());
    }

    void setWantedState(NodeAdminStateUpdater.State wantedState) {
        this.wantedState = wantedState;
    }

    <T> T inOrder(T t) {
        return inOrder.verify(t, timeout(5000));
    }

    @Override
    public void close() {
        // First, stop NodeAdmin and all the NodeAgents
        nodeAdminStateUpdater.stop();

        terminated = true;
        do {
            try {
                loopThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } while (loopThread.isAlive());
    }
}
