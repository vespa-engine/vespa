package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.maintenance.retire.RetirementPolicy;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.Allocation;
import com.yahoo.vespa.hosted.provision.node.Generation;
import com.yahoo.vespa.hosted.provision.node.History;
import com.yahoo.vespa.hosted.provision.node.Status;
import com.yahoo.vespa.hosted.provision.testutils.FlavorConfigBuilder;
import org.junit.Before;
import org.junit.Test;

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
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author freva
 */
public class NodeRetirerTest {
    private final List<Flavor> flavors = makeFlavors(5);
    private final Map<String, Node> nodesByHostname = new HashMap<>();
    private final NodeRepository nodeRepository = mock(NodeRepository.class);

    @Test
    public void testRetireUnallocatedNodes() {
        addNodesByFlavor(Node.State.ready, 5, 3, 77, 47);
        deployApp("vespa", "calendar", 0, 3);
        deployApp("vespa", "notes", 2, 12);
        deployApp("sports", "results", 2, 7);
        deployApp("search", "images", 3, 6);

        RetirementPolicy policy = node -> node.ipAddresses().equals(Collections.singleton("::1"));
        NodeRetirer retirer = new NodeRetirer(nodeRepository, Duration.ofDays(1), policy);
        Map<Flavor, Long> expected = expectedCountsByFlavor(0, 1, 56, 39);
        Map<Flavor, Long> actual = retirer.getNumberSpareReadyNodesByFlavor(nodeRepository.getNodes());
        assertEquals(expected, actual);

        retirer.retireUnallocated();
        Map<Flavor, Long> parkedCountsByFlavor = nodesByHostname.values().stream()
                .filter(node -> node.state() == Node.State.parked)
                .collect(Collectors.groupingBy(Node::flavor, Collectors.counting()));
        expected.remove(flavors.get(0)); // Flavor-0 has 0 ready nodes, so just remove it to easily compare maps
        assertEquals(expected, parkedCountsByFlavor);

        expected = expectedCountsByFlavor(0, 0, 0, 0);
        actual = retirer.getNumberSpareReadyNodesByFlavor(nodeRepository.getNodes());
        assertEquals(expected, actual);

        // Lets remove all the currently parked nodes and reprovision them different IP address
        nodesByHostname.entrySet().stream().filter(entry -> entry.getValue().state() == Node.State.parked).forEach(entry ->
                updateNode(entry.getKey(), entry.getValue().flavor(), Node.State.ready, Optional.empty(), Collections.singleton("::2")));

        expected = expectedCountsByFlavor(0, 1, 56, 39);
        actual = retirer.getNumberSpareReadyNodesByFlavor(nodeRepository.getNodes());
        assertEquals(expected, actual);

        retirer.retireUnallocated();
        parkedCountsByFlavor = nodesByHostname.values().stream()
                .filter(node -> node.state() == Node.State.parked)
                .collect(Collectors.groupingBy(Node::flavor, Collectors.counting()));
        expected = expectedCountsByFlavor(0, 1, 2, 2);
        expected.remove(flavors.get(0)); // Flavor-0 has 0 ready nodes, so just remove it to easily compare maps
        assertEquals(expected, parkedCountsByFlavor);
    }

    @Test
    public void testGetNumberSpareNodesWithNoActiveNodes() {
        addNodesByFlavor(Node.State.ready, 5, 3, 77);

        NodeRetirer retirer = new NodeRetirer(nodeRepository, Duration.ofDays(1), node -> false);
        Map<Flavor, Long> expected = expectedCountsByFlavor(3, 1, 75);
        Map<Flavor, Long> actual = retirer.getNumberSpareReadyNodesByFlavor(nodeRepository.getNodes());
        assertEquals(expected, actual);
    }

    @Test
    public void testGetNumberSpareNodesWithActiveNodes() {
        addNodesByFlavor(Node.State.ready, 5, 3, 77, 47);
        addNodesByFlavor(Node.State.active, 0, 10, 2, 230, 137);

        NodeRetirer retirer = new NodeRetirer(nodeRepository, Duration.ofDays(1), node -> false);
        Map<Flavor, Long> expected = expectedCountsByFlavor(3, 1, 75, 24);
        Map<Flavor, Long> actual = retirer.getNumberSpareReadyNodesByFlavor(nodeRepository.getNodes());
        assertEquals(expected, actual);
    }


    @Before
    public void setup() {
        when(nodeRepository.getNodes()).thenAnswer(invoc -> new ArrayList<>(nodesByHostname.values()));
        when(nodeRepository.lockUnallocated()).thenReturn(null);
        when(nodeRepository.park(anyString(), eq(Agent.system))).then(invocation -> {
            Object[] args = invocation.getArguments();
            String hostname = (String) args[0];
            Node nodeToPark = Optional.ofNullable(nodesByHostname.get(hostname))
                    .orElseThrow(() -> new RuntimeException("Could not find node with hostname " + hostname));
            if (nodeToPark.state() == Node.State.parked) throw new RuntimeException("Node already parked!");

            updateNode(nodeToPark.hostname(), nodeToPark.flavor(), Node.State.parked, nodeToPark.allocation(), nodeToPark.ipAddresses());
            return null;
        });
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
                int id = nodesByHostname.size();
                updateNode("host-" + id + ".yahoo.com", flavor, state, Optional.empty(), Collections.singleton("::1"));
            }
        }
    }

    private void deployApp(String tenantName, String applicationName, int flavorId, int numNodes) {
        Flavor flavor = flavors.get(flavorId);
        List<Node> freeNodes = nodeRepository.getNodes().stream()
                .filter(node -> node.state() == Node.State.ready)
                .filter(node -> node.flavor() == flavor)
                .collect(Collectors.toList());

        if (freeNodes.size() < numNodes) throw new IllegalArgumentException(
                "Not enough nodes to deploy " + applicationName + " on " + flavor + " needed " + numNodes + " but has " + freeNodes.size());

        ApplicationId applicationId = ApplicationId.from(tenantName, applicationName, "default");
        ClusterSpec cluster = ClusterSpec.request(ClusterSpec.Type.container, ClusterSpec.Id.from("test"), Version.fromString("6.99"));
        Allocation allocation = new Allocation(applicationId, ClusterMembership.from(cluster, 0), new Generation(0, 0), false);
        freeNodes.stream().limit(numNodes).forEach(node -> {
            updateNode(node.hostname(), node.flavor(), Node.State.active, Optional.of(allocation), node.ipAddresses());
        });
    }

    private List<Flavor> makeFlavors(int numFlavors) {
        FlavorConfigBuilder flavorConfigBuilder = new FlavorConfigBuilder();
        for (int i = 0; i < numFlavors; i++) {
            flavorConfigBuilder.addFlavor("flavor-" + i, 1. /* cpu*/, 3. /* mem GB*/, 2. /*disk GB*/, Flavor.Type.BARE_METAL);
        }
        return flavorConfigBuilder.build().flavor().stream().map(Flavor::new).collect(Collectors.toList());
    }

    private void updateNode(String hostname, Flavor flavor, Node.State state, Optional<Allocation> allocation, Set<String> ipAddresses) {
        Node node = new Node(
                UUID.randomUUID().toString(),
                ipAddresses,
                hostname,
                Optional.empty(),
                flavor,
                Status.initial(),
                state,
                allocation,
                History.empty(),
                NodeType.tenant);

        nodesByHostname.put(hostname, node);
    }
}
