// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.Zone;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.provisioning.FlavorConfigBuilder;
import com.yahoo.vespa.hosted.provision.provisioning.NodeRepositoryProvisioner;
import com.yahoo.vespa.hosted.provision.testutils.MockNameResolver;
import com.yahoo.vespa.hosted.provision.testutils.MockProvisionServiceProvider;
import org.junit.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * @author bratseth
 */
public class ReservationExpirerTest {

    private final Curator curator = new MockCurator();

    @Test
    public void ensure_reservation_times_out() {
        ManualClock clock = new ManualClock();
        NodeFlavors flavors = FlavorConfigBuilder.createDummies("default");
        NodeRepository nodeRepository = new NodeRepository(flavors, curator, clock, Zone.defaultZone(),
                                                           new MockNameResolver().mockAnyLookup(),
                                                           DockerImage.fromString("docker-registry.domain.tld:8080/dist/vespa"),
                                                           true, new InMemoryFlagSource());
        NodeRepositoryProvisioner provisioner = new NodeRepositoryProvisioner(nodeRepository, Zone.defaultZone(), new MockProvisionServiceProvider(), new InMemoryFlagSource());

        List<Node> nodes = new ArrayList<>(2);
        nodes.add(nodeRepository.createNode(UUID.randomUUID().toString(), UUID.randomUUID().toString(), Optional.empty(), new Flavor(new NodeResources(2, 8, 50, 1)), NodeType.tenant));
        nodes.add(nodeRepository.createNode(UUID.randomUUID().toString(), UUID.randomUUID().toString(), Optional.empty(), new Flavor(new NodeResources(2, 8, 50, 1)), NodeType.tenant));
        nodes.add(nodeRepository.createNode(UUID.randomUUID().toString(), UUID.randomUUID().toString(), Optional.empty(), flavors.getFlavorOrThrow("default"), NodeType.host));
        nodes = nodeRepository.addNodes(nodes, Agent.system);
        nodes = nodeRepository.setDirty(nodes, Agent.system, getClass().getSimpleName());

        // Reserve 2 nodes
        assertEquals(2, nodeRepository.getNodes(NodeType.tenant, Node.State.dirty).size());
        nodeRepository.setReady(nodes, Agent.system, getClass().getSimpleName());
        ApplicationId applicationId = new ApplicationId.Builder().tenant("foo").applicationName("bar").instanceName("fuz").build();
        ClusterSpec cluster = ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("test"), Version.fromString("6.42"), false, Optional.empty());
        provisioner.prepare(applicationId, cluster, Capacity.fromCount(2, new NodeResources(2, 8, 50, 1)), 1, null);
        assertEquals(2, nodeRepository.getNodes(NodeType.tenant, Node.State.reserved).size());

        // Reservation times out
        clock.advance(Duration.ofMinutes(14)); // Reserved but not used time out
        new ReservationExpirer(nodeRepository, clock, Duration.ofMinutes(10)).run();

        // Assert nothing is reserved
        assertEquals(0, nodeRepository.getNodes(NodeType.tenant, Node.State.reserved).size());
        List<Node> dirty = nodeRepository.getNodes(NodeType.tenant, Node.State.dirty);
        assertEquals(2, dirty.size());
        assertFalse(dirty.get(0).allocation().isPresent());
        assertFalse(dirty.get(1).allocation().isPresent());
    }

}
