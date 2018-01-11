// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.applicationmodel.ServiceInstance;
import com.yahoo.vespa.applicationmodel.ServiceStatus;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.orchestrator.ApplicationIdNotFoundException;
import com.yahoo.vespa.orchestrator.ApplicationStateChangeDeniedException;
import org.junit.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests automatic failing of nodes.
 *
 * @author bratseth
 * @author mpolden
 */
public class NodeFailerTest {

    @Test
    public void nodes_for_suspended_applications_are_not_failed() throws ApplicationStateChangeDeniedException, ApplicationIdNotFoundException {
        NodeFailTester tester = NodeFailTester.withTwoApplications();
        tester.suspend(NodeFailTester.app1);

        // Set two nodes down (one for each application) and wait 65 minutes
        String host_from_suspended_app = tester.nodeRepository.getNodes(NodeFailTester.app1, Node.State.active).get(1).hostname();
        String host_from_normal_app = tester.nodeRepository.getNodes(NodeFailTester.app2, Node.State.active).get(3).hostname();
        tester.serviceMonitor.setHostDown(host_from_suspended_app);
        tester.serviceMonitor.setHostDown(host_from_normal_app);
        tester.failer.run();
        tester.clock.advance(Duration.ofMinutes(65));
        tester.failer.run();

        assertEquals(Node.State.failed, tester.nodeRepository.getNode(host_from_normal_app).get().state());
        assertEquals(Node.State.active, tester.nodeRepository.getNode(host_from_suspended_app).get().state());
    }

