// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.noderepository;

import com.yahoo.application.Networking;
import com.yahoo.application.container.JDisc;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.host.FlavorOverrides;
import com.yahoo.vespa.hosted.node.admin.configserver.ConfigServerApi;
import com.yahoo.vespa.hosted.node.admin.configserver.ConfigServerApiImpl;
import com.yahoo.vespa.hosted.provision.restapi.NodesV2ApiHandler;
import com.yahoo.vespa.hosted.provision.testutils.ContainerConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the NodeRepository class used for talking to the node repository. It uses a mock from the node repository
 * which already contains some data.
 *
 * @author dybdahl
 */
public class RealNodeRepositoryTest {

    private static final double delta = 0.00000001;
    private JDisc container;
    private NodeRepository nodeRepositoryApi;

    private int findRandomOpenPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        }
    }

    /**
     * Starts NodeRepository with
     *   {@link com.yahoo.vespa.hosted.provision.testutils.MockNodeFlavors}
     *   {@link com.yahoo.vespa.hosted.provision.testutils.MockNodeRepository}
     *   {@link NodesV2ApiHandler}
     * These classes define some test data that is used in these tests.
     */
    @BeforeEach
    public void startContainer() throws Exception {
        Exception lastException = null;

        // This tries to bind a random open port for the node-repo mock, which is a race condition, so try
        // a few times before giving up
        for (int i = 0; i < 3; i++) {
            try {
                int port = findRandomOpenPort();
                container = JDisc.fromServicesXml(ContainerConfig.servicesXmlV2(port), Networking.enable);
                ConfigServerApi configServerApi = ConfigServerApiImpl.createForTesting(
                        List.of(URI.create("http://127.0.0.1:" + port)));
                waitForJdiscContainerToServe(configServerApi);
                return;
            } catch (RuntimeException e) {
                lastException = e;
            }
        }
        throw new RuntimeException("Failed to bind a port in three attempts, giving up", lastException);
    }

    private void waitForJdiscContainerToServe(ConfigServerApi configServerApi) throws InterruptedException {
        Instant start = Instant.now();
        nodeRepositoryApi = new RealNodeRepository(configServerApi);
        while (Instant.now().minusSeconds(120).isBefore(start)) {
            try {
                nodeRepositoryApi.getNodes("foobar");
                return;
            } catch (Exception e) {
                Thread.sleep(100);
            }
        }
        throw new RuntimeException("Could not get answer from container.");
    }

    @AfterEach
    public void stopContainer() {
        if (container != null) {
            container.close();
        }
    }

    @Test
    void testGetContainersToRunApi() {
        String dockerHostHostname = "dockerhost1.yahoo.com";

        List<NodeSpec> containersToRun = nodeRepositoryApi.getNodes(dockerHostHostname);
        assertEquals(1, containersToRun.size());
        NodeSpec node = containersToRun.get(0);
        assertEquals("host4.yahoo.com", node.hostname());
        assertEquals(DockerImage.fromString("docker-registry.domain.tld:8080/dist/vespa:6.42.0"), node.wantedDockerImage().get());
        assertEquals(NodeState.active, node.state());
        assertEquals(Long.valueOf(0), node.wantedRestartGeneration().get());
        assertEquals(Long.valueOf(0), node.currentRestartGeneration().get());
        assertEquals(1, node.vcpu(), delta);
        assertEquals(4, node.memoryGb(), delta);
        assertEquals(100, node.diskGb(), delta);
    }

    @Test
    void testGetContainer() {
        String hostname = "host4.yahoo.com";
        Optional<NodeSpec> node = nodeRepositoryApi.getOptionalNode(hostname);
        assertTrue(node.isPresent());
        assertEquals(hostname, node.get().hostname());
    }

    @Test
    void testGetContainerForNonExistingNode() {
        String hostname = "host-that-does-not-exist";
        Optional<NodeSpec> node = nodeRepositoryApi.getOptionalNode(hostname);
        assertFalse(node.isPresent());
    }

    @Test
    void testUpdateNodeAttributes() {
        String hostname = "host4.yahoo.com";
        nodeRepositoryApi.updateNodeAttributes(
                hostname,
                new NodeAttributes()
                        .withRestartGeneration(1)
                        .withDockerImage(DockerImage.fromString("registry.example.com/repo/image-1:6.2.3")));
    }

    @Test
    void testMarkAsReady() {
        nodeRepositoryApi.setNodeState("host5.yahoo.com", NodeState.dirty);
        nodeRepositoryApi.setNodeState("host5.yahoo.com", NodeState.ready);

        try {
            nodeRepositoryApi.setNodeState("host4.yahoo.com", NodeState.ready);
            fail("Should not be allowed to be marked ready as it is not registered as provisioned, dirty, failed or parked");
        } catch (RuntimeException ignored) {
            // expected
        }

        try {
            nodeRepositoryApi.setNodeState("host101.yahoo.com", NodeState.ready);
            fail("Expected failure because host101 does not exist");
        } catch (RuntimeException ignored) {
            // expected
        }
    }

    @Test
    void testAddNodes() {
        AddNode host = AddNode.forHost("host123.domain.tld",
                "id1",
                "default",
                Optional.of(FlavorOverrides.ofDisk(123)),
                NodeType.confighost,
                Set.of("::1"), Set.of("::2", "::3"));

        NodeResources nodeResources = new NodeResources(1, 2, 3, 4, NodeResources.DiskSpeed.slow, NodeResources.StorageType.local);
        AddNode node = AddNode.forNode("host123-1.domain.tld", "id1", "host123.domain.tld", nodeResources, NodeType.config, Set.of("::2", "::3"));

        assertFalse(nodeRepositoryApi.getOptionalNode("host123.domain.tld").isPresent());
        nodeRepositoryApi.addNodes(List.of(host, node));

        NodeSpec hostSpec = nodeRepositoryApi.getOptionalNode("host123.domain.tld").orElseThrow();
        assertEquals("id1", hostSpec.id());
        assertEquals("default", hostSpec.flavor());
        assertEquals(123, hostSpec.diskGb(), 0);
        assertEquals(NodeType.confighost, hostSpec.type());
        assertEquals(NodeResources.Architecture.x86_64, hostSpec.resources().architecture());

        NodeSpec nodeSpec = nodeRepositoryApi.getOptionalNode("host123-1.domain.tld").orElseThrow();
        assertEquals(nodeResources, nodeSpec.resources());
        assertEquals(NodeType.config, nodeSpec.type());
    }

}
