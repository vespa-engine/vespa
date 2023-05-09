// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.History;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisioningTester;
import com.yahoo.vespa.hosted.provision.testutils.MockDeployer;
import com.yahoo.vespa.orchestrator.OrchestrationException;
import com.yahoo.vespa.orchestrator.Orchestrator;
import org.junit.Test;

import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

/**
 * @author bratseth
 * @author mpolden
 */
public class InactiveAndFailedExpirerTest {

    private final NodeResources nodeResources = new NodeResources(2, 8, 50, 1);

    private final ApplicationId applicationId = ApplicationId.from(TenantName.from("foo"),
                                                                   ApplicationName.from("bar"),
                                                                   InstanceName.from("fuz"));

    @Test
    public void inactive_and_failed_times_out() {
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east"))).build();
        tester.makeReadyNodes(2, nodeResources);

        // Allocate then deallocate 2 nodes
        ClusterSpec cluster = ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("test")).vespaVersion("6.42").build();
        List<HostSpec> preparedNodes = tester.prepare(applicationId, cluster, Capacity.from(new ClusterResources(2, 1, nodeResources)));
        tester.activate(applicationId, new HashSet<>(preparedNodes));
        assertEquals(2, tester.getNodes(applicationId, Node.State.active).size());
        tester.activate(applicationId, List.of());
        List<Node> inactiveNodes = tester.getNodes(applicationId, Node.State.inactive).asList();
        assertEquals(2, inactiveNodes.size());

        // Inactive times out
        tester.advanceTime(Duration.ofMinutes(14));
        new InactiveExpirer(tester.nodeRepository(), Duration.ofMinutes(10), new TestMetric()).run();
        assertEquals(0, tester.nodeRepository().nodes().list(Node.State.inactive).size());
        NodeList dirty = tester.nodeRepository().nodes().list(Node.State.dirty);
        assertEquals(2, dirty.size());

        // One node is set back to ready
        Node ready = tester.move(Node.State.ready, dirty.asList().get(0));
        assertEquals("Allocated history is removed on readying",
                List.of(History.Event.Type.provisioned, History.Event.Type.readied),
                ready.history().events().stream().map(History.Event::type).toList());

