// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Zone;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.maintenance.retire.RetirementPolicy;
import com.yahoo.vespa.hosted.provision.provisioning.FlavorConfigBuilder;
import com.yahoo.vespa.hosted.provision.provisioning.FlavorSpareChecker;
import com.yahoo.vespa.hosted.provision.provisioning.NodeRepositoryProvisioner;
import com.yahoo.vespa.hosted.provision.testutils.MockDeployer;
import com.yahoo.vespa.hosted.provision.testutils.MockNameResolver;
import com.yahoo.vespa.orchestrator.OrchestrationException;
import com.yahoo.vespa.orchestrator.Orchestrator;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author freva
 */
public class NodeRetirerTester {
    public static final Zone zone = new Zone(Environment.prod, RegionName.from("us-east"));

    // Components with state
    public final ManualClock clock = new ManualClock();
    public final NodeRepository nodeRepository;
    private final FlavorSpareChecker flavorSpareChecker = mock(FlavorSpareChecker.class);
    private final MockDeployer deployer;
    private final JobControl jobControl;
    private final List<Flavor> flavors;

    // Use LinkedHashMap to keep order in which applications were deployed
    private final Map<ApplicationId, MockDeployer.ApplicationContext> apps = new LinkedHashMap<>();

    private final Orchestrator orchestrator = mock(Orchestrator.class);
    private RetiredExpirer retiredExpirer;
    private InactiveExpirer inactiveExpirer;
    private int nextNodeId = 0;

    NodeRetirerTester(NodeFlavors nodeFlavors) {
        Curator curator = new MockCurator();
        nodeRepository = new NodeRepository(nodeFlavors, curator, clock, zone, new MockNameResolver().mockAnyLookup(),
                                            new DockerImage("docker-registry.domain.tld:8080/dist/vespa"));
        jobControl = new JobControl(nodeRepository.database());
        NodeRepositoryProvisioner provisioner = new NodeRepositoryProvisioner(nodeRepository, nodeFlavors, zone);
        deployer = new MockDeployer(provisioner, apps);
        flavors = nodeFlavors.getFlavors().stream().sorted(Comparator.comparing(Flavor::name)).collect(Collectors.toList());

        try {
            doThrow(new RuntimeException()).when(orchestrator).acquirePermissionToRemove(any());
        } catch (OrchestrationException e) {
            e.printStackTrace();
        }
    }

    NodeRetirer makeNodeRetirer(RetirementPolicy policy) {
         return new NodeRetirer(nodeRepository, flavorSpareChecker, Duration.ofDays(1), deployer, jobControl, policy);
    }

    void createReadyNodesByFlavor(int... nums) {
        List<Node> nodes = new ArrayList<>();
        for (int i = 0; i < nums.length; i++) {
            Flavor flavor = flavors.get(i);
            for (int j = 0; j < nums[i]; j++) {
                int id = nextNodeId++;
                nodes.add(nodeRepository.createNode("node" + id, "host" + id + ".test.yahoo.com",
                        Collections.singleton("::1"), Optional.empty(), flavor, NodeType.tenant));
            }
        }

        nodes = nodeRepository.addNodes(nodes);
        nodes = nodeRepository.setDirty(nodes);
        nodeRepository.setReady(nodes);
    }

    void deployApp(String tenantName, String applicationName, int[] flavorIds, int[] numNodes) {
        final ApplicationId applicationId = ApplicationId.from(tenantName, applicationName, "default");
        final List<MockDeployer.ClusterContext> clusterContexts = new ArrayList<>();

        for (int i = 0; i < flavorIds.length; i++) {
            Flavor flavor = flavors.get(flavorIds[i]);
            ClusterSpec cluster = ClusterSpec.request(ClusterSpec.Type.container, ClusterSpec.Id.from("cluster-" + i), Version.fromString("6.99"));
            Capacity capacity = Capacity.fromNodeCount(numNodes[i], flavor.name());
            // If the number of node the app wants is divisible by 2, make it into 2 groups, otherwise as 1
            int numGroups = numNodes[i] % 2 == 0 ? 2 : 1;
            clusterContexts.add(new MockDeployer.ClusterContext(applicationId, cluster, capacity, numGroups));
        }

        apps.put(applicationId, new MockDeployer.ApplicationContext(applicationId, clusterContexts));
        deployer.deployFromLocalActive(applicationId, Duration.ZERO).get().activate();
    }

