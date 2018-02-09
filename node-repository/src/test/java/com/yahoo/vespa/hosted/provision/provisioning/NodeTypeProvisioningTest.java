// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.maintenance.JobControl;
import com.yahoo.vespa.hosted.provision.maintenance.RetiredExpirer;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.testutils.MockDeployer;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests provisioning by node type instead of by count and flavor
 * 
 * @author bratseth
 */
public class NodeTypeProvisioningTest {

    private final ProvisioningTester tester = new ProvisioningTester(new Zone(Environment.prod, RegionName.from("us-east")));

    private final ApplicationId application = tester.makeApplicationId(); // application using proxy nodes
    private final Capacity capacity = Capacity.fromRequiredNodeType(NodeType.proxy);
    private final ClusterSpec clusterSpec = ClusterSpec.request(
            ClusterSpec.Type.container, ClusterSpec.Id.from("test"), Version.fromString("6.42"));

    @Before
    public void setup() {
        tester.makeReadyNodes( 1, "small", NodeType.proxy);
        tester.makeReadyNodes( 3, "small", NodeType.host);
        tester.makeReadyNodes( 5, "small", NodeType.tenant);
        tester.makeReadyNodes(10, "large", NodeType.proxy);
        tester.makeReadyNodes(20, "large", NodeType.host);
        tester.makeReadyNodes(40, "large", NodeType.tenant);
    }

    @Test
    public void proxy_deployment() {
        { // Deploy
            List<HostSpec> hosts = deployProxies(application, tester);
            assertEquals("Reserved all proxies", 11, hosts.size());
            tester.activate(application, new HashSet<>(hosts));
            List<Node> nodes = tester.nodeRepository().getNodes(NodeType.proxy, Node.State.active);
            assertEquals("Activated all proxies", 11, nodes.size());
        }

        { // Redeploy with no changes
            List<HostSpec> hosts = deployProxies(application, tester);
            assertEquals(11, hosts.size());
            tester.activate(application, new HashSet<>(hosts));
            List<Node> nodes = tester.nodeRepository().getNodes(NodeType.proxy, Node.State.active);
            assertEquals(11, nodes.size());
        }

        { // Add 2 ready proxies then redeploy
            tester.makeReadyNodes(2, "small", NodeType.proxy);
            List<HostSpec> hosts = deployProxies(application, tester);
            assertEquals(13, hosts.size());
            tester.activate(application, new HashSet<>(hosts));
            List<Node> nodes = tester.nodeRepository().getNodes(NodeType.proxy, Node.State.active);
            assertEquals(13, nodes.size());
        }

        { // Remove 3 proxies then redeploy
            List<Node> nodes = tester.nodeRepository().getNodes(NodeType.proxy, Node.State.active);
            tester.nodeRepository().fail(nodes.get(0).hostname(), Agent.system, "Failing to unit test");
            tester.nodeRepository().fail(nodes.get(1).hostname(), Agent.system, "Failing to unit test");
            tester.nodeRepository().fail(nodes.get(5).hostname(), Agent.system, "Failing to unit test");
            
            List<HostSpec> hosts = deployProxies(application, tester);
            assertEquals(10, hosts.size());
            tester.activate(application, new HashSet<>(hosts));
            nodes = tester.nodeRepository().getNodes(NodeType.proxy, Node.State.active);
            assertEquals(10, nodes.size());
        }
    }

