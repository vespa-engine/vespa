package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.maintenance.retire.RetirementPolicy;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.Allocation;
import com.yahoo.vespa.hosted.provision.node.History;
import com.yahoo.vespa.hosted.provision.node.Status;
import com.yahoo.vespa.hosted.provision.provisioning.FlavorClusters;
import com.yahoo.vespa.hosted.provision.provisioning.FlavorClustersTest;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author freva
 */
@RunWith(Enclosed.class)
public class NodeRetirerTest {

    public static class FullNodeRepositoryTester {
        private final RetirementPolicy policy = node -> node.ipAddresses().equals(Collections.singleton("::1"));
        private NodeRetirerTester tester;
        private NodeRetirer retirer;

        @Test
        public void testRetireUnallocatedNodes() {
            NodeFlavors nodeFlavors = FlavorClustersTest.makeFlavors(6);
            FlavorClusters flavorClusters = new FlavorClusters(nodeFlavors.getFlavors());
            tester = new NodeRetirerTester(nodeFlavors);
            retirer = new NodeRetirer(tester.nodeRepository, NodeRetirerTester.zone, flavorClusters, Duration.ofDays(1), new JobControl(tester.nodeRepository.database()), policy);

            tester.createReadyNodesByFlavor(5, 3, 77, 47);
            tester.deployApp("vespa", "calendar", 0, 3);
            tester.deployApp("vespa", "notes", 2, 12);
            tester.deployApp("sports", "results", 2, 7);
            tester.deployApp("search", "images", 3, 6);

            // Not all nodes that we wanted to retire could be retired now (Not enough spare nodes)
            assertSpareCountsByFlavor(1, 3, 56, 40);
            assertFalse(retirer.retireUnallocated());
            assertParkedCountsByFlavor(1, 3, 56, 40);

            assertSpareCountsByFlavor(0, -1, 0, 0);
            // Lets change parked nodes IP address and set it back to ready
            tester.nodeRepository.getNodes(Node.State.parked)
                    .forEach(node -> {
                        Agent parkingAgent = node.history().event(History.Event.Type.parked).orElseThrow(RuntimeException::new).agent();
                        assertEquals(Agent.NodeRetirer, parkingAgent);
                        assertTrue("Nodes parked by NodeRetirer should also have wantToDeprovision flag set", node.status().wantToDeprovision());
                        tester.nodeRepository.write(node.withIpAddresses(Collections.singleton("::2")));
                        tester.nodeRepository.setDirty(node.hostname());
                        tester.nodeRepository.setReady(node.hostname());
                    });

            // The remaining nodes we wanted to retire has been retired
            assertSpareCountsByFlavor(1, 3, 56, 40);
            assertTrue(retirer.retireUnallocated());
            assertParkedCountsByFlavor(1, -1, 2, 1);
        }