    void iterateMaintainers() {
        if (retiredExpirer == null) {
            retiredExpirer = new RetiredExpirer(nodeRepository, orchestrator, deployer, clock, Duration.ofDays(30), Duration.ofMinutes(10), jobControl);
            inactiveExpirer = new InactiveExpirer(nodeRepository, clock, Duration.ofMinutes(10), jobControl);
        }

        clock.advance(Duration.ofMinutes(11));
        retiredExpirer.maintain();

        clock.advance(Duration.ofMinutes(11));
        inactiveExpirer.maintain();
    }

    void setNumberAllowedUnallocatedRetirementsPerFlavor(int... numAllowed) {
        for (int i = 0; i < numAllowed.length; i++) {
            Boolean[] responses = new Boolean[numAllowed[i]];
            Arrays.fill(responses, true);
            responses[responses.length - 1 ] = false;
            when(flavorSpareChecker.canRetireUnallocatedNodeWithFlavor(eq(flavors.get(i)))).thenReturn(true, responses);
        }
    }

    void setNumberAllowedAllocatedRetirementsPerFlavor(int... numAllowed) {
        for (int i = 0; i < numAllowed.length; i++) {
            Boolean[] responses = new Boolean[numAllowed[i]];
            Arrays.fill(responses, true);
            responses[responses.length - 1] = false;
            when(flavorSpareChecker.canRetireAllocatedNodeWithFlavor(eq(flavors.get(i)))).thenReturn(true, responses);
        }
    }

    void assertCountsForStateByFlavor(Node.State state, long... nums) {
        Map<Flavor, Long> expected = expectedCountsByFlavor(nums);
        Map<Flavor, Long> actual = nodeRepository.getNodes(state).stream()
                .collect(Collectors.groupingBy(Node::flavor, Collectors.counting()));
        assertEquals(expected, actual);
    }

    void assertParkedCountsByApplication(long... nums) {
        Map<ApplicationId, Long> expected = expectedCountsByApplication(nums);
        Map<ApplicationId, Long> actual = nodeRepository.getNodes(Node.State.parked).stream()
                .collect(Collectors.groupingBy(node -> node.allocation().get().owner(), Collectors.counting()));
        assertEquals(expected, actual);
    }

    // Nodes that are being retired or about to be retired (wantToRetire flag set), but are not yet fully retired (not parked)
    void assertRetiringCountsByApplication(long... nums) {
        Map<ApplicationId, Long> expected = expectedCountsByApplication(nums);
        Map<ApplicationId, Long> actual = nodeRepository.getNodes().stream()
                .filter(node -> node.status().wantToRetire())
                .filter(node -> node.allocation().isPresent())
                .filter(node -> node.allocation().get().membership().retired())
                .filter(node -> node.state() != Node.State.parked)
                .collect(Collectors.groupingBy(node -> node.allocation().get().owner(), Collectors.counting()));
        assertEquals(expected, actual);
    }

    private Map<Flavor, Long> expectedCountsByFlavor(long... nums) {
        Map<Flavor, Long> countsByFlavor = new HashMap<>();
        for (int i = 0; i < nums.length; i++) {
            if (nums[i] < 0) continue;
            Flavor flavor = flavors.get(i);
            countsByFlavor.put(flavor, nums[i]);
        }
        return countsByFlavor;
    }

    private Map<ApplicationId, Long> expectedCountsByApplication(long... nums) {
        Map<ApplicationId, Long> countsByApplicationId = new HashMap<>();
        Iterator<ApplicationId> iterator = apps.keySet().iterator();
        for (int i = 0; iterator.hasNext(); i++) {
            ApplicationId applicationId = iterator.next();
            if (nums[i] < 0) continue;
            countsByApplicationId.put(applicationId, nums[i]);
        }
        return countsByApplicationId;
    }

    static NodeFlavors makeFlavors(int numFlavors) {
        FlavorConfigBuilder flavorConfigBuilder = new FlavorConfigBuilder();
        for (int i = 0; i < numFlavors; i++) {
            flavorConfigBuilder.addFlavor("flavor-" + i, 1. /* cpu*/, 3. /* mem GB*/, 2. /*disk GB*/, Flavor.Type.BARE_METAL);
        }
        return new NodeFlavors(flavorConfigBuilder.build());
    }
}
