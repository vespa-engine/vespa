// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.component.Version;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.maintenance.RetiredExpirer;
import com.yahoo.vespa.hosted.provision.maintenance.TestMetric;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.testutils.MockDeployer;
import com.yahoo.vespa.service.duper.InfraApplication;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests provisioning by node type instead of by count and flavor
 * 
 * @author bratseth
 */
public class NodeTypeProvisioningTest {

    private final ProvisioningTester tester = new ProvisioningTester.Builder().build();

    private final InfraApplication proxyApp = ProvisioningTester.infraApplication(NodeType.proxy);

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
            tester.prepareAndActivateInfraApplication(proxyApp);
            NodeList nodes = tester.nodeRepository().nodes().list(Node.State.active).nodeType(NodeType.proxy);
            assertEquals("Activated all proxies", 11, nodes.size());
        }

        { // Redeploy with no changes
            tester.prepareAndActivateInfraApplication(proxyApp);
            NodeList nodes = tester.nodeRepository().nodes().list(Node.State.active).nodeType(NodeType.proxy);
            assertEquals(11, nodes.size());
        }

        { // Add 2 ready proxies then redeploy
            tester.makeReadyNodes(2, "small", NodeType.proxy);
            tester.prepareAndActivateInfraApplication(proxyApp);
            NodeList nodes = tester.nodeRepository().nodes().list(Node.State.active).nodeType(NodeType.proxy);
            assertEquals(13, nodes.size());
        }

        { // Remove 3 proxies then redeploy
            NodeList nodes = tester.nodeRepository().nodes().list(Node.State.active).nodeType(NodeType.proxy);
            tester.nodeRepository().nodes().fail(nodes.asList().get(0).hostname(), Agent.system, "Failing to unit test");
            tester.nodeRepository().nodes().fail(nodes.asList().get(1).hostname(), Agent.system, "Failing to unit test");
            tester.nodeRepository().nodes().fail(nodes.asList().get(5).hostname(), Agent.system, "Failing to unit test");
            
            tester.prepareAndActivateInfraApplication(proxyApp);
            nodes = tester.nodeRepository().nodes().list(Node.State.active).nodeType(NodeType.proxy);
            assertEquals(10, nodes.size());
        }
    }

    @Test
    public void retire_proxy() {
        MockDeployer deployer = new MockDeployer(tester.provisioner(),
                                                 tester.clock(),
                                                 List.of(new MockDeployer.ApplicationContext(proxyApp, Version.fromString("6.42"))));
        RetiredExpirer retiredExpirer =  new RetiredExpirer(tester.nodeRepository(),
                                                            deployer,
                                                            new TestMetric(),
                                                            Duration.ofDays(30),
                                                            Duration.ofMinutes(10));

        { // Deploy
            tester.prepareAndActivateInfraApplication(proxyApp);
            NodeList nodes = tester.nodeRepository().nodes().list(Node.State.active).nodeType(NodeType.proxy);
            assertEquals("Activated all proxies", 11, nodes.size());
        }

        Node nodeToRetire = tester.nodeRepository().nodes().list(Node.State.active).nodeType(NodeType.proxy).asList().get(5);
        { // Pick out a node and retire it
            tester.nodeRepository().nodes().write(nodeToRetire.withWantToRetire(true, Agent.system, tester.clock().instant()), () -> {});

            tester.prepareAndActivateInfraApplication(proxyApp);
            NodeList nodes = tester.nodeRepository().nodes().list(Node.State.active).nodeType(NodeType.proxy);
            assertEquals(11, nodes.size());

            // Verify that wantToRetire has been propagated
            assertTrue(tester.nodeRepository().nodes().node(nodeToRetire.hostname())
                    .flatMap(Node::allocation)
                    .map(allocation -> allocation.membership().retired())
                    .orElseThrow(RuntimeException::new));
        }

        { // Redeploying while the node is still retiring has no effect
            tester.prepareAndActivateInfraApplication(proxyApp);
            NodeList nodes = tester.nodeRepository().nodes().list(Node.State.active).nodeType(NodeType.proxy);
            assertEquals(11, nodes.size());

            // Verify that the node is still marked as retired
            assertTrue(tester.nodeRepository().nodes().node(nodeToRetire.hostname())
                    .flatMap(Node::allocation)
                    .map(allocation -> allocation.membership().retired())
                    .orElseThrow(RuntimeException::new));
        }

        {
            tester.advanceTime(Duration.ofMinutes(11));
            retiredExpirer.run();

            tester.prepareAndActivateInfraApplication(proxyApp);
            NodeList nodes = tester.nodeRepository().nodes().list(Node.State.active).nodeType(NodeType.proxy);
            assertEquals(10, nodes.size());

            // Verify that the node is now inactive
            assertEquals(Node.State.dirty, tester.nodeRepository().nodes().node(nodeToRetire.hostname())
                    .orElseThrow(RuntimeException::new).state());
        }
    }

    @Test
    public void retire_multiple_proxy_simultaneously() {
        MockDeployer deployer = new MockDeployer(tester.provisioner(),
                                                 tester.clock(),
                                                 List.of(new MockDeployer.ApplicationContext(proxyApp, Version.fromString("6.42"))));
        RetiredExpirer retiredExpirer =  new RetiredExpirer(tester.nodeRepository(),
                                                            deployer,
                                                            new TestMetric(),
                                                            Duration.ofDays(30),
                                                            Duration.ofMinutes(10));
        final int numNodesToRetire = 5;

        { // Deploy
            tester.prepareAndActivateInfraApplication(proxyApp);
            NodeList nodes = tester.nodeRepository().nodes().list(Node.State.active).nodeType(NodeType.proxy);
            assertEquals("Activated all proxies", 11, nodes.size());
        }

        List<Node> nodesToRetire = tester.nodeRepository().nodes().list(Node.State.active).nodeType(NodeType.proxy).asList()
                .subList(3, 3 + numNodesToRetire);
        {
            nodesToRetire.forEach(nodeToRetire ->
                    tester.nodeRepository().nodes().write(nodeToRetire.withWantToRetire(true, Agent.system, tester.clock().instant()), () -> {}));

            tester.prepareAndActivateInfraApplication(proxyApp);
            NodeList nodes = tester.nodeRepository().nodes().list(Node.State.active).nodeType(NodeType.proxy);
            assertEquals(11, nodes.size());

            // Verify that wantToRetire has been propagated
            List<Node> nodesCurrentlyRetiring = nodes.stream()
                    .filter(node -> node.allocation().get().membership().retired())
                    .toList();
            assertEquals(5, nodesCurrentlyRetiring.size());

            // The retiring nodes should be the nodes we marked for retirement
            assertTrue(Set.copyOf(nodesToRetire).containsAll(nodesCurrentlyRetiring));
        }

        { // Redeploying while the nodes are still retiring has no effect
            tester.prepareAndActivateInfraApplication(proxyApp);
            NodeList nodes = tester.nodeRepository().nodes().list(Node.State.active).nodeType(NodeType.proxy);
            assertEquals(11, nodes.size());

            // Verify that wantToRetire has been propagated
            List<Node> nodesCurrentlyRetiring = nodes.stream()
                    .filter(node -> node.allocation().get().membership().retired())
                    .toList();
            assertEquals(5, nodesCurrentlyRetiring.size());
        }

        {
            // Let all retired nodes expire
            tester.advanceTime(Duration.ofMinutes(11));
            retiredExpirer.run();

            tester.prepareAndActivateInfraApplication(proxyApp);

            // All currently active proxy nodes are not marked with wantToRetire or as retired
            long numRetiredActiveProxyNodes = tester.nodeRepository().nodes().list(Node.State.active).nodeType(NodeType.proxy).stream()
                    .filter(node -> !node.status().wantToRetire())
                    .filter(node -> !node.allocation().get().membership().retired())
                    .count();
            assertEquals(6, numRetiredActiveProxyNodes);

            // All the nodes that were marked with wantToRetire earlier are now dirty
            assertEquals(nodesToRetire.stream().map(Node::hostname).collect(Collectors.toSet()),
                    tester.nodeRepository().nodes().list(Node.State.dirty).stream().map(Node::hostname).collect(Collectors.toSet()));
        }
    }

}