    @Test
    public void node_failing() throws InterruptedException {
        NodeFailTester tester = NodeFailTester.withTwoApplications();

        // For a day all nodes work so nothing happens
        for (int minutes = 0; minutes < 24 * 60; minutes +=5 ) {
            tester.failer.run();
            tester.clock.advance(Duration.ofMinutes(5));
            tester.allNodesMakeAConfigRequestExcept();

            assertEquals( 0, tester.deployer.redeployments);
            assertEquals(12, tester.nodeRepository.getNodes(NodeType.tenant, Node.State.active).size());
            assertEquals( 0, tester.nodeRepository.getNodes(NodeType.tenant, Node.State.failed).size());
            assertEquals( 4, tester.nodeRepository.getNodes(NodeType.tenant, Node.State.ready).size());
        }

        // Hardware failures are detected on two ready nodes, which are then failed
        Node readyFail1 = tester.nodeRepository.getNodes(NodeType.tenant, Node.State.ready).get(2);
        Node readyFail2 = tester.nodeRepository.getNodes(NodeType.tenant, Node.State.ready).get(3);
        tester.nodeRepository.write(readyFail1.with(readyFail1.status().withHardwareFailureDescription(Optional.of("memory_mcelog"))));
        tester.nodeRepository.write(readyFail2.with(readyFail2.status().withHardwareFailureDescription(Optional.of("disk_smart"))));
        assertEquals(4, tester.nodeRepository.getNodes(NodeType.tenant, Node.State.ready).size());
        tester.failer.run();
        assertEquals(2, tester.nodeRepository.getNodes(NodeType.tenant, Node.State.ready).size());
        assertEquals(Node.State.failed, tester.nodeRepository.getNode(readyFail1.hostname()).get().state());
        assertEquals(Node.State.failed, tester.nodeRepository.getNode(readyFail2.hostname()).get().state());
        
        String downHost1 = tester.nodeRepository.getNodes(NodeFailTester.app1, Node.State.active).get(1).hostname();
        String downHost2 = tester.nodeRepository.getNodes(NodeFailTester.app2, Node.State.active).get(3).hostname();
        tester.serviceMonitor.setHostDown(downHost1);
        tester.serviceMonitor.setHostDown(downHost2);
        // nothing happens the first 45 minutes
        for (int minutes = 0; minutes < 45; minutes +=5 ) {
            tester.failer.run();
            tester.clock.advance(Duration.ofMinutes(5));
            tester.allNodesMakeAConfigRequestExcept();
            assertEquals( 0, tester.deployer.redeployments);
            assertEquals(12, tester.nodeRepository.getNodes(NodeType.tenant, Node.State.active).size());
            assertEquals( 2, tester.nodeRepository.getNodes(NodeType.tenant, Node.State.failed).size());
            assertEquals( 2, tester.nodeRepository.getNodes(NodeType.tenant, Node.State.ready).size());
        }
        tester.serviceMonitor.setHostUp(downHost1);

        // downHost2 should now be failed and replaced, but not downHost1
        tester.clock.advance(Duration.ofDays(1));
        tester.allNodesMakeAConfigRequestExcept();
        tester.failer.run();
        assertEquals( 1, tester.deployer.redeployments);
        assertEquals(12, tester.nodeRepository.getNodes(NodeType.tenant, Node.State.active).size());
        assertEquals( 3, tester.nodeRepository.getNodes(NodeType.tenant, Node.State.failed).size());
        assertEquals( 1, tester.nodeRepository.getNodes(NodeType.tenant, Node.State.ready).size());
        assertEquals(downHost2, tester.nodeRepository.getNodes(NodeType.tenant, Node.State.failed).get(0).hostname());

        // downHost1 fails again
        tester.serviceMonitor.setHostDown(downHost1);
        tester.failer.run();
        tester.clock.advance(Duration.ofMinutes(5));
        tester.allNodesMakeAConfigRequestExcept();
        // the system goes down
        tester.clock.advance(Duration.ofMinutes(120));
        tester.failer = tester.createFailer();
        tester.failer.run();
        // the host is still down and fails
        tester.clock.advance(Duration.ofMinutes(5));
        tester.allNodesMakeAConfigRequestExcept();
        tester.failer.run();
        assertEquals( 2, tester.deployer.redeployments);
        assertEquals(12, tester.nodeRepository.getNodes(NodeType.tenant, Node.State.active).size());
        assertEquals( 4, tester.nodeRepository.getNodes(NodeType.tenant, Node.State.failed).size());
        assertEquals( 0, tester.nodeRepository.getNodes(NodeType.tenant, Node.State.ready).size());

        // the last host goes down
        Node lastNode = tester.highestIndex(tester.nodeRepository.getNodes(NodeFailTester.app1, Node.State.active));
        tester.serviceMonitor.setHostDown(lastNode.hostname());
        // it is not failed because there are no ready nodes to replace it
        for (int minutes = 0; minutes < 75; minutes +=5 ) {
            tester.failer.run();
            tester.clock.advance(Duration.ofMinutes(5));
            tester.allNodesMakeAConfigRequestExcept();
            assertEquals( 2, tester.deployer.redeployments);
            assertEquals(12, tester.nodeRepository.getNodes(NodeType.tenant, Node.State.active).size());
            assertEquals( 4, tester.nodeRepository.getNodes(NodeType.tenant, Node.State.failed).size());
            assertEquals( 0, tester.nodeRepository.getNodes(NodeType.tenant, Node.State.ready).size());
        }

        // A new node is available
        tester.createReadyNodes(1, 16);
        tester.clock.advance(Duration.ofDays(1));
        tester.allNodesMakeAConfigRequestExcept();
        tester.failer.run();
        // The node is now failed
        assertEquals( 3, tester.deployer.redeployments);
        assertEquals(12, tester.nodeRepository.getNodes(NodeType.tenant, Node.State.active).size());
        assertEquals( 5, tester.nodeRepository.getNodes(NodeType.tenant, Node.State.failed).size());
        assertEquals( 0, tester.nodeRepository.getNodes(NodeType.tenant, Node.State.ready).size());
        assertTrue("The index of the last failed node is not reused",
                   tester.highestIndex(tester.nodeRepository.getNodes(NodeFailTester.app1, Node.State.active)).allocation().get().membership().index()
                   >
                   lastNode.allocation().get().membership().index());
    }
    
