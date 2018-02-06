// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Deployer;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.test.ManualClock;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.curator.transaction.CuratorTransaction;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.provisioning.FlavorConfigBuilder;
import com.yahoo.vespa.hosted.provision.provisioning.NodeRepositoryProvisioner;
import com.yahoo.vespa.hosted.provision.testutils.MockDeployer;
import com.yahoo.vespa.hosted.provision.testutils.MockNameResolver;
import com.yahoo.vespa.orchestrator.OrchestrationException;
import com.yahoo.vespa.orchestrator.Orchestrator;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author bratseth
 */
public class RetiredExpirerTest {

    private Curator curator = new MockCurator();
    private final ManualClock clock = new ManualClock();
    private final Zone zone = new Zone(Environment.prod, RegionName.from("us-east"));
    private final NodeFlavors nodeFlavors = FlavorConfigBuilder.createDummies("default");
    private final NodeRepository nodeRepository = new NodeRepository(nodeFlavors, curator, clock, zone,
            new MockNameResolver().mockAnyLookup(),
            new DockerImage("docker-registry.domain.tld:8080/dist/vespa"));
    private final NodeRepositoryProvisioner provisioner = new NodeRepositoryProvisioner(nodeRepository, nodeFlavors, zone);
    private final Orchestrator orchestrator = mock(Orchestrator.class);

    private static final Duration RETIRED_EXPIRATION = Duration.ofHours(12);

    @Before
    public void setup() throws OrchestrationException {
        // By default, orchestrator should deny all request for suspension so we can test expiration
        doThrow(new RuntimeException()).when(orchestrator).acquirePermissionToRemove(any());
    }

    @Test
    public void ensure_retired_nodes_time_out() {
        createReadyNodes(7, nodeRepository, nodeFlavors);
        createHostNodes(4, nodeRepository, nodeFlavors);

        ApplicationId applicationId = ApplicationId.from(TenantName.from("foo"), ApplicationName.from("bar"), InstanceName.from("fuz"));

        // Allocate content cluster of sizes 7 -> 2 -> 3:
        // Should end up with 3 nodes in the cluster (one previously retired), and 4 retired
        ClusterSpec cluster = ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("test"), Version.fromString("6.42"));
        int wantedNodes;
        activate(applicationId, cluster, wantedNodes=7, 1, provisioner);
        activate(applicationId, cluster, wantedNodes=2, 1, provisioner);
        activate(applicationId, cluster, wantedNodes=3, 1, provisioner);
        assertEquals(7, nodeRepository.getNodes(applicationId, Node.State.active).size());
        assertEquals(0, nodeRepository.getNodes(applicationId, Node.State.inactive).size());

        // Cause inactivation of retired nodes
        clock.advance(Duration.ofHours(30)); // Retire period spent
        MockDeployer deployer =
            new MockDeployer(provisioner,
                             Collections.singletonMap(applicationId, new MockDeployer.ApplicationContext(applicationId, cluster, Capacity.fromNodeCount(wantedNodes, Optional.of("default")), 1)));
        createRetiredExpirer(deployer).run();
        assertEquals(3, nodeRepository.getNodes(applicationId, Node.State.active).size());
        assertEquals(4, nodeRepository.getNodes(applicationId, Node.State.inactive).size());
        assertEquals(1, deployer.redeployments);

