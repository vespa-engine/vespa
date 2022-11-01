// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.NodeAllocationException;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import com.yahoo.config.provisioning.FlavorsConfig;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.curator.transaction.CuratorTransaction;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.Node.State;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.IP;
import org.junit.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author mortent
 */
public class DynamicAllocationTest {

    /**
     * Test relocation of nodes from spare hosts.
     * <p>
     * Setup 4 hosts and allocate one container on each (from two different applications)
     * getSpareCapacityProd() spares.
     * <p>
     * Check that it relocates containers away from the getSpareCapacityProd() spares
     * <p>
     * Initial allocation of app 1 and 2 --> final allocation (example using 2 spares):
     * <p>
     * |    |    |    |    |        |    |    |    |    |
     * |    |    |    |    |   -->  | 2a | 2b |    |    |
     * | 1a | 1b | 2a | 2b |        | 1a | 1b |    |    |
     */
    @Test
    public void relocate_nodes_from_spare_hosts() {
        int spareCount = 1;
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east")))
                                                                    .flavorsConfig(flavorsConfig())
                                                                    .spareCount(spareCount)
                                                                    .build();
        tester.makeReadyNodes(4, "host-small", NodeType.host, 32);
        tester.activateTenantHosts();
        List<Node> hosts = tester.nodeRepository().nodes().list(Node.State.active).nodeType(NodeType.host).asList();
        NodeResources flavor = new NodeResources(1, 4, 100, 1);

        // Application 1
        ApplicationId application1 = makeApplicationId("t1", "a1");
        ClusterSpec clusterSpec1 = clusterSpec("myContent.t1.a1");
        addAndAssignNode(application1, "1a", hosts.get(0).hostname(), clusterSpec1, flavor, 0, tester);
        addAndAssignNode(application1, "1b", hosts.get(1).hostname(), clusterSpec1, flavor, 1, tester);

        // Application 2
        ApplicationId application2 = makeApplicationId("t2", "a2");
        ClusterSpec clusterSpec2 = clusterSpec("myContent.t2.a2");
        addAndAssignNode(application2, "2a", hosts.get(2).hostname(), clusterSpec2, flavor, 3, tester);
        addAndAssignNode(application2, "2b", hosts.get(3).hostname(), clusterSpec2, flavor, 4, tester);

        // Redeploy both applications (to be agnostic on which hosts are picked as spares)
        deployApp(application1, clusterSpec1, flavor, tester, 2);
        deployApp(application2, clusterSpec2, flavor, tester, 2);

        // Assert that we have two spare nodes (two hosts that are don't have allocations)
        Set<String> hostsWithChildren = new HashSet<>();
        for (Node node : tester.nodeRepository().nodes().list(State.active).nodeType(NodeType.tenant).not().state(State.inactive).not().retired()) {
            hostsWithChildren.add(node.parentHostname().get());
        }
        assertEquals(4 - spareCount, hostsWithChildren.size());

    }