    @Test
    public void failing_ready_nodes() {
        NodeFailTester tester = NodeFailTester.withTwoApplications();

        // Add ready docker node
        tester.createReadyNodes(1, 16, "docker");

        // For a day all nodes work so nothing happens
        for (int minutes = 0, interval = 30; minutes < 24 * 60; minutes += interval) {
            tester.clock.advance(Duration.ofMinutes(interval));
            tester.allNodesMakeAConfigRequestExcept();
            tester.failer.run();
            assertEquals( 5, tester.nodeRepository.getNodes(NodeType.tenant, Node.State.ready).size());
        }
        
        List<Node> ready = tester.nodeRepository.getNodes(NodeType.tenant, Node.State.ready);

        // Two ready nodes and a ready docker node die, but only 2 of those are failed out
        tester.clock.advance(Duration.ofMinutes(180));
        Node dockerNode = ready.stream().filter(node -> node.flavor().getType() == Flavor.Type.DOCKER_CONTAINER).findFirst().get();
        List<Node> otherNodes = ready.stream()
                               .filter(node -> node.flavor().getType() != Flavor.Type.DOCKER_CONTAINER)
                               .collect(Collectors.toList());
        tester.allNodesMakeAConfigRequestExcept(otherNodes.get(0), otherNodes.get(2), dockerNode);
        tester.failer.run();
        assertEquals( 3, tester.nodeRepository.getNodes(NodeType.tenant, Node.State.ready).size());
        assertEquals( 2, tester.nodeRepository.getNodes(NodeType.tenant, Node.State.failed).size());

        // Another ready node dies and the node that died earlier, are allowed to fail
        tester.clock.advance(Duration.ofDays(1));
        tester.allNodesMakeAConfigRequestExcept(otherNodes.get(0), otherNodes.get(2), dockerNode, otherNodes.get(3));
        tester.failer.run();
        assertEquals( 1, tester.nodeRepository.getNodes(NodeType.tenant, Node.State.ready).size());
        assertEquals(otherNodes.get(1), tester.nodeRepository.getNodes(NodeType.tenant, Node.State.ready).get(0));
        assertEquals( 4, tester.nodeRepository.getNodes(NodeType.tenant, Node.State.failed).size());
    }

