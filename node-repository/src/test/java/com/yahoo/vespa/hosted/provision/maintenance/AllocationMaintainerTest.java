package com.yahoo.vespa.hosted.provision.maintenance;

import com.google.common.collect.ImmutableSet;
import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Zone;
import com.yahoo.config.provisioning.FlavorsConfig;
import com.yahoo.path.Path;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.provisioning.AllocationVisualizer;
import com.yahoo.vespa.hosted.provision.provisioning.DockerHostCapacity;
import com.yahoo.vespa.hosted.provision.provisioning.FlavorConfigBuilder;
import com.yahoo.vespa.hosted.provision.provisioning.NodeRepositoryProvisioner;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisioningTester;
import com.yahoo.vespa.hosted.provision.testutils.MockDeployer;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author smorgrav
 */
public class AllocationMaintainerTest {

    private final Map<ApplicationId, MockDeployer.ApplicationContext> apps = new HashMap<>();

    private NodeRepository nodeRepository;
    private MockDeployer deployer;
    private ProvisioningTester tester;
    private AllocationMaintainer maintainer;
    private RetiredExpirer retiredExpirer;
    private InactiveExpirer incactiveExpirer;

    @Before
    public void setup() {
        Zone zone = new Zone(Environment.prod, RegionName.from("us-east"));
        tester = new ProvisioningTester(zone, flavorsConfig());
        nodeRepository = tester.nodeRepository();
        enableDynamicAllocation(tester);
        NodeRepositoryProvisioner provisioner = new NodeRepositoryProvisioner(nodeRepository, nodeRepository.getAvailableFlavors(), zone);
        JobControl jobControl = new JobControl(nodeRepository.database());
        deployer = new MockDeployer(provisioner, apps);
        maintainer = new AllocationMaintainer(deployer, nodeRepository, Duration.ofHours(1l), jobControl, 2);
        retiredExpirer = new RetiredExpirer(nodeRepository, deployer, tester.clock(), Duration.ofHours(1l), jobControl);
        incactiveExpirer = new InactiveExpirer(nodeRepository, tester.clock(), Duration.ofHours(1l), jobControl);
    }

    @Test
    public void spare_maintenance_simple_workflow() {
        tester.makeReadyNodes(5, "d-4", NodeType.host, 32);
        deployZoneApp(tester);

        Flavor d1 = tester.nodeRepository().getAvailableFlavors().getFlavorOrThrow("d-1");
        Flavor d2 = tester.nodeRepository().getAvailableFlavors().getFlavorOrThrow("d-2");
        Flavor d4 = tester.nodeRepository().getAvailableFlavors().getFlavorOrThrow("d-4");

        ApplicationId appA = tester.makeApplicationId();
        List<HostSpec> hostsA = deployApp(appA, 3, d2);

        ApplicationId appB = tester.makeApplicationId();
        List<HostSpec> hostsB = deployApp(appB, 3, d1);


        //Fail one node
        String hostname = hostsA.get(0).hostname();
        tester.fail(hostsA.get(0));

        // Deploy again - get replacement node
        hostsA = tester.prepare(appA,
                ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("myContent"), Version.fromString("6.100")),
                3, 1, d2.canonicalName());
        tester.activate(appA, ImmutableSet.copyOf(hostsA));

        // Simulate failed -> dirty -> remove node
        nodeRepository.remove(hostname);

