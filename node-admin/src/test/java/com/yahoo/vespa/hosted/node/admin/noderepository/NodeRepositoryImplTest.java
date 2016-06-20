// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.noderepository;

import com.google.common.collect.Sets;
import com.yahoo.application.Networking;
import com.yahoo.application.container.JDisc;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;
import com.yahoo.vespa.hosted.node.admin.docker.ContainerName;
import com.yahoo.vespa.hosted.node.admin.docker.DockerImage;
import com.yahoo.vespa.hosted.provision.testutils.ContainerConfig;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

/**
 * Tests the NodeRepository class used for talking to the node repository. It uses a mock from the node repository
 * which already contains some data.
 *
 * @author dybdahl
 */
public class NodeRepositoryImplTest {
    private JDisc container;
    private int port;
    private final Set<HostName> configServerHosts = Sets.newHashSet(new HostName("127.0.0.1"));


    private int findRandomOpenPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    /**
     * Starts NodeRepository with
     *   com.yahoo.vespa.hosted.provision.testutils.MockNodeFlavor
     *   com.yahoo.vespa.hosted.provision.testutils.MockNodeRepository
     *   com.yahoo.vespa.hosted.provision.restapi.v2.NodesApiHandler
     * These classes define some test data that is used in these tests.
     */
    @Before
    public void startContainer() throws Exception {
        port = findRandomOpenPort();
        System.err.println("PORT IS " + port);
        container = JDisc.fromServicesXml(ContainerConfig.servicesXmlV2(port), Networking.enable);
    }

    private void waitForJdiscContainerToServe() throws InterruptedException {
        Instant start = Instant.now();
        NodeRepository nodeRepositoryApi = new NodeRepositoryImpl(Sets.newHashSet(new HostName("127.0.0.1")), port, "foobar");
        while (Instant.now().minusSeconds(120).isBefore(start)) {
            try {
                nodeRepositoryApi.getContainersToRun();
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
    public void testGetContainersToRunAPi() throws IOException, InterruptedException {
        waitForJdiscContainerToServe();
        NodeRepository nodeRepositoryApi = new NodeRepositoryImpl(configServerHosts, port, "dockerhost4");
        final List<ContainerNodeSpec> containersToRun = nodeRepositoryApi.getContainersToRun();
        assertThat(containersToRun.size(), is(1));
        final ContainerNodeSpec nodeSpec = containersToRun.get(0);
        assertThat(nodeSpec.hostname, is(new HostName("host4.yahoo.com")));
        assertThat(nodeSpec.wantedDockerImage.get(), is(new DockerImage("image-123")));
        assertThat(nodeSpec.containerName, is(new ContainerName("host4")));
        assertThat(nodeSpec.nodeState, is(NodeState.RESERVED));
        assertThat(nodeSpec.wantedRestartGeneration.get(), is(0L));
        assertThat(nodeSpec.currentRestartGeneration.get(), is(0L));
        assertThat(nodeSpec.minCpuCores.get(), is(2.0));
        assertThat(nodeSpec.minMainMemoryAvailableGb.get(), is(16.0));
        assertThat(nodeSpec.minDiskAvailableGb.get(), is(400.0));
    }

    @Test
    public void testGetContainers() throws InterruptedException, IOException {
        waitForJdiscContainerToServe();
        NodeRepository nodeRepositoryApi = new NodeRepositoryImpl(configServerHosts, port, "dockerhost4");
        HostName hostname = new HostName("host4.yahoo.com");
        Optional<ContainerNodeSpec> nodeSpec = nodeRepositoryApi.getContainerNodeSpec(hostname);
        assertThat(nodeSpec.isPresent(), is(true));
        assertThat(nodeSpec.get().hostname, is(hostname));
        assertThat(nodeSpec.get().containerName, is(new ContainerName("host4")));
    }
}