    @Test
    public void failing_docker_hosts() {
        NodeFailTester tester = NodeFailTester.withTwoApplicationsOnDocker(7);

        // For a day all nodes work so nothing happens
        for (int minutes = 0, interval = 30; minutes < 24 * 60; minutes += interval) {
            tester.clock.advance(Duration.ofMinutes(interval));
            tester.allNodesMakeAConfigRequestExcept();
            tester.failer.run();
            assertEquals(8, tester.nodeRepository.getNodes(NodeType.tenant, Node.State.active).size());
            assertEquals(13, tester.nodeRepository.getNodes(NodeType.tenant, Node.State.ready).size());
            assertEquals(7, tester.nodeRepository.getNodes(NodeType.host, Node.State.active).size());
        }


        // Select the first host that has two active nodes
        String downHost1 = selectFirstParentHostWithNActiveNodesExcept(tester.nodeRepository, 2);
        tester.serviceMonitor.setHostDown(downHost1);

        // nothing happens the first 45 minutes
        for (int minutes = 0; minutes < 45; minutes += 5 ) {
            tester.failer.run();
            tester.clock.advance(Duration.ofMinutes(5));
            tester.allNodesMakeAConfigRequestExcept();
            assertEquals(0, tester.deployer.redeployments);
            assertEquals(8, tester.nodeRepository.getNodes(NodeType.tenant, Node.State.active).size());
            assertEquals(13, tester.nodeRepository.getNodes(NodeType.tenant, Node.State.ready).size());
            assertEquals(7, tester.nodeRepository.getNodes(NodeType.host, Node.State.active).size());
        }

        tester.clock.advance(Duration.ofMinutes(30));
        tester.allNodesMakeAConfigRequestExcept();
        tester.failer.run();

        assertEquals(2 + 1, tester.deployer.redeployments);
        assertEquals(3, tester.nodeRepository.getNodes(NodeType.tenant, Node.State.failed).size());
        assertEquals(8, tester.nodeRepository.getNodes(NodeType.tenant, Node.State.active).size());
        assertEquals(10, tester.nodeRepository.getNodes(NodeType.tenant, Node.State.ready).size());
        assertEquals(6, tester.nodeRepository.getNodes(NodeType.host, Node.State.active).size());


        // Now lets fail an active tenant node
        Node downTenant1 = tester.nodeRepository.getNodes(NodeType.tenant, Node.State.active).get(0);
        tester.serviceMonitor.setHostDown(downTenant1.hostname());

        // nothing happens during the entire day because of the failure throttling
        for (int minutes = 0, interval = 30; minutes < 24 * 60; minutes += interval) {
            tester.failer.run();
            tester.clock.advance(Duration.ofMinutes(interval));
            tester.allNodesMakeAConfigRequestExcept();
            assertEquals(3 + 1, tester.nodeRepository.getNodes(Node.State.failed).size());
        }

        tester.clock.advance(Duration.ofMinutes(30));
        tester.allNodesMakeAConfigRequestExcept();
        tester.failer.run();

        assertEquals(3 + 1, tester.deployer.redeployments);
        assertEquals(4, tester.nodeRepository.getNodes(NodeType.tenant, Node.State.failed).size());
        assertEquals(8, tester.nodeRepository.getNodes(NodeType.tenant, Node.State.active).size());
        assertEquals(9, tester.nodeRepository.getNodes(NodeType.tenant, Node.State.ready).size());
        assertEquals(6, tester.nodeRepository.getNodes(NodeType.host, Node.State.active).size());


        // Lets fail another host, make sure it is not the same where downTenant1 is a child
        String downHost2 = selectFirstParentHostWithNActiveNodesExcept(tester.nodeRepository, 2, downTenant1.parentHostname().get());
        tester.serviceMonitor.setHostDown(downHost2);
        tester.failer.run();
        tester.clock.advance(Duration.ofMinutes(90));
        tester.allNodesMakeAConfigRequestExcept();
        tester.failer.run();

        assertEquals(5 + 2, tester.deployer.redeployments);
        assertEquals(7, tester.nodeRepository.getNodes(NodeType.tenant, Node.State.failed).size());
        assertEquals(8, tester.nodeRepository.getNodes(NodeType.tenant, Node.State.active).size());
        assertEquals(6, tester.nodeRepository.getNodes(NodeType.tenant, Node.State.ready).size());
        assertEquals(5, tester.nodeRepository.getNodes(NodeType.host, Node.State.active).size());


        // We have only 5 hosts remaining, so if we fail another host, we should only be able to redeploy app1's
        // node, while app2's should remain
        String downHost3 = selectFirstParentHostWithNActiveNodesExcept(tester.nodeRepository, 2, downTenant1.parentHostname().get());
        tester.serviceMonitor.setHostDown(downHost3);
        tester.failer.run();
        tester.clock.advance(Duration.ofDays(1));
        tester.allNodesMakeAConfigRequestExcept();
        tester.failer.run();

        assertEquals(6 + 2, tester.deployer.redeployments);
        assertEquals(9, tester.nodeRepository.getNodes(NodeType.tenant, Node.State.failed).size());
        assertEquals(8, tester.nodeRepository.getNodes(NodeType.tenant, Node.State.active).size());
        assertEquals(4, tester.nodeRepository.getNodes(NodeType.tenant, Node.State.ready).size());
        assertEquals(5, tester.nodeRepository.getNodes(NodeType.host, Node.State.active).size());
    }