    /**
     * Test an allocation workflow:
     * <p>
     * 5 Hosts of capacity 3 (2 spares)
     * - Allocate app with 3 nodes
     * - Allocate app with 2 nodes
     * - Fail host and check redistribution
     */
    @Test
    public void relocate_failed_nodes() {
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east"))).flavorsConfig(flavorsConfig()).build();
        tester.makeReadyNodes(5, "host-small", NodeType.host, 32);
        tester.activateTenantHosts();
        NodeList hosts = tester.nodeRepository().nodes().list(Node.State.active).nodeType(NodeType.host);
        NodeResources resources = new NodeResources(1, 4, 100, 0.3);

        // Application 1
        ApplicationId application1 = makeApplicationId("t1", "a1");
        ClusterSpec clusterSpec1 = clusterSpec("myContent.t1.a1");
        deployApp(application1, clusterSpec1, resources, tester, 3);

        // Application 2
        ApplicationId application2 = makeApplicationId("t2", "a2");
        ClusterSpec clusterSpec2 = clusterSpec("myContent.t2.a2");
        deployApp(application2, clusterSpec2, resources, tester, 2);

        // Application 3
        ApplicationId application3 = makeApplicationId("t3", "a3");
        ClusterSpec clusterSpec3 = clusterSpec("myContent.t3.a3");
        deployApp(application3, clusterSpec3, resources, tester, 2);

        // App 2 and 3 should have been allocated to the same nodes - fail one of the parent hosts from there
        String parent = "host-1.yahoo.com";
        tester.nodeRepository().nodes().failOrMarkRecursively(parent, Agent.system, "Testing");

        // Redeploy all applications
        deployApp(application1, clusterSpec1, resources, tester, 3);
        deployApp(application2, clusterSpec2, resources, tester, 2);
        deployApp(application3, clusterSpec3, resources, tester, 2);

        Map<Integer, Integer> numberOfChildrenStat = new HashMap<>();
        for (Node host : hosts) {
            int nofChildren = tester.nodeRepository().nodes().list().childrenOf(host).size();
            if (!numberOfChildrenStat.containsKey(nofChildren)) {
                numberOfChildrenStat.put(nofChildren, 0);
            }
            numberOfChildrenStat.put(nofChildren, numberOfChildrenStat.get(nofChildren) + 1);
        }

        assertEquals(4, numberOfChildrenStat.get(2).intValue());
        assertEquals(1, numberOfChildrenStat.get(1).intValue());
    }

    @Test
    public void allocation_balancing() {
        // Here we test balancing between cpu and memory and ignore disk

        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east"))).flavorsConfig(flavorsConfig()).build();
        tester.makeReadyNodes(3, "flt", NodeType.host, 8); // cpu: 30, mem: 30
        tester.makeReadyNodes(3, "cpu", NodeType.host, 8); // cpu: 40, mem: 20
        tester.makeReadyNodes(3, "mem", NodeType.host, 8); // cpu: 20, mem: 40
        tester.activateTenantHosts();
        NodeResources fltResources = new NodeResources(6, 6, 10, 0.1);
        NodeResources cpuResources = new NodeResources(8, 4, 10, 0.1);
        NodeResources memResources = new NodeResources(4, 8, 10, 0.1);

        // Cpu heavy application
        ApplicationId application1 = makeApplicationId("t1", "a1");
        deployApp(application1, clusterSpec("c"), cpuResources, tester, 2);
        tester.assertAllocatedOn("Cpu nodes cause least skew increase", "cpu", application1);

        // Mem heavy application
        ApplicationId application2 = makeApplicationId("t2", "a2");
        deployApp(application2, clusterSpec("c"), memResources, tester, 2);
        tester.assertAllocatedOn("Mem nodes cause least skew increase", "mem", application2);

        // Flat application
        ApplicationId application3 = makeApplicationId("t3", "a3");
        deployApp(application3, clusterSpec("c"), fltResources, tester, 2);
        tester.assertAllocatedOn("Flat nodes cause least skew increase", "flt", application3);

        // Mem heavy application which can't all be allocated on mem nodes
        ApplicationId application4 = makeApplicationId("t4", "a4");
        deployApp(application4, clusterSpec("c"), memResources, tester, 3);
        assertEquals(2, tester.hostFlavorCount("mem", application4));
        assertEquals(1, tester.hostFlavorCount("flt", application4));

    }

    /**
     * Test redeployment of nodes that violates spare headroom - but without alternatives
     * <p>
     * Setup 2 hosts and allocate one app with a container on each. 2 spares
     * <p>
     * Initial allocation of app 1 --> final allocation:
     * <p>
     * |    |    |        |    |    |
     * |    |    |   -->  |    |    |
     * | 1a | 1b |        | 1a | 1b |
     */
    @Test
    public void do_not_relocate_nodes_from_spare_if_no_where_to_relocate_them() {
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east"))).flavorsConfig(flavorsConfig()).build();
        tester.makeReadyNodes(2, "host-small", NodeType.host, 32);
        tester.activateTenantHosts();
        List<Node> hosts = tester.nodeRepository().nodes().list(Node.State.active).nodeType(NodeType.host).asList();
        NodeResources flavor = new NodeResources(1, 4, 100, 1);

        // Application 1
        ApplicationId application1 = makeApplicationId("t1", "a1");
        ClusterSpec clusterSpec1 = clusterSpec("myContent.t1.a1");
        addAndAssignNode(application1, "1a", hosts.get(0).hostname(), clusterSpec1, flavor, 0, tester);
        addAndAssignNode(application1, "1b", hosts.get(1).hostname(), clusterSpec1, flavor, 1, tester);

        // Redeploy both applications (to be agnostic on which hosts are picked as spares)
        deployApp(application1, clusterSpec1, flavor, tester, 2);

        // Assert that we have two spare nodes (two hosts that are don't have allocations)
        Set<String> hostsWithChildren = new HashSet<>();
        for (Node node : tester.nodeRepository().nodes().list(State.active).nodeType(NodeType.tenant).not().state(State.inactive).not().retired()) {
            hostsWithChildren.add(node.parentHostname().get());
        }
        assertEquals(2, hostsWithChildren.size());
    }

