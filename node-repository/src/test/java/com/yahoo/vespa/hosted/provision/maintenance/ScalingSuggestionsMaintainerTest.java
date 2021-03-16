// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.collections.Pair;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Zone;
import com.yahoo.config.provisioning.FlavorsConfig;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.applications.Cluster;
import com.yahoo.vespa.hosted.provision.autoscale.NodeMetricSnapshot;
import com.yahoo.vespa.hosted.provision.autoscale.MetricsDb;
import com.yahoo.vespa.hosted.provision.autoscale.Resource;
import com.yahoo.vespa.hosted.provision.provisioning.FlavorConfigBuilder;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisioningTester;
import org.junit.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests the scaling suggestions maintainer integration.
 * The specific suggestions are not tested here.
 *
 * @author bratseth
 */
public class ScalingSuggestionsMaintainerTest {

    @Test
    public void testScalingSuggestionsMaintainer() {
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east3"))).flavorsConfig(flavorsConfig()).build();

        ApplicationId app1 = ProvisioningTester.applicationId("app1");
        ClusterSpec cluster1 = ProvisioningTester.containerClusterSpec();

        ApplicationId app2 = ProvisioningTester.applicationId("app2");
        ClusterSpec cluster2 = ProvisioningTester.contentClusterSpec();

        MetricsDb metricsDb = MetricsDb.createTestInstance(tester.nodeRepository());

        tester.makeReadyNodes(20, "flt", NodeType.host, 8);
        tester.activateTenantHosts();

        tester.deploy(app1, cluster1, Capacity.from(new ClusterResources(5, 1, new NodeResources(4, 4, 10, 0.1)),
                                                    new ClusterResources(5, 1, new NodeResources(4, 4, 10, 0.1)),
                                                    false, true));
        tester.deploy(app2, cluster2, Capacity.from(new ClusterResources(5, 1, new NodeResources(4, 4, 10, 0.1)),
                                                    new ClusterResources(10, 1, new NodeResources(6.5, 5, 15, 0.1)),
                                                    false, true));

        tester.clock().advance(Duration.ofHours(13));
        addMeasurements(0.90f, 0.90f, 0.90f, 0, 500, app1, tester.nodeRepository(), metricsDb);
        addMeasurements(0.99f, 0.99f, 0.99f, 0, 500, app2, tester.nodeRepository(), metricsDb);

        ScalingSuggestionsMaintainer maintainer = new ScalingSuggestionsMaintainer(tester.nodeRepository(),
                                                                                   metricsDb,
                                                                                   Duration.ofMinutes(1),
                                                                                   new TestMetric());
        maintainer.maintain();

        assertEquals("12 nodes with [vcpu: 6.0, memory: 5.1 Gb, disk 15.0 Gb, bandwidth: 0.1 Gbps]",
                     suggestionOf(app1, cluster1, tester).get().resources().toString());
        assertEquals("8 nodes with [vcpu: 11.0, memory: 4.0 Gb, disk 11.8 Gb, bandwidth: 0.1 Gbps]",
                     suggestionOf(app2, cluster2, tester).get().resources().toString());

        // Utilization goes way down
        tester.clock().advance(Duration.ofHours(13));
        addMeasurements(0.10f, 0.10f, 0.10f, 0, 500, app1, tester.nodeRepository(), metricsDb);
        maintainer.maintain();
        assertEquals("Suggestion stays at the peak value observed",
                     "12 nodes with [vcpu: 6.0, memory: 5.1 Gb, disk 15.0 Gb, bandwidth: 0.1 Gbps]",
                     suggestionOf(app1, cluster1, tester).get().resources().toString());
        // Utilization is still way down and a week has passed
        tester.clock().advance(Duration.ofDays(7));
        addMeasurements(0.10f, 0.10f, 0.10f, 0, 500, app1, tester.nodeRepository(), metricsDb);
        maintainer.maintain();
        assertEquals("Peak suggestion has been  outdated",
                     "5 nodes with [vcpu: 1.8, memory: 4.0 Gb, disk 10.0 Gb, bandwidth: 0.1 Gbps]",
                     suggestionOf(app1, cluster1, tester).get().resources().toString());
        assertTrue(shouldSuggest(app1, cluster1, tester));

        tester.clock().advance(Duration.ofDays(3));
        addMeasurements(0.7f, 0.7f, 0.7f, 0, 500, app1, tester.nodeRepository(), metricsDb);
        maintainer.maintain();
        var suggested = tester.nodeRepository().applications().get(app1).get().cluster(cluster1.id()).get().suggestedResources().get().resources();
        tester.deploy(app1, cluster1, Capacity.from(suggested, suggested, false, true));
        tester.clock().advance(Duration.ofDays(2));
        addMeasurements(0.2f,
                        (float)Resource.memory.idealAverageLoad(),
                        (float)Resource.disk.idealAverageLoad(),
                        0, 500, app1, tester.nodeRepository(), metricsDb);
        maintainer.maintain();
        assertEquals("Suggestion is to keep the current allocation",
                     suggested,
                     suggestionOf(app1, cluster1, tester).get().resources());
        assertFalse("Suggestion is not made as it matches what we have", shouldSuggest(app1, cluster1, tester));
    }

    private Optional<Cluster.Suggestion> suggestionOf(ApplicationId app, ClusterSpec cluster, ProvisioningTester tester) {
        return tester.nodeRepository().applications().get(app).get().cluster(cluster.id()).get().suggestedResources();
    }

    private boolean shouldSuggest(ApplicationId app, ClusterSpec cluster, ProvisioningTester tester) {
        var currentResources = tester.nodeRepository().nodes().list().owner(app).cluster(cluster.id()).not().retired().toResources();
        return tester.nodeRepository().applications().get(app).get().cluster(cluster.id()).get()
                     .shouldSuggestResources(currentResources);
    }

    public void addMeasurements(float cpu, float memory, float disk, int generation, int count, ApplicationId applicationId,
                                NodeRepository nodeRepository, MetricsDb db) {
        NodeList nodes = nodeRepository.nodes().list(Node.State.active).owner(applicationId);
        for (int i = 0; i < count; i++) {
            for (Node node : nodes)
                db.addNodeMetrics(List.of(new Pair<>(node.hostname(), new NodeMetricSnapshot(nodeRepository.clock().instant(),
                                                                                             cpu,
                                                                                             memory,
                                                                                             disk,
                                                                                             generation,
                                                                                             true,
                                                                                             true,
                                                                                             0.0))));
        }
    }

    private FlavorsConfig flavorsConfig() {
        FlavorConfigBuilder b = new FlavorConfigBuilder();
        b.addFlavor("flt", 30, 30, 40, 3, Flavor.Type.BARE_METAL);
        b.addFlavor("cpu", 40, 20, 40, 3, Flavor.Type.BARE_METAL);
        b.addFlavor("mem", 20, 40, 40, 3, Flavor.Type.BARE_METAL);
        return b.build();
    }

}