        /* Creates flavors where 'replaces' graph and node counts that looks like this:
         *           Total nodes: 40  1
         *                            |                     4   Total nodes: 7
         *          Total nodes: 20   |                     |   search.images nodes: 4
         *    vespa.notes nodes: 3    0                     |
         * sports.results nodes: 6   / \                    5   Total nodes: 5
         *                          /   \                       search.videos nodes: 2
         *       Total nodes: 25   2     3  Total nodes: 14
         */
        @Test
        public void testRetireAllocatedNodes() throws InterruptedException {
            NodeFlavors nodeFlavors = FlavorClustersTest.makeFlavors(
                    Collections.singletonList(1),   // 0 -> {1}
                    Collections.emptyList(),        // 1 -> {}
                    Collections.singletonList(0),   // 2 -> {0}
                    Collections.singletonList(0),   // 3 -> {0}
                    Collections.emptyList(),        // 4 -> {}
                    Collections.singletonList(4));  // 5 -> {4}
            FlavorClusters flavorClusters = new FlavorClusters(nodeFlavors.getFlavors());
            tester = new NodeRetirerTester(nodeFlavors);

            tester.createReadyNodesByFlavor(20, 40, 25, 14, 7, 5);
            tester.deployApp("vespa", "calendar", 3, 7);
            tester.deployApp("vespa", "notes", 0, 3);
            tester.deployApp("sports", "results", 0, 6);
            tester.deployApp("search", "images", 4, 4);
            tester.deployApp("search", "videos", 5, 2);

            JobControl jobControl = new JobControl(tester.nodeRepository.database());
            retirer = new NodeRetirer(tester.nodeRepository, NodeRetirerTester.zone, flavorClusters, Duration.ofDays(1), jobControl, policy);
            // Update IP addresses on ready nodes so that when they are deployed to, we wont retire them
            tester.nodeRepository.getNodes(Node.State.ready)
                    .forEach(node -> tester.nodeRepository.write(node.withIpAddresses(Collections.singleton("::2"))));

            assertSpareCountsByFlavor(10, 40, 25, 6, 2, 2);


            retireThenAssertSpareAndParkedCounts(new long[]{8, 40, 25, 5, 1, 1}, new long[]{1, 1, 1, 1, 1});

            // At this point we only have 1 spare node for flavors 4 & 5, 5 also replaces 4, which means that we can
            // only replace 1 of either flavor-4 or flavor-5.
            // search.videos (5th app) wont be replaced because search.images will get the last spare node in
            // flavor-4, flavor-5 cluster because it has more active nodes
            retireThenAssertSpareAndParkedCounts(new long[]{6, 40, 25, 4, 0, 1}, new long[]{2, 2, 2, 2, 1});

            // After redeploying search.images, it ended up on a flavor-4 node, so we still have a flavor-5 spare,
            // but we still wont be able to retire any nodes for search.videos as min spare for its flavor cluster is 0
            retireThenAssertSpareAndParkedCounts(new long[]{4, 40, 25, 3, 0, 1}, new long[]{3, 3, 3, 2, 1});

            // All 3 of vespa.notes old nodes have been retired, so its parked count should stay the same
            retireThenAssertSpareAndParkedCounts(new long[]{3, 40, 25, 2, 0, 1}, new long[]{4, 3, 4, 2, 1});

            // Only vespa.calendar and sports.results remain, but their flavors (3 and 0 respectively) are in the same
            // flavor cluster, because the min count for this cluster is 1, we can only retire one of them
            retireThenAssertSpareAndParkedCounts(new long[]{2, 40, 25, 1, 0, 1}, new long[]{5, 3, 5, 2, 1});

            // min flavor count for both flavor clusters is now 0, so no further change is expected
            retireThenAssertSpareAndParkedCounts(new long[]{2, 40, 25, 0, 0, 1}, new long[]{6, 3, 5, 2, 1});
            retireThenAssertSpareAndParkedCounts(new long[]{2, 40, 25, 0, 0, 1}, new long[]{6, 3, 5, 2, 1});

            tester.nodeRepository.getNodes(Node.State.parked)
                    .forEach(node -> assertTrue("Nodes parked by NodeRetirer should also have wantToDeprovision flag set",
                            node.status().wantToDeprovision()));
        }

        @Test
        public void testGetActiveApplicationIds() {
            NodeFlavors nodeFlavors = FlavorClustersTest.makeFlavors(1);
            FlavorClusters flavorClusters = new FlavorClusters(nodeFlavors.getFlavors());
            tester = new NodeRetirerTester(nodeFlavors);
            retirer = new NodeRetirer(tester.nodeRepository, NodeRetirerTester.zone, flavorClusters, Duration.ofDays(1), new JobControl(tester.nodeRepository.database()), policy);

            tester.createReadyNodesByFlavor(50);
            ApplicationId a1 = tester.deployApp("vespa", "calendar", 0, 10);
            ApplicationId a2 = tester.deployApp("vespa", "notes", 0, 12);
            ApplicationId a3 = tester.deployApp("sports", "results", 0, 7);
            ApplicationId a4 = tester.deployApp("search", "images", 0, 6);

            List<ApplicationId> expectedOrder = Arrays.asList(a2, a1, a3, a4);
            List<ApplicationId> actualOrder = retirer.getActiveApplicationIds(tester.nodeRepository.getNodes());
            assertEquals(expectedOrder, actualOrder);
        }

