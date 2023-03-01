// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.ClusterInfo;
import com.yahoo.config.provision.IntRange;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.applications.Cluster;
import com.yahoo.vespa.hosted.provision.applications.ScalingEvent;
import com.yahoo.vespa.hosted.provision.autoscale.ClusterModel;
import com.yahoo.vespa.hosted.provision.autoscale.Load;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.History;
import com.yahoo.vespa.hosted.provision.testutils.MockDeployer;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests the autoscaling maintainer integration.
 * The specific recommendations of the autoscaler are not tested here.
 *
 * @author bratseth
 */
public class AutoscalingMaintainerTest {

    @Test
    public void test_autoscaling_maintainer() {
        ApplicationId app1 = AutoscalingMaintainerTester.makeApplicationId("app1");
        ClusterSpec cluster1 = AutoscalingMaintainerTester.containerClusterSpec();

        ApplicationId app2 = AutoscalingMaintainerTester.makeApplicationId("app2");
        ClusterSpec cluster2 = AutoscalingMaintainerTester.containerClusterSpec();

        NodeResources lowResources = new NodeResources(4, 4, 10, 0.1);
        NodeResources highResources = new NodeResources(6.5, 9, 20, 0.1);

        AutoscalingMaintainerTester tester = new AutoscalingMaintainerTester(
                new MockDeployer.ApplicationContext(app1, cluster1, Capacity.from(new ClusterResources(2, 1, lowResources))),
                new MockDeployer.ApplicationContext(app2, cluster2, Capacity.from(new ClusterResources(2, 1, highResources))));

        tester.maintainer().maintain(); // noop
        assertTrue(tester.deployer().lastDeployTime(app1).isEmpty());
        assertTrue(tester.deployer().lastDeployTime(app2).isEmpty());

        tester.deploy(app1, cluster1, Capacity.from(new ClusterResources(5, 1, new NodeResources(4, 4, 10, 0.1)),
                                                    new ClusterResources(5, 1, new NodeResources(4, 4, 10, 0.1)),
                                                    IntRange.empty(), false, true, Optional.empty(), ClusterInfo.empty()));
        tester.deploy(app2, cluster2, Capacity.from(new ClusterResources(5, 1, new NodeResources(4, 4, 10, 0.1)),
                                                    new ClusterResources(10, 1, new NodeResources(6.5, 9, 20, 0.1)),
                                                    IntRange.empty(), false, true, Optional.empty(), ClusterInfo.empty()));

        tester.clock().advance(Duration.ofMinutes(10));
        tester.maintainer().maintain(); // noop
        assertTrue(tester.deployer().lastDeployTime(app1).isEmpty());
        assertTrue(tester.deployer().lastDeployTime(app2).isEmpty());

        tester.addMeasurements(0.9f, 0.9f, 0.9f, 0, 500, app1, cluster1.id());
        tester.addMeasurements(0.9f, 0.9f, 0.9f, 0, 500, app2, cluster2.id());

        tester.clock().advance(Duration.ofMinutes(10));
        tester.maintainer().maintain();
        assertTrue(tester.deployer().lastDeployTime(app1).isEmpty()); // since autoscaling is off
        assertTrue(tester.deployer().lastDeployTime(app2).isPresent());
        Load peakAt90 = tester.nodeRepository().applications().require(app1).cluster(cluster1.id()).get().target().peak();
        Load idealAt90 = tester.nodeRepository().applications().require(app1).cluster(cluster1.id()).get().target().ideal();
        assertNotEquals(Load.zero(), peakAt90);
        assertNotEquals(Load.zero(), idealAt90);

        // Verify that load is updated even when there's no other change
        tester.clock().advance(Duration.ofMinutes(10));
        tester.addMeasurements(0.8f, 0.8f, 0.8f, 0, 500, app1, cluster1.id());
        tester.maintainer().maintain();

        Load peakAt80 = tester.nodeRepository().applications().require(app1).cluster(cluster1.id()).get().target().peak();
        Load idealAt80 = tester.nodeRepository().applications().require(app1).cluster(cluster1.id()).get().target().ideal();
        assertNotEquals(peakAt90, peakAt80);
        assertNotEquals(idealAt90, idealAt80);
    }

