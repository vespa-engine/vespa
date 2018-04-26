// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.configserver.noderepository;

import com.yahoo.application.Networking;
import com.yahoo.application.container.JDisc;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.dockerapi.DockerImage;
import com.yahoo.vespa.hosted.node.admin.configserver.ConfigServerApi;
import com.yahoo.vespa.hosted.node.admin.configserver.ConfigServerApiImpl;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.testutils.ContainerConfig;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests the NodeRepository class used for talking to the node repository. It uses a mock from the node repository
 * which already contains some data.
 *
 * @author dybdahl
 */
public class RealNodeRepositoryTest {
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
     *   {@link com.yahoo.vespa.hosted.provision.restapi.v2.NodesApiHandler}
     * These classes define some test data that is used in these tests.
     */
    @Before
    public void startContainer() throws Exception {
        Exception lastException = null;

        // This tries to bind a random open port for the node-repo mock, which is a race condition, so try
        // a few times before giving up
        for (int i = 0; i < 3; i++) {
            try {
                final int port = findRandomOpenPort();
                container = JDisc.fromServicesXml(ContainerConfig.servicesXmlV2(port), Networking.enable);
                ConfigServerApi configServerApi = ConfigServerApiImpl.createWithSocketFactory(
                        Collections.singletonList(URI.create("http://127.0.0.1:" + port)),
                        SSLConnectionSocketFactory.getSocketFactory());
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

    @After
    public void stopContainer() {
        if (container != null) {
            container.close();
        }
    }

    @Test
    public void testGetContainersToRunApi() throws InterruptedException {
        String dockerHostHostname = "dockerhost1.yahoo.com";

        final List<NodeSpec> containersToRun = nodeRepositoryApi.getNodes(dockerHostHostname);
        assertThat(containersToRun.size(), is(1));
        final NodeSpec node = containersToRun.get(0);
        assertThat(node.getHostname(), is("host4.yahoo.com"));
        assertThat(node.getWantedDockerImage().get(), is(new DockerImage("docker-registry.domain.tld:8080/dist/vespa:6.42.0")));
        assertThat(node.getState(), is(Node.State.active));
        assertThat(node.getWantedRestartGeneration().get(), is(0L));
        assertThat(node.getCurrentRestartGeneration().get(), is(0L));
        assertThat(node.getMinCpuCores(), is(0.2));
        assertThat(node.getMinMainMemoryAvailableGb(), is(0.5));
        assertThat(node.getMinDiskAvailableGb(), is(100.0));
    }

    @Test
    public void testGetContainer() {
        String hostname = "host4.yahoo.com";
        Optional<NodeSpec> node = nodeRepositoryApi.getOptionalNode(hostname);
        assertThat(node.isPresent(), is(true));
        assertThat(node.get().getHostname(), is(hostname));
    }

    @Test
    public void testGetContainerForNonExistingNode() {
        String hostname = "host-that-does-not-exist";
        Optional<NodeSpec> node = nodeRepositoryApi.getOptionalNode(hostname);
        assertFalse(node.isPresent());
    }

    @Test
    public void testUpdateNodeAttributes() {
        String hostname = "host4.yahoo.com";
        nodeRepositoryApi.updateNodeAttributes(
                hostname,
                new NodeAttributes()
                        .withRestartGeneration(Optional.of(1L))
                        .withDockerImage(new DockerImage("image-1:6.2.3")));
    }

    @Test(expected = RuntimeException.class)
    public void testUpdateNodeAttributesWithBadValue() {
        String hostname = "host4.yahoo.com";
        nodeRepositoryApi.updateNodeAttributes(
                hostname,
                new NodeAttributes()
                        .withRestartGeneration(Optional.of(1L))
                        .withDockerImage(new DockerImage("image-1")));
    }

    @Test
    public void testMarkAsReady() {
        nodeRepositoryApi.setNodeState("host5.yahoo.com", Node.State.dirty);
        nodeRepositoryApi.setNodeState("host5.yahoo.com", Node.State.ready);

        try {
            nodeRepositoryApi.setNodeState("host4.yahoo.com", Node.State.ready);
            fail("Should not be allowed to be marked ready as it is not registered as provisioned, dirty, failed or parked");
        } catch (RuntimeException ignored) {
            // expected
        }

        try {
            nodeRepositoryApi.setNodeState("host101.yahoo.com", Node.State.ready);
            fail("Expected failure because host101 does not exist");
        } catch (RuntimeException ignored) {
            // expected
        }
    }

    @Test
    public void testAddNodes() {
        AddNode host = new AddNode("host123.domain.tld", "default", NodeType.confighost,
                Collections.singleton("::1"), new HashSet<>(Arrays.asList("::2", "::3")));

        AddNode node = new AddNode("host123-1.domain.tld", "host123.domain.tld", "docker", NodeType.config,
                new HashSet<>(Arrays.asList("::2", "::3")));

        List<AddNode> nodesToAdd = Arrays.asList(host, node);

        assertFalse(nodeRepositoryApi.getOptionalNode("host123.domain.tld").isPresent());
        nodeRepositoryApi.addNodes(nodesToAdd);

        NodeSpec hostSpecInNodeRepo = nodeRepositoryApi.getOptionalNode("host123.domain.tld")
                .orElseThrow(RuntimeException::new);

        assertEquals(host.nodeFlavor, hostSpecInNodeRepo.getFlavor());
        assertEquals(host.nodeType, hostSpecInNodeRepo.getNodeType());

        assertTrue(nodeRepositoryApi.getOptionalNode("host123-1.domain.tld").isPresent());
    }
}
