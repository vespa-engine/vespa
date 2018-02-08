// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.noderepository;

import com.yahoo.application.Networking;
import com.yahoo.application.container.JDisc;
import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;
import com.yahoo.vespa.hosted.dockerapi.DockerImage;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAttributes;
import com.yahoo.vespa.hosted.node.admin.util.ConfigServerHttpRequestExecutor;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.testutils.ContainerConfig;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

/**
 * Tests the NodeRepository class used for talking to the node repository. It uses a mock from the node repository
 * which already contains some data.
 *
 * @author dybdahl
 */
public class NodeRepositoryImplTest {
    private JDisc container;
    private ConfigServerHttpRequestExecutor requestExecutor;


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
                requestExecutor = ConfigServerHttpRequestExecutor.create(
                        Collections.singleton(URI.create("http://127.0.0.1:" + port)), Optional.empty(), Optional.empty(), Optional.empty());
                return;
            } catch (RuntimeException e) {
                lastException = e;
            }
        }
        throw new RuntimeException("Failed to bind a port in three attempts, giving up", lastException);
    }

    private void waitForJdiscContainerToServe() throws InterruptedException {
        Instant start = Instant.now();
        NodeRepository nodeRepositoryApi = new NodeRepositoryImpl(requestExecutor);
        while (Instant.now().minusSeconds(120).isBefore(start)) {
            try {
                nodeRepositoryApi.getContainersToRun("foobar");
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
    public void testGetContainersToRunApi() throws IOException, InterruptedException {
        waitForJdiscContainerToServe();
        NodeRepository nodeRepositoryApi = new NodeRepositoryImpl(requestExecutor);
        String dockerHostHostname = "dockerhost1.yahoo.com";

        final List<ContainerNodeSpec> containersToRun = nodeRepositoryApi.getContainersToRun(dockerHostHostname);
        assertThat(containersToRun.size(), is(1));
        final ContainerNodeSpec nodeSpec = containersToRun.get(0);
        assertThat(nodeSpec.hostname, is("host4.yahoo.com"));
        assertThat(nodeSpec.wantedDockerImage.get(), is(new DockerImage("docker-registry.domain.tld:8080/dist/vespa:6.42.0")));
        assertThat(nodeSpec.nodeState, is(Node.State.active));
        assertThat(nodeSpec.wantedRestartGeneration.get(), is(0L));
        assertThat(nodeSpec.currentRestartGeneration.get(), is(0L));
        assertThat(nodeSpec.minCpuCores, is(0.2));
        assertThat(nodeSpec.minMainMemoryAvailableGb, is(0.5));
        assertThat(nodeSpec.minDiskAvailableGb, is(100.0));
    }

    @Test
    public void testGetContainer() throws InterruptedException, IOException {
        waitForJdiscContainerToServe();
        NodeRepository nodeRepositoryApi = new NodeRepositoryImpl(requestExecutor);
        String hostname = "host4.yahoo.com";
        Optional<ContainerNodeSpec> nodeSpec = nodeRepositoryApi.getContainerNodeSpec(hostname);
        assertThat(nodeSpec.isPresent(), is(true));
        assertThat(nodeSpec.get().hostname, is(hostname));
    }

    @Test
    public void testGetContainerForNonExistingNode() throws InterruptedException, IOException {
        waitForJdiscContainerToServe();
        NodeRepository nodeRepositoryApi = new NodeRepositoryImpl(requestExecutor);
        String hostname = "host-that-does-not-exist";
        Optional<ContainerNodeSpec> nodeSpec = nodeRepositoryApi.getContainerNodeSpec(hostname);
        assertFalse(nodeSpec.isPresent());
    }

    @Test
    public void testUpdateNodeAttributes() throws InterruptedException, IOException {
        waitForJdiscContainerToServe();
        NodeRepository nodeRepositoryApi = new NodeRepositoryImpl(requestExecutor);
        String hostname = "host4.yahoo.com";
        nodeRepositoryApi.updateNodeAttributes(
                hostname,
                new NodeAttributes()
                        .withRestartGeneration(1L)
                        .withDockerImage(new DockerImage("image-1:6.2.3"))
                        .withVespaVersion("6.2.3"));
    }

    @Test(expected = RuntimeException.class)
    public void testUpdateNodeAttributesWithBadValue() throws InterruptedException, IOException {
        waitForJdiscContainerToServe();
        NodeRepository nodeRepositoryApi = new NodeRepositoryImpl(requestExecutor);
        String hostname = "host4.yahoo.com";
        nodeRepositoryApi.updateNodeAttributes(
                hostname,
                new NodeAttributes()
                        .withRestartGeneration(1L)
                        .withDockerImage(new DockerImage("image-1"))
                        .withVespaVersion("6.2.3\n"));
    }

    @Test
    public void testMarkAsReady() throws InterruptedException, IOException {
        NodeRepository nodeRepositoryApi = new NodeRepositoryImpl(requestExecutor);
        waitForJdiscContainerToServe();

        nodeRepositoryApi.markAsDirty("host5.yahoo.com");
        nodeRepositoryApi.markNodeAvailableForNewAllocation("host5.yahoo.com");

        try {
            nodeRepositoryApi.markNodeAvailableForNewAllocation("host4.yahoo.com");
            fail("Should not be allowed to be marked ready as it is not registered as provisioned, dirty, failed or parked");
        } catch (RuntimeException ignored) {
            // expected
        }

        try {
            nodeRepositoryApi.markNodeAvailableForNewAllocation("host101.yahoo.com");
            fail("Expected failure because host101 does not exist");
        } catch (RuntimeException ignored) {
            // expected
        }
    }
}
