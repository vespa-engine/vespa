// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
import com.yahoo.test.ManualClock;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.curator.transaction.CuratorTransaction;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.vespa.hosted.provision.provisioning.NodeRepositoryProvisioner;
import com.yahoo.vespa.hosted.provision.testutils.FlavorConfigBuilder;
import com.yahoo.vespa.hosted.provision.testutils.MockNameResolver;
import org.junit.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * @author bratseth
 */
public class RetiredExpirerTest {

    private Curator curator = new MockCurator();

    @Test
    public void ensure_retired_nodes_time_out() throws InterruptedException {
        ManualClock clock = new ManualClock();
        Zone zone = new Zone(Environment.prod, RegionName.from("us-east"));
        NodeFlavors nodeFlavors = FlavorConfigBuilder.createDummies("default");
        NodeRepository nodeRepository = new NodeRepository(nodeFlavors, curator, clock, zone,
                new MockNameResolver().mockAnyLookup());
        NodeRepositoryProvisioner provisioner = new NodeRepositoryProvisioner(nodeRepository, nodeFlavors, zone);

        createReadyNodes(7, nodeRepository, nodeFlavors);
        createHostNodes(4, nodeRepository, nodeFlavors);

        ApplicationId applicationId = ApplicationId.from(TenantName.from("foo"), ApplicationName.from("bar"), InstanceName.from("fuz"));

        // Allocate content cluster of sizes 7 -> 2 -> 3:
        // Should end up with 3 nodes in the cluster (one previously retired), and 3 retired
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
        new RetiredExpirer(nodeRepository, deployer, clock, Duration.ofHours(12), new JobControl(nodeRepository.database())).run();
        assertEquals(3, nodeRepository.getNodes(applicationId, Node.State.active).size());
        assertEquals(4, nodeRepository.getNodes(applicationId, Node.State.inactive).size());
        assertEquals(1, deployer.redeployments);

        // inactivated nodes are not retired
        for (Node node : nodeRepository.getNodes(applicationId, Node.State.inactive))
            assertFalse(node.allocation().get().membership().retired());
    }

    @Test
    public void ensure_retired_groups_time_out() throws InterruptedException {
        ManualClock clock = new ManualClock();
        Zone zone = new Zone(Environment.prod, RegionName.from("us-east"));
        NodeFlavors nodeFlavors = FlavorConfigBuilder.createDummies("default");
        NodeRepository nodeRepository = new NodeRepository(nodeFlavors, curator, clock, zone,
                new MockNameResolver().mockAnyLookup());
        NodeRepositoryProvisioner provisioner = new NodeRepositoryProvisioner(nodeRepository, nodeFlavors, zone);

        createReadyNodes(8, nodeRepository, nodeFlavors);
        createHostNodes(4, nodeRepository, nodeFlavors);

        ApplicationId applicationId = ApplicationId.from(TenantName.from("foo"), ApplicationName.from("bar"), InstanceName.from("fuz"));

        ClusterSpec cluster = ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("test"), Version.fromString("6.42"));
        activate(applicationId, cluster, 8, 8, provisioner);
        activate(applicationId, cluster, 1, 1, provisioner);
        assertEquals(8, nodeRepository.getNodes(applicationId, Node.State.active).size());
        assertEquals(0, nodeRepository.getNodes(applicationId, Node.State.inactive).size());

        // Cause inactivation of retired nodes
        clock.advance(Duration.ofHours(30)); // Retire period spent
        MockDeployer deployer =
            new MockDeployer(provisioner,
                             Collections.singletonMap(applicationId, new MockDeployer.ApplicationContext(applicationId, cluster, Capacity.fromNodeCount(1, Optional.of("default")), 1)));
        new RetiredExpirer(nodeRepository, deployer, clock, Duration.ofHours(12), new JobControl(nodeRepository.database())).run();
        assertEquals(1, nodeRepository.getNodes(applicationId, Node.State.active).size());
        assertEquals(7, nodeRepository.getNodes(applicationId, Node.State.inactive).size());
        assertEquals(1, deployer.redeployments);

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

}
