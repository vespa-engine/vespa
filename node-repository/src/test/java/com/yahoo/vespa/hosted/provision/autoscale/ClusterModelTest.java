// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.hosted.provision.applications.Application;
import com.yahoo.vespa.hosted.provision.applications.AutoscalingStatus;
import com.yahoo.vespa.hosted.provision.applications.Cluster;
import com.yahoo.vespa.hosted.provision.applications.Status;
import org.junit.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.IntFunction;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class ClusterModelTest {

    private static final double delta = 0.001;

    @Test
    public void test_traffic_headroom() {
        ManualClock clock = new ManualClock();
        Application application = Application.empty(ApplicationId.from("t1", "a1", "i1"));
        Cluster cluster = cluster(new NodeResources(1, 10, 100, 1));
        application = application.with(cluster);

        // No current traffic share: Ideal load is low but capped
        var model1 = new ClusterModel(application.with(new Status(0.0, 1.0)),
                                      cluster, clock, Duration.ofMinutes(10),
                                      timeseries(cluster,100, t -> t == 0 ? 10000.0 : 0.0, t -> 0.0, clock),
                                      ClusterNodesTimeseries.empty());
        assertEquals(0.131, model1.idealLoad().cpu(), delta);

        // Almost no current traffic share: Ideal load is low but capped
        var model2 = new ClusterModel(application.with(new Status(0.0001, 1.0)),
                                      cluster, clock, Duration.ofMinutes(10),
                                      timeseries(cluster,100, t -> t == 0 ? 10000.0 : 0.0, t -> 0.0, clock),
                                      ClusterNodesTimeseries.empty());
        assertEquals(0.131, model2.idealLoad().cpu(), delta);
    }

    @Test
    public void test_growth_headroom() {
        ManualClock clock = new ManualClock();

        Application application = Application.empty(ApplicationId.from("t1", "a1", "i1"));
        Cluster cluster = cluster(new NodeResources(1, 10, 100, 1));
        application = application.with(cluster);

        // No current traffic: Ideal load is low but capped
        var model1 = new ClusterModel(application,
                                      cluster, clock, Duration.ofMinutes(10),
                                      timeseries(cluster,100, t -> t == 0 ? 10000.0 : 0.0, t -> 0.0, clock),
                                      ClusterNodesTimeseries.empty());
        assertEquals(0.275, model1.idealLoad().cpu(), delta);

        // Almost no current traffic: Ideal load is low but capped
        var model2 = new ClusterModel(application.with(new Status(0.0001, 1.0)),
                                      cluster, clock, Duration.ofMinutes(10),
                                      timeseries(cluster,100, t -> t == 0 ? 10000.0 : 0.0001, t -> 0.0, clock),
                                      ClusterNodesTimeseries.empty());
        assertEquals(0.040, model2.idealLoad().cpu(), delta);
    }

    private Cluster cluster(NodeResources resources) {
        return new Cluster(ClusterSpec.Id.from("test"),
                          false,
                           new ClusterResources(5, 1, resources),
                           new ClusterResources(5, 1, resources),
                           Optional.empty(),
                           Optional.empty(),
                           List.of(),
                           AutoscalingStatus.empty());
    }

    /** Creates the given number of measurements, spaced 5 minutes between, using the given function */
    private ClusterTimeseries timeseries(Cluster cluster,
                                         int measurements,
                                         IntFunction<Double> queryRate,
                                         IntFunction<Double> writeRate,
                                         ManualClock clock) {
        List<ClusterMetricSnapshot> snapshots = new ArrayList<>(measurements);
        for (int i = 0; i < measurements; i++) {
            snapshots.add(new ClusterMetricSnapshot(clock.instant(), queryRate.apply(i), writeRate.apply(i)));
            clock.advance(Duration.ofMinutes(5));
        }
        return new ClusterTimeseries(cluster.id(),snapshots);
    }

}
