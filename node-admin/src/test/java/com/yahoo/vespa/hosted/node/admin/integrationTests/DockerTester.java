// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.integrationTests;

import com.yahoo.metrics.simple.MetricReceiver;
import com.yahoo.vespa.hosted.dockerapi.Docker;
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
import com.yahoo.vespa.hosted.node.admin.util.PathResolver;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Paths;
import java.time.Clock;
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
    final CallOrderVerifier callOrderVerifier = new CallOrderVerifier();
    final Docker dockerMock = new DockerMock(callOrderVerifier);
    final NodeRepoMock nodeRepositoryMock = new NodeRepoMock(callOrderVerifier);
    final OrchestratorMock orchestratorMock = new OrchestratorMock(callOrderVerifier);
    private final NodeAdminStateUpdater updater;
    private final NodeAdmin nodeAdmin;


    public DockerTester() {
        InetAddressResolver inetAddressResolver = mock(InetAddressResolver.class);
        try {
            when(inetAddressResolver.getInetAddressForHost(any(String.class))).thenReturn(InetAddress.getByName("1.1.1.1"));
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }

        Environment environment = new Environment.Builder()
                .inetAddressResolver(inetAddressResolver)
                .pathResolver(new PathResolver(Paths.get("/tmp"), Paths.get("/tmp"))).build();
        Clock clock = Clock.systemUTC();
        StorageMaintainerMock storageMaintainer = new StorageMaintainerMock(dockerMock, environment, callOrderVerifier, clock);


        MetricReceiverWrapper mr = new MetricReceiverWrapper(MetricReceiver.nullImplementation);
        final DockerOperations dockerOperations = new DockerOperationsImpl(dockerMock, environment);
        Function<String, NodeAgent> nodeAgentFactory = (hostName) -> new NodeAgentImpl(hostName, nodeRepositoryMock,
                orchestratorMock, dockerOperations, Optional.of(storageMaintainer), mr, environment, clock, Optional.empty());
        nodeAdmin = new NodeAdminImpl(dockerOperations, nodeAgentFactory, Optional.of(storageMaintainer), 100, mr, Optional.empty(), Clock.systemUTC());
        updater = new NodeAdminStateUpdater(nodeRepositoryMock, nodeAdmin, clock, orchestratorMock, "basehostname");
        updater.start(5);
    }

    public void addContainerNodeSpec(ContainerNodeSpec containerNodeSpec) {
        nodeRepositoryMock.updateContainerNodeSpec(containerNodeSpec);
    }

    public NodeAdmin getNodeAdmin() {
        return nodeAdmin;
    }

    public NodeAdminStateUpdater getNodeAdminStateUpdater() {
        return updater;
    }

    @Override
    public void close() {
        updater.deconstruct();
    }
}