    @Test
    public void autoscaling_discards_metric_values_from_before_rescaling() {
        ApplicationId app1 = AutoscalingMaintainerTester.makeApplicationId("app1");
        ClusterSpec cluster1 = AutoscalingMaintainerTester.containerClusterSpec();
        NodeResources lowResources = new NodeResources(4, 4, 10, 0.1);
        NodeResources highResources = new NodeResources(8, 8, 20, 0.1);
        Capacity app1Capacity = Capacity.from(new ClusterResources(2, 1, lowResources),
                                              new ClusterResources(4, 2, highResources));
        var tester = new AutoscalingMaintainerTester(new MockDeployer.ApplicationContext(app1, cluster1, app1Capacity));

        // Initial deployment at time 0
        tester.deploy(app1, cluster1, app1Capacity);

        // Measure overload
        tester.addMeasurements(0.9f, 0.9f, 0.9f, 0, 500, app1, cluster1.id());

        // Causes autoscaling
        tester.clock().advance(Duration.ofMinutes(10));
        Instant firstMaintenanceTime = tester.clock().instant();
        tester.maintainer().maintain();
        assertTrue(tester.deployer().lastDeployTime(app1).isPresent());
        assertEquals(firstMaintenanceTime.toEpochMilli(), tester.deployer().lastDeployTime(app1).get().toEpochMilli());
        List<ScalingEvent> events = tester.nodeRepository().applications().get(app1).get().cluster(cluster1.id()).get().scalingEvents();
        assertEquals(2, events.size());
        assertEquals(Optional.of(firstMaintenanceTime), events.get(0).completion());
        assertEquals(2, events.get(1).from().nodes());
        assertEquals(4, events.get(1).to().nodes());
        assertEquals(1, events.get(1).generation());
        assertEquals(firstMaintenanceTime.toEpochMilli(), events.get(1).at().toEpochMilli());

        // Measure overload still, since change is not applied, but metrics are discarded
        tester.addMeasurements(0.9f, 0.9f, 0.9f, 0, 500, app1, cluster1.id());
        tester.maintainer().maintain();
        assertEquals(firstMaintenanceTime.toEpochMilli(), tester.deployer().lastDeployTime(app1).get().toEpochMilli());

        // Measure underload, but no autoscaling since we still haven't measured we're on the new config generation
        tester.addMeasurements(0.1f, 0.1f, 0.1f, 0, 500, app1, cluster1.id());
        tester.maintainer().maintain();
        assertEquals(firstMaintenanceTime.toEpochMilli(), tester.deployer().lastDeployTime(app1).get().toEpochMilli());

        // Add measurement of the expected generation, leading to rescaling
        // - record scaling completion
        tester.clock().advance(Duration.ofMinutes(5));
        tester.addMeasurements(0.1f, 0.1f, 0.1f, 1, 1, app1, cluster1.id());
        tester.maintainer().maintain();
        assertEquals(firstMaintenanceTime.toEpochMilli(), tester.deployer().lastDeployTime(app1).get().toEpochMilli());
        // - measure underload
        tester.clock().advance(Duration.ofDays(4)); // Exit cooling period
        tester.addMeasurements(0.1f, 0.1f, 0.1f, 1, 500, app1, cluster1.id());
        Instant lastMaintenanceTime = tester.clock().instant();
        tester.maintainer().maintain();
        assertEquals(lastMaintenanceTime.toEpochMilli(), tester.deployer().lastDeployTime(app1).get().toEpochMilli());
        events = tester.nodeRepository().applications().get(app1).get().cluster(cluster1.id()).get().scalingEvents();
        assertEquals(2, events.get(2).generation());
    }

