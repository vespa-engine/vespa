// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.searchlib.rankingexpression.ExpressionFunction;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.hosted.provision.Node;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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

    @Before
    public void before() {
        this.curator = new MockCurator();
        this.tester = new ProvisioningTester(Zone.defaultZone(), createConfig(), curator);
    }

    @Test
    public void trusted_nodes_for_allocated_node() {
        String connectionSpec = "cfg1:1234,cfg2:1234,cfg3:1234";
        curator.setConnectionSpec(connectionSpec);
        List<String> configServers = toHostNames(connectionSpec);

        // Populate repo
        tester.makeReadyNodes(10, "default");

        List<Node> proxyNodes = tester.makeReadyNodes(3, "default", NodeType.proxy);
        tester.activateProxies();

        ApplicationId applicationId = tester.makeApplicationId();

        // Allocate 2 nodes
        ClusterSpec cluster = ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("test"), 
                                                  Optional.empty());
        List<HostSpec> prepared = tester.prepare(applicationId, cluster, Capacity.fromNodeCount(2), 1);
        tester.activate(applicationId, new HashSet<>(prepared));
        List<Node> activeNodes = tester.getNodes(applicationId, Node.State.active).asList();
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
        String connectionSpec = "cfg1:1234,cfg2:1234,cfg3:1234";
        curator.setConnectionSpec(connectionSpec);
        List<String> configServers = toHostNames(connectionSpec);

        // Populate repo
        List<Node> readyNodes = tester.makeReadyNodes(10, "default");
        List<Node> proxyNodes = tester.makeReadyNodes(3, "default", NodeType.proxy);
        tester.activateProxies();

        // Get trusted nodes for the first ready node
        Node node = readyNodes.get(0);
        List<Node> trustedNodes = tester.nodeRepository().getTrustedNodes(node);
        assertEquals(proxyNodes.size() + configServers.size(), trustedNodes.size());

        // Trusted nodes contains only proxy nodes and config servers
        assertContainsOnly(toHostNames(trustedNodes), flatten(Arrays.asList(toHostNames(proxyNodes), configServers)));
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
