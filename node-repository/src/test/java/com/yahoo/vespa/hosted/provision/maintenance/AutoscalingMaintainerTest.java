// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.hosted.provision.applications.Cluster;
import com.yahoo.vespa.hosted.provision.applications.ScalingEvent;
import com.yahoo.vespa.hosted.provision.testutils.MockDeployer;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
                                                    false, true));
        tester.deploy(app2, cluster2, Capacity.from(new ClusterResources(5, 1, new NodeResources(4, 4, 10, 0.1)),
                                                    new ClusterResources(10, 1, new NodeResources(6.5, 9, 20, 0.1)),
                                                    false, true));

        tester.clock().advance(Duration.ofMinutes(10));
        tester.maintainer().maintain(); // noop
        assertTrue(tester.deployer().lastDeployTime(app1).isEmpty());
        assertTrue(tester.deployer().lastDeployTime(app2).isEmpty());

        tester.addMeasurements(0.9f, 0.9f, 0.9f, 0, 500, app1);
        tester.addMeasurements(0.9f, 0.9f, 0.9f, 0, 500, app2);

        tester.clock().advance(Duration.ofMinutes(10));
        tester.maintainer().maintain();
        assertTrue(tester.deployer().lastDeployTime(app1).isEmpty()); // since autoscaling is off
        assertTrue(tester.deployer().lastDeployTime(app2).isPresent());
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
        tester.clock().advance(Duration.ofSeconds(1));
        tester.addMeasurements(0.9f, 0.9f, 0.9f, 0, 500, app1);

        // Causes autoscaling
        tester.clock().advance(Duration.ofSeconds(1));
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
        tester.clock().advance(Duration.ofSeconds(1));
        tester.addMeasurements(0.9f, 0.9f, 0.9f, 0, 500, app1);
        tester.clock().advance(Duration.ofSeconds(1));
        tester.maintainer().maintain();
        assertEquals(firstMaintenanceTime.toEpochMilli(), tester.deployer().lastDeployTime(app1).get().toEpochMilli());

        // Measure underload, but no autoscaling since we still haven't measured we're on the new config generation
        tester.clock().advance(Duration.ofSeconds(1));
        tester.addMeasurements(0.1f, 0.1f, 0.1f, 0, 500, app1);
        tester.clock().advance(Duration.ofSeconds(1));
        tester.maintainer().maintain();
        assertEquals(firstMaintenanceTime.toEpochMilli(), tester.deployer().lastDeployTime(app1).get().toEpochMilli());

        // Add measurement of the expected generation, leading to rescaling
        // - record scaling completion
        tester.clock().advance(Duration.ofMinutes(5));
        tester.addMeasurements(0.1f, 0.1f, 0.1f, 1, 1, app1);
        tester.maintainer().maintain();
        // - measure underload
        tester.clock().advance(Duration.ofHours(1));
        tester.addMeasurements(0.1f, 0.1f, 0.1f, 1, 500, app1);
        Instant lastMaintenanceTime = tester.clock().instant();
        tester.maintainer().maintain();
        assertEquals(lastMaintenanceTime.toEpochMilli(), tester.deployer().lastDeployTime(app1).get().toEpochMilli());
        events = tester.nodeRepository().applications().get(app1).get().cluster(cluster1.id()).get().scalingEvents();
        assertEquals(2, events.get(2).generation());
    }

    @Test
    public void test_toString() {
        assertEquals("4 nodes with [vcpu: 1.0, memory: 2.0 Gb, disk 4.0 Gb, bandwidth: 1.0 Gbps] (total: [vcpu: 4.0, memory: 8.0 Gb, disk 16.0 Gb, bandwidth: 4.0 Gbps])",
                AutoscalingMaintainer.toString(new ClusterResources(4, 1, new NodeResources(1, 2, 4, 1))));

        assertEquals("4 nodes (in 2 groups) with [vcpu: 1.0, memory: 2.0 Gb, disk 4.0 Gb, bandwidth: 1.0 Gbps] (total: [vcpu: 4.0, memory: 8.0 Gb, disk 16.0 Gb, bandwidth: 4.0 Gbps])",
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
        tester.addQueryRateMeasurements(app1, cluster1.id(), 12, t -> t == 0 ? 20.0 : 10.0);

        for (int i = 0; i < 20; i++) {
            // Record completion to keep scaling window at minimum
            tester.addMeasurements(0.1f, 0.1f, 0.1f, i, 1, app1);
            tester.maintainer().maintain();

            tester.clock().advance(Duration.ofDays(1));

            if (i % 2 == 0) // high load
                tester.addMeasurements(0.9f, 0.9f, 0.9f, i, 200, app1);
            else // low load
                tester.addMeasurements(0.1f, 0.1f, 0.1f, i, 200, app1);
            tester.maintainer().maintain();
        }

        assertEquals(Cluster.maxScalingEvents, tester.cluster(app1, cluster1).scalingEvents().size());
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

        // deploy
        tester.deploy(app1, cluster1, app1Capacity);

        autoscale(false, Duration.ofMinutes( 1), Duration.ofMinutes( 5), clock, app1, cluster1, tester);
        autoscale( true, Duration.ofMinutes(19), Duration.ofMinutes(10), clock, app1, cluster1, tester);
        autoscale( true, Duration.ofMinutes(40), Duration.ofMinutes(20), clock, app1, cluster1, tester);
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
        tester.addMeasurements(load, load, load, generation, 200, application);
        tester.maintainer().maintain();
        assertEvent("Measured completion of the last scaling event, but no new autoscaling yet",
                    generation, Optional.of(clock.instant()),
                    tester.cluster(application, cluster).lastScalingEvent().get());
        if (down)
            clock.advance(expectedWindow.minus(completionTime).plus(expectedWindow.multipliedBy(2)));
        else
            clock.advance(expectedWindow.minus(completionTime));

        tester.addMeasurements(load, load, load, generation, 200, application);
        tester.maintainer().maintain();
        assertEquals("We passed window duration so a new autoscaling is started: " +
                     tester.cluster(application, cluster).autoscalingStatus(),
                     generation + 1,
                     tester.cluster(application, cluster).lastScalingEvent().get().generation());
    }

    private void assertEvent(String explanation,
                             long expectedGeneration, Optional<Instant> expectedCompletion, ScalingEvent event) {
        assertEquals(explanation + ". Generation", expectedGeneration, event.generation());
        assertEquals(explanation + ". Generation " + expectedGeneration + ". Completion",
                     expectedCompletion, event.completion());
    }



}