        // Dirty times out for the other one
        tester.advanceTime(Duration.ofMinutes(14));
        new DirtyExpirer(tester.nodeRepository(), Duration.ofMinutes(10), new TestMetric()).run();
        assertEquals(0, tester.nodeRepository().nodes().list(Node.State.dirty).nodeType(NodeType.tenant).size());
        NodeList failed = tester.nodeRepository().nodes().list(Node.State.failed).nodeType(NodeType.tenant);
        assertEquals(1, failed.size());
        assertEquals(1, failed.first().get().status().failCount());
    }

    @Test
    public void node_that_wants_to_retire_is_moved_to_parked() throws OrchestrationException {
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east"))).build();
        ClusterSpec cluster = ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("test")).vespaVersion("6.42").build();
        tester.makeReadyNodes(5, nodeResources);

        // Allocate two nodes
        {
            List<HostSpec> hostSpecs = tester.prepare(applicationId,
                                                      cluster,
                                                      Capacity.from(new ClusterResources(2, 1, nodeResources)));
            tester.activate(applicationId, new HashSet<>(hostSpecs));
            assertEquals(2, tester.getNodes(applicationId, Node.State.active).size());
        }

        // Flag one node for retirement and redeploy
        {
            Node toRetire = tester.getNodes(applicationId, Node.State.active).asList().get(0);
            tester.patchNode(toRetire, (node) -> node.withWantToRetire(true, Agent.operator, tester.clock().instant()));
            List<HostSpec> hostSpecs = tester.prepare(applicationId, cluster, Capacity.from(new ClusterResources(2, 1, nodeResources)));
            tester.activate(applicationId, new HashSet<>(hostSpecs));
        }

        // Retire times out and one node is moved to inactive
        tester.advanceTime(Duration.ofMinutes(11)); // Trigger RetiredExpirer
        MockDeployer deployer = new MockDeployer(
                tester.provisioner(),
                tester.clock(),
                Collections.singletonMap(
                        applicationId,
                        new MockDeployer.ApplicationContext(applicationId, cluster,
                                                            Capacity.from(new ClusterResources(2, 1, nodeResources),
                                                                          false, true))
                )
        );
        Orchestrator orchestrator = mock(Orchestrator.class);
        doThrow(new RuntimeException()).when(orchestrator).acquirePermissionToRemove(any());
        new RetiredExpirer(tester.nodeRepository(), deployer, new TestMetric(),
                           Duration.ofDays(30), Duration.ofMinutes(10)).run();
        assertEquals(1, tester.nodeRepository().nodes().list(Node.State.inactive).size());

        // Inactive times out and one node is moved to parked
        tester.advanceTime(Duration.ofMinutes(11)); // Trigger InactiveExpirer
        new InactiveExpirer(tester.nodeRepository(), Duration.ofMinutes(10), new TestMetric()).run();
        assertEquals(1, tester.nodeRepository().nodes().list(Node.State.parked).size());
    }

    @Test
    public void tester_applications_expire_immediately() {
        ApplicationId testerId = ApplicationId.from(applicationId.tenant().value(),
                                                    applicationId.application().value(),
                                                    applicationId.instance().value() + "-t");

        // Allocate then deallocate a node
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east"))).build();
        tester.makeReadyNodes(1, nodeResources);
        ClusterSpec cluster = ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("test")).vespaVersion("6.42").build();
        List<HostSpec> preparedNodes = tester.prepare(testerId, cluster, Capacity.from(new ClusterResources(2, 1, nodeResources)));
        tester.activate(testerId, new HashSet<>(preparedNodes));
        assertEquals(1, tester.getNodes(testerId, Node.State.active).size());
        tester.activate(testerId, List.of());
        List<Node> inactiveNodes = tester.getNodes(testerId, Node.State.inactive).asList();
        assertEquals(1, inactiveNodes.size());

        // See that nodes are moved to dirty immediately.
        new InactiveExpirer(tester.nodeRepository(), Duration.ofMinutes(10), new TestMetric()).run();
        assertEquals(0, tester.nodeRepository().nodes().list(Node.State.inactive).size());
        NodeList dirty = tester.nodeRepository().nodes().list(Node.State.dirty);
        assertEquals(1, dirty.size());
        assertTrue(dirty.first().get().allocation().isPresent());
    }

    @Test
    public void nodes_marked_for_deprovisioning_move_to_parked() {
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east"))).build();
        tester.makeReadyHosts(2, nodeResources);

        // Activate and deallocate
        ClusterSpec cluster = ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("test")).vespaVersion("6.42").build();
        List<HostSpec> preparedNodes = tester.prepare(applicationId, cluster, Capacity.fromRequiredNodeType(NodeType.host));
        tester.activate(applicationId, new HashSet<>(preparedNodes));
        assertEquals(2, tester.getNodes(applicationId, Node.State.active).size());
        tester.activate(applicationId, List.of());
        List<Node> inactiveNodes = tester.getNodes(applicationId, Node.State.inactive).asList();
        assertEquals(2, inactiveNodes.size());

        // Nodes marked for deprovisioning are moved to dirty and then parked when readied by host-admin
        tester.patchNodes(inactiveNodes, (node) -> node.withWantToRetire(true, true, Agent.system, tester.clock().instant()));
        tester.advanceTime(Duration.ofMinutes(11));
        new InactiveExpirer(tester.nodeRepository(), Duration.ofMinutes(10), new TestMetric()).run();

        NodeList expired = tester.nodeRepository().nodes().list(Node.State.dirty);
        assertEquals(2, expired.size());
        expired.forEach(node -> tester.nodeRepository().nodes().markNodeAvailableForNewAllocation(node.hostname(), Agent.operator, "Readied by host-admin"));
        assertEquals(2, tester.nodeRepository().nodes().list(Node.State.parked).size());
    }

}
