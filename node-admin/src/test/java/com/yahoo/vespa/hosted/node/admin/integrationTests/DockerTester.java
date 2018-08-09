// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.integrationTests;

import com.yahoo.concurrent.classlock.ClassLocking;
import com.yahoo.config.provision.NodeType;
import com.yahoo.metrics.simple.MetricReceiver;
import com.yahoo.vespa.hosted.dockerapi.Docker;
import com.yahoo.vespa.hosted.dockerapi.metrics.MetricReceiverWrapper;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeSpec;
import com.yahoo.vespa.hosted.node.admin.config.ConfigServerConfig;
import com.yahoo.vespa.hosted.node.admin.docker.DockerOperations;
import com.yahoo.vespa.hosted.node.admin.docker.DockerOperationsImpl;
import com.yahoo.vespa.hosted.node.admin.maintenance.acl.AclMaintainer;
import com.yahoo.vespa.hosted.node.admin.maintenance.identity.AthenzCredentialsMaintainer;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdmin;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdminImpl;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdminStateUpdaterImpl;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgent;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentImpl;
import com.yahoo.vespa.hosted.node.admin.component.Environment;
import com.yahoo.vespa.hosted.node.admin.task.util.network.IPAddressesImpl;
import com.yahoo.vespa.hosted.node.admin.util.InetAddressResolver;
import com.yahoo.vespa.hosted.node.admin.component.PathResolver;
import com.yahoo.vespa.hosted.provision.Node;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
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
    private static final Path PATH_TO_VESPA_HOME = Paths.get("/opt/vespa");
    static final String NODE_PROGRAM = PATH_TO_VESPA_HOME.resolve("bin/vespa-nodectl").toString();
    static final String DOCKER_HOST_HOSTNAME = "host.domain.tld";

    final CallOrderVerifier callOrderVerifier = new CallOrderVerifier();
    final Docker dockerMock = new DockerMock(callOrderVerifier);
    final NodeRepoMock nodeRepositoryMock = new NodeRepoMock(callOrderVerifier);
    final NodeAdminStateUpdaterImpl nodeAdminStateUpdater;
    final NodeAdmin nodeAdmin;
    private final OrchestratorMock orchestratorMock = new OrchestratorMock(callOrderVerifier);


    DockerTester() {
        InetAddressResolver inetAddressResolver = mock(InetAddressResolver.class);
        try {
            when(inetAddressResolver.getInetAddressForHost(any(String.class))).thenReturn(InetAddress.getByName("1.1.1.1"));
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }

        Environment environment = new Environment.Builder()
                .configServerConfig(new ConfigServerConfig(new ConfigServerConfig.Builder()))
                .inetAddressResolver(inetAddressResolver)
                .region("us-east-1")
                .environment("prod")
                .system("main")
                .pathResolver(new PathResolver(PATH_TO_VESPA_HOME, Paths.get("/tmp"), Paths.get("/tmp")))
                .cloud("mycloud")
                .build();

        NodeSpec hostSpec = new NodeSpec.Builder()
                .hostname(DOCKER_HOST_HOSTNAME)
                .state(Node.State.active)
                .nodeType(NodeType.host)
                .flavor("default")
                .wantedRestartGeneration(1L)
                .currentRestartGeneration(1L)
                .build();
        nodeRepositoryMock.updateNodeRepositoryNode(hostSpec);

        Clock clock = Clock.systemUTC();
        DockerOperations dockerOperations = new DockerOperationsImpl(dockerMock, environment, null, new IPAddressesImpl());
        StorageMaintainerMock storageMaintainer = new StorageMaintainerMock(dockerOperations, null, environment, callOrderVerifier, clock);
        AclMaintainer aclMaintainer = mock(AclMaintainer.class);
        AthenzCredentialsMaintainer athenzCredentialsMaintainer = mock(AthenzCredentialsMaintainer.class);


        MetricReceiverWrapper mr = new MetricReceiverWrapper(MetricReceiver.nullImplementation);
        Function<String, NodeAgent> nodeAgentFactory = (hostName) -> new NodeAgentImpl(hostName, nodeRepositoryMock,
                orchestratorMock, dockerOperations, storageMaintainer, aclMaintainer, environment, clock, NODE_AGENT_SCAN_INTERVAL, athenzCredentialsMaintainer);
        nodeAdmin = new NodeAdminImpl(dockerOperations, nodeAgentFactory, storageMaintainer, aclMaintainer, mr, Clock.systemUTC());
        nodeAdminStateUpdater = new NodeAdminStateUpdaterImpl(nodeRepositoryMock, orchestratorMock, storageMaintainer,
                nodeAdmin, DOCKER_HOST_HOSTNAME, clock, NODE_ADMIN_CONVERGE_STATE_INTERVAL,
                Optional.of(new ClassLocking()));
        nodeAdminStateUpdater.start();
    }

    /** Adds a node to node-repository mock that is running on this host */
    void addChildNodeRepositoryNode(NodeSpec nodeSpec) {
        nodeRepositoryMock.updateNodeRepositoryNode(new NodeSpec.Builder(nodeSpec)
                .parentHostname(DOCKER_HOST_HOSTNAME)
                .build());
    }

    @Override
    public void close() {
        nodeAdminStateUpdater.stop();
    }
}
