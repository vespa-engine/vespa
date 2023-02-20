// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.noderepository;

import com.yahoo.application.Networking;
import com.yahoo.application.container.JDisc;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.WireguardKey;
import com.yahoo.config.provision.host.FlavorOverrides;
import com.yahoo.vespa.hosted.node.admin.configserver.ConfigServerApi;
import com.yahoo.vespa.hosted.node.admin.configserver.ConfigServerApiImpl;
import com.yahoo.vespa.hosted.node.admin.task.util.network.VersionedIpAddress;
import com.yahoo.vespa.hosted.node.admin.wireguard.WireguardPeer;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

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
                container = JDisc.fromServicesXml(ContainerConfig.servicesXmlV2(port, CloudAccount.empty), Networking.enable);
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
        var hostname = "host4.yahoo.com";
        var dockerImage = "registry.example.com/repo/image-1:6.2.3";
        var wireguardKey = WireguardKey.from("111122223333444455556666777788889999000042c=");

        nodeRepositoryApi.updateNodeAttributes(
                hostname,
                new NodeAttributes()
                        .withRestartGeneration(1)
                        .withDockerImage(DockerImage.fromString(dockerImage))
                        .withWireguardPubkey(wireguardKey));

        NodeSpec hostSpec = nodeRepositoryApi.getOptionalNode(hostname).orElseThrow();
        assertEquals(1, hostSpec.currentRestartGeneration().orElseThrow());
        assertEquals(dockerImage, hostSpec.currentDockerImage().orElseThrow().asString());
        assertEquals(wireguardKey.value(), hostSpec.wireguardPubkey().orElseThrow().value());
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

    @Test
    void wireguard_peer_config_can_be_retrieved_for_configservers_and_exclave_nodes() {

        //// Configservers ////

        List<WireguardPeer> cfgPeers =  nodeRepositoryApi.getConfigserverPeers();

        // cfg2 does not have a wg public key, so should not be included
        assertEquals(1, cfgPeers.size());

        assertWireguardPeer(cfgPeers.get(0), "cfg1.yahoo.com",
                            "::201:1", "127.0.201.1",
                            "lololololololololololololololololololololoo=");

        //// Exclave nodes ////

        List<WireguardPeer> exclavePeers =  nodeRepositoryApi.getExclavePeers();

        // host3 does not have a wg public key, so should not be included
        assertEquals(1, exclavePeers.size());

        assertWireguardPeer(exclavePeers.get(0), "dockerhost2.yahoo.com",
                            "::101:1", "127.0.101.1",
                            "000011112222333344445555666677778888999900c=");
    }

    private void assertWireguardPeer(WireguardPeer peer, String hostname, String ipv6, String ipv4, String publicKey) {
        assertEquals(hostname, peer.hostname().value());
        assertEquals(2, peer.ipAddresses().size());
        assertIp(peer.ipAddresses().get(0), ipv6, 6);
        assertIp(peer.ipAddresses().get(1), ipv4, 4);
        assertEquals(publicKey, peer.publicKey().value());
    }

    private void assertIp(VersionedIpAddress ip, String expectedIp, int expectedVersion) {
        assertEquals(expectedIp, ip.asString());
        assertEquals(expectedVersion, ip.version().version());
    }

}