    @Test
    public void retire_proxy() {
        MockDeployer deployer = new MockDeployer(
                tester.provisioner(),
                Collections.singletonMap(
                        application, new MockDeployer.ApplicationContext(application, clusterSpec, capacity, 1)));
        RetiredExpirer retiredExpirer =  new RetiredExpirer(tester.nodeRepository(), tester.orchestrator(), deployer,
                tester.clock(), Duration.ofDays(30), Duration.ofMinutes(10), new JobControl(tester.nodeRepository().database()));

        { // Deploy
            List<HostSpec> hosts = deployProxies(application, tester);
            assertEquals("Reserved all proxies", 11, hosts.size());
            tester.activate(application, new HashSet<>(hosts));
            List<Node> nodes = tester.nodeRepository().getNodes(NodeType.proxy, Node.State.active);
            assertEquals("Activated all proxies", 11, nodes.size());
        }

        Node nodeToRetire = tester.nodeRepository().getNodes(NodeType.proxy, Node.State.active).get(5);
        { // Pick out a node and retire it
            tester.nodeRepository().write(nodeToRetire.with(nodeToRetire.status().withWantToRetire(true)));

            List<HostSpec> hosts = deployProxies(application, tester);
            assertEquals(11, hosts.size());
            tester.activate(application, new HashSet<>(hosts));
            List<Node> nodes = tester.nodeRepository().getNodes(NodeType.proxy, Node.State.active);
            assertEquals(11, nodes.size());

            // Verify that wantToRetire has been propagated
            assertTrue(tester.nodeRepository().getNode(nodeToRetire.hostname())
                    .flatMap(Node::allocation)
                    .map(allocation -> allocation.membership().retired())
                    .orElseThrow(RuntimeException::new));
        }

        { // Redeploying while the node is still retiring has no effect
            List<HostSpec> hosts = deployProxies(application, tester);
            assertEquals(11, hosts.size());
            tester.activate(application, new HashSet<>(hosts));
            List<Node> nodes = tester.nodeRepository().getNodes(NodeType.proxy, Node.State.active);
            assertEquals(11, nodes.size());

            // Verify that the node is still marked as retired
            assertTrue(tester.nodeRepository().getNode(nodeToRetire.hostname())
                    .flatMap(Node::allocation)
                    .map(allocation -> allocation.membership().retired())
                    .orElseThrow(RuntimeException::new));
        }

        {
            tester.advanceTime(Duration.ofMinutes(11));
            retiredExpirer.run();

            List<HostSpec> hosts = deployProxies(application, tester);
            assertEquals(10, hosts.size());
            tester.activate(application, new HashSet<>(hosts));
            List<Node> nodes = tester.nodeRepository().getNodes(NodeType.proxy, Node.State.active);
            assertEquals(10, nodes.size());

            // Verify that the node is now inactive
            assertEquals(Node.State.inactive, tester.nodeRepository().getNode(nodeToRetire.hostname())
                    .orElseThrow(RuntimeException::new).state());
        }
    }

