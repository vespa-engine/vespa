package com.yahoo.vespa.hosted.provision.provisioning;

import com.google.common.collect.ImmutableSet;
import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.OutOfCapacityException;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Zone;
import com.yahoo.config.provisioning.FlavorsConfig;
import com.yahoo.path.Path;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

/**
 * @author mortent
 */
public class DynamicDockerProvisioningTest {

    @Test
    public void spare_capacity_used_only_when_replacement() {
        // Use spare capacity only when replacement (i.e one node is failed)
        // Test should allocate as much capacity as possible, verify that it is not possible to allocate one more unit
        // Verify that there is still capacity (available spare)
        // Fail one node and redeploy, Verify that one less node is empty.

        // Setup test
        ProvisioningTester tester = new ProvisioningTester(new Zone(Environment.prod, RegionName.from("us-east")), flavorsConfig());
        enableDynamicAllocation(tester);
        ApplicationId application1 = tester.makeApplicationId();
        tester.makeReadyNodes(5, "host-small", NodeType.host, 32);
        deployZoneApp(tester);
        Flavor flavor = tester.nodeRepository().getAvailableFlavors().getFlavorOrThrow("d-3");

        // Deploy initial state (can max deploy 3 nodes due to redundancy requirements)
        List<HostSpec> hosts = tester.prepare(application1,
                ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("myContent"), Version.fromString("6.100")),
                3, 1, flavor.canonicalName());
        tester.activate(application1, ImmutableSet.copyOf(hosts));

        DockerHostCapacity capacity = new DockerHostCapacity(tester.nodeRepository().getNodes(Node.State.values()));
        assertThat(capacity.freeCapacityInFlavorEquivalence(flavor), greaterThan(0));

        List<Node> initialSpareCapacity = findSpareCapacity(tester);
        assertThat(initialSpareCapacity.size(), is(2));

        try {
            hosts = tester.prepare(application1,
                    ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("myContent"), Version.fromString("6.100")),
                    4, 1, flavor.canonicalName());
            fail("Was able to deploy with 4 nodes, should not be able to use spare capacity");
        } catch (OutOfCapacityException e) {
        }

        tester.fail(hosts.get(0));
        hosts = tester.prepare(application1,
                ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("myContent"), Version.fromString("6.100")),
                3, 1, flavor.canonicalName());
        tester.activate(application1, ImmutableSet.copyOf(hosts));

        List<Node> finalSpareCapacity = findSpareCapacity(tester);

        assertThat(finalSpareCapacity.size(), is(1));

        // Uncomment the statement below to walk through the allocation events visually
        //AllocationVisualizer.visualize(tester.getAllocationSnapshots());
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
        b.addFlavor("host-large", 6., 6., 6, Flavor.Type.BARE_METAL);
        b.addFlavor("host-small", 3., 3., 3, Flavor.Type.BARE_METAL);
        b.addFlavor("d-1", 1, 1., 1, Flavor.Type.DOCKER_CONTAINER);
        b.addFlavor("d-2", 2, 2., 2, Flavor.Type.DOCKER_CONTAINER);
        b.addFlavor("d-3", 3, 3., 3, Flavor.Type.DOCKER_CONTAINER);
        b.addFlavor("d-3-disk", 3, 3., 5, Flavor.Type.DOCKER_CONTAINER);
        b.addFlavor("d-3-mem", 3, 5., 3, Flavor.Type.DOCKER_CONTAINER);
        b.addFlavor("d-3-cpu", 5, 3., 3, Flavor.Type.DOCKER_CONTAINER);
        return b.build();
    }

    private List<HostSpec> deployZoneApp(ProvisioningTester tester) {
        ApplicationId applicationId = tester.makeApplicationId();
        List<HostSpec> list = tester.prepare(applicationId,
                ClusterSpec.request(ClusterSpec.Type.container,
                        ClusterSpec.Id.from("node-admin"),
                        Version.fromString("6.42")),
                Capacity.fromRequiredNodeType(NodeType.host),
                1);
        tester.activate(applicationId, ImmutableSet.copyOf(list));
        return list;
    }

    private void enableDynamicAllocation(ProvisioningTester tester) {
        tester.getCurator().set(Path.fromString("/provision/v1/dynamicDockerAllocation"), new byte[0]);
    }
}
