// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.node.NodeAcl;
import org.junit.Test;

import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * @author mpolden
 */
public class AclProvisioningTest {

    private final NodeResources nodeResources = new NodeResources(2, 8, 50, 1);

    private final ProvisioningTester tester = new ProvisioningTester.Builder().build();

    @Test
    public void trusted_nodes_for_allocated_node() {
        NodeList configServers = tester.makeConfigServers(3, "d-1-4-10", Version.fromString("6.123.456"));

        // Populate repo
        tester.makeReadyNodes(10, new NodeResources(1, 4, 10, 1));
        List<Node> host = tester.makeReadyNodes(1, new NodeResources(1, 4, 10, 1), NodeType.host);
        ApplicationId zoneApplication = ProvisioningTester.applicationId();
        tester.deploy(zoneApplication, Capacity.fromRequiredNodeType(NodeType.host));
        tester.makeReadyChildren(1, new NodeResources(1, 4, 10, 1),
                                 host.get(0).hostname());
        List<Node> proxyNodes = tester.makeReadyNodes(3, new NodeResources(1, 4, 10, 1), NodeType.proxy);

        // Allocate 2 nodes
        ApplicationId application = ProvisioningTester.applicationId();
        List<Node> activeNodes = tester.deploy(application, Capacity.from(new ClusterResources(2, 1, new NodeResources(1, 4, 10, 1)), false, true));
        assertEquals(2, activeNodes.size());

        // Get trusted nodes for the first active node
        Node node = activeNodes.get(0);
        List<Node> hostOfNode = node.parentHostname().flatMap(tester.nodeRepository().nodes()::node).map(List::of).orElseGet(List::of);
        Supplier<NodeAcl> nodeAcls = () -> node.acl(tester.nodeRepository().nodes().list(), tester.nodeRepository().loadBalancers());

        // Trusted nodes are active nodes in same application, proxy nodes and config servers
        assertAcls(List.of(activeNodes, proxyNodes, configServers.asList(), hostOfNode),
                   Set.of("10.2.3.0/24", "10.4.5.0/24"),
                   List.of(nodeAcls.get()));
    }

    @Test
    public void trusted_nodes_for_unallocated_node() {
        NodeList configServers = tester.makeConfigServers(3, "default", Version.fromString("6.123.456"));

        // Populate repo
        tester.makeReadyNodes(10, nodeResources);
        List<Node> proxyNodes = tester.makeReadyNodes(3, "default", NodeType.proxy);

        // Allocate 2 nodes to an application
        deploy(2);

        // Get trusted nodes for a ready tenant node
        Node node = tester.nodeRepository().nodes().list(Node.State.ready).nodeType(NodeType.tenant).first().get();
        NodeAcl nodeAcl = node.acl(tester.nodeRepository().nodes().list(), tester.nodeRepository().loadBalancers());
        NodeList tenantNodes = tester.nodeRepository().nodes().list().nodeType(NodeType.tenant);

        // Trusted nodes are all proxy-, config-, and, tenant-nodes
        assertAcls(List.of(proxyNodes, configServers.asList(), tenantNodes.asList()), List.of(nodeAcl));
    }

    @Test
    public void trusted_nodes_for_config_server() {
        NodeList configServers = tester.makeConfigServers(3, "default", Version.fromString("6.123.456"));

        // Populate repo
        tester.makeReadyNodes(10, nodeResources);
        List<Node> proxyNodes = tester.makeReadyNodes(3, "default", NodeType.proxy);

        // Allocate 2 nodes
        deploy(4);
        NodeList tenantNodes = tester.nodeRepository().nodes().list().nodeType(NodeType.tenant);

        // Get trusted nodes for the first config server
        Node node = tester.nodeRepository().nodes().node("cfg1")
                .orElseThrow(() -> new RuntimeException("Failed to find cfg1"));
        NodeAcl nodeAcl = node.acl(tester.nodeRepository().nodes().list(), tester.nodeRepository().loadBalancers());

        // Trusted nodes is all tenant nodes, all proxy nodes, all config servers and load balancer subnets
        assertAcls(List.of(tenantNodes.asList(), proxyNodes, configServers.asList()), Set.of("10.2.3.0/24", "10.4.5.0/24"), List.of(nodeAcl));
    }

    @Test
    public void trusted_nodes_for_proxy() {
        NodeList configServers = tester.makeConfigServers(3, "default", Version.fromString("6.123.456"));

        // Populate repo
        tester.makeReadyNodes(10, "default");
        tester.makeReadyNodes(3, "default", NodeType.proxy);

        // Deploy zone application
        ApplicationId zoneApplication = ProvisioningTester.applicationId();
        tester.deploy(zoneApplication, Capacity.fromRequiredNodeType(NodeType.proxy));

        // Get trusted nodes for first proxy node
        NodeList proxyNodes = tester.nodeRepository().nodes().list().owner(zoneApplication);
        Node node = proxyNodes.first().get();
        NodeAcl nodeAcl = node.acl(tester.nodeRepository().nodes().list(), tester.nodeRepository().loadBalancers());

        // Trusted nodes is all config servers and all proxy nodes
        assertAcls(List.of(proxyNodes.asList(), configServers.asList()), List.of(nodeAcl));
    }