        // Deploy again
        hostsA = tester.prepare(appA,
                ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("myContent"), Version.fromString("6.100")),
                3, 1, d2.canonicalName());
        tester.activate(appA, ImmutableSet.copyOf(hostsA));

        // Add checkpoint
        tester.addAllocationSnapshot("State before maintainer");

        // Check that only one host is free (and thus a complete spare)
        DockerHostCapacity capacity = new DockerHostCapacity(nodeRepository.getNodes());
        Assert.assertEquals(1, capacity.getNofHostsAvailableFor(d4));

        // We have we need to reclaim spares
        maintainer.maintain();

        // Add checkpoint - we should have one retired node now
        tester.addAllocationSnapshot("State after maintainer");

        // The retire timeout is set to 1 hour - wait more than that
        tester.advanceTime(Duration.ofHours(2l));
        retiredExpirer.maintain();

        tester.addAllocationSnapshot("After retiredExpirer");

        // The retire timeout is set to 1 hour - wait more than that
        tester.advanceTime(Duration.ofHours(2l));
        incactiveExpirer.maintain();

        tester.addAllocationSnapshot("After inactiveExpirer");

        //Assert on that we have one dirty
        List<Node> dirtyNodes = nodeRepository.getNodes(Node.State.dirty);
        Assert.assertEquals(1, dirtyNodes.size());

        // Simulate Node Agent deleting
        nodeRepository.remove(dirtyNodes.get(0).hostname());

        tester.addAllocationSnapshot("Final state");

        // Uncomment the statement below to walk through the allocation events visually
        AllocationVisualizer.visualize(tester.getAllocationSnapshots());
    }

    @Test
    public void non_prod_do_not_have_spares() {
        ProvisioningTester tester = new ProvisioningTester(new Zone(Environment.perf, RegionName.from("us-east")), flavorsConfig());
        enableDynamicAllocation(tester);
        tester.makeReadyNodes(3, "host-small", NodeType.host, 32);
        deployZoneApp(tester);
        Flavor flavor = tester.nodeRepository().getAvailableFlavors().getFlavorOrThrow("d-3");

        ApplicationId application1 = tester.makeApplicationId();
        List<HostSpec> hosts = tester.prepare(application1,
                ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("myContent"), Version.fromString("6.100")),
                3, 1, flavor.canonicalName());
        tester.activate(application1, ImmutableSet.copyOf(hosts));

        List<Node> initialSpareCapacity = findSpareCapacity(tester);
        assertThat(initialSpareCapacity.size(), is(0));
    }

    private List<Node> findSpareCapacity(ProvisioningTester tester) {
        List<Node> nodes = tester.nodeRepository().getNodes(Node.State.values());
        NodeList nl = new NodeList(nodes);
        return nodes.stream()
                .filter(n -> n.type() == NodeType.host)
                .filter(n -> nl.childNodes(n).size() == 0) // Nodes without children
                .collect(Collectors.toList());
    }

    private FlavorsConfig flavorsConfig() {
        FlavorConfigBuilder b = new FlavorConfigBuilder();
        b.addFlavor("d-1", 1, 1., 1, Flavor.Type.DOCKER_CONTAINER);
        b.addFlavor("d-2", 2, 2., 2, Flavor.Type.DOCKER_CONTAINER);
        b.addFlavor("d-4", 4, 4., 4, Flavor.Type.BARE_METAL);
        return b.build();
    }

    private List<HostSpec> deployApp(ApplicationId appId, int nodeCount, Flavor flavor) {
        ClusterSpec clusterSpec = ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("myContent"), Version.fromString("6.100"));
        Capacity capacity = Capacity.fromNodeCount(nodeCount, flavor.canonicalName());
        List<HostSpec> hosts = tester.prepare(appId, clusterSpec,
                capacity, 1);
        tester.activate(appId, ImmutableSet.copyOf(hosts));

        MockDeployer.ApplicationContext context = new MockDeployer.ApplicationContext(appId, clusterSpec, capacity, 1);
        apps.put(appId, context);

        return hosts;
    }

    private List<HostSpec> deployZoneApp(ProvisioningTester tester) {
        ApplicationId applicationId = tester.makeApplicationId();
        ClusterSpec clusterSpec = ClusterSpec.request(ClusterSpec.Type.container,
                ClusterSpec.Id.from("node-admin"),
                Version.fromString("6.42"));
        Capacity capacity = Capacity.fromRequiredNodeType(NodeType.host);
        List<HostSpec> list = tester.prepare(applicationId, clusterSpec, capacity, 1);

        // Activate
        tester.activate(applicationId, ImmutableSet.copyOf(list));

        // Bookkeeping
        MockDeployer.ApplicationContext context = new MockDeployer.ApplicationContext(applicationId, clusterSpec, capacity, 1);
        apps.put(applicationId, context);

        return list;
    }

    private void enableDynamicAllocation(ProvisioningTester tester) {
        tester.getCurator().set(Path.fromString("/provision/v1/dynamicDockerAllocation"), new byte[0]);
    }
}
