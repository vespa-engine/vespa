// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.maintenance.retire.RetirementPolicy;
import com.yahoo.vespa.hosted.provision.node.Agent;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author freva
 */
public class NodeRetirerTest {

    private NodeRetirerTester tester;
    private NodeRetirer retirer;
    private final RetirementPolicy policy = mock(RetirementPolicy.class);

    @Before
    public void setup() {
        doAnswer(invoke -> {
            boolean shouldRetire = ((Node) invoke.getArguments()[0]).ipAddresses().equals(Collections.singleton("::1"));
            return shouldRetire ? Optional.of("Some reason") : Optional.empty();
        }).when(policy).shouldRetire(any(Node.class));
        when(policy.isActive()).thenReturn(true);

        NodeFlavors nodeFlavors = NodeRetirerTester.makeFlavors(5);
        tester = new NodeRetirerTester(nodeFlavors);
        retirer = spy(tester.makeNodeRetirer(policy));

        tester.createReadyNodesByFlavor(21, 42, 27, 15, 8);
        tester.deployApp("vespa",  "calendar",  new int[]{3},       new int[]{7});
        tester.deployApp("vespa",  "notes",     new int[]{0},       new int[]{3});
        tester.deployApp("sports", "results",   new int[]{0},       new int[]{6});
        tester.deployApp("search", "images",    new int[]{3},       new int[]{4});
        tester.deployApp("search", "videos",    new int[]{2},       new int[]{2});
        tester.deployApp("tester", "my-app",    new int[]{1, 2},    new int[]{4, 6});
    }

    @Test
    public void testRetireUnallocated() {
        tester.assertCountsForStateByFlavor(Node.State.ready, 12, 38, 19, 4, 8);
        tester.setNumberAllowedUnallocatedRetirementsPerFlavor(6, 30, 15, 2, 4);
        assertFalse(retirer.retireUnallocated());
        tester.assertCountsForStateByFlavor(Node.State.parked, 6, 30, 15, 2, 4);

        tester.assertCountsForStateByFlavor(Node.State.ready, 6, 8, 4, 2, 4);
        tester.setNumberAllowedUnallocatedRetirementsPerFlavor(10, 20, 5, 5, 4);
        assertTrue(retirer.retireUnallocated());
        tester.assertCountsForStateByFlavor(Node.State.parked, 12, 38, 19, 4, 8);

        tester.nodeRepository.getNodes().forEach(node ->
                assertEquals(node.status().wantToDeprovision(), node.state() == Node.State.parked));
    }

    @Test
    public void testRetireAllocated() {
        // Update IP addresses on ready nodes so that when they are deployed to, we wont retire them
        tester.nodeRepository.getNodes(Node.State.ready)
                .forEach(node -> tester.nodeRepository.write(node.withIpAddresses(Collections.singleton("::2"))));

        tester.assertCountsForStateByFlavor(Node.State.active, 9, 4, 8, 11, -1);

        tester.setNumberAllowedAllocatedRetirementsPerFlavor(3, 2, 4, 2);
        retirer.retireAllocated();
        tester.assertParkedCountsByApplication(-1, -1, -1, -1, -1, -1); // Nodes should be in retired, but not yet parked
        tester.assertRetiringCountsByApplication(1, 1, 1, 1, 1, 2);

        // Until the nodes we set to retire are fully retired and moved to parked, we should not attempt to retire any other nodes
        retirer.retireAllocated();
        retirer.retireAllocated();
        tester.assertRetiringCountsByApplication(1, 1, 1, 1, 1, 2);

        tester.iterateMaintainers();
        tester.assertParkedCountsByApplication(1, 1, 1, 1, 1, 2);

        // We can retire 1 more of flavor-0, 1 more of flavor-1, 2 more of flavor-2:
        //  app 6 has the most nodes, so it gets to retire flavor-1 and flavor-2
        //  app 3 is the largest that is on flavor-0, so it gets the last node
        //  app 5 is gets the last node with flavor-2
        retirer.retireAllocated();
        tester.iterateMaintainers();
        tester.assertParkedCountsByApplication(1, 1, 2, 1, 2, 4);

        // No more retirements are possible
        retirer.retireAllocated();
        tester.iterateMaintainers();
        tester.assertParkedCountsByApplication(1, 1, 2, 1, 2, 4);

        tester.nodeRepository.getNodes().forEach(node ->
                assertEquals(node.status().wantToDeprovision(), node.state() == Node.State.parked));
    }

