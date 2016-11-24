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
import com.yahoo.vespa.hosted.provision.testutils.MockNameResolver;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.yahoo.vespa.hosted.provision.provisioning.ProvisioningTester.createConfig;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
        List<String> configServers = setConfigServers("cfg1:1234,cfg2:1234,cfg3:1234");

        // Populate repo
        tester.makeReadyNodes(10, "default");
        List<Node> proxyNodes = tester.makeReadyNodes(3, "default", NodeType.proxy);

        // Allocate 2 nodes
        List<Node> activeNodes = allocateNodes(2);
        assertEquals(2, activeNodes.size());

        // Get trusted nodes for the first active node
        Node node = activeNodes.get(0);
        List<Node> trustedNodes = tester.nodeRepository().getTrustedNodes(node);
        assertEquals(activeNodes.size() + proxyNodes.size() + configServers.size(), trustedNodes.size());

        // Trusted nodes contains active nodes in same application, proxy nodes and config servers
        List<String> expected = flatten(Arrays.asList(toHostNames(activeNodes), toHostNames(proxyNodes), configServers));
        assertContainsOnly(toHostNames(trustedNodes), expected);
    }

    @Test
    public void trusted_nodes_for_unallocated_node() {
        List<String> configServers = setConfigServers("cfg1:1234,cfg2:1234,cfg3:1234");

        // Populate repo
        List<Node> readyNodes = tester.makeReadyNodes(10, "default");
        List<Node> proxyNodes = tester.makeReadyNodes(3, "default", NodeType.proxy);

        // Get trusted nodes for the first ready node
        Node node = readyNodes.get(0);
        List<Node> trustedNodes = tester.nodeRepository().getTrustedNodes(node);
        assertEquals(proxyNodes.size() + configServers.size(), trustedNodes.size());

        // Trusted nodes contains only proxy nodes and config servers
        assertContainsOnly(toHostNames(trustedNodes), flatten(Arrays.asList(toHostNames(proxyNodes), configServers)));
    }

    @Test
    public void trusted_nodes_for_config_server() {
        List<String> configServers = setConfigServers("cfg1:1234,cfg2:1234,cfg3:1234");

        // Populate repo
        tester.makeReadyNodes(10, "default");
        List<Node> proxyNodes = tester.makeReadyNodes(3, "default", NodeType.proxy);

        // Allocate 2 nodes
        allocateNodes(4);
        List<Node> tenantNodes = tester.nodeRepository().getNodes(NodeType.tenant);

        // Get trusted nodes for the first config server
        Node node = tester.nodeRepository().getConfigNode("cfg1")
                .orElseThrow(() -> new RuntimeException("Failed to find cfg1"));
        List<Node> trustedNodes = tester.nodeRepository().getTrustedNodes(node);
        assertEquals(configServers.size() + tenantNodes.size() + proxyNodes.size(), trustedNodes.size());

        // Trusted nodes contains all tenant nodes, all proxy nodes and all config servers
        assertContainsOnly(toHostNames(trustedNodes), flatten(Arrays.asList(toHostNames(proxyNodes),
                toHostNames(tenantNodes), configServers)));
    }

    @Test
    public void trusted_nodes_for_proxy() {
        List<String> configServers = setConfigServers("cfg1:1234,cfg2:1234,cfg3:1234");

        // Populate repo
        tester.makeReadyNodes(10, "default");
        List<Node> proxyNodes = tester.makeReadyNodes(3, "default", NodeType.proxy);

        // Get trusted nodes for first proxy node
        Node node = proxyNodes.get(0);
        List<Node> trustedNodes = tester.nodeRepository().getTrustedNodes(node);
        assertEquals(configServers.size(), trustedNodes.size());

        // Trusted nodes contains all config servers
        assertContainsOnly(toHostNames(trustedNodes), configServers);
    }

    @Test
    public void resolves_hostnames_from_connection_spec() {
        setConfigServers("cfg1:1234,cfg2:1234,cfg3:1234");
        nameResolver.addRecord("cfg1", "127.0.0.1");
        nameResolver.addRecord("cfg2", "127.0.0.2");
        nameResolver.addRecord("cfg3", "127.0.0.3");

        List<Node> readyNodes = tester.makeReadyNodes(1, "default", NodeType.tenant);
        List<Node> trustedNodes = tester.nodeRepository().getTrustedNodes(readyNodes.get(0));

        assertEquals(3, trustedNodes.size());
        assertEquals("127.0.0.1", trustedNodes.get(0).ipAddress());
        assertEquals("127.0.0.2", trustedNodes.get(1).ipAddress());
        assertEquals("127.0.0.3", trustedNodes.get(2).ipAddress());
    }

    private List<Node> allocateNodes(int nodeCount) {
        ApplicationId applicationId = tester.makeApplicationId();
        ClusterSpec cluster = ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("test"),
                Optional.empty());
        List<HostSpec> prepared = tester.prepare(applicationId, cluster, Capacity.fromNodeCount(nodeCount), 1);
        tester.activate(applicationId, new HashSet<>(prepared));
        return tester.getNodes(applicationId, Node.State.active).asList();
    }

    private List<String> setConfigServers(String connectionSpec) {
        curator.setConnectionSpec(connectionSpec);
        return toHostNames(connectionSpec);
    }

    private static <T> void assertContainsOnly(Collection<T> a, Collection<T> b) {
        assertTrue(a.containsAll(b) && b.containsAll(a));
    }

    private static <T> List<T> flatten(List<List<T>> lists) {
        return lists.stream().flatMap(Collection::stream).collect(Collectors.toList());
    }

    private static List<String> toHostNames(String connectionSpec) {
        return Arrays.stream(connectionSpec.split(","))
                .map(hostPort -> hostPort.split(":")[0])
                .collect(Collectors.toList());
    }

    private static List<String> toHostNames(List<Node> node) {
        return node.stream().map(Node::hostname).collect(Collectors.toList());
    }
}