        @Test
        public void testGetRetireableNodesForApplication() {
            NodeFlavors nodeFlavors = FlavorClustersTest.makeFlavors(1);
            FlavorClusters flavorClusters = new FlavorClusters(nodeFlavors.getFlavors());
            tester = new NodeRetirerTester(nodeFlavors);
            retirer = new NodeRetirer(tester.nodeRepository, NodeRetirerTester.zone, flavorClusters, Duration.ofDays(1), new JobControl(tester.nodeRepository.database()), policy);

            tester.createReadyNodesByFlavor(10);
            tester.deployApp("vespa", "calendar", 0, 10);

            List<Node> nodes = tester.nodeRepository.getNodes();
            Set<String> actual = retirer.getRetireableNodesForApplication(nodes).stream().map(Node::hostname).collect(Collectors.toSet());
            Set<String> expected = nodes.stream().map(Node::hostname).collect(Collectors.toSet());
            assertEquals(expected, actual);

            Node nodeWantToRetire = tester.nodeRepository.getNode("host3.test.yahoo.com").orElseThrow(RuntimeException::new);
            tester.nodeRepository.write(nodeWantToRetire.with(nodeWantToRetire.status().withWantToRetire(true)));
            Node nodeToFail = tester.nodeRepository.getNode("host5.test.yahoo.com").orElseThrow(RuntimeException::new);
            tester.nodeRepository.fail(nodeToFail.hostname(), Agent.system, "Failed for unit testing");
            Node nodeToUpdate = tester.nodeRepository.getNode("host8.test.yahoo.com").orElseThrow(RuntimeException::new);
            tester.nodeRepository.write(nodeToUpdate.withIpAddresses(Collections.singleton("::2")));

            nodes = tester.nodeRepository.getNodes();
            Set<String> excluded = Stream.of(nodeWantToRetire, nodeToFail, nodeToUpdate).map(Node::hostname).collect(Collectors.toSet());
            Set<String> actualAfterUpdates = retirer.getRetireableNodesForApplication(nodes).stream().map(Node::hostname).collect(Collectors.toSet());
            Set<String> expectedAfterUpdates = nodes.stream().map(Node::hostname).filter(node -> !excluded.contains(node)).collect(Collectors.toSet());
            assertEquals(expectedAfterUpdates, actualAfterUpdates);
        }

        @Test
        public void testGetNumberNodesAllowToRetireForApplication() {
            NodeFlavors nodeFlavors = FlavorClustersTest.makeFlavors(1);
            FlavorClusters flavorClusters = new FlavorClusters(nodeFlavors.getFlavors());
            tester = new NodeRetirerTester(nodeFlavors);
            retirer = new NodeRetirer(tester.nodeRepository, NodeRetirerTester.zone, flavorClusters, Duration.ofDays(1), new JobControl(tester.nodeRepository.database()), policy);

            tester.createReadyNodesByFlavor(10);
            tester.deployApp("vespa", "calendar", 0, 10);

            long actualAllActive = retirer.getNumberNodesAllowToRetireForApplication(tester.nodeRepository.getNodes(), 2);
            assertEquals(2, actualAllActive);

            // Lets put 3 random nodes in wantToRetire
            List<Node> nodesToRetire = tester.nodeRepository.getNodes().stream().limit(3).collect(Collectors.toList());
            nodesToRetire.forEach(node -> tester.nodeRepository.write(node.with(node.status().withWantToRetire(true))));
            long actualOneWantToRetire = retirer.getNumberNodesAllowToRetireForApplication(tester.nodeRepository.getNodes(), 2);
            assertEquals(0, actualOneWantToRetire);

            // Now 2 of those finish retiring and go to parked
            nodesToRetire.stream().limit(2).forEach(node ->
                    tester.nodeRepository.park(node.hostname(), Agent.system, "Parked for unit testing"));
            long actualOneRetired = retirer.getNumberNodesAllowToRetireForApplication(tester.nodeRepository.getNodes(), 2);
            assertEquals(1, actualOneRetired);
        }

        private void assertSpareCountsByFlavor(long... nums) {
            Map<Flavor, Long> expectedSpareCountsByFlavor = tester.expectedCountsByFlavor(nums);
            Map<Flavor, Long> actualSpaceCountsByFlavor = retirer.getNumberSpareReadyNodesByFlavor(tester.nodeRepository.getNodes());
            assertEquals(expectedSpareCountsByFlavor, actualSpaceCountsByFlavor);
        }

        private void assertParkedCountsByFlavor(long... nums) {
            Map<Flavor, Long> expected = tester.expectedCountsByFlavor(nums);
            Map<Flavor, Long> actual = tester.nodeRepository.getNodes(Node.State.parked).stream()
                    .collect(Collectors.groupingBy(Node::flavor, Collectors.counting()));
            assertEquals(expected, actual);
        }

        private void assertParkedCountsByApplication(long... nums) {
            Map<ApplicationId, Long> expected = tester.expectedCountsByApplication(nums);
            Map<ApplicationId, Long> actual = tester.nodeRepository.getNodes(Node.State.parked).stream()
                    .collect(Collectors.groupingBy(node -> node.allocation().get().owner(), Collectors.counting()));
            assertEquals(expected, actual);
        }

        private void retireThenAssertSpareAndParkedCounts(long[] spareCountsByFlavor, long[] parkedCountsByApp) {
            retirer.retireAllocated();
            tester.iterateMaintainers();
            assertSpareCountsByFlavor(spareCountsByFlavor);
            assertParkedCountsByApplication(parkedCountsByApp);
        }
    }

