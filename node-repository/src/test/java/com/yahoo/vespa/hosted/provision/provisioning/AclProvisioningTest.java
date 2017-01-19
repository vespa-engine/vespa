// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeAcl;
import com.yahoo.vespa.hosted.provision.testutils.MockNameResolver;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.yahoo.vespa.hosted.provision.provisioning.ProvisioningTester.createConfig;
import static java.util.Collections.singleton;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * @author mpolden
 */
public class AclProvisioningTest {

    private MockCurator curator;
    private ProvisioningTester tester;
    private MockNameResolver nameResolver;

    @Before
    public void before() {
        this.curator = new MockCurator();
        this.nameResolver = new MockNameResolver().mockAnyLookup();
        this.tester = new ProvisioningTester(Zone.defaultZone(), createConfig(), curator, nameResolver);
    }

    @Test
    public void trusted_nodes_for_allocated_node() {
        List<Node> configServers = setConfigServers("cfg1:1234,cfg2:1234,cfg3:1234");

        // Populate repo
        tester.makeReadyNodes(10, "default");
        List<Node> proxyNodes = tester.makeReadyNodes(3, "default", NodeType.proxy);

        // Allocate 2 nodes
        List<Node> activeNodes = allocateNodes(2);
        assertEquals(2, activeNodes.size());

        // Get trusted nodes for the first active node
        Node node = activeNodes.get(0);
        List<NodeAcl> nodeAcls = tester.nodeRepository().getNodeAcls(node, false);

        // Trusted nodes is active nodes in same application, proxy nodes and config servers
        assertAcls(Arrays.asList(activeNodes, proxyNodes, configServers), nodeAcls);
    }

    @Test
    public void trusted_nodes_for_unallocated_node() {
        List<Node> configServers = setConfigServers("cfg1:1234,cfg2:1234,cfg3:1234");

        // Populate repo
        List<Node> readyNodes = tester.makeReadyNodes(10, "default");
        List<Node> proxyNodes = tester.makeReadyNodes(3, "default", NodeType.proxy);

        // Get trusted nodes for the first ready node
        Node node = readyNodes.get(0);
        List<NodeAcl> nodeAcls = tester.nodeRepository().getNodeAcls(node, false);

        // Trusted nodes is proxy nodes and config servers
        assertAcls(Arrays.asList(proxyNodes, configServers), nodeAcls);
    }

    @Test
    public void trusted_nodes_for_config_server() {
        List<Node> configServers = setConfigServers("cfg1:1234,cfg2:1234,cfg3:1234");

        // Populate repo
        tester.makeReadyNodes(10, "default");
        List<Node> proxyNodes = tester.makeReadyNodes(3, "default", NodeType.proxy);

        // Allocate 2 nodes
        allocateNodes(4);
        List<Node> tenantNodes = tester.nodeRepository().getNodes(NodeType.tenant);

        // Get trusted nodes for the first config server
        Node node = tester.nodeRepository().getConfigNode("cfg1")
                .orElseThrow(() -> new RuntimeException("Failed to find cfg1"));
        List<NodeAcl> nodeAcls = tester.nodeRepository().getNodeAcls(node, false);

        // Trusted nodes is all tenant nodes, all proxy nodes and all config servers
        assertAcls(Arrays.asList(tenantNodes, proxyNodes, configServers), nodeAcls);
    }

    @Test
    public void trusted_nodes_for_proxy() {
        List<Node> configServers = setConfigServers("cfg1:1234,cfg2:1234,cfg3:1234");

        // Populate repo
        tester.makeReadyNodes(10, "default");
        List<Node> proxyNodes = tester.makeReadyNodes(3, "default", NodeType.proxy);

        // Get trusted nodes for first proxy node
        Node node = proxyNodes.get(0);
        List<NodeAcl> nodeAcls = tester.nodeRepository().getNodeAcls(node, false);

        // Trusted nodes is all config servers and all proxy nodes
        assertAcls(Arrays.asList(proxyNodes, configServers), nodeAcls);
    }