    @Test(expected = NodeAllocationException.class)
    public void multiple_groups_are_on_separate_parent_hosts() {
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east"))).flavorsConfig(flavorsConfig()).build();
        tester.makeReadyNodes(5, "host-small", NodeType.host, 32);
        tester.activateTenantHosts();

        //Deploy an application having 6 nodes (3 nodes in 2 groups). We only have 5 hosts available
        ApplicationId application1 = ProvisioningTester.applicationId();
        tester.prepare(application1, clusterSpec("myContent.t1.a1"), 6, 2, new NodeResources(1, 4, 100, 1));

        fail("Two groups have been allocated to the same parent host");
    }

    @Test
    public void spare_capacity_used_only_when_replacement() {
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east")))
                                                                    .flavorsConfig(flavorsConfig())
                                                                    .spareCount(2)
                                                                    .build();

        // Setup test
        ApplicationId application1 = ProvisioningTester.applicationId();
        tester.makeReadyNodes(5, "host-small", NodeType.host, 32);
        tester.activateTenantHosts();
        NodeResources flavor = new NodeResources(1, 4, 100, 1);

        // Deploy initial state (can max deploy 3 nodes due to redundancy requirements)
        ClusterSpec clusterSpec = clusterSpec("myContent.t1.a1");
        List<HostSpec> hosts = tester.prepare(application1, clusterSpec, 3, 1, flavor);
        tester.activate(application1, Set.copyOf(hosts));

        List<Node> initialSpareCapacity = findSpareCapacity(tester);
        assertEquals(2, initialSpareCapacity.size());

        try {
            hosts = tester.prepare(application1, clusterSpec, 4, 1, flavor);
            fail("Was able to deploy with 4 nodes, should not be able to use spare capacity");
        } catch (NodeAllocationException ignored) { }

        tester.fail(hosts.get(0));
        hosts = tester.prepare(application1, clusterSpec, 3, 1, flavor);
        tester.activate(application1, Set.copyOf(hosts));

        List<Node> finalSpareCapacity = findSpareCapacity(tester);
        assertEquals(1, finalSpareCapacity.size());
    }

    @Test
    public void does_not_allocate_to_suspended_hosts() {
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east"))).flavorsConfig(flavorsConfig()).build();
        tester.makeReadyNodes(4, "host-small", NodeType.host, 32);
        tester.activateTenantHosts();

        HostName randomHost = new HostName(tester.nodeRepository().nodes().list(State.active).first().get().hostname());
        tester.orchestrator().suspend(randomHost);

        ApplicationId application1 = ProvisioningTester.applicationId();
        ClusterSpec clusterSpec = clusterSpec("myContent.t1.a1");
        NodeResources flavor = new NodeResources(1, 4, 100, 1);

        try {
            tester.prepare(application1, clusterSpec, 4, 1, flavor);
            fail("Should not be able to deploy 4 nodes on 4 hosts because 1 is suspended");
        } catch (NodeAllocationException ignored) { }

        // Resume the host, the deployment goes through
        tester.orchestrator().resume(randomHost);
        tester.activate(application1, tester.prepare(application1, clusterSpec, 4, 1, flavor));
        Set<String> hostnames = tester.getNodes(application1, State.active).hostnames();

        // Verify that previously allocated nodes are not affected by host suspension
        tester.orchestrator().suspend(randomHost);
        tester.activate(application1, tester.prepare(application1, clusterSpec, 4, 1, flavor));
        assertEquals(hostnames, tester.getNodes(application1, State.active).hostnames());
    }

