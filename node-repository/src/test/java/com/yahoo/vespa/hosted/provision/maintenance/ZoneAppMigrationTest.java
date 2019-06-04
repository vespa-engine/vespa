package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisioningTester;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.yahoo.config.provision.ClusterSpec.Type.container;
import static com.yahoo.config.provision.ClusterSpec.Type.content;
import static org.junit.Assert.assertEquals;

/**
 * This is a temporary test to verify the requirements needed for a successful migration of tenant
 * host nodes out of the zone-application.
 *
 * TODO: Remove after removing tenant hosts from zone-app
 *
 * @author freva
 */
public class ZoneAppMigrationTest {

    private final ManualClock clock = new ManualClock();
    private final ProvisioningTester tester = new ProvisioningTester.Builder().build();
    private final InactiveExpirer inactiveExpirer = new InactiveExpirer(tester.nodeRepository(), clock, Duration.ofDays(99));

    private final Version version = Version.fromString("7.42.23");

    private final ApplicationId zoneApp = ApplicationId.from("hosted-vespa", "routing", "default");
    private final ApplicationId proxyHostApp = ApplicationId.from("hosted-vespa", "proxy-host", "default");
    private final ApplicationId tenantHostApp = ApplicationId.from("hosted-vespa", "tenant-host", "default");
    private final ApplicationId app1 = tester.makeApplicationId();
    private final ApplicationId app2 = tester.makeApplicationId();


    @Test
    public void tenant_host_deallocation_test() {
        assertEquals(5, tester.nodeRepository().getNodes(NodeType.proxy, Node.State.active).size());
        assertEquals(20, tester.nodeRepository().getNodes(NodeType.host, Node.State.active).size());
        assertEquals(15, tester.nodeRepository().getNodes(NodeType.tenant, Node.State.active).size());

        Set<Node> tenantNodes = Set.copyOf(tester.nodeRepository().getNodes(NodeType.tenant));

        // Activate zone-app with only proxy nodes, all tenant hosts become inactive, no change to other nodes
        tester.activate(zoneApp, prepareSystemApplication(zoneApp, NodeType.proxy, "routing"));
        assertEquals(5, tester.nodeRepository().getNodes(NodeType.proxy, Node.State.active).size());
        assertEquals(20, tester.nodeRepository().getNodes(NodeType.host, Node.State.inactive).size());
        assertEquals(tenantNodes, Set.copyOf(tester.nodeRepository().getNodes(NodeType.tenant)));

        // All tenant hosts become dirty, no change to other nodes
        inactiveExpirer.maintain();
        assertEquals(5, tester.nodeRepository().getNodes(NodeType.proxy, Node.State.active).size());
        assertEquals(20, tester.nodeRepository().getNodes(NodeType.host, Node.State.dirty).size());
        assertEquals(tenantNodes, Set.copyOf(tester.nodeRepository().getNodes(NodeType.tenant)));
        // No reboot generation incrementation
        assertEquals(0, tester.nodeRepository().getNodes(NodeType.host).stream().mapToLong(node -> node.status().reboot().wanted()).sum());

        tester.nodeRepository().getNodes(NodeType.host)
                .forEach(node -> tester.nodeRepository().setReady(node.hostname(), Agent.operator, "Readied by host-admin"));
        assertEquals(5, tester.nodeRepository().getNodes(NodeType.proxy, Node.State.active).size());
        assertEquals(20, tester.nodeRepository().getNodes(NodeType.host, Node.State.ready).size());
        assertEquals(tenantNodes, Set.copyOf(tester.nodeRepository().getNodes(NodeType.tenant)));

        tester.activate(tenantHostApp, prepareSystemApplication(tenantHostApp, NodeType.host, "tenant-host"));
        assertEquals(5, tester.nodeRepository().getNodes(NodeType.proxy, Node.State.active).size());
        assertEquals(20, tester.nodeRepository().getNodes(NodeType.host, Node.State.active).size());
        assertEquals(tenantNodes, Set.copyOf(tester.nodeRepository().getNodes(NodeType.tenant)));

        // All tenant hosts are allocated to tenant host application
        assertEquals(Set.copyOf(tester.nodeRepository().getNodes(NodeType.host)),
                Set.copyOf(tester.nodeRepository().getNodes(tenantHostApp)));

        // All proxy nodes are still allocated to zone-app
        assertEquals(Set.copyOf(tester.nodeRepository().getNodes(NodeType.proxy)),
                Set.copyOf(tester.nodeRepository().getNodes(zoneApp)));
    }

