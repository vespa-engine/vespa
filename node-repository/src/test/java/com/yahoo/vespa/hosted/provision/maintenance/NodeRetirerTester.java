// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Zone;
import com.yahoo.test.ManualClock;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.curator.transaction.CuratorTransaction;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.provisioning.NodeRepositoryProvisioner;
import com.yahoo.vespa.hosted.provision.testutils.MockDeployer;
import com.yahoo.vespa.hosted.provision.testutils.MockNameResolver;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;


/**
 * @author freva
 */
public class NodeRetirerTester {
    public static final Zone zone = new Zone(Environment.prod, RegionName.from("us-east"));

    // Components with state
    public final ManualClock clock;
    public final NodeRepository nodeRepository;
    private final NodeRepositoryProvisioner provisioner;
    private final Curator curator;
    private final List<Flavor> flavors;

    // Use LinkedHashMap to keep order in which applications were deployed
    private final Map<ApplicationId, MockDeployer.ApplicationContext> apps = new LinkedHashMap<>();

    private PeriodicApplicationMaintainer applicationMaintainer;
    private RetiredExpirer retiredExpirer;
    private InactiveExpirer inactiveExpirer;
    private int nextNodeId = 0;

    public NodeRetirerTester(NodeFlavors nodeFlavors) {
        clock = new ManualClock();
        curator = new MockCurator();
        nodeRepository = new NodeRepository(nodeFlavors, curator, clock, zone, new MockNameResolver().mockAnyLookup());
        provisioner = new NodeRepositoryProvisioner(nodeRepository, nodeFlavors, zone);
        flavors = nodeFlavors.getFlavors().stream().sorted(Comparator.comparing(Flavor::name)).collect(Collectors.toList());
    }

    public void createReadyNodesByFlavor(int... nums) {
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

    public ApplicationId deployApp(String tenantName, String applicationName, int flavorId, int numNodes) {
        Flavor flavor = flavors.get(flavorId);

        ApplicationId applicationId = ApplicationId.from(tenantName, applicationName, "default");
        ClusterSpec cluster = ClusterSpec.request(ClusterSpec.Type.container, ClusterSpec.Id.from("test"), Version.fromString("6.99"));
        Capacity capacity = Capacity.fromNodeCount(numNodes, flavor.name());
        apps.put(applicationId, new MockDeployer.ApplicationContext(applicationId, cluster, capacity, 1));

        activate(applicationId, cluster, capacity);
        return applicationId;
    }

    public void iterateMaintainers() {
        if (applicationMaintainer == null) {
            MockDeployer deployer = new MockDeployer(provisioner, apps);
            JobControl jobControl = new JobControl(nodeRepository.database());
            applicationMaintainer = new PeriodicApplicationMaintainerTest.TestablePeriodicApplicationMaintainer(
                    deployer, nodeRepository, Duration.ofMinutes(10), Optional.empty());
            retiredExpirer = new RetiredExpirer(nodeRepository, deployer, clock, Duration.ofMinutes(10), jobControl);
            inactiveExpirer = new InactiveExpirer(nodeRepository, clock, Duration.ofMinutes(10), jobControl);

        }

        applicationMaintainer.maintain();

        clock.advance(Duration.ofMinutes(11));
        retiredExpirer.maintain();

        clock.advance(Duration.ofMinutes(11));
        inactiveExpirer.maintain();
    }

    private void activate(ApplicationId applicationId, ClusterSpec cluster, Capacity capacity) {
        List<HostSpec> hosts = provisioner.prepare(applicationId, cluster, capacity, 1, null);
        NestedTransaction transaction = new NestedTransaction().add(new CuratorTransaction(curator));
        provisioner.activate(transaction, applicationId, hosts);
        transaction.commit();
    }

    public Map<Flavor, Long> expectedCountsByFlavor(long... nums) {
        Map<Flavor, Long> countsByFlavor = new HashMap<>();
        for (int i = 0; i < nums.length; i++) {
            if (nums[i] < 0) continue;
            Flavor flavor = flavors.get(i);
            countsByFlavor.put(flavor, nums[i]);
        }
        return countsByFlavor;
    }

    public Map<ApplicationId, Long> expectedCountsByApplication(long... nums) {
        Map<ApplicationId, Long> countsByApplicationId = new HashMap<>();
        Iterator<ApplicationId> iterator = apps.keySet().iterator();
        for (int i = 0; iterator.hasNext(); i++) {
            if (nums[i] < 0) continue;
            ApplicationId applicationId = iterator.next();
            countsByApplicationId.put(applicationId, nums[i]);
        }
        return countsByApplicationId;
    }
}