    /**
     * For testing methods that require minimal node repository and flavor setup
     */
    public static class HelperMethodsTester {
        private final List<Flavor> flavors = FlavorClustersTest.makeFlavors(5).getFlavors();
        private final List<Node> nodes = new ArrayList<>();
        private final NodeRetirer retirer = mock(NodeRetirer.class);

        @Test
        public void testGetNumberSpareNodesWithNoActiveNodes() {
            addNodesByFlavor(Node.State.ready, 5, 3, 77);

            Map<Flavor, Long> expected = expectedCountsByFlavor(5, 3, 77);
            Map<Flavor, Long> actual = retirer.getNumberSpareReadyNodesByFlavor(nodes);
            assertEquals(expected, actual);
        }

        @Test
        public void testGetNumberSpareNodesWithActiveNodes() {
            addNodesByFlavor(Node.State.ready, 5, 3, 77, 47);
            addNodesByFlavor(Node.State.active, 0, 10, 2, 230, 137);

            Map<Flavor, Long> expected = expectedCountsByFlavor(5, 2, 76, 24);
            Map<Flavor, Long> actual = retirer.getNumberSpareReadyNodesByFlavor(nodes);
            assertEquals(expected, actual);
        }

        @Before
        public void setup() {
            when(retirer.getNumSpareNodes(any(Long.class), any(Long.class))).thenCallRealMethod();
            when(retirer.getNumberSpareReadyNodesByFlavor(any())).thenCallRealMethod();
        }

        private Map<Flavor, Long> expectedCountsByFlavor(int... nums) {
            Map<Flavor, Long> countsByFlavor = new HashMap<>();
            for (int i = 0; i < nums.length; i++) {
                Flavor flavor = flavors.get(i);
                countsByFlavor.put(flavor, (long) nums[i]);
            }
            return countsByFlavor;
        }

        private void addNodesByFlavor(Node.State state, int... nums) {
            for (int i = 0; i < nums.length; i++) {
                Flavor flavor = flavors.get(i);
                for (int j = 0; j < nums[i]; j++) {
                    int id = nodes.size();
                    Node node = createNode("host-" + id + ".yahoo.com", flavor, state, Optional.empty(), Collections.singleton("::1"));
                    nodes.add(node);
                }
            }
        }

        private Node createNode(String hostname, Flavor flavor, Node.State state, Optional<Allocation> allocation, Set<String> ipAddresses) {
            return new Node(
                    UUID.randomUUID().toString(),
                    ipAddresses,
                    Collections.emptySet(),
                    hostname,
                    Optional.empty(),
                    flavor,
                    Status.initial(),
                    state,
                    allocation,
                    History.empty(),
                    NodeType.tenant);
        }
    }

    /**
     * For testing methods that require no internal state and independent of other methods
     */
    public static class IndependentMethodTester {
        private final NodeRetirer retirer = mock(NodeRetirer.class);

        @Test
        public void testGetNumSpareNodes() {
            when(retirer.getNumSpareNodes(any(Long.class), any(Long.class))).thenCallRealMethod();

            assertEquals(retirer.getNumSpareNodes(0, 0), 0L);
            assertEquals(retirer.getNumSpareNodes(0, 1), 1L);
            assertEquals(retirer.getNumSpareNodes(0, 100), 100L);

            assertEquals(retirer.getNumSpareNodes(1, 0), 0L);
            assertEquals(retirer.getNumSpareNodes(1, 1), 0L);
            assertEquals(retirer.getNumSpareNodes(1, 2), 1L);
            assertEquals(retirer.getNumSpareNodes(43, 23), 18L);
        }

        @Test
        public void testGetMinAmongstKeys() {
            when(retirer.getMinAmongstKeys(any(), any())).thenCallRealMethod();

            Map<String, Integer> map = createMapWith(4, 10, 43, 23, 7, 53, 2, 12, 42, 10);
            Set<String> keys = createKeySetWith(1, 3, 4, 5, 7, 9);
            assertEquals("4", retirer.getMinAmongstKeys(map, keys)); // Smallest value is 7, which is index 4
        }

        private Map<String, Integer> createMapWith(int... values) {
            return IntStream.range(0, values.length).boxed().collect(Collectors.toMap(String::valueOf, i -> values[i]));
        }

        private Set<String> createKeySetWith(int... keys) {
            return Arrays.stream(keys).boxed().map(String::valueOf).collect(Collectors.toSet());
        }
    }
}
