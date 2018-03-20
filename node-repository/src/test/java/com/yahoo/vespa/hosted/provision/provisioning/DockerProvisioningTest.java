// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests deployment to docker images which share the same physical host.
 *
 * @author bratseth
 */
public class DockerProvisioningTest {

    private static final String dockerFlavor = "dockerSmall";

    @Test
    public void docker_application_deployment() {
        ProvisioningTester tester = new ProvisioningTester(new Zone(Environment.prod, RegionName.from("us-east")));
        ApplicationId application1 = tester.makeApplicationId();

        for (int i = 1; i < 10; i++)
            tester.makeReadyDockerNodes(1, dockerFlavor, "dockerHost" + i);

        Version wantedVespaVersion = Version.fromString("6.39");
        int nodeCount = 7;
        List<HostSpec> hosts = tester.prepare(application1,
                                              ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("myContent"), wantedVespaVersion, false),
                                              nodeCount, 1, dockerFlavor);
        tester.activate(application1, new HashSet<>(hosts));

        NodeList nodes = tester.getNodes(application1, Node.State.active);
        assertEquals(nodeCount, nodes.size());
        assertEquals(dockerFlavor, nodes.asList().get(0).flavor().canonicalName());

        // Upgrade Vespa version on nodes
        Version upgradedWantedVespaVersion = Version.fromString("6.40");
        List<HostSpec> upgradedHosts = tester.prepare(application1,
                                                      ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("myContent"), upgradedWantedVespaVersion, false),
                                                      nodeCount, 1, dockerFlavor);
        tester.activate(application1, new HashSet<>(upgradedHosts));
        NodeList upgradedNodes = tester.getNodes(application1, Node.State.active);
        assertEquals(nodeCount, upgradedNodes.size());
        assertEquals(dockerFlavor, upgradedNodes.asList().get(0).flavor().canonicalName());
        assertEquals(hosts, upgradedHosts);
    }

    /** Exclusive app first, then non-exclusive: Should give the same result as below */
    @Test
    public void docker_application_deployment_with_exclusive_app_first() {
        ProvisioningTester tester = new ProvisioningTester(new Zone(Environment.prod, RegionName.from("us-east")));
        for (int i = 1; i <= 4; i++)
            tester.makeReadyVirtualNode(i, dockerFlavor, "host1");
        for (int i = 5; i <= 8; i++)
            tester.makeReadyVirtualNode(i, dockerFlavor, "host2");
        for (int i = 9; i <= 12; i++)
            tester.makeReadyVirtualNode(i, dockerFlavor, "host3");
        for (int i = 13; i <= 16; i++)
            tester.makeReadyVirtualNode(i, dockerFlavor, "host4");

        ApplicationId application1 = tester.makeApplicationId();
        prepareAndActivate(application1, 2, true, tester);
        assertEquals(setOf("host1", "host2"), hostsOf(tester.getNodes(application1, Node.State.active)));

        ApplicationId application2 = tester.makeApplicationId();
        prepareAndActivate(application2, 2, false, tester);
        assertEquals("Application is assigned to separate hosts",
                     setOf("host3", "host4"), hostsOf(tester.getNodes(application2, Node.State.active)));
    }

    /** Non-exclusive app first, then an exclusive: Should give the same result as above */
    @Test
    public void docker_application_deployment_with_exclusive_app_last() {
        ProvisioningTester tester = new ProvisioningTester(new Zone(Environment.prod, RegionName.from("us-east")));
        for (int i = 1; i <= 4; i++)
            tester.makeReadyVirtualNode(i, dockerFlavor, "host1");
        for (int i = 5; i <= 8; i++)
            tester.makeReadyVirtualNode(i, dockerFlavor, "host2");
        for (int i = 9; i <= 12; i++)
            tester.makeReadyVirtualNode(i, dockerFlavor, "host3");
        for (int i = 13; i <= 16; i++)
            tester.makeReadyVirtualNode(i, dockerFlavor, "host4");

        ApplicationId application1 = tester.makeApplicationId();
        prepareAndActivate(application1, 2, false, tester);
        assertEquals(setOf("host1", "host2"), hostsOf(tester.getNodes(application1, Node.State.active)));

        ApplicationId application2 = tester.makeApplicationId();
        prepareAndActivate(application2, 2, true, tester);
        assertEquals("Application is assigned to separate hosts",
                     setOf("host3", "host4"), hostsOf(tester.getNodes(application2, Node.State.active)));
    }

    /** Test making an application exclusive */
    @Test
    public void docker_application_deployment_change_to_exclusive_and_back() {
        ProvisioningTester tester = new ProvisioningTester(new Zone(Environment.prod, RegionName.from("us-east")));
        for (int i = 1; i <= 4; i++)
            tester.makeReadyVirtualNode(i, dockerFlavor, "host1");
        for (int i = 5; i <= 8; i++)
            tester.makeReadyVirtualNode(i, dockerFlavor, "host2");
        for (int i = 9; i <= 12; i++)
            tester.makeReadyVirtualNode(i, dockerFlavor, "host3");
        for (int i = 13; i <= 16; i++)
            tester.makeReadyVirtualNode(i, dockerFlavor, "host4");

        ApplicationId application1 = tester.makeApplicationId();
        prepareAndActivate(application1, 2, false, tester);
        for (Node node : tester.getNodes(application1, Node.State.active).asList())
            assertFalse(node.allocation().get().membership().cluster().isExclusive());

        prepareAndActivate(application1, 2, true, tester);
        assertEquals(setOf("host1", "host2"), hostsOf(tester.getNodes(application1, Node.State.active)));
        for (Node node : tester.getNodes(application1, Node.State.active).asList())
            assertTrue(node.allocation().get().membership().cluster().isExclusive());

        prepareAndActivate(application1, 2, false, tester);
        assertEquals(setOf("host1", "host2"), hostsOf(tester.getNodes(application1, Node.State.active)));
        for (Node node : tester.getNodes(application1, Node.State.active).asList())
            assertFalse(node.allocation().get().membership().cluster().isExclusive());
    }

    /** Non-exclusive app first, then an exclusive: Should give the same result as above */
    @Test
    public void docker_application_deployment_with_exclusive_app_causing_allocation_failure() {
        ProvisioningTester tester = new ProvisioningTester(new Zone(Environment.prod, RegionName.from("us-east")));
        for (int i = 1; i <= 4; i++)
            tester.makeReadyVirtualNode(i, dockerFlavor, "host1");
        for (int i = 5; i <= 8; i++)
            tester.makeReadyVirtualNode(i, dockerFlavor, "host2");
        for (int i = 9; i <= 12; i++)
            tester.makeReadyVirtualNode(i, dockerFlavor, "host3");
        for (int i = 13; i <= 16; i++)
            tester.makeReadyVirtualNode(i, dockerFlavor, "host4");

        ApplicationId application1 = tester.makeApplicationId();
        prepareAndActivate(application1, 2, true, tester);
        assertEquals(setOf("host1", "host2"), hostsOf(tester.getNodes(application1, Node.State.active)));

        try {
            ApplicationId application2 = tester.makeApplicationId();
            prepareAndActivate(application2, 3, false, tester);
            fail("Expected allocation failure");
        }
        catch (Exception e) {
            assertEquals("No room for 3 nodes as 2 of 4 hosts are exclusive",
                         "Could not satisfy request for 3 nodes of flavor 'dockerSmall' for container cluster 'myContainer' group 0 6.39: Not enough nodes available due to host exclusivity constraints.",
                         e.getMessage());
        }

        // Adding 3 nodes of another cluster for the same application works:
        Set<HostSpec> hosts = new HashSet<>(tester.prepare(application1,
                                                           ClusterSpec.request(ClusterSpec.Type.container, ClusterSpec.Id.from("myContainer2"), Version.fromString("6.39"), false),
                                                           Capacity.fromNodeCount(3, Optional.of(dockerFlavor), false),
                                                           1));
        tester.activate(application1, hosts);
    }

    // In dev, test and staging you get nodes with default flavor, but we should get specified flavor for docker nodes
    @Test
    public void get_specified_flavor_not_default_flavor_for_docker() {
        ProvisioningTester tester = new ProvisioningTester(new Zone(Environment.test, RegionName.from("corp-us-east-1")));
        ApplicationId application1 = tester.makeApplicationId();
        tester.makeReadyDockerNodes(1, dockerFlavor, "dockerHost");

        List<HostSpec> hosts = tester.prepare(application1, ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("myContent"), Version.fromString("6.42"), false), 1, 1, dockerFlavor);
        tester.activate(application1, new HashSet<>(hosts));

        NodeList nodes = tester.getNodes(application1, Node.State.active);
        assertEquals(1, nodes.size());
        assertEquals(dockerFlavor, nodes.asList().get(0).flavor().canonicalName());
    }

    private Set setOf(String ... strings) {
        return Stream.of(strings).collect(Collectors.toSet());
    }

    private Set hostsOf(NodeList nodes) {
        return nodes.asList().stream().map(Node::parentHostname).map(Optional::get).collect(Collectors.toSet());
    }

    private void prepareAndActivate(ApplicationId application, int nodeCount, boolean exclusive, ProvisioningTester tester) {
        Set<HostSpec> hosts = new HashSet<>(tester.prepare(application,
                                            ClusterSpec.request(ClusterSpec.Type.container, ClusterSpec.Id.from("myContainer"), Version.fromString("6.39"), exclusive),
                                            Capacity.fromNodeCount(nodeCount, Optional.of(dockerFlavor), false),
                                            1));
        tester.activate(application, hosts);
    }

}