    @Test
    public void failing_proxy_nodes() {
        NodeFailTester tester = NodeFailTester.withProxyApplication();

        // For a day all nodes work so nothing happens
        for (int minutes = 0; minutes < 24 * 60; minutes +=5 ) {
            tester.failer.run();
            tester.clock.advance(Duration.ofMinutes(5));
            tester.allNodesMakeAConfigRequestExcept();

            assertEquals(16, tester.nodeRepository.getNodes(NodeType.proxy, Node.State.active).size());
        }

        Set<String> downHosts = new HashSet<>();
        downHosts.add("host4");
        downHosts.add("host5");

        for (String downHost : downHosts)
            tester.serviceMonitor.setHostDown(downHost);
        // nothing happens the first 45 minutes
        for (int minutes = 0; minutes < 45; minutes +=5 ) {
            tester.failer.run();
            tester.clock.advance(Duration.ofMinutes(5));
            tester.allNodesMakeAConfigRequestExcept();
            assertEquals( 0, tester.deployer.redeployments);
            assertEquals(16, tester.nodeRepository.getNodes(NodeType.proxy, Node.State.active).size());
            assertEquals( 0, tester.nodeRepository.getNodes(NodeType.proxy, Node.State.failed).size());
        }

        tester.clock.advance(Duration.ofMinutes(60));
        tester.failer.run();

        // one down host should now be failed, but not two as we are only allowed to fail one proxy
        assertEquals( 1, tester.deployer.redeployments);
        assertEquals(15, tester.nodeRepository.getNodes(NodeType.proxy, Node.State.active).size());
        assertEquals( 1, tester.nodeRepository.getNodes(NodeType.proxy, Node.State.failed).size());
        String failedHost1 = tester.nodeRepository.getNodes(NodeType.proxy, Node.State.failed).get(0).hostname();
        assertTrue(downHosts.contains(failedHost1));

        // trying to fail again will still not fail the other down host
        tester.clock.advance(Duration.ofMinutes(60));
        tester.failer.run();
        assertEquals(15, tester.nodeRepository.getNodes(NodeType.proxy, Node.State.active).size());

        // The first down host is removed, which causes the second one to be moved to failed
        tester.nodeRepository.removeRecursively(failedHost1);
        tester.failer.run();
        assertEquals( 2, tester.deployer.redeployments);
        assertEquals(14, tester.nodeRepository.getNodes(NodeType.proxy, Node.State.active).size());
        assertEquals( 1, tester.nodeRepository.getNodes(NodeType.proxy, Node.State.failed).size());
        String failedHost2 = tester.nodeRepository.getNodes(NodeType.proxy, Node.State.failed).get(0).hostname();
        assertFalse(failedHost1.equals(failedHost2));
        assertTrue(downHosts.contains(failedHost2));
    }

    @Test
    public void failing_divergent_ready_nodes() {
        NodeFailTester tester = NodeFailTester.withNoApplications();

        Node readyNode = tester.createReadyNodes(1).get(0);

        tester.failer.run();

        assertEquals(Node.State.ready, readyNode.state());

        tester.nodeRepository.write(readyNode.with(readyNode.status()
                .withHardwareDivergence(Optional.of("{\"specVerificationReport\":{\"actualIpv6Connection\":false}}"))));

        tester.failer.run();

        assertEquals(1, tester.nodeRepository.getNodes(Node.State.failed).size());
    }