    @Test
    public void test_toString() {
        assertEquals("4 nodes with [vcpu: 1.0, memory: 2.0 Gb, disk 4.0 Gb, bandwidth: 1.0 Gbps, architecture: x86_64] (total: [vcpu: 4.0, memory: 8.0 Gb, disk 16.0 Gb, bandwidth: 4.0 Gbps, architecture: x86_64])",
                AutoscalingMaintainer.toString(new ClusterResources(4, 1, new NodeResources(1, 2, 4, 1))));

        assertEquals("4 nodes (in 2 groups) with [vcpu: 1.0, memory: 2.0 Gb, disk 4.0 Gb, bandwidth: 1.0 Gbps, architecture: x86_64] (total: [vcpu: 4.0, memory: 8.0 Gb, disk 16.0 Gb, bandwidth: 4.0 Gbps, architecture: x86_64])",
                AutoscalingMaintainer.toString(new ClusterResources(4, 2, new NodeResources(1, 2, 4, 1))));
    }

    @Test
    public void test_scaling_event_recording() {
        ApplicationId app1 = AutoscalingMaintainerTester.makeApplicationId("app1");
        ClusterSpec cluster1 = AutoscalingMaintainerTester.containerClusterSpec();
        NodeResources lowResources = new NodeResources(4, 4, 10, 0.1);
        NodeResources highResources = new NodeResources(8, 8, 20, 0.1);
        Capacity app1Capacity = Capacity.from(new ClusterResources(2, 1, lowResources),
                                              new ClusterResources(4, 2, highResources));
        var tester = new AutoscalingMaintainerTester(new MockDeployer.ApplicationContext(app1, cluster1, app1Capacity));

        // deploy
        tester.deploy(app1, cluster1, app1Capacity);

        int measurements = 6;
        Duration samplePeriod = Duration.ofSeconds(150);
        for (int i = 0; i < 20; i++) {
            // Record completion to keep scaling window at minimum
            tester.addMeasurements(0.1f, 0.1f, 0.1f, i, 1, app1, cluster1.id());
            tester.maintainer().maintain();

            tester.clock().advance(Duration.ofDays(1));

            if (i % 2 == 0) { // high load
                tester.addMeasurements(0.99f, 0.99f, 0.99f, i, measurements, app1, cluster1.id());
            }
            else { // low load
                tester.addMeasurements(0.2f, 0.2f, 0.2f, i, measurements, app1, cluster1.id());
            }
            tester.clock().advance(samplePeriod.negated().multipliedBy(measurements));
            tester.addQueryRateMeasurements(app1, cluster1.id(), measurements, t -> (t == 0 ? 20.0 : 10.0 ));
            tester.maintainer().maintain();
        }

        assertEquals(Cluster.maxScalingEvents, tester.cluster(app1, cluster1).scalingEvents().size());

        // Complete last event
        tester.addMeasurements(0.1f, 0.1f, 0.1f, 20, 1, app1, cluster1.id());
        tester.maintainer().maintain();
        assertEquals("Last event is completed",
                     tester.clock().instant(),
                     tester.cluster(app1, cluster1).scalingEvents().get(Cluster.maxScalingEvents - 1).completion().get());
    }

    @Test
    public void test_autoscaling_window() {
        ApplicationId app1 = AutoscalingMaintainerTester.makeApplicationId("app1");
        ClusterSpec cluster1 = AutoscalingMaintainerTester.containerClusterSpec();
        NodeResources lowResources = new NodeResources(4, 4, 10, 0.1);
        NodeResources highResources = new NodeResources(8, 8, 20, 0.1);
        Capacity app1Capacity = Capacity.from(new ClusterResources(2, 1, lowResources),
                                              new ClusterResources(4, 2, highResources));
        var tester = new AutoscalingMaintainerTester(new MockDeployer.ApplicationContext(app1, cluster1, app1Capacity));
        ManualClock clock = tester.clock();

        tester.deploy(app1, cluster1, app1Capacity);

        autoscale(false, Duration.ofMinutes( 1), Duration.ofMinutes( 5), clock, app1, cluster1, tester);
        autoscale( true, Duration.ofMinutes(19), Duration.ofMinutes(10), clock, app1, cluster1, tester);
    }

