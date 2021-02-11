// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.applicationmodel.ServiceInstance;
import com.yahoo.vespa.applicationmodel.ServiceStatus;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.Report;
import com.yahoo.vespa.hosted.provision.node.Reports;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.yahoo.vespa.hosted.provision.node.Report.Type.HARD_FAIL;
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


    private static final Report badTotalMemorySizeReport = Report.basicReport(
            "badTotalMemorySize", HARD_FAIL, Instant.now(), "too low");

    @Test
    public void fail_nodes_with_severe_reports_if_allowed_to_be_down() {
        NodeFailTester tester = NodeFailTester.withTwoApplicationsOnDocker(6);
        String hostWithFailureReports = selectFirstParentHostWithNActiveNodesExcept(tester.nodeRepository, 2);

        // Set failure report to the parent and all its children.
        tester.nodeRepository.nodes().list().stream()
                .filter(node -> node.hostname().equals(hostWithFailureReports))
                .forEach(node -> {
                    Node updatedNode = node.with(node.reports().withReport(badTotalMemorySizeReport));
                    tester.nodeRepository.nodes().write(updatedNode, () -> {});
                });

        testNodeFailingWith(tester, hostWithFailureReports);
    }

    private void testNodeFailingWith(NodeFailTester tester, String hostWithHwFailure) {
        // The host should have 2 nodes in active and 1 ready
        Map<Node.State, List<String>> hostnamesByState = tester.nodeRepository.nodes().list().childrenOf(hostWithHwFailure).asList().stream()
                .collect(Collectors.groupingBy(Node::state, Collectors.mapping(Node::hostname, Collectors.toList())));
        assertEquals(2, hostnamesByState.get(Node.State.active).size());
        assertEquals(1, hostnamesByState.get(Node.State.ready).size());

        // Suspend the first of the active nodes
        tester.suspend(hostnamesByState.get(Node.State.active).get(0));

        tester.runMaintainers();
        tester.clock.advance(Duration.ofHours(25));
        tester.allNodesMakeAConfigRequestExcept();
        tester.runMaintainers();

        // The first (and the only) ready node and the 1st active node that was allowed to fail should be failed
        Map<Node.State, List<String>> expectedHostnamesByState1Iter = Map.of(
                Node.State.failed, List.of(hostnamesByState.get(Node.State.ready).get(0), hostnamesByState.get(Node.State.active).get(0)),
                Node.State.active, hostnamesByState.get(Node.State.active).subList(1, 2));
        Map<Node.State, List<String>> hostnamesByState1Iter = tester.nodeRepository.nodes().list().childrenOf(hostWithHwFailure).asList().stream()
                .collect(Collectors.groupingBy(Node::state, Collectors.mapping(Node::hostname, Collectors.toList())));
        assertEquals(expectedHostnamesByState1Iter, hostnamesByState1Iter);

        // Suspend the second of the active nodes
        tester.suspend(hostnamesByState.get(Node.State.active).get(1));

        tester.clock.advance(Duration.ofHours(25));
        tester.allNodesMakeAConfigRequestExcept();
        tester.runMaintainers();

        // All of the children should be failed now
        Set<Node.State> childStates2Iter = tester.nodeRepository.nodes().list().childrenOf(hostWithHwFailure).asList().stream()
                .map(Node::state).collect(Collectors.toSet());
        assertEquals(Set.of(Node.State.failed), childStates2Iter);
        // The host itself is still active as it too must be allowed to suspend
        assertEquals(Node.State.active, tester.nodeRepository.nodes().node(hostWithHwFailure).get().state());

        tester.suspend(hostWithHwFailure);
        tester.runMaintainers();
        assertEquals(Node.State.failed, tester.nodeRepository.nodes().node(hostWithHwFailure).get().state());
        assertEquals(4, tester.nodeRepository.nodes().list(Node.State.failed).size());
    }

    @Test
    public void hw_fail_only_if_whole_host_is_suspended() {
        NodeFailTester tester = NodeFailTester.withTwoApplicationsOnDocker(6);
        String hostWithFailureReports = selectFirstParentHostWithNActiveNodesExcept(tester.nodeRepository, 2);
        assertEquals(Node.State.active, tester.nodeRepository.nodes().node(hostWithFailureReports).get().state());

        // The host has 2 nodes in active and 1 ready
        Map<Node.State, List<String>> hostnamesByState = tester.nodeRepository.nodes().list().childrenOf(hostWithFailureReports).asList().stream()
                .collect(Collectors.groupingBy(Node::state, Collectors.mapping(Node::hostname, Collectors.toList())));
        assertEquals(2, hostnamesByState.get(Node.State.active).size());
        String activeChild1 = hostnamesByState.get(Node.State.active).get(0);
        String activeChild2 = hostnamesByState.get(Node.State.active).get(1);
        assertEquals(1, hostnamesByState.get(Node.State.ready).size());
        String readyChild = hostnamesByState.get(Node.State.ready).get(0);

        // Set failure report to the parent and all its children.
        Report badTotalMemorySizeReport = Report.basicReport("badTotalMemorySize", HARD_FAIL, Instant.now(), "too low");
        tester.nodeRepository.nodes().list().stream()
                .filter(node -> node.hostname().equals(hostWithFailureReports))
                .forEach(node -> {
                    Node updatedNode = node.with(node.reports().withReport(badTotalMemorySizeReport));
                    tester.nodeRepository.nodes().write(updatedNode, () -> {});
                });

        // The ready node will be failed, but neither the host nor the 2 active nodes since they have not been suspended
        tester.allNodesMakeAConfigRequestExcept();
        tester.runMaintainers();
        assertEquals(Node.State.failed, tester.nodeRepository.nodes().node(readyChild).get().state());
        assertEquals(Node.State.active, tester.nodeRepository.nodes().node(hostWithFailureReports).get().state());
        assertEquals(Node.State.active, tester.nodeRepository.nodes().node(activeChild1).get().state());
        assertEquals(Node.State.active, tester.nodeRepository.nodes().node(activeChild2).get().state());

        // Suspending the host will not fail any more since none of the children are suspened
        tester.suspend(hostWithFailureReports);
        tester.clock.advance(Duration.ofHours(25));
        tester.allNodesMakeAConfigRequestExcept();
        tester.runMaintainers();
        assertEquals(Node.State.failed, tester.nodeRepository.nodes().node(readyChild).get().state());
        assertEquals(Node.State.active, tester.nodeRepository.nodes().node(hostWithFailureReports).get().state());
        assertEquals(Node.State.active, tester.nodeRepository.nodes().node(activeChild1).get().state());
        assertEquals(Node.State.active, tester.nodeRepository.nodes().node(activeChild2).get().state());

        // Suspending one child node will fail that out.
        tester.suspend(activeChild1);
        tester.clock.advance(Duration.ofHours(25));
        tester.allNodesMakeAConfigRequestExcept();
        tester.runMaintainers();
        assertEquals(Node.State.failed, tester.nodeRepository.nodes().node(readyChild).get().state());
        assertEquals(Node.State.active, tester.nodeRepository.nodes().node(hostWithFailureReports).get().state());
        assertEquals(Node.State.failed, tester.nodeRepository.nodes().node(activeChild1).get().state());
        assertEquals(Node.State.active, tester.nodeRepository.nodes().node(activeChild2).get().state());

        // Suspending the second child node will fail that out and the host.
        tester.suspend(activeChild2);
        tester.clock.advance(Duration.ofHours(25));
        tester.allNodesMakeAConfigRequestExcept();
        tester.runMaintainers();
        assertEquals(Node.State.failed, tester.nodeRepository.nodes().node(readyChild).get().state());
        assertEquals(Node.State.failed, tester.nodeRepository.nodes().node(hostWithFailureReports).get().state());
        assertEquals(Node.State.failed, tester.nodeRepository.nodes().node(activeChild1).get().state());
        assertEquals(Node.State.failed, tester.nodeRepository.nodes().node(activeChild2).get().state());
    }

    @Test
    public void nodes_for_suspended_applications_are_not_failed() {
        NodeFailTester tester = NodeFailTester.withTwoApplications();
        tester.suspend(NodeFailTester.app1);

        // Set two nodes down (one for each application) and wait 65 minutes
        String host_from_suspended_app = tester.nodeRepository.nodes().list(Node.State.active).owner(NodeFailTester.app1).asList().get(1).hostname();
        String host_from_normal_app = tester.nodeRepository.nodes().list(Node.State.active).owner(NodeFailTester.app2).asList().get(3).hostname();
        tester.serviceMonitor.setHostDown(host_from_suspended_app);
        tester.serviceMonitor.setHostDown(host_from_normal_app);
        tester.runMaintainers();
        tester.clock.advance(Duration.ofMinutes(65));
        tester.runMaintainers();

        assertTrue(tester.nodeRepository.nodes().node(host_from_normal_app).get().isDown());
        assertTrue(tester.nodeRepository.nodes().node(host_from_suspended_app).get().isDown());
        assertEquals(Node.State.failed, tester.nodeRepository.nodes().node(host_from_normal_app).get().state());
        assertEquals(Node.State.active, tester.nodeRepository.nodes().node(host_from_suspended_app).get().state());
    }

    @Test
    public void zone_is_not_working_if_too_many_nodes_down() {
        NodeFailTester tester = NodeFailTester.withTwoApplications();

        tester.serviceMonitor.setHostDown(tester.nodeRepository.nodes().list(Node.State.active).owner(NodeFailTester.app1).asList().get(0).hostname());
        tester.runMaintainers();
        assertTrue(tester.nodeRepository.nodes().isWorking());

        tester.serviceMonitor.setHostDown(tester.nodeRepository.nodes().list(Node.State.active).owner(NodeFailTester.app1).asList().get(1).hostname());
        tester.runMaintainers();
        assertTrue(tester.nodeRepository.nodes().isWorking());

        tester.serviceMonitor.setHostDown(tester.nodeRepository.nodes().list(Node.State.active).owner(NodeFailTester.app1).asList().get(2).hostname());
        tester.runMaintainers();
        assertFalse(tester.nodeRepository.nodes().isWorking());

        tester.clock.advance(Duration.ofMinutes(65));
        tester.runMaintainers();
        assertTrue("Node failing is deactivated", tester.nodeRepository.nodes().list(Node.State.failed).isEmpty());
    }

    @Test
    public void node_failing() {
        NodeFailTester tester = NodeFailTester.withTwoApplications();

        // For a day all nodes work so nothing happens
        for (int minutes = 0; minutes < 24 * 60; minutes +=5 ) {
            tester.runMaintainers();
            tester.clock.advance(Duration.ofMinutes(5));
            tester.allNodesMakeAConfigRequestExcept();

            assertEquals( 0, tester.deployer.redeployments);
            assertEquals(12, tester.nodeRepository.nodes().list(Node.State.active).nodeType(NodeType.tenant).size());
            assertEquals( 0, tester.nodeRepository.nodes().list(Node.State.failed).nodeType(NodeType.tenant).size());
            assertEquals( 4, tester.nodeRepository.nodes().list(Node.State.ready).nodeType(NodeType.tenant).size());
        }

        // Hardware failures are detected on two ready nodes, which are then failed
        Node readyFail1 = tester.nodeRepository.nodes().list(Node.State.ready).nodeType(NodeType.tenant).asList().get(2);
        Node readyFail2 = tester.nodeRepository.nodes().list(Node.State.ready).nodeType(NodeType.tenant).asList().get(3);
        tester.nodeRepository.nodes().write(readyFail1.with(new Reports().withReport(badTotalMemorySizeReport)), () -> {});
        tester.nodeRepository.nodes().write(readyFail2.with(new Reports().withReport(badTotalMemorySizeReport)), () -> {});
        assertEquals(4, tester.nodeRepository.nodes().list(Node.State.ready).nodeType(NodeType.tenant).size());
        tester.runMaintainers();
        assertEquals(2, tester.nodeRepository.nodes().list(Node.State.ready).nodeType(NodeType.tenant).size());
        assertEquals(Node.State.failed, tester.nodeRepository.nodes().node(readyFail1.hostname()).get().state());
        assertEquals(Node.State.failed, tester.nodeRepository.nodes().node(readyFail2.hostname()).get().state());

        String downHost1 = tester.nodeRepository.nodes().list(Node.State.active).owner(NodeFailTester.app1).asList().get(1).hostname();
        String downHost2 = tester.nodeRepository.nodes().list(Node.State.active).owner(NodeFailTester.app2).asList().get(3).hostname();
        tester.serviceMonitor.setHostDown(downHost1);
        tester.serviceMonitor.setHostDown(downHost2);
        // nothing happens the first 45 minutes
        for (int minutes = 0; minutes < 45; minutes +=5 ) {
            tester.runMaintainers();
            tester.clock.advance(Duration.ofMinutes(5));
            tester.allNodesMakeAConfigRequestExcept();
            assertEquals( 0, tester.deployer.redeployments);
            assertEquals(12, tester.nodeRepository.nodes().list(Node.State.active).nodeType(NodeType.tenant).size());
            assertEquals( 2, tester.nodeRepository.nodes().list(Node.State.failed).nodeType(NodeType.tenant).size());
            assertEquals( 2, tester.nodeRepository.nodes().list(Node.State.ready).nodeType(NodeType.tenant).size());
        }
        tester.serviceMonitor.setHostUp(downHost1);

        // downHost2 should now be failed and replaced, but not downHost1
        tester.clock.advance(Duration.ofDays(1));
        tester.allNodesMakeAConfigRequestExcept();
        tester.runMaintainers();
        assertEquals( 1, tester.deployer.redeployments);
        assertEquals(12, tester.nodeRepository.nodes().list(Node.State.active).nodeType(NodeType.tenant).size());
        assertEquals( 3, tester.nodeRepository.nodes().list(Node.State.failed).nodeType(NodeType.tenant).size());
        assertEquals( 1, tester.nodeRepository.nodes().list(Node.State.ready).nodeType(NodeType.tenant).size());
        assertEquals(downHost2, tester.nodeRepository.nodes().list(Node.State.failed).nodeType(NodeType.tenant).asList().get(0).hostname());

        // downHost1 fails again
        tester.serviceMonitor.setHostDown(downHost1);
        tester.runMaintainers();
        tester.clock.advance(Duration.ofMinutes(5));
        tester.allNodesMakeAConfigRequestExcept();
        // the system goes down
        tester.clock.advance(Duration.ofMinutes(120));
        tester.failer = tester.createFailer();
        tester.runMaintainers();
        // the host is still down and fails
        tester.clock.advance(Duration.ofMinutes(5));
        tester.allNodesMakeAConfigRequestExcept();
        tester.runMaintainers();
        assertEquals( 2, tester.deployer.redeployments);
        assertEquals(12, tester.nodeRepository.nodes().list(Node.State.active).nodeType(NodeType.tenant).size());
        assertEquals( 4, tester.nodeRepository.nodes().list(Node.State.failed).nodeType(NodeType.tenant).size());
        assertEquals( 0, tester.nodeRepository.nodes().list(Node.State.ready).nodeType(NodeType.tenant).size());

        // the last host goes down
        Node lastNode = tester.highestIndex(tester.nodeRepository.nodes().list(Node.State.active).owner(NodeFailTester.app1));
        tester.serviceMonitor.setHostDown(lastNode.hostname());
        // it is not failed because there are no ready nodes to replace it
        for (int minutes = 0; minutes < 75; minutes +=5 ) {
            tester.runMaintainers();
            tester.clock.advance(Duration.ofMinutes(5));
            tester.allNodesMakeAConfigRequestExcept();
            assertEquals( 2, tester.deployer.redeployments);
            assertEquals(12, tester.nodeRepository.nodes().list(Node.State.active).nodeType(NodeType.tenant).size());
            assertEquals( 4, tester.nodeRepository.nodes().list(Node.State.failed).nodeType(NodeType.tenant).size());
            assertEquals( 0, tester.nodeRepository.nodes().list(Node.State.ready).nodeType(NodeType.tenant).size());
        }

        // A new node is available
        tester.createReadyNodes(1, 16, NodeFailTester.nodeResources);
        tester.clock.advance(Duration.ofDays(1));
        tester.allNodesMakeAConfigRequestExcept();
        tester.runMaintainers();
        // The node is now failed
        assertEquals( 3, tester.deployer.redeployments);
        assertEquals(12, tester.nodeRepository.nodes().list(Node.State.active).nodeType(NodeType.tenant).size());
        assertEquals( 5, tester.nodeRepository.nodes().list(Node.State.failed).nodeType(NodeType.tenant).size());
        assertEquals( 0, tester.nodeRepository.nodes().list(Node.State.ready).nodeType(NodeType.tenant).size());
        assertTrue("The index of the last failed node is not reused",
                   tester.highestIndex(tester.nodeRepository.nodes().list(Node.State.active).owner(NodeFailTester.app1)).allocation().get().membership().index()
                   >
                   lastNode.allocation().get().membership().index());
    }

    @Test
    public void re_activate_grace_period_test() {
        NodeFailTester tester = NodeFailTester.withTwoApplications();
        String downNode = tester.nodeRepository.nodes().list(Node.State.active).owner(NodeFailTester.app1).asList().get(1).hostname();

        tester.serviceMonitor.setHostDown(downNode);
        tester.allNodesMakeAConfigRequestExcept();
        tester.runMaintainers();
        assertEquals(0, tester.nodeRepository.nodes().list(Node.State.failed).nodeType(NodeType.tenant).size());

        tester.clock.advance(Duration.ofMinutes(75));
        tester.allNodesMakeAConfigRequestExcept();
        tester.runMaintainers();
        assertEquals(1, tester.nodeRepository.nodes().list(Node.State.failed).nodeType(NodeType.tenant).size());
        assertEquals(Node.State.failed, tester.nodeRepository.nodes().node(downNode).get().state());

        // Re-activate the node. It is still down, but should not be failed out until the grace period has passed again
        tester.nodeRepository.nodes().reactivate(downNode, Agent.system, getClass().getSimpleName());
        tester.clock.advance(Duration.ofMinutes(30));
        tester.allNodesMakeAConfigRequestExcept();
        tester.runMaintainers();
        assertEquals(0, tester.nodeRepository.nodes().list(Node.State.failed).nodeType(NodeType.tenant).size());

        tester.clock.advance(Duration.ofMinutes(45));
        tester.allNodesMakeAConfigRequestExcept();
        tester.runMaintainers();
        assertEquals(1, tester.nodeRepository.nodes().list(Node.State.failed).nodeType(NodeType.tenant).size());
        assertEquals(Node.State.failed, tester.nodeRepository.nodes().node(downNode).get().state());
    }

    @Test
    public void node_failing_can_allocate_spare() {
        var resources = new NodeResources(1, 20, 15, 1);
        Capacity capacity = Capacity.from(new ClusterResources(3, 1, resources), false, true);
        NodeFailTester tester = NodeFailTester.withOneUndeployedApplication(capacity);
        assertEquals("Test depends on this setting in NodeFailTester", 1, tester.nodeRepository.spareCount());
        tester.createAndActivateHosts(4, resources); // Just one extra - becomes designated spare
        ClusterSpec cluster = ClusterSpec.request(ClusterSpec.Type.container, ClusterSpec.Id.from("test")).vespaVersion("6.42").build();
        tester.activate(NodeFailTester.app1, cluster, capacity);

        String downHost = tester.nodeRepository.nodes().list(Node.State.active).owner(NodeFailTester.app1).first().get().hostname();
        tester.serviceMonitor.setHostDown(downHost);

        // nothing happens the first 45 minutes
        for (int minutes = 0; minutes < 45; minutes +=5 ) {
            tester.runMaintainers();
            tester.clock.advance(Duration.ofMinutes(5));
            tester.allNodesMakeAConfigRequestExcept();
            assertEquals(0, tester.deployer.redeployments);
            assertEquals(3, tester.nodeRepository.nodes().list(Node.State.active).nodeType(NodeType.tenant).size());
            assertEquals(0, tester.nodeRepository.nodes().list(Node.State.failed).nodeType(NodeType.tenant).size());
        }

        // downHost should now be failed and replaced
        tester.clock.advance(Duration.ofDays(1));
        tester.allNodesMakeAConfigRequestExcept();
        tester.runMaintainers();
        assertEquals(1, tester.deployer.redeployments);
        assertEquals(1, tester.nodeRepository.nodes().list(Node.State.failed).nodeType(NodeType.tenant).size());
        assertEquals(3, tester.nodeRepository.nodes().list(Node.State.active).nodeType(NodeType.tenant).size());
        assertEquals(downHost, tester.nodeRepository.nodes().list(Node.State.failed).nodeType(NodeType.tenant).asList().get(0).hostname());
    }

    @Test
    public void failing_ready_nodes() {
        NodeFailTester tester = NodeFailTester.withTwoApplications();

        // Add ready docker node
        NodeResources newNodeResources = new NodeResources(3, 4, 5, 1);
        tester.createReadyNodes(1, 16, newNodeResources);

        // For a day all nodes work so nothing happens
        for (int minutes = 0, interval = 30; minutes < 24 * 60; minutes += interval) {
            tester.clock.advance(Duration.ofMinutes(interval));
            tester.allNodesMakeAConfigRequestExcept();
            tester.runMaintainers();
            assertEquals( 5, tester.nodeRepository.nodes().list(Node.State.ready).nodeType(NodeType.tenant).size());
        }
        
        NodeList ready = tester.nodeRepository.nodes().list(Node.State.ready).nodeType(NodeType.tenant);

        // Two ready nodes and a ready docker node die, but only 2 of those are failed out
        tester.clock.advance(Duration.ofMinutes(180));
        Node dockerNode = ready.stream().filter(node -> node.resources().equals(newNodeResources)).findFirst().get();
        List<Node> otherNodes = ready.stream()
                               .filter(node -> ! node.resources().equals(newNodeResources))
                               .collect(Collectors.toList());
        tester.allNodesMakeAConfigRequestExcept(otherNodes.get(0), otherNodes.get(2), dockerNode);
        tester.runMaintainers();
        assertEquals( 3, tester.nodeRepository.nodes().list(Node.State.ready).nodeType(NodeType.tenant).size());
        assertEquals( 2, tester.nodeRepository.nodes().list(Node.State.failed).nodeType(NodeType.tenant).size());

        // Another ready node dies and the node that died earlier, are allowed to fail
        tester.clock.advance(Duration.ofDays(1));
        tester.allNodesMakeAConfigRequestExcept(otherNodes.get(0), otherNodes.get(2), dockerNode, otherNodes.get(3));
        tester.runMaintainers();
        assertEquals( 1, tester.nodeRepository.nodes().list(Node.State.ready).nodeType(NodeType.tenant).size());
        assertEquals(otherNodes.get(1), tester.nodeRepository.nodes().list(Node.State.ready).nodeType(NodeType.tenant).first().get());
        assertEquals( 4, tester.nodeRepository.nodes().list(Node.State.failed).nodeType(NodeType.tenant).size());
    }

    @Test
    public void docker_host_not_failed_without_config_requests() {
        NodeFailTester tester = NodeFailTester.withTwoApplications();

        // For a day all nodes work so nothing happens
        for (int minutes = 0, interval = 30; minutes < 24 * 60; minutes += interval) {
            tester.clock.advance(Duration.ofMinutes(interval));
            tester.allNodesMakeAConfigRequestExcept();
            tester.runMaintainers();
            assertEquals( 3, tester.nodeRepository.nodes().list(Node.State.ready).nodeType(NodeType.host).size());
            assertEquals( 0, tester.nodeRepository.nodes().list(Node.State.failed).nodeType(NodeType.host).size());
        }

        // Two ready nodes and a ready docker node die, but only 2 of those are failed out
        tester.clock.advance(Duration.ofMinutes(180));
        Node dockerHost = tester.nodeRepository.nodes().list(Node.State.ready).nodeType(NodeType.host).iterator().next();
        tester.allNodesMakeAConfigRequestExcept(dockerHost);
        tester.runMaintainers();
        assertEquals( 3, tester.nodeRepository.nodes().list(Node.State.ready).nodeType(NodeType.host).size());
        assertEquals( 0, tester.nodeRepository.nodes().list(Node.State.failed).nodeType(NodeType.host).size());
    }

    @Test
    public void failing_docker_hosts() {
        NodeFailTester tester = NodeFailTester.withTwoApplicationsOnDocker(7);

        // For a day all nodes work so nothing happens
        for (int minutes = 0, interval = 30; minutes < 24 * 60; minutes += interval) {
            tester.clock.advance(Duration.ofMinutes(interval));
            tester.allNodesMakeAConfigRequestExcept();
            tester.runMaintainers();
            assertEquals(8, tester.nodeRepository.nodes().list(Node.State.active).nodeType(NodeType.tenant).size());
            assertEquals(13, tester.nodeRepository.nodes().list(Node.State.ready).nodeType(NodeType.tenant).size());
            assertEquals(7, tester.nodeRepository.nodes().list(Node.State.active).nodeType(NodeType.host).size());
        }


        // Select the first host that has two active nodes
        String downHost1 = selectFirstParentHostWithNActiveNodesExcept(tester.nodeRepository, 2);
        tester.serviceMonitor.setHostDown(downHost1);

        // nothing happens the first 45 minutes
        for (int minutes = 0; minutes < 45; minutes += 5 ) {
            tester.runMaintainers();
            tester.clock.advance(Duration.ofMinutes(5));
            tester.allNodesMakeAConfigRequestExcept();
            assertEquals(0, tester.deployer.redeployments);
            assertEquals(8, tester.nodeRepository.nodes().list(Node.State.active).nodeType(NodeType.tenant).size());
            assertEquals(13, tester.nodeRepository.nodes().list(Node.State.ready).nodeType(NodeType.tenant).size());
            assertEquals(7, tester.nodeRepository.nodes().list(Node.State.active).nodeType(NodeType.host).size());
        }

        tester.clock.advance(Duration.ofMinutes(30));
        tester.allNodesMakeAConfigRequestExcept();
        tester.runMaintainers();

        assertEquals(2 + 1, tester.deployer.redeployments);
        assertEquals(3, tester.nodeRepository.nodes().list(Node.State.failed).nodeType(NodeType.tenant).size());
        assertEquals(8, tester.nodeRepository.nodes().list(Node.State.active).nodeType(NodeType.tenant).size());
        assertEquals(10, tester.nodeRepository.nodes().list(Node.State.ready).nodeType(NodeType.tenant).size());
        assertEquals(6, tester.nodeRepository.nodes().list(Node.State.active).nodeType(NodeType.host).size());


        // Now lets fail an active tenant node
        Node downTenant1 = tester.nodeRepository.nodes().list(Node.State.active).nodeType(NodeType.tenant).first().get();
        tester.serviceMonitor.setHostDown(downTenant1.hostname());

        // nothing happens during the entire day because of the failure throttling
        for (int minutes = 0, interval = 30; minutes < 24 * 60; minutes += interval) {
            tester.runMaintainers();
            tester.clock.advance(Duration.ofMinutes(interval));
            tester.allNodesMakeAConfigRequestExcept();
            assertEquals(3 + 1, tester.nodeRepository.nodes().list(Node.State.failed).size());
        }

        tester.clock.advance(Duration.ofMinutes(30));
        tester.allNodesMakeAConfigRequestExcept();
        tester.runMaintainers();

        assertEquals(3 + 1, tester.deployer.redeployments);
        assertEquals(4, tester.nodeRepository.nodes().list(Node.State.failed).nodeType(NodeType.tenant).size());
        assertEquals(8, tester.nodeRepository.nodes().list(Node.State.active).nodeType(NodeType.tenant).size());
        assertEquals(9, tester.nodeRepository.nodes().list(Node.State.ready).nodeType(NodeType.tenant).size());
        assertEquals(6, tester.nodeRepository.nodes().list(Node.State.active).nodeType(NodeType.host).size());


        // Lets fail another host, make sure it is not the same where downTenant1 is a child
        String downHost2 = selectFirstParentHostWithNActiveNodesExcept(tester.nodeRepository, 2, downTenant1.parentHostname().get());
        tester.serviceMonitor.setHostDown(downHost2);
        tester.runMaintainers();
        tester.clock.advance(Duration.ofMinutes(90));
        tester.allNodesMakeAConfigRequestExcept();
        tester.runMaintainers();

        assertEquals(5 + 2, tester.deployer.redeployments);
        assertEquals(7, tester.nodeRepository.nodes().list(Node.State.failed).nodeType(NodeType.tenant).size());
        assertEquals(8, tester.nodeRepository.nodes().list(Node.State.active).nodeType(NodeType.tenant).size());
        assertEquals(6, tester.nodeRepository.nodes().list(Node.State.ready).nodeType(NodeType.tenant).size());
        assertEquals(5, tester.nodeRepository.nodes().list(Node.State.active).nodeType(NodeType.host).size());

        // We have only 5 hosts remaining, so if we fail another host, we should only be able to redeploy app1's
        // node, while app2's should remain
        String downHost3 = selectFirstParentHostWithNActiveNodesExcept(tester.nodeRepository, 2, downTenant1.parentHostname().get());
        tester.serviceMonitor.setHostDown(downHost3);
        tester.runMaintainers();
        tester.clock.advance(Duration.ofDays(1));
        tester.allNodesMakeAConfigRequestExcept();
        tester.runMaintainers();

        assertEquals(6 + 2, tester.deployer.redeployments);
        assertEquals(9, tester.nodeRepository.nodes().list(Node.State.failed).nodeType(NodeType.tenant).size());
        assertEquals(8, tester.nodeRepository.nodes().list(Node.State.active).nodeType(NodeType.tenant).size());
        assertEquals(4, tester.nodeRepository.nodes().list(Node.State.ready).nodeType(NodeType.tenant).size());
        assertEquals(5, tester.nodeRepository.nodes().list(Node.State.active).nodeType(NodeType.host).size());
    }

    @Test
    public void failing_proxy_nodes() {
        test_infra_application_fail(NodeType.proxy, 10, 1);
    }

    @Test
    public void failing_config_hosts() {
        test_infra_application_fail(NodeType.confighost, 3, 0);
    }

    private void test_infra_application_fail(NodeType nodeType, int count, int expectedFailCount) {
        NodeFailTester tester = NodeFailTester.withInfraApplication(nodeType, count);

        // For a day all nodes work so nothing happens
        for (int minutes = 0; minutes < 24 * 60; minutes +=5 ) {
            tester.runMaintainers();
            tester.clock.advance(Duration.ofMinutes(5));
            tester.allNodesMakeAConfigRequestExcept();

            assertEquals(count, tester.nodeRepository.nodes().list(Node.State.active).nodeType(nodeType).size());
        }

        Set<String> downHosts = Set.of("host2", "host3");

        for (String downHost : downHosts)
            tester.serviceMonitor.setHostDown(downHost);
        // nothing happens the first 45 minutes
        for (int minutes = 0; minutes < 45; minutes +=5 ) {
            tester.runMaintainers();
            tester.clock.advance(Duration.ofMinutes(5));
            tester.allNodesMakeAConfigRequestExcept();
            assertEquals( 0, tester.deployer.redeployments);
            assertEquals(count, tester.nodeRepository.nodes().list(Node.State.active).nodeType(nodeType).size());
        }

        tester.clock.advance(Duration.ofMinutes(60));
        tester.runMaintainers();

        // one down host should now be failed, but not two as we are only allowed to fail one proxy
        assertEquals(expectedFailCount, tester.deployer.redeployments);
        assertEquals(count - expectedFailCount, tester.nodeRepository.nodes().list(Node.State.active).nodeType(nodeType).size());
        assertEquals(expectedFailCount, tester.nodeRepository.nodes().list(Node.State.failed).nodeType(nodeType).size());
        tester.nodeRepository.nodes().list(Node.State.failed).nodeType(nodeType)
                .forEach(node -> assertTrue(downHosts.contains(node.hostname())));

        // trying to fail again will still not fail the other down host
        tester.clock.advance(Duration.ofMinutes(60));
        tester.runMaintainers();
        assertEquals(count - expectedFailCount, tester.nodeRepository.nodes().list(Node.State.active).nodeType(nodeType).size());
    }

    @Test
    public void failing_divergent_ready_nodes() {
        NodeFailTester tester = NodeFailTester.withNoApplications();

        Node readyNode = tester.createReadyNodes(1).get(0);

        tester.runMaintainers();
        assertEquals(Node.State.ready, readyNode.state());

        tester.nodeRepository.nodes().write(readyNode.with(new Reports().withReport(badTotalMemorySizeReport)), () -> {});

        tester.runMaintainers();
        assertEquals(1, tester.nodeRepository.nodes().list(Node.State.failed).size());
    }

    @Test
    public void node_failing_throttle() {
        // Throttles based on a absolute number in small zone
        {
            // 50 regular tenant nodes, 10 hosts with each 3 tenant nodes, total 90 nodes
            NodeFailTester tester = NodeFailTester.withTwoApplicationsOnDocker(10);
            List<Node> readyNodes = tester.createReadyNodes(50, 30);
            NodeList hosts = tester.nodeRepository.nodes().list().nodeType(NodeType.host);
            List<Node> deadNodes = readyNodes.subList(0, 4);

            // 2 hours pass, 4 physical nodes die
            for (int minutes = 0, interval = 30; minutes < 2 * 60; minutes += interval) {
                tester.clock.advance(Duration.ofMinutes(interval));
                tester.allNodesMakeAConfigRequestExcept(deadNodes);
            }

            // 2 nodes are failed (the minimum amount that are always allowed to fail)
            tester.runMaintainers();
            assertEquals(2, tester.nodeRepository.nodes().list(Node.State.failed).size());
            assertEquals("Throttling is indicated by the metric", 1, tester.metric.values.get(NodeFailer.throttlingActiveMetric));
            assertEquals("Throttled node failures", 2, tester.metric.values.get(NodeFailer.throttledNodeFailuresMetric));

            // 6 more hours pass, no more nodes are failed
            for (int minutes = 0, interval = 30; minutes < 6 * 60; minutes += interval) {
                tester.clock.advance(Duration.ofMinutes(interval));
                tester.allNodesMakeAConfigRequestExcept(deadNodes);
            }
            tester.runMaintainers();
            assertEquals(2, tester.nodeRepository.nodes().list(Node.State.failed).size());
            assertEquals("Throttling is indicated by the metric", 1, tester.metric.values.get(NodeFailer.throttlingActiveMetric));
            assertEquals("Throttled node failures", 2, tester.metric.values.get(NodeFailer.throttledNodeFailuresMetric));

            // 18 more hours pass, the remaining dead nodes are allowed to fail
            for (int minutes = 0, interval = 30; minutes < 18 * 60; minutes += interval) {
                tester.clock.advance(Duration.ofMinutes(interval));
                tester.allNodesMakeAConfigRequestExcept(deadNodes);
            }
            tester.runMaintainers();
            assertEquals(4, tester.nodeRepository.nodes().list(Node.State.failed).size());

            // 24 more hours pass, nothing happens
            for (int minutes = 0, interval = 30; minutes < 24 * 60; minutes += interval) {
                tester.clock.advance(Duration.ofMinutes(interval));
                tester.allNodesMakeAConfigRequestExcept(deadNodes);
            }

            // 3 hosts fail. 2 of them and all of their children are allowed to fail
            List<Node> failedHosts = hosts.asList().subList(0, 3);
            failedHosts.forEach(host -> {
                tester.serviceMonitor.setHostDown(host.hostname());
                deadNodes.add(host);
            });
            tester.runMaintainers();
            tester.clock.advance(Duration.ofMinutes(61));
            tester.allNodesMakeAConfigRequestExcept(deadNodes);

            tester.runMaintainers();
            assertEquals(4 + /* already failed */
                         2 + /* hosts */
                         (2 * 3) /* containers per host */,
                         tester.nodeRepository.nodes().list(Node.State.failed).size());
            assertEquals("Throttling is indicated by the metric", 1, tester.metric.values.get(NodeFailer.throttlingActiveMetric));
            assertEquals("Throttled host failures", 1, tester.metric.values.get(NodeFailer.throttledHostFailuresMetric));

            // 24 more hours pass without any other nodes being failed out
            for (int minutes = 0, interval = 30; minutes <= 23 * 60; minutes += interval) {
                tester.clock.advance(Duration.ofMinutes(interval));
                tester.allNodesMakeAConfigRequestExcept(deadNodes);
            }
            tester.runMaintainers();
            assertEquals(12, tester.nodeRepository.nodes().list(Node.State.failed).size());
            assertEquals("Throttling is indicated by the metric", 1, tester.metric.values.get(NodeFailer.throttlingActiveMetric));
            assertEquals("Throttled host failures", 1, tester.metric.values.get(NodeFailer.throttledHostFailuresMetric));

            // The final host and its containers are failed out
            tester.clock.advance(Duration.ofMinutes(30));
            tester.runMaintainers();
            assertEquals(16, tester.nodeRepository.nodes().list(Node.State.failed).size());
            assertEquals("Throttling is not indicated by the metric, as no throttled attempt is made", 0, tester.metric.values.get(NodeFailer.throttlingActiveMetric));
            assertEquals("No throttled node failures", 0, tester.metric.values.get(NodeFailer.throttledNodeFailuresMetric));

            // Nothing else to fail
            tester.clock.advance(Duration.ofHours(25));
            tester.allNodesMakeAConfigRequestExcept(deadNodes);
            tester.runMaintainers();
            assertEquals(16, tester.nodeRepository.nodes().list(Node.State.failed).size());
            assertEquals("Throttling is not indicated by the metric", 0, tester.metric.values.get(NodeFailer.throttlingActiveMetric));
            assertEquals("No throttled node failures", 0, tester.metric.values.get(NodeFailer.throttledNodeFailuresMetric));
        }

        // Throttles based on percentage in large zone
        {
            NodeFailTester tester = NodeFailTester.withNoApplications();
            List<Node> readyNodes = tester.createReadyNodes(500);
            List<Node> deadNodes = readyNodes.subList(0, 15);

            // 2 hours pass, 15 nodes (3%) die
            for (int minutes = 0, interval = 30; minutes < 2 * 60; minutes += interval) {
                tester.clock.advance(Duration.ofMinutes(interval));
                tester.allNodesMakeAConfigRequestExcept(deadNodes);
            }
            tester.runMaintainers();
            // 2% are allowed to fail
            assertEquals(10, tester.nodeRepository.nodes().list(Node.State.failed).size());
            assertEquals("Throttling is indicated by the metric.", 1, tester.metric.values.get(NodeFailer.throttlingActiveMetric));
            assertEquals("Throttled node failures", 5, tester.metric.values.get(NodeFailer.throttledNodeFailuresMetric));

            // 6 more hours pass, no more nodes are failed
            for (int minutes = 0, interval = 30; minutes < 6 * 60; minutes += interval) {
                tester.clock.advance(Duration.ofMinutes(interval));
                tester.allNodesMakeAConfigRequestExcept(deadNodes);
            }
            tester.runMaintainers();
            assertEquals(10, tester.nodeRepository.nodes().list(Node.State.failed).size());
            assertEquals("Throttling is indicated by the metric.", 1, tester.metric.values.get(NodeFailer.throttlingActiveMetric));
            assertEquals("Throttled node failures", 5, tester.metric.values.get(NodeFailer.throttledNodeFailuresMetric));

            // 18 more hours pass, 24 hours since the first 10 nodes were failed. The remaining 5 are failed
            for (int minutes = 0, interval = 30; minutes < 18 * 60; minutes += interval) {
                tester.clock.advance(Duration.ofMinutes(interval));
                tester.allNodesMakeAConfigRequestExcept(deadNodes);
            }
            tester.runMaintainers();
            assertEquals(15, tester.nodeRepository.nodes().list(Node.State.failed).size());
            assertEquals("Throttling is not indicated by the metric, as no throttled attempt is made.", 0, tester.metric.values.get(NodeFailer.throttlingActiveMetric));
            assertEquals("No throttled node failures", 0, tester.metric.values.get(NodeFailer.throttledNodeFailuresMetric));
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

        return NodeHealthTracker.allDown(services);
    }

    /**
     * Selects the first parent host that:
     *  - has exactly n nodes in state 'active'
     *  - is not present in the 'except' array
     */
    private static String selectFirstParentHostWithNActiveNodesExcept(NodeRepository nodeRepository, int n, String... except) {
        Set<String> exceptSet = Arrays.stream(except).collect(Collectors.toSet());
        return nodeRepository.nodes().list(Node.State.active).nodeType(NodeType.tenant).stream()
                .collect(Collectors.groupingBy(Node::parentHostname))
                .entrySet().stream()
                .filter(entry -> entry.getValue().size() == n)
                .map(Map.Entry::getKey)
                .flatMap(parentHost -> Stream.of(parentHost.get()))
                .filter(node -> ! exceptSet.contains(node))
                .findFirst().get();
    }

}
