// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.integrationTests;

import com.yahoo.concurrent.classlock.ClassLocking;
import com.yahoo.metrics.simple.MetricReceiver;
import com.yahoo.vespa.hosted.dockerapi.Docker;
import com.yahoo.vespa.hosted.dockerapi.metrics.MetricReceiverWrapper;
import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;
import com.yahoo.vespa.hosted.node.admin.configserver.ConfigServerClients;
import com.yahoo.vespa.hosted.node.admin.configserver.ConfigServerClientsImpl;
import com.yahoo.vespa.hosted.node.admin.docker.DockerOperations;
import com.yahoo.vespa.hosted.node.admin.docker.DockerOperationsImpl;
import com.yahoo.vespa.hosted.node.admin.maintenance.acl.AclMaintainer;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdmin;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdminImpl;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdminStateUpdaterImpl;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgent;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentImpl;
import com.yahoo.vespa.hosted.node.admin.component.Environment;
import com.yahoo.vespa.hosted.node.admin.util.InetAddressResolver;
import com.yahoo.vespa.hosted.node.admin.component.PathResolver;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author musum
 */
// Need to deconstruct nodeAdminStateUpdater
public class DockerTester implements AutoCloseable {
    private static final Duration NODE_AGENT_SCAN_INTERVAL = Duration.ofMillis(100);
    private static final Duration NODE_ADMIN_CONVERGE_STATE_INTERVAL = Duration.ofMillis(10);

    final CallOrderVerifier callOrderVerifier = new CallOrderVerifier();
    final Docker dockerMock = new DockerMock(callOrderVerifier);
    final NodeRepoMock nodeRepositoryMock = new NodeRepoMock(callOrderVerifier);
    final NodeAdminStateUpdaterImpl nodeAdminStateUpdater;
    final NodeAdmin nodeAdmin;
    private final OrchestratorMock orchestratorMock = new OrchestratorMock(callOrderVerifier);
    private final ConfigServerClients configServerClients = new ConfigServerClientsImpl(nodeRepositoryMock, orchestratorMock);


    DockerTester() {
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
        DockerOperations dockerOperations = new DockerOperationsImpl(dockerMock, environment, null);
        StorageMaintainerMock storageMaintainer = new StorageMaintainerMock(dockerOperations, null, environment, callOrderVerifier, clock);
        AclMaintainer aclMaintainer = mock(AclMaintainer.class);


        MetricReceiverWrapper mr = new MetricReceiverWrapper(MetricReceiver.nullImplementation);
        Function<String, NodeAgent> nodeAgentFactory = (hostName) -> new NodeAgentImpl(hostName, configServerClients,
                dockerOperations, storageMaintainer, aclMaintainer, environment, clock, NODE_AGENT_SCAN_INTERVAL);
        nodeAdmin = new NodeAdminImpl(dockerOperations, nodeAgentFactory, storageMaintainer, aclMaintainer, mr, Clock.systemUTC());
        nodeAdminStateUpdater = new NodeAdminStateUpdaterImpl(configServerClients, storageMaintainer,
                nodeAdmin, "basehostname", clock, NODE_ADMIN_CONVERGE_STATE_INTERVAL,
                Optional.of(new ClassLocking()));
        nodeAdminStateUpdater.start();
    }

    void addContainerNodeSpec(ContainerNodeSpec containerNodeSpec) {
        nodeRepositoryMock.updateContainerNodeSpec(containerNodeSpec);
    }

    @Override
    public void close() {
        nodeAdminStateUpdater.stop();
    }
}