    @Test
    public void conflicting_type_allocation_test() {
        // Re-allocate tenant host from zone-app to tenant-host app
        tester.activate(zoneApp, prepareSystemApplication(zoneApp, NodeType.proxy, "routing"));
        inactiveExpirer.maintain();
        tester.nodeRepository().getNodes(NodeType.host)
                .forEach(node -> tester.nodeRepository().setReady(node.hostname(), Agent.operator, "Readied by host-admin"));
        tester.activate(tenantHostApp, prepareSystemApplication(tenantHostApp, NodeType.host, "tenant-host"));

        // Re-deploying zone-app with both type proxy and host has no effect (no tenant hosts are re-allocated from tenant-host app)
        Set<Node> allNodes = Set.copyOf(tester.nodeRepository().getNodes());
        List<HostSpec> proxyHostSpecs = prepareSystemApplication(zoneApp, NodeType.proxy, "routing");
        List<HostSpec> nodeAdminHostSpecs = prepareSystemApplication(zoneApp, NodeType.host, "node-admin");
        List<HostSpec> zoneAppHostSpecs = concat(proxyHostSpecs, nodeAdminHostSpecs, Collectors.toList());
        tester.activate(zoneApp, zoneAppHostSpecs);
        assertEquals(0, nodeAdminHostSpecs.size());
        assertEquals(allNodes, Set.copyOf(tester.nodeRepository().getNodes()));

        // Provision another host and redeploy zone-app
        Node newHost = tester.makeReadyNodes(1, "large", NodeType.host).get(0);
        proxyHostSpecs = prepareSystemApplication(zoneApp, NodeType.proxy, "routing");
        nodeAdminHostSpecs = prepareSystemApplication(zoneApp, NodeType.host, "node-admin");
        zoneAppHostSpecs = concat(proxyHostSpecs, nodeAdminHostSpecs, Collectors.toList());
        tester.activate(zoneApp, zoneAppHostSpecs);

        assertEquals(1, nodeAdminHostSpecs.size()); // The newly provisioned host is prepared
        newHost = tester.nodeRepository().getNode(newHost.hostname()).orElseThrow(); // Update newHost after it has been allocated
        Set<Node> allNodesWithNewHost = concat(allNodes, Set.of(newHost), Collectors.toSet());
        assertEquals(allNodesWithNewHost, Set.copyOf(tester.nodeRepository().getNodes()));
        // The new host is allocated to zone-app, while the old ones are still allocated to tenant-host app
        assertEquals(zoneApp, newHost.allocation().get().owner());
    }

    @Before
    public void setup() {
        tester.makeReadyNodes(5, "large", NodeType.proxyhost);
        tester.makeReadyNodes(5, "large", NodeType.proxy);
        tester.makeReadyNodes(20, "large", NodeType.host, 3);

        tester.activate(proxyHostApp, prepareSystemApplication(proxyHostApp, NodeType.proxyhost, "proxy-host"));
        List<HostSpec> proxyHostSpecs = prepareSystemApplication(zoneApp, NodeType.proxy, "routing");
        List<HostSpec> nodeAdminHostSpecs = prepareSystemApplication(zoneApp, NodeType.host, "node-admin");
        List<HostSpec> zoneAppHostSpecs = concat(proxyHostSpecs, nodeAdminHostSpecs, Collectors.toList());
        tester.activate(zoneApp, zoneAppHostSpecs);

        activateTenantApplication(app1, 3, 4);
        activateTenantApplication(app2, 5, 3);
    }

    private List<HostSpec> prepareSystemApplication(ApplicationId applicationId, NodeType nodeType, String clusterId) {
        return tester.prepare(applicationId,
                ClusterSpec.request(container, ClusterSpec.Id.from(clusterId), version, false, Set.of()),
                Capacity.fromRequiredNodeType(nodeType),
                1);
    }

    private void activateTenantApplication(ApplicationId app, int numContainerNodes, int numContentNodes) {
        List<HostSpec> combinedHostSpecs = new ArrayList<>(numContainerNodes + numContentNodes);

        combinedHostSpecs.addAll(tester.prepare(app,
                ClusterSpec.request(container, ClusterSpec.Id.from("web"), version, false, Set.of()),
                Capacity.fromCount(numContainerNodes, new NodeResources(2, 2, 50)),
                1));

        combinedHostSpecs.addAll(tester.prepare(app,
                ClusterSpec.request(content, ClusterSpec.Id.from("store"), version, false, Set.of()),
                Capacity.fromCount(numContentNodes, new NodeResources(1, 4, 50)),
                1));

        tester.activate(app, combinedHostSpecs);
    }

    private <T, R, A> R concat(Collection<T> c1, Collection<T> c2, Collector<? super T, A, R> collector) {
        return Stream.concat(c1.stream(), c2.stream())
                .collect(collector);
    }
}
