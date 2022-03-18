// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.provisioning.FlavorConfigBuilder;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisioningTester;
import org.junit.Test;

import java.time.Duration;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class ReservationExpirerTest {

    @Test
    public void ensure_reservation_times_out() {
        NodeFlavors flavors = FlavorConfigBuilder.createDummies("default");
        ProvisioningTester tester = new ProvisioningTester.Builder().flavors(flavors.getFlavors()).build();
        ManualClock clock = tester.clock();
        NodeRepository nodeRepository = tester.nodeRepository();
        TestMetric metric = new TestMetric();

        NodeResources nodeResources = new NodeResources(2, 8, 50, 1).justNumbers();
        NodeResources hostResources = nodeResources.add(nodeResources).add(nodeResources);
        tester.makeReadyNodes(2, nodeResources);
        tester.makeReadyHosts(1, hostResources);

        // Reserve 2 nodes
        assertEquals(2, nodeRepository.nodes().list(Node.State.ready).nodeType(NodeType.tenant).size());
        ApplicationId applicationId = new ApplicationId.Builder().tenant("foo").applicationName("bar").instanceName("fuz").build();
        ClusterSpec cluster = ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("test")).vespaVersion("6.42").build();
        tester.provisioner().prepare(applicationId, cluster, Capacity.from(new ClusterResources(2, 1, nodeResources)), null);
        assertEquals(2, nodeRepository.nodes().list(Node.State.reserved).nodeType(NodeType.tenant).size());

        // Reservation times out
        clock.advance(Duration.ofMinutes(14)); // Reserved but not used time out
        new ReservationExpirer(nodeRepository, Duration.ofMinutes(10), metric).run();

        // Assert nothing is reserved
        assertEquals(0, nodeRepository.nodes().list(Node.State.reserved).nodeType(NodeType.tenant).size());
        NodeList dirty = nodeRepository.nodes().list(Node.State.dirty).nodeType(NodeType.tenant);
        assertEquals(2, dirty.size());
        assertEquals(2, metric.values.get("expired.reserved"));
    }

}