    @Test
    public void trusted_nodes_for_docker_host() {
        List<Node> configServers = setConfigServers("cfg1:1234,cfg2:1234,cfg3:1234");

        // Populate repo
        List<Node> dockerHostNodes = tester.makeReadyNodes(2, "default", NodeType.host);

        List<NodeAcl> acls = tester.nodeRepository().getNodeAcls(dockerHostNodes.get(0), false);

        // Trusted nodes is all Docker hosts and all config servers
        assertAcls(Arrays.asList(dockerHostNodes, configServers), acls.get(0));
    }

    @Test
    public void trusted_nodes_for_child_nodes_of_docker_host() {
        List<Node> configServers = setConfigServers("cfg1:1234,cfg2:1234,cfg3:1234");

        // Populate repo
        List<Node> dockerHostNodes = tester.makeReadyNodes(2, "default", NodeType.host);
        Node dockerHostNodeUnderTest = dockerHostNodes.get(0);
        List<Node> dockerNodes = tester.makeReadyDockerNodes(5, "docker1",
                dockerHostNodeUnderTest.hostname());

        List<NodeAcl> acls = tester.nodeRepository().getNodeAcls(dockerHostNodeUnderTest, true);

        // ACLs for each container on the Docker host
        assertFalse(dockerNodes.isEmpty());
        assertEquals(dockerNodes.size(), acls.size());
        for (Node dockerNode : dockerNodes) {
            NodeAcl nodeAcl = acls.stream()
                    .filter(acl -> acl.node().equals(dockerNode))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Expected to find ACL for node " + dockerNode.hostname()));
            assertEquals(dockerHostNodeUnderTest.hostname(), dockerNode.parentHostname().get());
            // Since the containers are unallocated, they only trust config servers
            assertAcls(Collections.singletonList(configServers), nodeAcl);
        }
    }

    @Test
    public void resolves_hostnames_from_connection_spec() {
        setConfigServers("cfg1:1234,cfg2:1234,cfg3:1234");
        nameResolver.addRecord("cfg1", "127.0.0.1")
                .addRecord("cfg2", "127.0.0.2")
                .addRecord("cfg3", "127.0.0.3");

        List<Node> readyNodes = tester.makeReadyNodes(1, "default", NodeType.tenant);
        List<NodeAcl> nodeAcls = tester.nodeRepository().getNodeAcls(readyNodes.get(0), false);

        assertEquals(3, nodeAcls.get(0).trustedNodes().size());
        assertEquals(singleton("127.0.0.1"), nodeAcls.get(0).trustedNodes().get(0).ipAddresses());
        assertEquals(singleton("127.0.0.2"), nodeAcls.get(0).trustedNodes().get(1).ipAddresses());
        assertEquals(singleton("127.0.0.3"), nodeAcls.get(0).trustedNodes().get(2).ipAddresses());
    }

    private List<Node> allocateNodes(int nodeCount) {
        ApplicationId applicationId = tester.makeApplicationId();
        ClusterSpec cluster = ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("test"),
                Optional.empty());
        List<HostSpec> prepared = tester.prepare(applicationId, cluster, Capacity.fromNodeCount(nodeCount), 1);
        tester.activate(applicationId, new HashSet<>(prepared));
        return tester.getNodes(applicationId, Node.State.active).asList();
    }

    private List<Node> setConfigServers(String connectionSpec) {
        curator.setConnectionSpec(connectionSpec);
        return tester.nodeRepository().getConfigNodes();
    }

    private static void assertAcls(List<List<Node>> expected, NodeAcl actual) {
        assertAcls(expected, Collections.singletonList(actual));
    }

    private static void assertAcls(List<List<Node>> expected, List<NodeAcl> actual) {
        List<Node> nodes = expected.stream()
                .flatMap(Collection::stream)
                .sorted(Comparator.comparing(Node::hostname))
                .collect(Collectors.toList());
        List<Node> trustedNodes = actual.stream()
                .flatMap(acl -> acl.trustedNodes().stream())
                .collect(Collectors.toList());
        assertEquals(nodes, trustedNodes);
    }
}