    @Test
    public void trusted_nodes_for_children() {
        NodeList configServers = tester.makeConfigServers(3, "default", Version.fromString("6.123.456"));

        // Populate repo
        List<Node> hosts = tester.makeReadyNodes(2, "default", NodeType.host);
        Node host = hosts.get(0);
        List<Node> nodes = tester.makeReadyChildren(5, new NodeResources(1, 4, 10, 1),
                                                          host.hostname());

        List<NodeAcl> acls = tester.nodeRepository().getChildAcls(host);

        // ACLs for each container on the host
        assertFalse(nodes.isEmpty());
        assertEquals(nodes.size(), acls.size());
        for (Node node : nodes) {
            NodeAcl nodeAcl = acls.stream()
                    .filter(acl -> acl.node().equals(node))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Expected to find ACL for node " + node.hostname()));
            assertEquals(host.hostname(), node.parentHostname().get());
            assertAcls(List.of(configServers.asList(), nodes, List.of(host)), nodeAcl);
        }
    }

    @Test
    public void trusted_nodes_for_controllers() {
        tester.makeReadyNodes(3, "default", NodeType.controller);

        // Allocate
        ApplicationId controllerApplication = ProvisioningTester.applicationId();
        List<Node> controllers = tester.deploy(controllerApplication, Capacity.fromRequiredNodeType(NodeType.controller));

        // Controllers and hosts all trust each other
        NodeAcl controllerAcl = controllers.get(0).acl(tester.nodeRepository().nodes().list(), tester.nodeRepository().loadBalancers());
        assertAcls(List.of(controllers), Set.of("10.2.3.0/24", "10.4.5.0/24"), List.of(controllerAcl));
        assertEquals(Set.of(22, 4443, 443), controllerAcl.trustedPorts());
    }

    @Test
    public void trusted_nodes_for_application_with_load_balancer() {
        // Provision hosts and containers
        var hosts = tester.makeReadyNodes(2, "default", NodeType.host);
        tester.activateTenantHosts();
        for (var host : hosts) {
            tester.makeReadyChildren(2, new NodeResources(2, 8, 50, 1),
                                     host.hostname());
        }

        // Deploy application
        var application = ProvisioningTester.applicationId();
        List<Node> activeNodes = deploy(application, 2);
        assertEquals(2, activeNodes.size());

        // Load balancer is allocated to application
        var loadBalancers = tester.nodeRepository().loadBalancers().list(application);
        assertEquals(1, loadBalancers.asList().size());
        var lbNetworks = loadBalancers.asList().get(0).instance().networks();
        assertEquals(2, lbNetworks.size());

        // ACL for nodes with allocation trust their respective load balancer networks, if any
        for (var host : hosts) {
            var acls = tester.nodeRepository().getChildAcls(host);
            assertEquals(2, acls.size());
            assertEquals(Set.of(), acls.get(0).trustedNetworks());
            assertEquals(application, acls.get(1).node().allocation().get().owner());
            assertEquals(lbNetworks, acls.get(1).trustedNetworks());
        }
    }

    @Test
    public void resolves_hostnames_from_connection_spec() {
        tester.makeConfigServers(3, "default", Version.fromString("6.123.456"));

        List<Node> readyNodes = tester.makeReadyNodes(1, "default", NodeType.proxy);
        NodeAcl nodeAcl = readyNodes.get(0).acl(tester.nodeRepository().nodes().list(), tester.nodeRepository().loadBalancers());

        assertEquals(3, nodeAcl.trustedNodes().size());
        Iterator<Node> trustedNodes = nodeAcl.trustedNodes().iterator();
        assertEquals(Set.of("127.0.1.1"), trustedNodes.next().ipConfig().primary());
        assertEquals(Set.of("127.0.1.2"), trustedNodes.next().ipConfig().primary());
        assertEquals(Set.of("127.0.1.3"), trustedNodes.next().ipConfig().primary());
    }

    private List<Node> deploy(int nodeCount) {
        return deploy(ProvisioningTester.applicationId(), nodeCount);
    }

    private List<Node> deploy(ApplicationId application, int nodeCount) {
        return tester.deploy(application, Capacity.from(new ClusterResources(nodeCount, 1, nodeResources)));
    }

    private static void assertAcls(List<List<Node>> expected, NodeAcl actual) {
        assertAcls(expected, Collections.singletonList(actual));
    }

    private static void assertAcls(List<List<Node>> expectedNodes, List<NodeAcl> actual) {
        assertAcls(expectedNodes, Set.of(), actual);
    }

    private static void assertAcls(List<List<Node>> expectedNodes, Set<String> expectedNetworks, List<NodeAcl> actual) {
        List<Node> expectedTrustedNodes = expectedNodes.stream()
                .flatMap(List::stream)
                .distinct()
                .sorted(Comparator.comparing(Node::hostname))
                .collect(Collectors.toList());
        List<Node> actualTrustedNodes = actual.stream()
                .flatMap(acl -> acl.trustedNodes().stream())
                .distinct()
                .sorted(Comparator.comparing(Node::hostname))
                .collect(Collectors.toList());
        assertEquals(expectedTrustedNodes, actualTrustedNodes);

        Set<String> actualTrustedNetworks = actual.stream()
                .flatMap(acl -> acl.trustedNetworks().stream())
                .collect(Collectors.toSet());
        assertEquals(expectedNetworks, actualTrustedNetworks);
    }
}