    @Test
    public void testGetActiveApplicationIds() {
        List<String> expectedOrder = Arrays.asList(
                "tester.my-app", "vespa.calendar", "sports.results", "search.images", "vespa.notes", "search.videos");
        List<String> actualOrder = retirer.getActiveApplicationIds(tester.nodeRepository.getNodes()).stream()
                .map(applicationId -> applicationId.toShortString().replace(":default", ""))
                .collect(Collectors.toList());
        assertEquals(expectedOrder, actualOrder);
    }

    @Test
    public void testGetRetireableNodesForApplication() {
        ApplicationId app = new ApplicationId.Builder().tenant("vespa").applicationName("calendar").build();

        List<Node> nodes = tester.nodeRepository.getNodes(app);
        Set<String> actual = retirer.filterRetireableNodes(nodes).stream().map(Node::hostname).collect(Collectors.toSet());
        Set<String> expected = nodes.stream().map(Node::hostname).collect(Collectors.toSet());
        assertEquals(expected, actual);

        Node nodeWantToRetire = tester.nodeRepository.getNode("host3.test.yahoo.com").orElseThrow(RuntimeException::new);
        tester.nodeRepository.write(nodeWantToRetire.with(nodeWantToRetire.status().withWantToRetire(true)));
        Node nodeToFail = tester.nodeRepository.getNode("host5.test.yahoo.com").orElseThrow(RuntimeException::new);
        tester.nodeRepository.fail(nodeToFail.hostname(), Agent.system, "Failed for unit testing");
        Node nodeToUpdate = tester.nodeRepository.getNode("host8.test.yahoo.com").orElseThrow(RuntimeException::new);
        tester.nodeRepository.write(nodeToUpdate.withIpAddresses(Collections.singleton("::2")));

        nodes = tester.nodeRepository.getNodes(app);
        Set<String> excluded = Stream.of(nodeWantToRetire, nodeToFail, nodeToUpdate).map(Node::hostname).collect(Collectors.toSet());
        Set<String> actualAfterUpdates = retirer.filterRetireableNodes(nodes).stream().map(Node::hostname).collect(Collectors.toSet());
        Set<String> expectedAfterUpdates = nodes.stream().map(Node::hostname).filter(node -> !excluded.contains(node)).collect(Collectors.toSet());
        assertEquals(expectedAfterUpdates, actualAfterUpdates);
    }

    @Test
    public void testGetNumberNodesAllowToRetireForCluster() {
        ApplicationId app = new ApplicationId.Builder().tenant("vespa").applicationName("calendar").build();
        long actualAllActive = retirer.getNumberNodesAllowToRetireForCluster(tester.nodeRepository.getNodes(app), 2);
        assertEquals(2, actualAllActive);

        // Lets put 3 random nodes in wantToRetire
        List<Node> nodesToRetire = tester.nodeRepository.getNodes(app).stream().limit(3).collect(Collectors.toList());
        nodesToRetire.forEach(node -> tester.nodeRepository.write(node.with(node.status().withWantToRetire(true))));
        long actualOneWantToRetire = retirer.getNumberNodesAllowToRetireForCluster(tester.nodeRepository.getNodes(app), 2);
        assertEquals(0, actualOneWantToRetire);

        // Now 2 of those finish retiring and go to parked
        nodesToRetire.stream().limit(2).forEach(node ->
                tester.nodeRepository.park(node.hostname(), false, Agent.system, "Parked for unit testing"));
        long actualOneRetired = retirer.getNumberNodesAllowToRetireForCluster(tester.nodeRepository.getNodes(app), 2);
        assertEquals(1, actualOneRetired);
    }

    @Test
    public void inactivePolicyDoesNothingTest() {
        when(policy.isActive()).thenReturn(false);
        retirer.maintain();

        verify(retirer, never()).retireUnallocated();
        verify(retirer, never()).retireAllocated();
    }

}
