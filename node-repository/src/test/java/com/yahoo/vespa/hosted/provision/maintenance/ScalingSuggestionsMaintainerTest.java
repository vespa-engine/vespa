// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.ClusterInfo;
import com.yahoo.config.provision.IntRange;
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
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.autoscale.Autoscaling;
import com.yahoo.vespa.hosted.provision.autoscale.Load;
import com.yahoo.vespa.hosted.provision.autoscale.NodeMetricSnapshot;
import com.yahoo.vespa.hosted.provision.provisioning.FlavorConfigBuilder;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisioningTester;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
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
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east3")))
                                                                    .flavorsConfig(flavorsConfig())
                                                                    .build();
        ApplicationId app1 = ProvisioningTester.applicationId("app1");
        ApplicationId app2 = ProvisioningTester.applicationId("app2");
        ClusterSpec cluster1 = ProvisioningTester.containerClusterSpec();
        ClusterSpec cluster2 = ProvisioningTester.contentClusterSpec();
        tester.makeReadyNodes(20, "flt", NodeType.host, 8);
        tester.activateTenantHosts();

        tester.deploy(app1, cluster1, Capacity.from(new ClusterResources(5, 1, new NodeResources(4, 4, 10, 0.1)),
                                                    new ClusterResources(5, 1, new NodeResources(4, 4, 10, 0.1)),
                                                    IntRange.empty(), false, true, Optional.empty(), ClusterInfo.empty()));
        storeCompletion(app1, cluster1.id(), tester.nodeRepository());
        tester.deploy(app2, cluster2, Capacity.from(new ClusterResources(5, 1, new NodeResources(4, 4, 10, 0.1)),
                                                    new ClusterResources(10, 1, new NodeResources(6.5, 5, 15, 0.1)),
                                                    IntRange.empty(), false, true, Optional.empty(), ClusterInfo.empty()));
        storeCompletion(app2, cluster2.id(), tester.nodeRepository());
        tester.clock().advance(Duration.ofHours(13));
        Duration timeAdded = addMeasurements(0.90f, 0.90f, 0.90f, 0, 500, app1, tester.nodeRepository());
        tester.clock().advance(timeAdded.negated());
        addMeasurements(0.99f, 0.99f, 0.99f, 0, 500, app2, tester.nodeRepository());

        ScalingSuggestionsMaintainer maintainer = new ScalingSuggestionsMaintainer(tester.nodeRepository(),
                                                                                   Duration.ofMinutes(1),
                                                                                   new TestMetric());
        maintainer.maintain();

        assertEquals("8 nodes with [vcpu: 3.2, memory: 4.5 Gb, disk: 10.0 Gb, bandwidth: 0.1 Gbps, architecture: any]",
                     suggestionOf(app1, cluster1, tester).resources().get().toString());
        assertEquals("8 nodes with [vcpu: 3.6, memory: 4.7 Gb, disk: 11.8 Gb, bandwidth: 0.1 Gbps, architecture: any]",
                     suggestionOf(app2, cluster2, tester).resources().get().toString());

        // Utilization goes way down
        tester.clock().advance(Duration.ofHours(13));
        addMeasurements(0.10f, 0.10f, 0.10f, 0, 500, app1, tester.nodeRepository());
        maintainer.maintain();
        assertEquals("Suggestion stays at the peak value observed",
                     "8 nodes with [vcpu: 3.2, memory: 4.5 Gb, disk: 10.0 Gb, bandwidth: 0.1 Gbps, architecture: any]",
                     suggestionOf(app1, cluster1, tester).resources().get().toString());
        // Utilization is still way down and a week has passed
        tester.clock().advance(Duration.ofDays(7));
        addMeasurements(0.10f, 0.10f, 0.10f, 0, 500, app1, tester.nodeRepository());
        maintainer.maintain();
        assertEquals("Peak suggestion has been  outdated",
                     "3 nodes with [vcpu: 1.2, memory: 4.0 Gb, disk: 10.0 Gb, bandwidth: 0.1 Gbps, architecture: any]",
                     suggestionOf(app1, cluster1, tester).resources().get().toString());
        assertTrue(shouldSuggest(app1, cluster1, tester));

        tester.clock().advance(Duration.ofDays(3));
        addMeasurements(0.7f, 0.7f, 0.7f, 0, 500, app1, tester.nodeRepository());
        maintainer.maintain();
        var suggested = tester.nodeRepository().applications().get(app1).get().cluster(cluster1.id()).get().suggested().resources().get();
        tester.deploy(app1, cluster1, Capacity.from(suggested, suggested,
                                                    IntRange.empty(), false, true, Optional.empty(), ClusterInfo.empty()));
        tester.clock().advance(Duration.ofDays(2));
        addMeasurements(0.2f, 0.65f, 0.6f,
                        0, 500, app1, tester.nodeRepository());
        maintainer.maintain();
        assertEquals("Suggestion is to keep the current allocation",
                     suggested,
                     suggestionOf(app1, cluster1, tester).resources().get());
        assertFalse("Suggestion is not made as it matches what we have", shouldSuggest(app1, cluster1, tester));
    }

    private void storeCompletion(ApplicationId appId, ClusterSpec.Id clusterId, NodeRepository nodeRepository) {
        try (var lock = nodeRepository.applications().lock(appId)) {
            var app = nodeRepository.applications().require(appId);
            var cluster = app.cluster(clusterId).get();
            cluster = cluster.with(cluster.lastScalingEvent().get().withCompletion(nodeRepository.clock().instant()));
            app = app.with(cluster);
            nodeRepository.applications().put(app, lock);
        }
    }

    private Autoscaling suggestionOf(ApplicationId app, ClusterSpec cluster, ProvisioningTester tester) {
        return tester.nodeRepository().applications().get(app).get().cluster(cluster.id()).get().suggested();
    }

    private boolean shouldSuggest(ApplicationId app, ClusterSpec cluster, ProvisioningTester tester) {
        var currentResources = tester.nodeRepository().nodes().list().owner(app).cluster(cluster.id()).not().retired().toResources();
        return tester.nodeRepository().applications().get(app).get().cluster(cluster.id()).get()
                     .shouldSuggestResources(currentResources);
    }

    public Duration addMeasurements(float cpu, float memory, float disk, int generation, int count,
                                    ApplicationId applicationId,
                                    NodeRepository nodeRepository) {
        NodeList nodes = nodeRepository.nodes().list(Node.State.active).owner(applicationId);
        Instant startTime = nodeRepository.clock().instant();
        for (int i = 0; i < count; i++) {
            for (Node node : nodes)
                nodeRepository.metricsDb().addNodeMetrics(List.of(new Pair<>(node.hostname(),
                                                                             new NodeMetricSnapshot(nodeRepository.clock().instant(),
                                                                                                    new Load(cpu, memory, disk),
                                                                                                    generation,
                                                                                                    true,
                                                                                                    true,
                                                                                                    0.0))));
            ((ManualClock)nodeRepository.clock()).advance(Duration.ofSeconds(150));
        }
        return Duration.between(startTime, nodeRepository.clock().instant());
    }

    private FlavorsConfig flavorsConfig() {
        FlavorConfigBuilder b = new FlavorConfigBuilder();
        b.addFlavor("flt", 30, 30, 40, 3, Flavor.Type.BARE_METAL);
        b.addFlavor("cpu", 40, 20, 40, 3, Flavor.Type.BARE_METAL);
        b.addFlavor("mem", 20, 40, 40, 3, Flavor.Type.BARE_METAL);
        return b.build();
    }

}