    @Test
    public void test_autoscaling_ignores_measurements_during_warmup() {
        ApplicationId app1 = AutoscalingMaintainerTester.makeApplicationId("app1");
        ClusterSpec cluster1 = AutoscalingMaintainerTester.containerClusterSpec();
        NodeResources resources = new NodeResources(4, 4, 10, 1);
        ClusterResources min = new ClusterResources(2, 1, resources);
        ClusterResources max = new ClusterResources(20, 1, resources);
        var capacity = Capacity.from(min, max);
        var tester = new AutoscalingMaintainerTester(new MockDeployer.ApplicationContext(app1, cluster1, capacity));

        // Add a scaling event
        tester.deploy(app1, cluster1, capacity);
        tester.addMeasurements(1.0f, 0.3f, 0.3f, 0, 4, app1, cluster1.id());
        tester.maintainer().maintain();
        assertEquals("Scale up: " + tester.cluster(app1, cluster1).target().status(),
                     1,
                     tester.cluster(app1, cluster1).lastScalingEvent().get().generation());

        // measurements with outdated generation are ignored -> no autoscaling
        var duration = tester.addMeasurements(3.0f, 0.3f, 0.3f, 0, 2, app1, cluster1.id());
        tester.maintainer().maintain();
        assertEquals("Measurements with outdated generation are ignored -> no autoscaling",
                     1,
                     tester.cluster(app1, cluster1).lastScalingEvent().get().generation());
        tester.clock().advance(duration.negated());

        duration = tester.addMeasurements(3.0f, 0.3f, 0.3f, 1, 2, app1, cluster1.id());
        tester.maintainer().maintain();
        assertEquals("Measurements right after generation change are ignored -> no autoscaling",
                     1,
                     tester.cluster(app1, cluster1).lastScalingEvent().get().generation());
        tester.clock().advance(duration.negated());

        // Add a restart event
        tester.clock().advance(ClusterModel.warmupDuration.plus(Duration.ofMinutes(1)));
        tester.nodeRepository().nodes().list().owner(app1).asList().forEach(node -> recordRestart(node, tester.nodeRepository()));

        duration = tester.addMeasurements(3.0f, 0.3f, 0.3f, 1, 2, app1, cluster1.id());
        tester.maintainer().maintain();
        assertEquals("Measurements right after restart are ignored -> no autoscaling",
                     1,
                     tester.cluster(app1, cluster1).lastScalingEvent().get().generation());
        tester.clock().advance(duration.negated());

        tester.clock().advance(ClusterModel.warmupDuration.plus(Duration.ofMinutes(1)));
        tester.addMeasurements(3.0f, 0.3f, 0.3f, 1, 2, app1, cluster1.id());
        tester.maintainer().maintain();
        assertEquals("We have valid measurements -> scale up",
                     2,
                     tester.cluster(app1, cluster1).lastScalingEvent().get().generation());
    }

    @Test
    public void test_cd_autoscaling_test() {
        ApplicationId app1 = AutoscalingMaintainerTester.makeApplicationId("app1");
        ClusterSpec cluster1 = AutoscalingMaintainerTester.containerClusterSpec();
        NodeResources resources = new NodeResources(1, 4, 50, 1);
        ClusterResources min = new ClusterResources( 2, 1, resources);
        ClusterResources max = new ClusterResources(3, 1, resources);
        var capacity = Capacity.from(min, max);
        var tester = new AutoscalingMaintainerTester(new Zone(SystemName.cd, Environment.prod, RegionName.from("us-east3")),
                                                     new MockDeployer.ApplicationContext(app1, cluster1, capacity));
        ManualClock clock = tester.clock();

        tester.deploy(app1, cluster1, capacity);
        assertEquals(2,
                     tester.nodeRepository().nodes().list(Node.State.active)
                           .owner(app1)
                           .cluster(cluster1.id())
                           .size());

        autoscale(false, Duration.ofMinutes( 1), Duration.ofMinutes( 5), clock, app1, cluster1, tester);
        assertEquals(3,
                     tester.nodeRepository().nodes().list(Node.State.active)
                           .owner(app1)
                           .cluster(cluster1.id())
                           .size());
    }