        // inactivated nodes are not retired
        for (Node node : nodeRepository.getNodes(applicationId, Node.State.inactive))
            assertFalse(node.allocation().get().membership().retired());
    }

    @Test
    public void ensure_retired_groups_time_out() {
        createReadyNodes(8, nodeRepository, nodeFlavors);
        createHostNodes(4, nodeRepository, nodeFlavors);

        ApplicationId applicationId = ApplicationId.from(TenantName.from("foo"), ApplicationName.from("bar"), InstanceName.from("fuz"));

        ClusterSpec cluster = ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("test"), Version.fromString("6.42"));
        activate(applicationId, cluster, 8, 8, provisioner);
        activate(applicationId, cluster, 2, 2, provisioner);
        assertEquals(8, nodeRepository.getNodes(applicationId, Node.State.active).size());
        assertEquals(0, nodeRepository.getNodes(applicationId, Node.State.inactive).size());

        // Cause inactivation of retired nodes
        clock.advance(Duration.ofHours(30)); // Retire period spent
        MockDeployer deployer =
            new MockDeployer(provisioner,
                             Collections.singletonMap(applicationId, new MockDeployer.ApplicationContext(applicationId, cluster, Capacity.fromNodeCount(2, Optional.of("default")), 1)));
        createRetiredExpirer(deployer).run();
        assertEquals(2, nodeRepository.getNodes(applicationId, Node.State.active).size());
        assertEquals(6, nodeRepository.getNodes(applicationId, Node.State.inactive).size());
        assertEquals(1, deployer.redeployments);

        // inactivated nodes are not retired
        for (Node node : nodeRepository.getNodes(applicationId, Node.State.inactive))
            assertFalse(node.allocation().get().membership().retired());
    }

    @Test
    public void ensure_early_inactivation() throws OrchestrationException {
        createReadyNodes(7, nodeRepository, nodeFlavors);
        createHostNodes(4, nodeRepository, nodeFlavors);

        ApplicationId applicationId = ApplicationId.from(TenantName.from("foo"), ApplicationName.from("bar"), InstanceName.from("fuz"));

        // Allocate content cluster of sizes 7 -> 2 -> 3:
        // Should end up with 3 nodes in the cluster (one previously retired), and 4 retired
        ClusterSpec cluster = ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("test"), Version.fromString("6.42"));
        int wantedNodes;
        activate(applicationId, cluster, wantedNodes=7, 1, provisioner);
        activate(applicationId, cluster, wantedNodes=2, 1, provisioner);
        activate(applicationId, cluster, wantedNodes=3, 1, provisioner);
        assertEquals(7, nodeRepository.getNodes(applicationId, Node.State.active).size());
        assertEquals(0, nodeRepository.getNodes(applicationId, Node.State.inactive).size());

        // Cause inactivation of retired nodes
        MockDeployer deployer =
                new MockDeployer(provisioner,
                        Collections.singletonMap(
                                applicationId,
                                new MockDeployer.ApplicationContext(applicationId, cluster, Capacity.fromNodeCount(wantedNodes, Optional.of("default")), 1)));


        // Allow the 1st and 3rd retired nodes permission to inactivate
        doNothing()
                .doThrow(new OrchestrationException("Permission not granted 1"))
                .doNothing()
                .doThrow(new OrchestrationException("Permission not granted 2"))
                .when(orchestrator).acquirePermissionToRemove(any());

        RetiredExpirer retiredExpirer = createRetiredExpirer(deployer);
        retiredExpirer.run();
        assertEquals(5, nodeRepository.getNodes(applicationId, Node.State.active).size());
        assertEquals(2, nodeRepository.getNodes(applicationId, Node.State.inactive).size());
        assertEquals(1, deployer.redeployments);
        verify(orchestrator, times(4)).acquirePermissionToRemove(any());

        // Running it again has no effect
        retiredExpirer.run();
        assertEquals(5, nodeRepository.getNodes(applicationId, Node.State.active).size());
        assertEquals(2, nodeRepository.getNodes(applicationId, Node.State.inactive).size());
        assertEquals(1, deployer.redeployments);
        verify(orchestrator, times(6)).acquirePermissionToRemove(any());

        clock.advance(RETIRED_EXPIRATION.plusMinutes(1));
        retiredExpirer.run();
        assertEquals(3, nodeRepository.getNodes(applicationId, Node.State.active).size());
        assertEquals(4, nodeRepository.getNodes(applicationId, Node.State.inactive).size());
        assertEquals(2, deployer.redeployments);
        verify(orchestrator, times(6)).acquirePermissionToRemove(any());

        // inactivated nodes are not retired
        for (Node node : nodeRepository.getNodes(applicationId, Node.State.inactive))
            assertFalse(node.allocation().get().membership().retired());
    }

    private void activate(ApplicationId applicationId, ClusterSpec cluster, int nodes, int groups, NodeRepositoryProvisioner provisioner) {
        List<HostSpec> hosts = provisioner.prepare(applicationId, cluster, Capacity.fromNodeCount(nodes), groups, null);
        NestedTransaction transaction = new NestedTransaction().add(new CuratorTransaction(curator));
        provisioner.activate(transaction, applicationId, hosts);
        transaction.commit();
    }

    private void createReadyNodes(int count, NodeRepository nodeRepository, NodeFlavors nodeFlavors) {
        List<Node> nodes = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
            nodes.add(nodeRepository.createNode("node" + i, "node" + i, Optional.empty(), nodeFlavors.getFlavorOrThrow("default"), NodeType.tenant));
        nodes = nodeRepository.addNodes(nodes);
        nodes = nodeRepository.setDirty(nodes);
        nodeRepository.setReady(nodes);
    }

    private void createHostNodes(int count, NodeRepository nodeRepository, NodeFlavors nodeFlavors) {
        List<Node> nodes = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
            nodes.add(nodeRepository.createNode("parent" + i, "parent" + i, Optional.empty(), nodeFlavors.getFlavorOrThrow("default"), NodeType.host));
        nodes = nodeRepository.addNodes(nodes);
        nodes = nodeRepository.setDirty(nodes);
        nodeRepository.setReady(nodes);
    }

    private RetiredExpirer createRetiredExpirer(Deployer deployer) {
        return new RetiredExpirer(
                nodeRepository,
                orchestrator,
                deployer,
                clock,
                Duration.ofDays(30), /* Maintenance interval, use large value so it never runs by itself */
                RETIRED_EXPIRATION,
                new JobControl(nodeRepository.database()));
    }
}
