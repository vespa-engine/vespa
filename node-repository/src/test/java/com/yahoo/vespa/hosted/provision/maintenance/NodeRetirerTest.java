package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.maintenance.retire.RetirementPolicy;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.Allocation;
import com.yahoo.vespa.hosted.provision.node.History;
import com.yahoo.vespa.hosted.provision.node.Status;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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

        @Test
        public void testRetireUnallocatedNodes() {
            NodeRetirerTester tester = new NodeRetirerTester(NodeRetirerTester.makeFlavors(5));
            RetirementPolicy policy = node -> node.ipAddresses().equals(Collections.singleton("::1"));
            NodeRetirer retirer = new NodeRetirer(tester.nodeRepository, NodeRetirerTester.zone, Duration.ofDays(1), new JobControl(tester.nodeRepository.database()), policy);

            tester.createReadyNodesByFlavor(5, 3, 77, 47);
            tester.deployApp("vespa", "calendar", 0, 3);
            tester.deployApp("vespa", "notes", 2, 12);
            tester.deployApp("sports", "results", 2, 7);
            tester.deployApp("search", "images", 3, 6);

            Map<Flavor, Long> expected = tester.expectedCountsByFlavor(1, 3, 56, 40);
            Map<Flavor, Long> actual = retirer.getNumberSpareReadyNodesByFlavor(tester.nodeRepository.getNodes());
            assertEquals(expected, actual);

            // Not all nodes that we wanted to retire could be retired now (Not enough spare nodes)
            assertFalse(retirer.retireUnallocated());
            Map<Flavor, Long> parkedCountsByFlavor = tester.nodeRepository.getNodes(Node.State.parked).stream()
                    .collect(Collectors.groupingBy(Node::flavor, Collectors.counting()));
            assertEquals(expected, parkedCountsByFlavor);

            expected = tester.expectedCountsByFlavor(0, -1, 0, 0);
            actual = retirer.getNumberSpareReadyNodesByFlavor(tester.nodeRepository.getNodes());
            assertEquals(expected, actual);

            // Lets change parked nodes IP address and set it back to ready
            tester.nodeRepository.getNodes(Node.State.parked)
                    .forEach(node -> {
                        Agent parkingAgent = node.history().event(History.Event.Type.parked).orElseThrow(RuntimeException::new).agent();
                        assertEquals(Agent.NodeRetirer, parkingAgent);
                        tester.nodeRepository.write(node.withIpAddresses(Collections.singleton("::2")));
                        tester.nodeRepository.setDirty(node.hostname());
                        tester.nodeRepository.setReady(node.hostname());
                    });

            expected = tester.expectedCountsByFlavor(1, 3, 56, 40);
            actual = retirer.getNumberSpareReadyNodesByFlavor(tester.nodeRepository.getNodes());
            assertEquals(expected, actual);

            // The remaining nodes we wanted to retire has been retired
            assertTrue(retirer.retireUnallocated());
            parkedCountsByFlavor = tester.nodeRepository.getNodes(Node.State.parked).stream()
                    .collect(Collectors.groupingBy(Node::flavor, Collectors.counting()));
            expected = tester.expectedCountsByFlavor(1, -1, 2, 1);
            assertEquals(expected, parkedCountsByFlavor);
        }
    }


    /**
     * For testing methods that require minimal node repository and flavor setup
     */
    public static class HelperMethodsTester {
        private final List<Flavor> flavors = NodeRetirerTester.makeFlavors(5).getFlavors();
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
    }
}
