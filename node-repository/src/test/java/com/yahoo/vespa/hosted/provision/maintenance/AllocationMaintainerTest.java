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
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.provisioning.AllocationVisualizer;
import com.yahoo.vespa.hosted.provision.provisioning.DockerHostCapacity;
import com.yahoo.vespa.hosted.provision.provisioning.FlavorConfigBuilder;
import com.yahoo.vespa.hosted.provision.provisioning.NodeRepositoryProvisioner;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisioningTester;
import com.yahoo.vespa.hosted.provision.testutils.MockDeployer;
import org.junit.Assert;
import org.junit.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    public void setup(boolean headroomUseCase) {
        Zone zone = new Zone(headroomUseCase ? Environment.dev : Environment.prod, RegionName.from("us-east"));
        tester = new ProvisioningTester(zone, flavorsConfig(headroomUseCase));
        nodeRepository = tester.nodeRepository();
        enableDynamicAllocation(tester);
        NodeRepositoryProvisioner provisioner = new NodeRepositoryProvisioner(nodeRepository, nodeRepository.getAvailableFlavors(), zone);
        JobControl jobControl = new JobControl(nodeRepository.database());
        deployer = new MockDeployer(provisioner, apps);
        maintainer = new AllocationMaintainer(nodeRepository, Duration.ofHours(1l), jobControl, headroomUseCase ? 0 : 2);
        retiredExpirer = new RetiredExpirer(nodeRepository, deployer, tester.clock(), Duration.ofHours(1l), jobControl);
        incactiveExpirer = new InactiveExpirer(nodeRepository, tester.clock(), Duration.ofHours(1l), jobControl);
    }

    @Test
    public void spare_maintenance_simple_workflow() {
        setup(false);
        tester.makeReadyNodes(5, "d-4", NodeType.host, 32);
        deployZoneApp(tester);

        Flavor d1 = tester.nodeRepository().getAvailableFlavors().getFlavorOrThrow("d-1");
        Flavor d2 = tester.nodeRepository().getAvailableFlavors().getFlavorOrThrow("d-2");
        Flavor d4 = tester.nodeRepository().getAvailableFlavors().getFlavorOrThrow("d-4");

        ApplicationId appA = tester.makeApplicationId();
        List<HostSpec> hostsA = deployApp(appA, 3, d2);

        ApplicationId appB = tester.makeApplicationId();
        deployApp(appB, 3, d1);

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

        simulatePostMainenanceActivities();

        // We should now have reclaimed the space for the spare node
        Assert.assertEquals(2, capacity.getNofHostsAvailableFor(d4));

        // Uncomment the statement below to walk through the allocation events visually
        //AllocationVisualizer.visualize(tester.getAllocationSnapshots());
    }

    @Test
    public void headroom_maintenance() {
        setup(true);

        // Allocate two applications on one docker host
        tester.makeReadyNodes(1, "d-4", NodeType.host, 32);
        deployZoneApp(tester);

        Flavor d2 = tester.nodeRepository().getAvailableFlavors().getFlavorOrThrow("d-2");

        ApplicationId appA = tester.makeApplicationId();
        List<HostSpec> hostsA = deployApp(appA, 1, d2);

        ApplicationId appB = tester.makeApplicationId();
        deployApp(appB, 1, d2);

        // Add one additional docker host
        tester.makeReadyNodes(1, "d-4", NodeType.host, 32);
        deployZoneApp(tester);

        tester.addAllocationSnapshot("Post added docker host");

        // Maintenance should no try to reallocate one app from the first to the second host
        maintainer.maintain();

        simulatePostMainenanceActivities();

        // We should now have headroom for 2 d-2
        DockerHostCapacity capacity = new DockerHostCapacity(nodeRepository.getNodes());
        Assert.assertEquals(2, capacity.getNofHostsAvailableFor(d2));

        // Uncomment the statement below to walk through the allocation events visually
        AllocationVisualizer.visualize(tester.getAllocationSnapshots());
    }


    private void simulatePostMainenanceActivities() {
        // Add checkpoint - for visual inspection
        tester.addAllocationSnapshot("Post allocation maintenance");

        // The retire timeout is set to 1 hour - wait more than that
        tester.advanceTime(Duration.ofHours(2l));
        retiredExpirer.maintain();

        tester.addAllocationSnapshot("Post retiredExpirer");

        // The retire timeout is set to 1 hour - wait more than that
        tester.advanceTime(Duration.ofHours(2l));
        incactiveExpirer.maintain();

        tester.addAllocationSnapshot("After inactiveExpirer");

        //Assert on that we have one dirty
        List<Node> dirtyNodes = nodeRepository.getNodes(Node.State.dirty);

        // Simulate Node Agent deleting the nodes
        for (Node node : dirtyNodes) {
            nodeRepository.remove(node.hostname());
        }

        tester.addAllocationSnapshot("Final state");
    }

    private FlavorsConfig flavorsConfig(boolean withHeadroom) {
        FlavorConfigBuilder b = new FlavorConfigBuilder();
        b.addFlavor("d-1", 1, 1., 1, Flavor.Type.DOCKER_CONTAINER);
        b.addFlavor("d-2", 2, 2., 2, Flavor.Type.DOCKER_CONTAINER);
        if (withHeadroom) {
            b.addFlavor("d-2", 2, 2., 2, Flavor.Type.DOCKER_CONTAINER).idealHeadroom(2);
        } else {
            b.addFlavor("d-2", 2, 2., 2, Flavor.Type.DOCKER_CONTAINER);
        }
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