    @Test
    public void non_prod_zones_do_not_have_spares() {
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.perf, RegionName.from("us-east"))).flavorsConfig(flavorsConfig()).build();
        tester.makeReadyNodes(3, "host-small", NodeType.host, 32);
        tester.activateTenantHosts();
        ApplicationId application1 = ProvisioningTester.applicationId();
        List<HostSpec> hosts = tester.prepare(application1, clusterSpec("myContent.t1.a1"), 3, 1, new NodeResources(1, 4, 100, 1));
        tester.activate(application1, Set.copyOf(hosts));

        List<Node> initialSpareCapacity = findSpareCapacity(tester);
        assertEquals(0, initialSpareCapacity.size());
    }

    @Test
    public void cd_uses_slow_disk_hosts() {
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(SystemName.cd, Environment.test, RegionName.from("us-east"))).flavorsConfig(flavorsConfig()).build();
        tester.makeReadyNodes(4, new Flavor(new NodeResources(1, 8, 120, 1, NodeResources.DiskSpeed.slow)), NodeType.host, 10, true);
        tester.activateTenantHosts();
        ApplicationId application1 = ProvisioningTester.applicationId();
        List<HostSpec> hosts = tester.prepare(application1, clusterSpec("myContent.t1.a1"), 3, 1, new NodeResources(1, 4, 100, 1));
        tester.activate(application1, Set.copyOf(hosts));
    }

    @Test(expected = NodeAllocationException.class)
    public void allocation_should_fail_when_host_is_not_in_allocatable_state() {
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east"))).flavorsConfig(flavorsConfig()).build();
        tester.makeProvisionedNodes(3, "host-small", NodeType.host, 32).forEach(node ->
                                                                                        tester.nodeRepository().nodes().fail(node.hostname(), Agent.system, getClass().getSimpleName()));

        ApplicationId application = ProvisioningTester.applicationId();
        tester.prepare(application, clusterSpec("myContent.t2.a2"), 2, 1, new NodeResources(1, 40, 100, 1));
    }

    @Test
    public void provision_dual_stack_containers() {
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east"))).flavorsConfig(flavorsConfig()).build();
        tester.makeReadyNodes(2, "host-large", NodeType.host, 10, true);
        tester.activateTenantHosts();

        ApplicationId application = ProvisioningTester.applicationId();
        List<HostSpec> hosts = tester.prepare(application, clusterSpec("myContent.t1.a1"), 2, 1, new NodeResources(1, 4, 100, 1));
        tester.activate(application, hosts);

        NodeList activeNodes = tester.nodeRepository().nodes().list().owner(application);
        assertEquals(Set.of("127.0.127.2", "::2"), activeNodes.asList().get(0).ipConfig().primary());
        assertEquals(Set.of("127.0.127.13", "::d"), activeNodes.asList().get(1).ipConfig().primary());
    }

    @Test
    public void provisioning_fast_disk_speed_do_not_get_slow_nodes() {
        provisionFastAndSlowThenDeploy(NodeResources.DiskSpeed.fast, true);
    }

    @Test
    public void provisioning_slow_disk_speed_do_not_get_fast_nodes() {
        provisionFastAndSlowThenDeploy(NodeResources.DiskSpeed.slow, true);
    }

    @Test
    public void provisioning_any_disk_speed_gets_slow_and_fast_nodes() {
        provisionFastAndSlowThenDeploy(NodeResources.DiskSpeed.any, false);
    }

    @Test
    public void slow_disk_nodes_are_preferentially_allocated() {
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east"))).flavorsConfig(flavorsConfig()).build();
        tester.makeReadyNodes(2, new Flavor(new NodeResources(1, 8, 120, 1, NodeResources.DiskSpeed.fast)), NodeType.host, 10, true);
        tester.makeReadyNodes(2, new Flavor(new NodeResources(1, 8, 120, 1, NodeResources.DiskSpeed.slow)), NodeType.host, 10, true);
        tester.activateTenantHosts();

        ApplicationId application = ProvisioningTester.applicationId();
        ClusterSpec cluster = ClusterSpec.request(ClusterSpec.Type.container, ClusterSpec.Id.from("test")).vespaVersion("1").build();
        NodeResources resources = new NodeResources(1, 4, 100, 1, NodeResources.DiskSpeed.any);

        List<HostSpec> hosts = tester.prepare(application, cluster, 2, 1, resources);
        assertEquals(2, hosts.size());
        assertEquals(NodeResources.DiskSpeed.slow, hosts.get(0).advertisedResources().diskSpeed());
        assertEquals(NodeResources.DiskSpeed.slow, hosts.get(1).advertisedResources().diskSpeed());
        tester.activate(application, hosts);
    }

    private void provisionFastAndSlowThenDeploy(NodeResources.DiskSpeed requestDiskSpeed, boolean expectNodeAllocationFailure) {
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east"))).flavorsConfig(flavorsConfig()).build();
        tester.makeReadyNodes(2, new Flavor(new NodeResources(1, 8, 120, 1, NodeResources.DiskSpeed.fast)), NodeType.host, 10, true);
        tester.makeReadyNodes(2, new Flavor(new NodeResources(1, 8, 120, 1, NodeResources.DiskSpeed.slow)), NodeType.host, 10, true);
        tester.activateTenantHosts();

        ApplicationId application = ProvisioningTester.applicationId();
        ClusterSpec cluster = ClusterSpec.request(ClusterSpec.Type.container, ClusterSpec.Id.from("test")).vespaVersion("1").build();
        NodeResources resources = new NodeResources(1, 4, 100, 1, requestDiskSpeed);

        try {
            List<HostSpec> hosts = tester.prepare(application, cluster, 4, 1, resources);
            if (expectNodeAllocationFailure) fail("Expected node allocation fail");
            assertEquals(4, hosts.size());
            tester.activate(application, hosts);
        }
        catch (NodeAllocationException e) {
            if ( ! expectNodeAllocationFailure) throw e;
        }
    }

    @Test
    public void node_resources_are_relaxed_in_dev() {
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.dev, RegionName.from("us-east"))).flavorsConfig(flavorsConfig()).build();
        tester.makeReadyNodes(2, new Flavor(new NodeResources(1, 8, 120, 1, NodeResources.DiskSpeed.fast)), NodeType.host, 10, true);
        tester.makeReadyNodes(2, new Flavor(new NodeResources(1, 8, 120, 1, NodeResources.DiskSpeed.slow)), NodeType.host, 10, true);
        tester.activateTenantHosts();

        ApplicationId application = ProvisioningTester.applicationId();
        ClusterSpec cluster = ClusterSpec.request(ClusterSpec.Type.container, ClusterSpec.Id.from("test")).vespaVersion("1").build();
        NodeResources resources = new NodeResources(1, 4, 100, 1, NodeResources.DiskSpeed.fast);

        List<HostSpec> hosts = tester.prepare(application, cluster, 4, 1, resources);
        assertEquals(1, hosts.size());
        tester.activate(application, hosts);
        assertEquals(0.1, hosts.get(0).advertisedResources().vcpu(), 0.000001);
        assertEquals(0.1, hosts.get(0).advertisedResources().bandwidthGbps(), 0.000001);
        assertEquals("Slow nodes are allowed in dev and preferred because they are cheaper",
                     NodeResources.DiskSpeed.slow, hosts.get(0).advertisedResources().diskSpeed());
    }

    @Test
    public void switching_from_legacy_flavor_syntax_to_resources_does_not_cause_reallocation() {
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east"))).flavorsConfig(flavorsConfig()).build();
        tester.makeReadyNodes(2, new Flavor(new NodeResources(5, 20, 1400, 3)), NodeType.host, 10, true);
        tester.activateTenantHosts();

        ApplicationId application = ProvisioningTester.applicationId();
        ClusterSpec cluster = ClusterSpec.request(ClusterSpec.Type.container, ClusterSpec.Id.from("test")).vespaVersion("1").build();

        List<HostSpec> hosts1 = tester.prepare(application, cluster, Capacity.from(new ClusterResources(2, 1, NodeResources.fromLegacyName("d-2-8-500")), false, true));
        tester.activate(application, hosts1);

        NodeResources resources = new NodeResources(1.5, 8, 500, 0.3);
        List<HostSpec> hosts2 = tester.prepare(application, cluster, Capacity.from(new ClusterResources(2, 1, resources)));
        tester.activate(application, hosts2);

        assertEquals(hosts1, hosts2);
    }

    @Test
    public void prefer_exclusive_network_switch() {
        // Hosts are provisioned, without switch information
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east"))).flavorsConfig(flavorsConfig()).build();
        NodeResources hostResources = new NodeResources(32, 128, 2000, 10);
        List<Node> hosts0 = tester.makeReadyNodes(3, hostResources, NodeType.host, 5);
        tester.activateTenantHosts();

        // Application is deployed
        ClusterSpec cluster = ClusterSpec.request(ClusterSpec.Type.container, ClusterSpec.Id.from("test")).vespaVersion("1").build();
        NodeResources resources = new NodeResources(2, 4, 50, 1, NodeResources.DiskSpeed.any);
        ApplicationId app1 = ApplicationId.from("t1", "a1", "i1");
        tester.activate(app1, tester.prepare(app1, cluster, Capacity.from(new ClusterResources(2, 1, resources))));
        tester.assertSwitches(Set.of(), app1, cluster.id());

        // One host is provisioned on a known switch
        String switch0 = "switch0";
        {
            List<Node> hosts = tester.makeReadyNodes(1, hostResources, NodeType.host, 5);
            tester.activateTenantHosts();
            tester.patchNodes(hosts, (host) -> host.withSwitchHostname(switch0));
        }

        // Redeploy does not change allocation as a host with switch information is no better or worse than hosts
        // without switch information
        NodeList allocatedNodes = tester.nodeRepository().nodes().list().owner(app1);
        tester.activate(app1, tester.prepare(app1, cluster, Capacity.from(new ClusterResources(2, 1, resources))));
        assertEquals("Allocation unchanged", allocatedNodes, tester.nodeRepository().nodes().list().owner(app1));

        // Initial hosts are attached to the same switch
        tester.patchNodes(hosts0, (host) -> host.withSwitchHostname(switch0));

        // Redeploy does not change allocation
        tester.activate(app1, tester.prepare(app1, cluster, Capacity.from(new ClusterResources(2, 1, resources))));
        assertEquals("Allocation unchanged", allocatedNodes, tester.nodeRepository().nodes().list().owner(app1));

        // One regular host and one slow-disk host are provisioned on the same switch
        String switch1 = "switch1";
        Node hostWithSlowDisk;
        {
            NodeResources slowDisk = hostResources.with(NodeResources.DiskSpeed.slow);
            List<Node> hosts = tester.makeReadyNodes(1, slowDisk, NodeType.host, 5);
            hosts.addAll(tester.makeReadyNodes(1, hostResources, NodeType.host, 5));
            tester.patchNodes(hosts, (host) -> host.withSwitchHostname(switch1));
            tester.activateTenantHosts();
            hostWithSlowDisk = hosts.get(0);
        }

        // Redeploy does not change allocation as we prefer to keep our already active nodes
        tester.activate(app1, tester.prepare(app1, cluster, Capacity.from(new ClusterResources(2, 1, resources))));
        tester.assertSwitches(Set.of(switch0), app1, cluster.id());

        // A node is retired
        tester.patchNode(tester.nodeRepository().nodes().list().owner(app1).asList().get(0),
                         (node) -> node.withWantToRetire(true, Agent.system, tester.clock().instant()));

        // Redeploy allocates new node on a distinct switch, and the host with slowest disk (cheapest) on that switch
        tester.activate(app1, tester.prepare(app1, cluster, Capacity.from(new ClusterResources(2, 1, resources))));
        tester.assertSwitches(Set.of(switch0, switch1), app1, cluster.id());
        assertTrue("Host with slow disk on " + switch1 + " is chosen", tester.nodeRepository().nodes().list().owner(app1).state(State.active).stream()
                                                                             .anyMatch(node -> node.hasParent(hostWithSlowDisk.hostname())));

        // Growing cluster picks new node on exclusive switch
        String switch2 = "switch2";
        {
            List<Node> hosts = tester.makeReadyNodes(1, hostResources, NodeType.host, 5);
            tester.activateTenantHosts();
            tester.patchNodes(hosts, (host) -> host.withSwitchHostname(switch2));
        }
        tester.activate(app1, tester.prepare(app1, cluster, Capacity.from(new ClusterResources(3, 1, resources))));
        tester.assertSwitches(Set.of(switch0, switch1, switch2), app1, cluster.id());

        // Growing cluster further can reuse switches as we're now out of exclusive ones
        tester.activate(app1, tester.prepare(app1, cluster, Capacity.from(new ClusterResources(4, 1, resources))));
        tester.assertSwitches(Set.of(switch0, switch1, switch2), app1, cluster.id());

        // Additional cluster can reuse switches of existing cluster
        ClusterSpec cluster2 = ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("content")).vespaVersion("1").build();
        tester.activate(app1, tester.prepare(app1, cluster2, Capacity.from(new ClusterResources(3, 1, resources))));
        tester.assertSwitches(Set.of(switch0, switch1, switch2), app1, cluster2.id());

        // Another application is deployed on exclusive switches
        ApplicationId app2 = ApplicationId.from("t2", "a2", "i2");
        tester.activate(app2, tester.prepare(app2, cluster, Capacity.from(new ClusterResources(3, 1, resources))));
        tester.assertSwitches(Set.of(switch0, switch1, switch2), app2, cluster.id());
    }

    private ApplicationId makeApplicationId(String tenant, String appName) {
        return ApplicationId.from(tenant, appName, "default");
    }

    private void deployApp(ApplicationId id, ClusterSpec spec, NodeResources flavor, ProvisioningTester tester, int nodeCount) {
        List<HostSpec> hostSpec = tester.prepare(id, spec, nodeCount, 1, flavor);
        tester.activate(id, new HashSet<>(hostSpec));
    }

    private void addAndAssignNode(ApplicationId id, String hostname, String parentHostname, ClusterSpec clusterSpec, NodeResources flavor, int index, ProvisioningTester tester) {
        Node node1a = Node.create("open1", new IP.Config(Set.of("127.0.233." + index), Set.of()), hostname,
                                  new Flavor(flavor), NodeType.tenant).parentHostname(parentHostname).build();
        ClusterMembership clusterMembership1 = ClusterMembership.from(
                clusterSpec.with(Optional.of(ClusterSpec.Group.from(0))), index); // Need to add group here so that group is serialized in node allocation
        Node node1aAllocation = node1a.allocate(id, clusterMembership1, node1a.resources(), Instant.now());

        tester.nodeRepository().nodes().addNodes(Collections.singletonList(node1aAllocation), Agent.system);
        NestedTransaction transaction = new NestedTransaction().add(new CuratorTransaction(tester.getCurator()));
        tester.nodeRepository().nodes().activate(Collections.singletonList(node1aAllocation), transaction);
        transaction.commit();
    }

    private List<Node> findSpareCapacity(ProvisioningTester tester) {
        NodeList nodes = tester.nodeRepository().nodes().list(State.values());
        return nodes.nodeType(NodeType.host)
                    .matching(host -> nodes.childrenOf(host).size() == 0) // Hosts without children
                    .asList();
    }

    private FlavorsConfig flavorsConfig() {
        FlavorConfigBuilder b = new FlavorConfigBuilder();
        b.addFlavor("host-large", 6, 24, 800, 6, Flavor.Type.BARE_METAL);
        b.addFlavor("host-small", 3, 12, 400, 3, Flavor.Type.BARE_METAL);
        b.addFlavor("flt", 30, 30, 400, 3, Flavor.Type.BARE_METAL);
        b.addFlavor("cpu", 40, 20, 400, 3, Flavor.Type.BARE_METAL);
        b.addFlavor("mem", 20, 40, 400, 3, Flavor.Type.BARE_METAL);
        return b.build();
    }

    private ClusterSpec clusterSpec(String clusterId) {
        return ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from(clusterId)).vespaVersion("6.42").build();
    }

}
