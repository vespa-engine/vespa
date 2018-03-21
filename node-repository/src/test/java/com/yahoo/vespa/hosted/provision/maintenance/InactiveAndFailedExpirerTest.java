// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.History;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisioningTester;
import com.yahoo.vespa.hosted.provision.testutils.MockDeployer;
import com.yahoo.vespa.orchestrator.OrchestrationException;
import com.yahoo.vespa.orchestrator.Orchestrator;
import org.junit.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

/**
 * @author bratseth
 * @author mpolden
 */
public class InactiveAndFailedExpirerTest {

    private final ApplicationId applicationId = ApplicationId.from(TenantName.from("foo"), ApplicationName.from("bar"),
            InstanceName.from("fuz"));

    @Test
    public void inactive_and_failed_times_out() {
        ProvisioningTester tester = new ProvisioningTester(new Zone(Environment.prod, RegionName.from("us-east")));
        List<Node> nodes = tester.makeReadyNodes(2, "default");

        // Allocate then deallocate 2 nodes
        ClusterSpec cluster = ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("test"), Version.fromString("6.42"), false);
        List<HostSpec> preparedNodes = tester.prepare(applicationId, cluster, Capacity.fromNodeCount(2), 1);
        tester.activate(applicationId, new HashSet<>(preparedNodes));
        assertEquals(2, tester.getNodes(applicationId, Node.State.active).size());
        tester.deactivate(applicationId);
        List<Node> inactiveNodes = tester.getNodes(applicationId, Node.State.inactive).asList();
        assertEquals(2, inactiveNodes.size());

        // Inactive times out
        tester.advanceTime(Duration.ofMinutes(14));
        new InactiveExpirer(tester.nodeRepository(), tester.clock(), Duration.ofMinutes(10), new JobControl(tester.nodeRepository().database())).run();
        assertEquals(0, tester.nodeRepository().getNodes(Node.State.inactive).size());
        List<Node> dirty = tester.nodeRepository().getNodes(Node.State.dirty);
        assertEquals(2, dirty.size());
        assertFalse(dirty.get(0).allocation().isPresent());
        assertFalse(dirty.get(1).allocation().isPresent());

        // One node is set back to ready
        Node ready = tester.nodeRepository().setReady(Collections.singletonList(dirty.get(0)), Agent.system, getClass().getSimpleName()).get(0);
        assertEquals("Allocated history is removed on readying",
                Arrays.asList(History.Event.Type.provisioned, History.Event.Type.readied),
                ready.history().events().stream().map(History.Event::type).collect(Collectors.toList()));

        // Dirty times out for the other one
        tester.advanceTime(Duration.ofMinutes(14));
        new DirtyExpirer(tester.nodeRepository(), tester.clock(), Duration.ofMinutes(10), new JobControl(tester.nodeRepository().database())).run();
        assertEquals(0, tester.nodeRepository().getNodes(NodeType.tenant, Node.State.dirty).size());
        List<Node> failed = tester.nodeRepository().getNodes(NodeType.tenant, Node.State.failed);
        assertEquals(1, failed.size());
        assertEquals(1, failed.get(0).status().failCount());
    }

    @Test
    public void reboot_generation_is_increased_when_node_moves_to_dirty() {
        ProvisioningTester tester = new ProvisioningTester(new Zone(Environment.prod, RegionName.from("us-east")));
        List<Node> nodes = tester.makeReadyNodes(2, "default");

        // Allocate and deallocate a single node
        ClusterSpec cluster = ClusterSpec.request(ClusterSpec.Type.content,
                                                  ClusterSpec.Id.from("test"), 
                                                  Version.fromString("6.42"),
                                                  false);
        List<HostSpec> preparedNodes = tester.prepare(applicationId, cluster, Capacity.fromNodeCount(2), 1);
        tester.activate(applicationId, new HashSet<>(preparedNodes));
        assertEquals(2, tester.getNodes(applicationId, Node.State.active).size());
        tester.deactivate(applicationId);
        List<Node> inactiveNodes = tester.getNodes(applicationId, Node.State.inactive).asList();
        assertEquals(2, inactiveNodes.size());

        // Check reboot generation before node is moved. New nodes transition from provisioned to dirty, so their
        // wanted reboot generation will always be 1.
        long wantedRebootGeneration = inactiveNodes.get(0).status().reboot().wanted();
        assertEquals(1, wantedRebootGeneration);

        // Inactive times out and node is moved to dirty
        tester.advanceTime(Duration.ofMinutes(14));
        new InactiveExpirer(tester.nodeRepository(), tester.clock(), Duration.ofMinutes(10), new JobControl(tester.nodeRepository().database())).run();
        List<Node> dirty = tester.nodeRepository().getNodes(Node.State.dirty);
        assertEquals(2, dirty.size());

        // Reboot generation is increased
        assertEquals(wantedRebootGeneration + 1, dirty.get(0).status().reboot().wanted());
    }

    @Test
    public void node_that_wants_to_retire_is_moved_to_parked() throws OrchestrationException {
        ProvisioningTester tester = new ProvisioningTester(new Zone(Environment.prod, RegionName.from("us-east")));
        ClusterSpec cluster = ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("test"), 
                                                  Version.fromString("6.42"), false);
        tester.makeReadyNodes(5, "default");

        // Allocate two nodes
        {
            List<HostSpec> hostSpecs = tester.prepare(applicationId, cluster, Capacity.fromNodeCount(2), 1);
            tester.activate(applicationId, new HashSet<>(hostSpecs));
            assertEquals(2, tester.getNodes(applicationId, Node.State.active).size());
        }


        // Flag one node for retirement and redeploy
        {
            Node toRetire = tester.getNodes(applicationId, Node.State.active).asList().get(0);
            tester.patchNode(toRetire.with(toRetire.status().withWantToRetire(true)));
            List<HostSpec> hostSpecs = tester.prepare(applicationId, cluster, Capacity.fromNodeCount(2), 1);
            tester.activate(applicationId, new HashSet<>(hostSpecs));
        }

        // Retire times out and one node is moved to inactive
        tester.advanceTime(Duration.ofMinutes(11)); // Trigger RetiredExpirer
        MockDeployer deployer = new MockDeployer(
                tester.provisioner(),
                Collections.singletonMap(
                        applicationId,
                        new MockDeployer.ApplicationContext(applicationId, cluster,
                                                            Capacity.fromNodeCount(2,
                                                                                   Optional.of("default"),
                                                                                   false),
                                                            1)
                )
        );
        Orchestrator orchestrator = mock(Orchestrator.class);
        doThrow(new RuntimeException()).when(orchestrator).acquirePermissionToRemove(any());
        new RetiredExpirer(tester.nodeRepository(), tester.orchestrator(), deployer, tester.clock(), Duration.ofDays(30),
                Duration.ofMinutes(10), new JobControl(tester.nodeRepository().database())).run();
        assertEquals(1, tester.nodeRepository().getNodes(Node.State.inactive).size());

        // Inactive times out and one node is moved to parked
        tester.advanceTime(Duration.ofMinutes(11)); // Trigger InactiveExpirer
        new InactiveExpirer(tester.nodeRepository(), tester.clock(), Duration.ofMinutes(10), new JobControl(tester.nodeRepository().database())).run();
        assertEquals(1, tester.nodeRepository().getNodes(Node.State.parked).size());
    }
}