    @Test
    public void node_failing_throttle() {
        // Throttles based on a absolute number in small zone
        {
            // 50 regular tenant nodes, 10 hosts with each 3 tenant nodes, total 90 nodes
            NodeFailTester tester = NodeFailTester.withTwoApplicationsOnDocker(10);
            List<Node> readyNodes = tester.createReadyNodes(50, 30);
            List<Node> hosts = tester.nodeRepository.getNodes(NodeType.host);

            List<Node> deadNodes = readyNodes.subList(0, 4);

            // 2 hours pass, 4 nodes die
            for (int minutes = 0, interval = 30; minutes < 2 * 60; minutes += interval) {
                tester.clock.advance(Duration.ofMinutes(interval));
                tester.allNodesMakeAConfigRequestExcept(deadNodes);
            }

            // 2 nodes are failed (the minimum amount that are always allowed to fail)
            tester.failer.run();
            assertEquals(2, tester.nodeRepository.getNodes(Node.State.failed).size());

            // 6 more hours pass, no more nodes are failed
            for (int minutes = 0, interval = 30; minutes < 6 * 60; minutes += interval) {
                tester.clock.advance(Duration.ofMinutes(interval));
                tester.allNodesMakeAConfigRequestExcept(deadNodes);
            }
            tester.failer.run();
            assertEquals(2, tester.nodeRepository.getNodes(Node.State.failed).size());

            // 2 docker hosts now fail, 1 of them (with all its children is allowed to fail)
            hosts.subList(0, 2).forEach(host -> {
                tester.serviceMonitor.setHostDown(host.hostname());
                deadNodes.add(host);
            });
            tester.failer.run();
            tester.clock.advance(Duration.ofMinutes(61));
            tester.allNodesMakeAConfigRequestExcept(deadNodes);

            tester.failer.run();
            assertEquals(6, tester.nodeRepository.getNodes(Node.State.failed).size());

            // 24 more hours pass without any other nodes being failed out
            for (int minutes = 0, interval = 30; minutes <= 23 * 60; minutes += interval) {
                tester.clock.advance(Duration.ofMinutes(interval));
                tester.allNodesMakeAConfigRequestExcept(deadNodes);
            }
            tester.failer.run();
            assertEquals(6, tester.nodeRepository.getNodes(Node.State.failed).size());

            // Next, the 2 ready nodes that were dead from the start are failed out, and finally
            // the second host and all its children are failed
            tester.clock.advance(Duration.ofMinutes(30));
            tester.failer.run();
            assertEquals(12, tester.nodeRepository.getNodes(Node.State.failed).size());

            // Nothing else to fail
            tester.clock.advance(Duration.ofHours(25));
            tester.allNodesMakeAConfigRequestExcept(deadNodes);
            tester.failer.run();
            assertEquals(12, tester.nodeRepository.getNodes(Node.State.failed).size());
        }

        // Throttles based on percentage in large zone
        {
            NodeFailTester tester = NodeFailTester.withNoApplications();
            List<Node> readyNodes = tester.createReadyNodes(500);
            List<Node> deadNodes = readyNodes.subList(0, 10);

            // 2 hours pass, 10 nodes (2%) die
            for (int minutes = 0, interval = 30; minutes < 2 * 60; minutes += interval) {
                tester.clock.advance(Duration.ofMinutes(interval));
                tester.allNodesMakeAConfigRequestExcept(deadNodes);
            }
            tester.failer.run();
            // 1% are allowed to fail
            assertEquals(5, tester.nodeRepository.getNodes(Node.State.failed).size());

            // 6 more hours pass, no more nodes are failed
            for (int minutes = 0, interval = 30; minutes < 6 * 60; minutes += interval) {
                tester.clock.advance(Duration.ofMinutes(interval));
                tester.allNodesMakeAConfigRequestExcept(deadNodes);
            }
            tester.failer.run();
            assertEquals(5, tester.nodeRepository.getNodes(Node.State.failed).size());

            // 18 more hours pass, 24 hours since the first 5 nodes were failed. The remaining 5 are failed
            for (int minutes = 0, interval = 30; minutes < 18 * 60; minutes += interval) {
                tester.clock.advance(Duration.ofMinutes(interval));
                tester.allNodesMakeAConfigRequestExcept(deadNodes);
            }
            tester.failer.run();
            assertEquals(10, tester.nodeRepository.getNodes(Node.State.failed).size());
        }
    }

    @Test
    public void testUpness() {
        assertFalse(badNode(0, 0, 0));
        assertFalse(badNode(0, 0, 2));
        assertFalse(badNode(0, 3, 0));
        assertFalse(badNode(0, 3, 2));
        assertTrue(badNode(1, 0, 0));
        assertTrue(badNode(1, 0, 2));
        assertFalse(badNode(1, 3, 0));
        assertFalse(badNode(1, 3, 2));
    }

    private void addServiceInstances(List<ServiceInstance> list, ServiceStatus status, int num) {
        for (int i = 0; i < num; ++i) {
            ServiceInstance service = mock(ServiceInstance.class);
            when(service.serviceStatus()).thenReturn(status);
            list.add(service);
        }
    }

    private boolean badNode(int numDown, int numUp, int numNotChecked) {
        List<ServiceInstance> services = new ArrayList<>();
        addServiceInstances(services, ServiceStatus.DOWN, numDown);
        addServiceInstances(services, ServiceStatus.UP, numUp);
        addServiceInstances(services, ServiceStatus.NOT_CHECKED, numNotChecked);
        Collections.shuffle(services);

        return NodeFailer.badNode(services);
    }

    /**
     * Selects the first parent host that:
     *  - has exactly n nodes in state 'active'
     *  - is not present in the 'except' array
     */
    private static String selectFirstParentHostWithNActiveNodesExcept(NodeRepository nodeRepository, int n, String... except) {
        Set<String> exceptSet = Arrays.stream(except).collect(Collectors.toSet());
        return nodeRepository.getNodes(NodeType.tenant, Node.State.active).stream()
                .collect(Collectors.groupingBy(Node::parentHostname))
                .entrySet().stream()
                .filter(entry -> entry.getValue().size() == n)
                .map(Map.Entry::getKey)
                .flatMap(parentHost -> Stream.of(parentHost.get()))
                .filter(node -> ! exceptSet.contains(node))
                .findFirst().get();
    }

}