    @Test
    public void test_cd_test_not_specifying_node_resources() {
        ApplicationId app1 = AutoscalingMaintainerTester.makeApplicationId("app1");
        ClusterSpec cluster1 = AutoscalingMaintainerTester.containerClusterSpec();
        ClusterResources resources = new ClusterResources( 2, 1, NodeResources.unspecified());
        var capacity = Capacity.from(resources);
        var tester = new AutoscalingMaintainerTester(new Zone(SystemName.cd, Environment.prod, RegionName.from("us-east3")),
                                                     new MockDeployer.ApplicationContext(app1, cluster1, capacity));
        tester.deploy(app1, cluster1, capacity); // Deploy should succeed and allocate the nodes
        assertEquals(2,
                     tester.nodeRepository().nodes().list(Node.State.active)
                           .owner(app1)
                           .cluster(cluster1.id())
                           .size());
    }

    @Test
    public void empty_autoscaling_is_ignored() {
        ApplicationId app1 = AutoscalingMaintainerTester.makeApplicationId("app1");
        ClusterSpec cluster1 = AutoscalingMaintainerTester.containerClusterSpec();
        NodeResources resources = new NodeResources(4, 4, 10, 1);
        ClusterResources min = new ClusterResources(2, 1, resources);
        ClusterResources max = new ClusterResources(20, 1, resources);
        var capacity = Capacity.from(min, max);
        var tester = new AutoscalingMaintainerTester(new MockDeployer.ApplicationContext(app1, cluster1, capacity));

        // Add a scaling event
        tester.deploy(app1, cluster1, capacity);
        tester.addMeasurements(1.0f, 0.3f, 0.3f, 0, 4, app1, cluster1.id());
        tester.maintainer().maintain();
        assertEquals("Scale up: " + tester.cluster(app1, cluster1).target().status(),
                     1,
                     tester.cluster(app1, cluster1).lastScalingEvent().get().generation());
        Load peak = tester.cluster(app1, cluster1).target().peak();
        assertNotEquals(Load.zero(), peak);

        // Old measurements go out of scope and no new ones are made
        tester.clock().advance(Duration.ofDays(1));
        tester.maintainer().maintain();
        Load newPeak = tester.cluster(app1, cluster1).target().peak();
        assertEquals("Old measurements are retained", peak, newPeak);
    }

    private void autoscale(boolean down, Duration completionTime, Duration expectedWindow,
                           ManualClock clock, ApplicationId application, ClusterSpec cluster,
                           AutoscalingMaintainerTester tester) {
        long generation = tester.cluster(application, cluster).lastScalingEvent().get().generation();
        tester.maintainer().maintain();
        assertFalse("Not measured to be on the last generation yet",
                    tester.cluster(application, cluster).lastScalingEvent().get().completion().isPresent());

        clock.advance(completionTime);
        float load = down ? 0.1f : 1.0f;
        tester.addMeasurements(load, load, load, generation, 1, application, cluster.id());
        tester.maintainer().maintain();
        assertEvent("Measured completion of the last scaling event, but no new autoscaling yet",
                    generation, Optional.of(clock.instant()),
                    tester.cluster(application, cluster).lastScalingEvent().get());
        if (down)
            clock.advance(expectedWindow.minus(completionTime).plus(expectedWindow.multipliedBy(2)));
        else
            clock.advance(expectedWindow.minus(completionTime));

        tester.addMeasurements(load, load, load, generation, 200, application, cluster.id());
        tester.maintainer().maintain();
        assertEquals("We passed window duration so a new autoscaling is started: " +
                     tester.cluster(application, cluster).target().status(),
                     generation + 1,
                     tester.cluster(application, cluster).lastScalingEvent().get().generation());
    }

    private void recordRestart(Node node, NodeRepository nodeRepository) {
        var upEvent = new History.Event(History.Event.Type.up, Agent.system, nodeRepository.clock().instant());
        try (var locked = nodeRepository.nodes().lockAndGetRequired(node)) {
            nodeRepository.nodes().write(locked.node().with(locked.node().history().with(upEvent)), locked);
        }
    }

    private void assertEvent(String explanation,
                             long expectedGeneration, Optional<Instant> expectedCompletion, ScalingEvent event) {
        assertEquals(explanation + ". Generation", expectedGeneration, event.generation());
        assertEquals(explanation + ". Generation " + expectedGeneration + ". Completion",
                     expectedCompletion, event.completion());
    }



}