    @Test
    public void retire_multiple_proxy_simultaneously() {
        MockDeployer deployer = new MockDeployer(
                tester.provisioner(),
                Collections.singletonMap(
                        application, new MockDeployer.ApplicationContext(application, clusterSpec, capacity, 1)));
        RetiredExpirer retiredExpirer =  new RetiredExpirer(tester.nodeRepository(), tester.orchestrator(), deployer,
                tester.clock(), Duration.ofDays(30), Duration.ofMinutes(10), new JobControl(tester.nodeRepository().database()));
        final int numNodesToRetire = 5;

        { // Deploy
            List<HostSpec> hosts = deployProxies(application, tester);
            assertEquals("Reserved all proxies", 11, hosts.size());
            tester.activate(application, new HashSet<>(hosts));
            List<Node> nodes = tester.nodeRepository().getNodes(NodeType.proxy, Node.State.active);
            assertEquals("Activated all proxies", 11, nodes.size());
        }

        List<Node> nodesToRetire = tester.nodeRepository().getNodes(NodeType.proxy, Node.State.active)
                .subList(3, 3 + numNodesToRetire);
        String currentyRetiringHostname;
        {
            nodesToRetire.forEach(nodeToRetire ->
                    tester.nodeRepository().write(nodeToRetire.with(nodeToRetire.status().withWantToRetire(true))));

            List<HostSpec> hosts = deployProxies(application, tester);
            assertEquals(11, hosts.size());
            tester.activate(application, new HashSet<>(hosts));
            List<Node> nodes = tester.nodeRepository().getNodes(NodeType.proxy, Node.State.active);
            assertEquals(11, nodes.size());

            // Verify that wantToRetire has been propagated
            List<Node> nodesCurrentlyRetiring = nodes.stream()
                    .filter(node -> node.allocation().get().membership().retired())
                    .collect(Collectors.toList());
            assertEquals(1, nodesCurrentlyRetiring.size());

            // The retiring node should be one of the nodes we marked for retirement
            currentyRetiringHostname = nodesCurrentlyRetiring.get(0).hostname();
            assertTrue(nodesToRetire.stream().map(Node::hostname).filter(hostname -> hostname.equals(currentyRetiringHostname)).count() == 1);
        }

        { // Redeploying while the node is still retiring has no effect
            List<HostSpec> hosts = deployProxies(application, tester);
            assertEquals(11, hosts.size());
            tester.activate(application, new HashSet<>(hosts));
            List<Node> nodes = tester.nodeRepository().getNodes(NodeType.proxy, Node.State.active);
            assertEquals(11, nodes.size());

            // Verify that wantToRetire has been propagated
            List<Node> nodesCurrentlyRetiring = nodes.stream()
                    .filter(node -> node.allocation().get().membership().retired())
                    .collect(Collectors.toList());
            assertEquals(1, nodesCurrentlyRetiring.size());

            // The node that started retiring is still the only one retiring
            assertEquals(currentyRetiringHostname, nodesCurrentlyRetiring.get(0).hostname());
        }

        {
            tester.advanceTime(Duration.ofMinutes(11));
            retiredExpirer.run();

            List<HostSpec> hosts = deployProxies(application, tester);
            assertEquals(10, hosts.size());
            tester.activate(application, new HashSet<>(hosts));
            List<Node> nodes = tester.nodeRepository().getNodes(NodeType.proxy, Node.State.active);
            assertEquals(10, nodes.size());

            // Verify the node we previously set to retire has finished retiring
            assertEquals(Node.State.inactive, tester.nodeRepository().getNode(currentyRetiringHostname)
                    .orElseThrow(RuntimeException::new).state());

            // Verify that a node is currently retiring
            List<Node> nodesCurrentlyRetiring = nodes.stream()
                    .filter(node -> node.allocation().get().membership().retired())
                    .collect(Collectors.toList());
            assertEquals(1, nodesCurrentlyRetiring.size());

            // This node is different from the one that was retiring previously
            String newRetiringHostname = nodesCurrentlyRetiring.get(0).hostname();
            assertNotEquals(currentyRetiringHostname, newRetiringHostname);
            // ... but is one of the nodes that were put to wantToRetire earlier
            assertTrue(nodesToRetire.stream().map(Node::hostname).filter(hostname -> hostname.equals(newRetiringHostname)).count() == 1);
        }


        for (int i = 0; i < 10; i++){
            tester.advanceTime(Duration.ofMinutes(11));
            retiredExpirer.run();
            List<HostSpec> hosts = deployProxies(application, tester);
            tester.activate(application, new HashSet<>(hosts));
        }

        // After a long time, all currently active proxy nodes are not marked with wantToRetire or as retired
        long numRetiredActiveProxyNodes = tester.nodeRepository().getNodes(NodeType.proxy, Node.State.active).stream()
                .filter(node -> !node.status().wantToRetire())
                .filter(node -> !node.allocation().get().membership().retired())
                .count();
        assertEquals(11 - numNodesToRetire, numRetiredActiveProxyNodes);

        // All the nodes that were marked with wantToRetire earlier are now inactive
        assertEquals(nodesToRetire.stream().map(Node::hostname).collect(Collectors.toSet()),
                tester.nodeRepository().getNodes(Node.State.inactive).stream().map(Node::hostname).collect(Collectors.toSet()));
    }

    private List<HostSpec> deployProxies(ApplicationId application, ProvisioningTester tester) {
        return tester.prepare(application, clusterSpec, capacity, 1);
    }

}
